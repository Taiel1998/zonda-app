#!/usr/bin/env python3
"""Aplica en el firewall los bloqueos que aprueban las personas en Zonda (decisión 51).

Corre en el firewall (fw-lab, Linux con nftables). Cada 10 segundos le pregunta al servidor qué tiene que
estar bloqueado (GET /firewall/bloqueos, con el token del firewall), lo valida por su cuenta y deja la tabla
de Zonda igual a eso: agrega lo que falta, saca lo que se desbloqueó y vuelve a poner lo que cambió de
duración. Después le cuenta al servidor qué quedó aplicado, qué rechazó y por qué, y su lista de «nunca
bloquear» (POST /firewall/informe).

El servidor no tiene ninguna clave del firewall: es el firewall el que pregunta, y no aplica nada sin
validarlo. Solo bloquea direcciones IPv4 de un equipo, nunca lo que está en su lista de «nunca bloquear»
(las puertas de enlace, la red de los servidores) ni la dirección del servidor de Zonda, por 24 horas como
mucho o hasta desbloquear, y 20 equipos a la vez como mucho.

Los bloqueos van en una tabla propia, inet zonda (zonda.nft), aparte de las reglas del firewall, que no se
tocan: un conjunto con los equipos bloqueados y el vencimiento de cada uno, y una cadena que descarta lo que
sale de esos equipos o va hacia ellos antes que las reglas del firewall, así corta también las conexiones ya
establecidas. El vencimiento lo cumple el núcleo, aunque el servidor esté apagado. Si alguien recarga las
reglas del firewall (nftables.conf empieza con flush ruleset) o cambia la tabla, el programa la arma de nuevo
y vuelve a poner los bloqueos.

Si el servidor no contesta, no toca nada: lo que vence, vence solo, y lo demás sigue hasta que vuelva. Solo
usa la biblioteca estándar de Python y tres órdenes fijas de nft (leer la tabla, armarla con zonda.nft y
cambiar sus equipos), sin shell. Corre con un usuario propio y un solo permiso del núcleo, CAP_NET_ADMIN
(zonda-firewall.service).

Uso en el firewall (lo arranca zonda-firewall.service):
    python3 agente_firewall.py --servidor http://10.0.2.2:8000 --token-archivo /etc/zonda-firewall/token \\
        --nunca-bloquear 192.168.20.0/24,192.168.30.1
Para ver qué haría, sin tocar el firewall ni avisarle al servidor:
    python3 agente_firewall.py ... --probar
"""

import argparse
import http.client
import ipaddress
import json
import os
import socket
import subprocess
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
from dataclasses import dataclass, field
from datetime import datetime
from pathlib import Path

TABLA = "zonda"
CONJUNTO = "bloqueados"
CADENA = "antes_del_filtro"
# Lo más largo que acepta con vencimiento. Sin vencimiento (hasta desbloquear) lo deja una persona en Zonda,
# con la alerta revisada como ataque real.
MAXIMO_SEGUNDOS = 86400
# Equipos bloqueados a la vez, como mucho (el servidor tiene el mismo tope, ZONDA_MAX_BLOQUEOS), y el tamaño
# del conjunto en zonda.nft, que el tope no puede pasar.
TOPE = 20
TAMANIO_CONJUNTO = 256
CADA = 10
# Si lo que le queda a un bloqueo según el servidor y según el núcleo difiere en más de estos segundos, alguien
# cambió la duración en Zonda: se vuelve a poner con la nueva.
TOLERANCIA = 15
# Lo más grande que acepta como respuesta del servidor, y lo que el servidor acepta por lista en el informe.
MAXIMO_RESPUESTA = 1_000_000
MAXIMO_INFORME = 1000
MAXIMO_NUNCA = 200
# nft corre sin shell y con un PATH fijo.
ENTORNO_NFT = {"PATH": "/usr/sbin:/usr/bin:/sbin:/bin"}
MOTIVOS = {
    "invalido": "no es la dirección IPv4 de un equipo o la duración no es una de las permitidas",
    "nunca_bloquear": "está en la lista de «nunca bloquear»",
    "tope": "ya está bloqueado el máximo de equipos a la vez",
}


_ultimos: dict[str, float] = {}


def registrar(mensaje: str, cada: float = 0) -> None:
    """Escribe en el registro; con `cada`, el mismo mensaje sale como mucho una vez en esos segundos (si el
    servidor está apagado, no se llena el registro con un error cada 10 segundos)."""
    ahora = time.monotonic()
    if cada and ahora - _ultimos.get(mensaje, -cada) < cada:
        return
    _ultimos[mensaje] = ahora
    print(f"{datetime.now().strftime('%Y-%m-%d %H:%M:%S')} {mensaje}", flush=True)


class SinConexion(Exception):
    """El servidor no contestó."""


class ErrorDelServidor(Exception):
    """El servidor contestó con un error o con algo que no se entiende."""


class ErrorNft(Exception):
    """nft no pudo hacer lo que se le pidió."""


class TablaRota(Exception):
    """La tabla de Zonda no está, o no es la que arma zonda.nft."""


# ---- Qué se puede bloquear ----


def ip_bloqueable(texto) -> str | None:
    """La IP de un equipo, que tiene sentido bloquear, con el mismo criterio que el servidor: IPv4, y ni
    difusión, ni multidifusión, ni la dirección vacía, ni la propia máquina, ni una de enlace local."""
    if not isinstance(texto, str):
        return None
    try:
        ip = ipaddress.ip_address(texto)
    except ValueError:
        return None
    if ip.version != 4 or ip.is_multicast or ip.is_unspecified or ip.is_loopback or ip.is_link_local or ip.is_reserved:
        return None
    return str(ip)


def duracion_permitida(segundos) -> bool:
    """None (hasta desbloquear) o los segundos que le quedan, de 1 a 24 horas."""
    return segundos is None or (type(segundos) is int and 1 <= segundos <= MAXIMO_SEGUNDOS)


def redes(texto: str) -> list:
    """Las redes o direcciones de la lista de «nunca bloquear», separadas por comas o espacios. ValueError si
    alguna no es una red."""
    return [ipaddress.ip_network(parte, strict=False) for parte in texto.replace(",", " ").split()]


def en_la_lista(ip: str, lista: list) -> bool:
    direccion = ipaddress.ip_address(ip)
    return any(direccion in red for red in lista)


def direcciones_del_servidor(url: str) -> list:
    """Las direcciones del servidor de Zonda, que el firewall nunca bloquea: si lo bloqueara, ya no podría
    preguntarle por los desbloqueos. Vacía si el nombre no se puede resolver ahora."""
    nombre = urllib.parse.urlsplit(url).hostname
    if not nombre:
        return []
    try:
        datos = socket.getaddrinfo(nombre, None, proto=socket.IPPROTO_TCP)
    except OSError:
        return []
    return sorted({ipaddress.ip_network(direccion[0]) for *_, direccion in datos}, key=lambda red: (red.version, red))


def duracion_nft(segundos: int) -> str:
    """Los segundos como los escribe nft: 1h, 59m2s, 1d."""
    partes = []
    for unidad, tamanio in (("d", 86400), ("h", 3600), ("m", 60), ("s", 1)):
        cantidad, segundos = divmod(segundos, tamanio)
        if cantidad:
            partes.append(f"{cantidad}{unidad}")
    return "".join(partes)


def texto_duracion(segundos: int | None) -> str:
    return "hasta desbloquear" if segundos is None else f"por {duracion_nft(segundos)}"


def coincide(quedan: int | None, segundos: int | None) -> bool:
    """Si lo que tiene el núcleo (los segundos que le quedan; None: sin vencimiento) es lo que pide el servidor."""
    if quedan is None or segundos is None:
        return quedan is None and segundos is None
    return abs(quedan - segundos) <= TOLERANCIA


@dataclass
class Plan:
    """Lo que hay que cambiar en la tabla de Zonda para que quede como pide el servidor, ya validado."""

    # Lo que tiene que quedar bloqueado: IP → (segundos, los bloqueos de Zonda que lo piden). None: hasta desbloquear.
    deseados: dict[str, tuple[int | None, list[str]]] = field(default_factory=dict)
    # Lo que no se aplica: (bloqueo, IP que mandó el servidor, motivo).
    rechazados: list[tuple[str, str, str]] = field(default_factory=list)
    sacar: list[str] = field(default_factory=list)
    agregar: dict[str, int | None] = field(default_factory=dict)


def planear(pedidos: list, en_el_firewall: dict[str, int | None], nunca: list, tope: int = TOPE) -> Plan:
    """Valida cada bloqueo que pide el servidor y lo compara con lo que tiene la tabla (IP → segundos que le
    quedan; None: sin vencimiento). Rechaza, con su motivo, lo que no es la IP de un equipo o no tiene una
    duración permitida (invalido), lo que está en la lista de «nunca bloquear» (nunca_bloquear) y lo que pasa
    el tope (tope: siguen primero los que ya estaban bloqueados, y después los más viejos). Un bloqueo sin id
    no se puede contar: se ignora."""
    plan = Plan()
    validos: dict[str, tuple[int | None, list[str]]] = {}
    for pedido in pedidos:
        bloqueo_id = pedido.get("id") if isinstance(pedido, dict) else None
        if not isinstance(bloqueo_id, str) or not 1 <= len(bloqueo_id) <= 64:
            continue
        texto, segundos = pedido.get("ip"), pedido.get("segundos")
        ip = ip_bloqueable(texto)
        if ip is None or not duracion_permitida(segundos):
            plan.rechazados.append((bloqueo_id, " ".join(str(texto).split())[:45], "invalido"))
        elif en_la_lista(ip, nunca):
            plan.rechazados.append((bloqueo_id, ip, "nunca_bloquear"))
        else:
            # Un equipo pedido dos veces queda con lo más largo.
            antes, ids = validos.get(ip, (0, []))
            validos[ip] = (None if antes is None or segundos is None else max(antes, segundos), ids + [bloqueo_id])
    for posicion, ip in enumerate(sorted(validos, key=lambda ip: ip not in en_el_firewall)):
        if posicion < tope:
            plan.deseados[ip] = validos[ip]
        else:
            plan.rechazados.extend((bloqueo_id, ip, "tope") for bloqueo_id in validos[ip][1])
    plan.sacar = [ip for ip in en_el_firewall if ip not in plan.deseados]
    for ip, (segundos, _) in plan.deseados.items():
        if ip not in en_el_firewall:
            plan.agregar[ip] = segundos
        elif not coincide(en_el_firewall[ip], segundos):
            plan.sacar.append(ip)
            plan.agregar[ip] = segundos
    return plan


def aplicados(plan: Plan, en_el_firewall: dict[str, int | None]) -> list[str]:
    """Los bloqueos de Zonda que están en la tabla como los pidió el servidor: solo eso cuenta como aplicado."""
    return [
        bloqueo_id
        for ip, (segundos, ids) in plan.deseados.items()
        if ip in en_el_firewall and coincide(en_el_firewall[ip], segundos)
        for bloqueo_id in ids
    ]


def contar(plan: Plan) -> None:
    """Escribe en el registro lo que cambió en la tabla."""
    for ip in plan.sacar:
        if ip not in plan.agregar:
            registrar(f"Desbloqueado {ip}")
    for ip, segundos in plan.agregar.items():
        ids = ", ".join(plan.deseados[ip][1])
        registrar(f"{'Bloqueado de nuevo' if ip in plan.sacar else 'Bloqueado'} {ip} {texto_duracion(segundos)} ({ids})")


# ---- La tabla de Zonda en nftables ----


def ejecutar(argumentos: list[str], entrada: str | None = None) -> tuple[int, str, str]:
    """Corre nft sin shell: código de salida, salida y errores."""
    try:
        hecho = subprocess.run(argumentos, input=entrada, capture_output=True, text=True, timeout=15, env=ENTORNO_NFT, check=False)
    except (OSError, subprocess.TimeoutExpired) as error:
        return 127, "", str(error)
    return hecho.returncode, hecho.stdout, hecho.stderr


def _campo_que_descarta(regla: dict) -> str | None:
    """saddr o daddr si la regla es exactamente «ip saddr|daddr @bloqueados counter drop»; None si es otra cosa."""
    expresiones = regla.get("expr")
    if not isinstance(expresiones, list) or not expresiones or expresiones[-1] != {"drop": None}:
        return None
    campos = []
    for expresion in expresiones[:-1]:
        if not isinstance(expresion, dict):
            return None
        if "counter" in expresion:
            continue
        coincidencia = expresion.get("match")
        if not isinstance(coincidencia, dict) or coincidencia.get("right") != "@" + CONJUNTO:
            return None
        izquierda = coincidencia.get("left")
        dato = izquierda.get("payload") if isinstance(izquierda, dict) else None
        if not isinstance(dato, dict) or dato.get("protocol") != "ip":
            return None
        campos.append(dato.get("field"))
    return campos[0] if campos in (["saddr"], ["daddr"]) else None


def leer_tabla(salida: str) -> dict[str, int | None]:
    """Los equipos bloqueados de la tabla de Zonda, de `nft -j list table inet zonda` (libnftables-json): IP →
    segundos que le quedan (None: sin vencimiento). TablaRota si no es la tabla que arma zonda.nft: le falta
    el conjunto, la cadena o una de sus dos reglas, o alguien las cambió."""
    try:
        objetos = json.loads(salida)["nftables"]
        if not isinstance(objetos, list):
            raise TypeError
    except (ValueError, KeyError, TypeError):
        raise TablaRota("no se pudo leer") from None
    conjunto = cadena = None
    reglas = []
    for objeto in objetos:
        if not isinstance(objeto, dict):
            continue
        if isinstance(objeto.get("set"), dict) and objeto["set"].get("name") == CONJUNTO:
            conjunto = objeto["set"]
        elif isinstance(objeto.get("chain"), dict) and objeto["chain"].get("name") == CADENA:
            cadena = objeto["chain"]
        elif isinstance(objeto.get("rule"), dict) and objeto["rule"].get("chain") == CADENA:
            reglas.append(objeto["rule"])
    banderas = conjunto.get("flags", []) if conjunto else []
    banderas = [banderas] if isinstance(banderas, str) else banderas
    if conjunto is None or conjunto.get("type") != "ipv4_addr" or "timeout" not in banderas:
        raise TablaRota("no tiene el conjunto de los equipos bloqueados")
    if cadena is None or (cadena.get("type"), cadena.get("hook"), cadena.get("policy", "accept")) != ("filter", "forward", "accept"):
        raise TablaRota("no tiene la cadena que descarta antes del filtro")
    if sorted(str(_campo_que_descarta(regla)) for regla in reglas) != ["daddr", "saddr"]:
        raise TablaRota("tiene las reglas cambiadas")
    elementos: dict[str, int | None] = {}
    for elemento in conjunto.get("elem", []):
        if isinstance(elemento, dict) and isinstance(elemento.get("elem"), dict):
            valor, quedan = elemento["elem"].get("val"), elemento["elem"].get("expires", elemento["elem"].get("timeout"))
        else:
            valor, quedan = elemento, None
        if isinstance(valor, str):
            elementos[valor] = quedan if type(quedan) is int else None
    return elementos


class Nft:
    """La tabla de Zonda, con las tres órdenes de nft que usa el programa: leerla, armarla con zonda.nft y
    cambiar sus equipos bloqueados."""

    def __init__(self, tabla: Path, programa: str = "nft", correr=ejecutar) -> None:
        self.tabla = tabla
        self.programa = programa
        self.correr = correr

    def _nft(self, *argumentos: str, entrada: str | None = None) -> str:
        codigo, salida, error = self.correr([self.programa, *argumentos], entrada)
        if codigo != 0:
            lineas = (error or salida).strip().splitlines()
            raise ErrorNft(lineas[0] if lineas else f"nft terminó con el código {codigo}")
        return salida

    def leer(self) -> dict[str, int | None]:
        try:
            salida = self._nft("-j", "list", "table", "inet", TABLA)
        except ErrorNft as error:
            raise TablaRota(f"no está ({error})") from None
        return leer_tabla(salida)

    def asegurar(self) -> dict[str, int | None]:
        """Lo que tiene bloqueado la tabla. Si no está o alguien la cambió, la arma de nuevo con zonda.nft, vacía:
        los bloqueos los vuelve a poner la vuelta."""
        try:
            return self.leer()
        except TablaRota as motivo:
            self._nft("-f", str(self.tabla))
            registrar(f"La tabla de Zonda {motivo}: se armó de nuevo con {self.tabla}")
        try:
            return self.leer()
        except TablaRota as motivo:
            raise ErrorNft(f"la tabla de Zonda {motivo}, también después de armarla") from None

    def cambiar(self, sacar: list[str], agregar: dict[str, int | None]) -> None:
        """Saca y agrega equipos en una sola transacción de nft: o se hace todo, o nada. Para cambiar la duración
        de uno, lo saca y lo vuelve a poner."""
        if not sacar and not agregar:
            return
        lineas = [f"delete element inet {TABLA} {CONJUNTO} {{ {ip} }}" for ip in sacar]
        lineas += [
            f"add element inet {TABLA} {CONJUNTO} {{ {ip}{'' if segundos is None else ' timeout ' + duracion_nft(segundos)} }}"
            for ip, segundos in agregar.items()
        ]
        self._nft("-f", "/dev/stdin", entrada="\n".join(lineas) + "\n")


# ---- El servidor de Zonda ----


class _SinRedirecciones(urllib.request.HTTPRedirectHandler):
    """No sigue redirecciones: el token del firewall va solo al servidor configurado."""

    def redirect_request(self, *argumentos, **nombrados):
        return None


class Servidor:
    """El servidor de Zonda, con el token del firewall en la cabecera X-Token-Firewall. Sin proxies ni
    redirecciones, y con HTTPS el certificado se verifica."""

    def __init__(self, url: str, token: str, espera: float = 5.0) -> None:
        self.url = url.rstrip("/")
        self.token = token
        self.espera = espera
        self._abridor = urllib.request.build_opener(urllib.request.ProxyHandler({}), _SinRedirecciones())

    def pedir(self, metodo: str, ruta: str, cuerpo: dict | None = None):
        datos = None if cuerpo is None else json.dumps(cuerpo, separators=(",", ":")).encode("utf-8")
        cabeceras = {"X-Token-Firewall": self.token, "Accept": "application/json"}
        if datos is not None:
            cabeceras["Content-Type"] = "application/json"
        pedido = urllib.request.Request(self.url + ruta, data=datos, headers=cabeceras, method=metodo)
        try:
            with self._abridor.open(pedido, timeout=self.espera) as respuesta:
                contenido = respuesta.read(MAXIMO_RESPUESTA + 1)
        except urllib.error.HTTPError as error:
            detalle = " ".join(error.read(300).decode("utf-8", "replace").split())
            raise ErrorDelServidor(f"el servidor contestó {error.code}: {detalle}") from None
        except (urllib.error.URLError, http.client.HTTPException, OSError) as error:
            raise SinConexion(str(getattr(error, "reason", error))) from None
        if len(contenido) > MAXIMO_RESPUESTA:
            raise ErrorDelServidor("el servidor contestó algo demasiado grande")
        try:
            return json.loads(contenido)
        except ValueError:
            raise ErrorDelServidor("el servidor contestó algo que no es JSON") from None


# ---- Programa ----


class Agente:
    """Cada vuelta pregunta, valida, aplica y cuenta."""

    def __init__(self, servidor, nft: Nft, nunca: list, tope: int = TOPE, probar: bool = False, direcciones=direcciones_del_servidor) -> None:
        self.servidor = servidor
        self.nft = nft
        self.nunca = nunca
        self.tope = tope
        self.probar = probar
        self.direcciones = direcciones
        self.del_servidor: list = []

    def protegidas(self) -> list:
        """La lista de «nunca bloquear», más las direcciones del servidor de Zonda que no estén en ella (las
        últimas que se pudieron resolver, si ahora no se puede)."""
        self.del_servidor = self.direcciones(self.servidor.url) or self.del_servidor
        de_mas = [red for red in self.del_servidor if not any(red.version == otra.version and red.subnet_of(otra) for otra in self.nunca)]
        return self.nunca + de_mas

    def vuelta(self) -> bool:
        """Una vuelta. False si algo no salió (el servidor no contestó, nft falló o el informe no llegó): lo que
        falta se hace en la vuelta siguiente."""
        try:
            pedidos = self.servidor.pedir("GET", "/firewall/bloqueos")["bloqueos"]
            if not isinstance(pedidos, list):
                raise TypeError
        except (SinConexion, ErrorDelServidor) as error:
            registrar(f"No se pudo preguntar al servidor ({error}). El firewall queda como está.", cada=300)
            return False
        except (KeyError, TypeError):
            registrar("El servidor no mandó la lista de bloqueos. El firewall queda como está.", cada=300)
            return False
        nunca = self.protegidas()
        if self.probar:
            return self._mostrar(pedidos, nunca)
        completa = True
        try:
            plan, en_el_firewall = self._aplicar(pedidos, nunca)
        except (ErrorNft, TablaRota) as error:
            registrar(f"No se pudo cambiar la tabla de Zonda: {error}", cada=60)
            plan, en_el_firewall, completa = planear(pedidos, {}, nunca, self.tope), {}, False
            # Sin saber qué tiene la tabla, el tope no se puede contar: esos rechazos esperan a la vuelta siguiente.
            plan.rechazados = [rechazo for rechazo in plan.rechazados if rechazo[2] != "tope"]
        for bloqueo_id, ip, motivo in plan.rechazados:
            registrar(f"No se aplica el bloqueo {bloqueo_id} ({ip}): {MOTIVOS[motivo]}", cada=3600)
        informe = {
            "aplicados": aplicados(plan, en_el_firewall)[:MAXIMO_INFORME],
            "rechazados": [{"id": bloqueo_id, "motivo": motivo} for bloqueo_id, _, motivo in plan.rechazados][:MAXIMO_INFORME],
            "nuncaBloquear": [str(red) for red in nunca][:MAXIMO_NUNCA],
        }
        try:
            self.servidor.pedir("POST", "/firewall/informe", informe)
        except (SinConexion, ErrorDelServidor) as error:
            registrar(f"No se pudo contarle al servidor qué se aplicó ({error}).", cada=300)
            return False
        return completa

    def _aplicar(self, pedidos: list, nunca: list) -> tuple[Plan, dict[str, int | None]]:
        """Deja la tabla igual a lo que pide el servidor, ya validado. Devuelve el plan y lo que quedó en la
        tabla, leído de nuevo: lo que cuenta como aplicado es lo que está. Si un equipo vence justo entre la
        lectura y el cambio, nft rechaza toda la transacción: se lee otra vez y se reintenta una vez."""
        en_el_firewall = self.nft.asegurar()
        plan = planear(pedidos, en_el_firewall, nunca, self.tope)
        if not plan.sacar and not plan.agregar:
            return plan, en_el_firewall
        try:
            self.nft.cambiar(plan.sacar, plan.agregar)
        except ErrorNft:
            plan = planear(pedidos, self.nft.asegurar(), nunca, self.tope)
            self.nft.cambiar(plan.sacar, plan.agregar)
        contar(plan)
        return plan, self.nft.leer()

    def _mostrar(self, pedidos: list, nunca: list) -> bool:
        """Lo que haría la vuelta, sin tocar la tabla ni avisarle al servidor (--probar)."""
        try:
            en_el_firewall = self.nft.leer()
        except TablaRota as motivo:
            registrar(f"La tabla de Zonda {motivo}: la armaría con {self.nft.tabla}")
            en_el_firewall = {}
        plan = planear(pedidos, en_el_firewall, nunca, self.tope)
        registrar(f"El servidor pide {len(pedidos)} bloqueos y la tabla tiene {len(en_el_firewall)} equipos")
        for ip in plan.sacar:
            if ip not in plan.agregar:
                registrar(f"Sacaría {ip}")
        for ip, segundos in plan.agregar.items():
            registrar(f"{'Volvería a poner' if ip in plan.sacar else 'Bloquearía'} {ip} {texto_duracion(segundos)}")
        for bloqueo_id, ip, motivo in plan.rechazados:
            registrar(f"Rechazaría el bloqueo {bloqueo_id} ({ip}): {MOTIVOS[motivo]}")
        if not plan.sacar and not plan.agregar and not plan.rechazados:
            registrar("No cambiaría nada")
        return True


def leer_token(ruta: str | None) -> str:
    """El token del firewall, de un archivo: el que se indica o el que le pasa systemd ($CREDENTIALS_DIRECTORY).
    Si no, de ZONDA_FIREWALL_TOKEN. Nunca como argumento, para que no se vea en la lista de procesos."""
    if not ruta and os.environ.get("CREDENTIALS_DIRECTORY"):
        ruta = os.path.join(os.environ["CREDENTIALS_DIRECTORY"], "token")
    if ruta:
        return Path(ruta).expanduser().read_text(encoding="utf-8").strip()
    return os.environ.get("ZONDA_FIREWALL_TOKEN", "").strip()


def leer_argumentos(argv: list[str] | None = None) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Aplica en el firewall los bloqueos que aprueban las personas en Zonda.")
    parser.add_argument("--servidor", default=os.environ.get("ZONDA_SERVIDOR", "http://10.0.2.2:8000"))
    parser.add_argument("--token-archivo", help="archivo con el token del firewall (si no, $CREDENTIALS_DIRECTORY/token o ZONDA_FIREWALL_TOKEN)")
    parser.add_argument("--nunca-bloquear", default="", help="redes o direcciones que nunca se bloquean, separadas por comas")
    parser.add_argument("--tope", type=int, default=TOPE, help="equipos bloqueados a la vez, como mucho")
    parser.add_argument("--cada", type=float, default=CADA, help="segundos entre vueltas")
    parser.add_argument("--tabla", default="/etc/zonda-firewall/zonda.nft", help="el archivo que arma la tabla de Zonda")
    parser.add_argument("--nft", default="nft", help="el programa nft")
    parser.add_argument("--una-vez", action="store_true", help="hace una vuelta y termina")
    parser.add_argument("--probar", action="store_true", help="muestra qué haría, sin tocar el firewall ni avisarle al servidor")
    return parser.parse_args(argv)


def main(argv: list[str] | None = None) -> int:
    argumentos = leer_argumentos(argv)
    try:
        token = leer_token(argumentos.token_archivo)
    except OSError as error:
        registrar(f"No se pudo leer el token del firewall ({error}).")
        return 2
    if not token:
        registrar("Falta el token del firewall (--token-archivo o ZONDA_FIREWALL_TOKEN).")
        return 2
    try:
        nunca = redes(argumentos.nunca_bloquear)
    except ValueError as error:
        registrar(f"La lista de «nunca bloquear» tiene algo que no es una red ({error}).")
        return 2
    esquema = urllib.parse.urlsplit(argumentos.servidor).scheme
    if esquema not in ("http", "https"):
        registrar(f"El servidor tiene que ser una dirección http o https: {argumentos.servidor}")
        return 2
    if not 1 <= argumentos.tope <= TAMANIO_CONJUNTO or argumentos.cada < 1:
        registrar(f"El tope va de 1 a {TAMANIO_CONJUNTO} equipos, y las vueltas, cada 1 segundo o más.")
        return 2
    if esquema == "http":
        registrar("El servidor es http, sin cifrar: solo para el laboratorio.")
    nft = Nft(Path(argumentos.tabla), argumentos.nft)
    agente = Agente(Servidor(argumentos.servidor, token), nft, nunca, argumentos.tope, probar=argumentos.probar)
    lista = ", ".join(str(red) for red in nunca) or "nada más que el servidor de Zonda"
    registrar(f"Preguntando a {argumentos.servidor} cada {argumentos.cada:g} s. Nunca bloquea: {lista}. Tope: {argumentos.tope} equipos.")
    while True:
        completa = agente.vuelta()
        if argumentos.una_vez or argumentos.probar:
            return 0 if completa else 1
        time.sleep(argumentos.cada)


if __name__ == "__main__":
    sys.exit(main())
