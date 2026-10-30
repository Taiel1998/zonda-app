#!/usr/bin/env python3
"""Resume el tráfico que ve Zeek para el tablero web de Zonda (decisión 49).

Corre en el sensor (zeek-lab), al lado del análisis en vivo y sin tocarlo. Sigue el conn.log de Zeek y
suma cada conexión:

- por tramo de 5 segundos y servicio: las conexiones que empezaron en el tramo, cuántas de ellas no
  llegaron a establecerse (sin respuesta, rechazadas o cortadas en el saludo) y los bytes y paquetes,
  repartidos entre los tramos que duró la conexión;
- por minuto: quién habló con quién y por qué servicio.

Cada 5 segundos le manda lo nuevo al servidor (POST /ingesta/trafico, con el token de la ingesta). El
servidor suma: una conexión larga, que Zeek escribe cuando termina, completa tramos que ya habían llegado.

Si el servidor no contesta, junta lo que no pudo mandar y lo manda después (hasta un día de tráfico).
Recuerda hasta dónde leyó el conn.log en un archivo de estado: si se reinicia, sigue desde ahí y no
cuenta dos veces. Solo usa la biblioteca estándar de Python, porque el sensor tiene la memoria justa.

Uso en el sensor:
    python3 metricas_trafico.py --servidor http://10.0.2.2:8000 --token-archivo ~/.zonda_token_ingesta
Para llenar el tablero con la última semana del archivo de Zeek, una sola vez:
    python3 metricas_trafico.py ... --rellenar-horas 168
Para probar con un archivo y salir:
    python3 metricas_trafico.py --archivo conn.log --servidor http://127.0.0.1:8001 --token X --una-vez
"""

import argparse
import gzip
import json
import math
import os
import re
import sys
import time
import urllib.error
import urllib.request
from datetime import datetime, timedelta, timezone
from pathlib import Path

TRAMO = 5
MINUTO = 60
# Estados de Zeek de una conexión que no llegó a establecerse: S0 (nadie contestó, como el descarte del
# firewall), REJ (rechazada), RSTOS0 y RSTRH (cortadas antes del saludo), SH y SHR (medio abiertas).
FALLIDAS = {"S0", "REJ", "RSTOS0", "RSTRH", "SH", "SHR"}
# El nombre del servicio cuando Zeek no lo reconoció, por el puerto de destino; si no está acá, "otro".
PUERTOS = {
    20: "ftp-data", 21: "ftp", 22: "ssh", 23: "telnet", 25: "smtp", 53: "dns", 67: "dhcp", 68: "dhcp",
    69: "tftp", 80: "http", 88: "krb", 110: "pop3", 123: "ntp", 135: "dce_rpc", 137: "netbios",
    138: "netbios", 139: "netbios", 143: "imap", 161: "snmp", 389: "ldap", 443: "ssl", 445: "smb",
    465: "smtp", 587: "smtp", 636: "ldap", 993: "imap", 995: "pop3", 1433: "mssql", 3306: "mysql",
    3389: "rdp", 5353: "mdns", 5355: "llmnr", 5432: "postgresql", 5900: "vnc", 8080: "http", 8443: "ssl",
}
NO_VALIDO = re.compile(r"[^a-z0-9_.-]")
# Una conexión reparte sus bytes en a lo sumo un día de tramos.
DURACION_MAXIMA = 86400
# Filas como mucho por envío (el servidor acepta hasta 50.000 por lista).
FILAS_POR_ENVIO = 20000
# Lo que no se pudo mandar se guarda hasta un día; después se descarta.
RETENER = 86400


def registrar(mensaje: str) -> None:
    print(f"{datetime.now().strftime('%Y-%m-%d %H:%M:%S')} {mensaje}", flush=True)


# ---- Lectura del conn.log ----


def campos_de_cabecera(linea: str) -> list[str] | None:
    """Los nombres de las columnas de la línea #fields del conn.log de Zeek, o None si no es esa línea."""
    if linea.startswith("#fields"):
        return linea.rstrip("\n").split("\t")[1:]
    return None


def leer_registro(linea: str, campos: list[str] | None) -> dict | None:
    """Una conexión del conn.log, en TSV (con sus campos) o en JSON. None si es un comentario o está rota."""
    linea = linea.rstrip("\n")
    if not linea or linea.startswith("#"):
        return None
    if linea.startswith("{"):
        try:
            return json.loads(linea)
        except ValueError:
            return None
    if campos is None:
        return None
    valores = linea.split("\t")
    if len(valores) != len(campos):
        return None
    return dict(zip(campos, valores))


def _numero(registro: dict, clave: str) -> float:
    valor = registro.get(clave)
    if valor in (None, "-", "(empty)", ""):
        return 0.0
    try:
        return float(valor)
    except (TypeError, ValueError):
        return 0.0


def servicio_de(registro: dict) -> str:
    """El servicio que reconoció Zeek (el primero, si dice varios); si no reconoció ninguno, el del puerto
    de destino; ICMP aparte; y si no, "otro"."""
    zeek = str(registro.get("service") or "-").split(",")[0].strip().lower()
    if zeek not in ("", "-", "(empty)"):
        return NO_VALIDO.sub("", zeek).lstrip("_.-")[:32] or "otro"
    if str(registro.get("proto", "")).lower() == "icmp":
        return "icmp"
    try:
        puerto = int(float(registro.get("id.resp_p", -1)))
    except (TypeError, ValueError):
        return "otro"
    return PUERTOS.get(puerto, "otro")


class Acumulador:
    """Lo que se sumó y todavía no se mandó al servidor, por tramo y servicio, y por minuto y par."""

    def __init__(self) -> None:
        self.tramos: dict[tuple[int, str], list[int]] = {}
        self.pares: dict[tuple[int, str, str, str], list[int]] = {}
        self.conexiones = 0

    def vacio(self) -> bool:
        return not self.tramos and not self.pares

    def sumar(self, registro: dict) -> bool:
        """Suma una conexión; False si no tiene lo mínimo (hora y direcciones)."""
        inicio = _numero(registro, "ts")
        origen, destino = registro.get("id.orig_h"), registro.get("id.resp_h")
        if inicio <= 0 or not origen or not destino or origen == "-" or destino == "-":
            return False
        servicio = servicio_de(registro)
        fallida = 1 if str(registro.get("conn_state", "")) in FALLIDAS else 0
        bytes_ = int(_numero(registro, "orig_ip_bytes") + _numero(registro, "resp_ip_bytes"))
        if bytes_ == 0:
            bytes_ = int(_numero(registro, "orig_bytes") + _numero(registro, "resp_bytes"))
        paquetes = int(_numero(registro, "orig_pkts") + _numero(registro, "resp_pkts"))
        duracion = min(max(_numero(registro, "duration"), 0.0), DURACION_MAXIMA)

        primero = int(inicio // TRAMO) * TRAMO
        self._tramo(primero, servicio, conexiones=1, fallidas=fallida)
        for t, parte_bytes, parte_paquetes in repartir(inicio, duracion, bytes_, paquetes):
            self._tramo(t, servicio, bytes_=parte_bytes, paquetes=parte_paquetes)
        clave_par = (int(inicio // MINUTO) * MINUTO, origen, destino, servicio)
        par = self.pares.setdefault(clave_par, [0, 0, 0])
        par[0] += 1
        par[1] += fallida
        par[2] += bytes_
        self.conexiones += 1
        return True

    def _tramo(self, t: int, servicio: str, conexiones: int = 0, fallidas: int = 0, bytes_: int = 0, paquetes: int = 0) -> None:
        fila = self.tramos.setdefault((t, servicio), [0, 0, 0, 0])
        fila[0] += conexiones
        fila[1] += fallidas
        fila[2] += bytes_
        fila[3] += paquetes

    def lotes(self) -> list[tuple[list, list]]:
        """Las claves de lo acumulado, en envíos de a lo sumo FILAS_POR_ENVIO filas por lista."""
        tramos, pares = sorted(self.tramos), sorted(self.pares)
        return [
            (tramos[i : i + FILAS_POR_ENVIO], pares[i : i + FILAS_POR_ENVIO])
            for i in range(0, max(len(tramos), len(pares)), FILAS_POR_ENVIO)
        ]

    def cuerpo(self, claves_tramos: list, claves_pares: list) -> dict:
        return {
            "tramos": [
                {"t": t, "servicio": s, "conexiones": c, "fallidas": f, "bytes": b, "paquetes": p}
                for (t, s) in claves_tramos
                for (c, f, b, p) in [self.tramos[(t, s)]]
            ],
            "pares": [
                {"minuto": m, "origen": o, "destino": d, "servicio": s, "conexiones": c, "fallidas": f, "bytes": b}
                for (m, o, d, s) in claves_pares
                for (c, f, b) in [self.pares[(m, o, d, s)]]
            ],
        }

    def olvidar(self, claves_tramos: list, claves_pares: list) -> None:
        for clave in claves_tramos:
            del self.tramos[clave]
        for clave in claves_pares:
            del self.pares[clave]

    def descartar_viejo(self, limite: int) -> int:
        """Si el servidor no contesta por mucho tiempo, olvida lo anterior a `limite` para no llenar la memoria."""
        antes = len(self.tramos) + len(self.pares)
        self.tramos = {k: v for k, v in self.tramos.items() if k[0] >= limite}
        self.pares = {k: v for k, v in self.pares.items() if k[0] >= limite}
        return antes - len(self.tramos) - len(self.pares)


def repartir(inicio: float, duracion: float, bytes_: int, paquetes: int):
    """Reparte los bytes y paquetes de una conexión entre los tramos de 5 segundos que duró, en proporción
    al tiempo que pasó en cada uno. Los enteros se reparten sin perder nada: el resto va al último tramo."""
    if bytes_ == 0 and paquetes == 0:
        return []
    fin = inicio + duracion
    primero = int(inicio // TRAMO) * TRAMO
    ultimo = int(fin // TRAMO) * TRAMO if duracion > 0 else primero
    if ultimo == primero:
        return [(primero, bytes_, paquetes)]
    partes = []
    dados_b = dados_p = 0
    t = primero
    while t <= ultimo:
        solapa = max(0.0, min(t + TRAMO, fin) - max(t, inicio))
        if t == ultimo:
            parte_b, parte_p = bytes_ - dados_b, paquetes - dados_p
        else:
            parte_b = math.floor(bytes_ * solapa / duracion)
            parte_p = math.floor(paquetes * solapa / duracion)
        dados_b += parte_b
        dados_p += parte_p
        if parte_b or parte_p:
            partes.append((t, parte_b, parte_p))
        t += TRAMO
    return partes


class Seguidor:
    """Sigue el conn.log de Zeek como `tail -F`: lee las líneas nuevas, se da cuenta cuando Zeek lo rota
    (cada hora lo mueve al archivo y empieza uno nuevo) y recuerda hasta dónde leyó."""

    def __init__(self, ruta: Path, inodo: int | None = None, posicion: int = 0, desde_el_final: bool = False) -> None:
        self.ruta = ruta
        self.archivo = None
        self.inodo = None
        self.campos: list[str] | None = None
        self.pendiente = ""
        self._abrir(inodo, posicion, desde_el_final)

    def _abrir(self, inodo: int | None, posicion: int, desde_el_final: bool) -> None:
        try:
            datos = os.stat(self.ruta)
        except FileNotFoundError:
            self.archivo, self.inodo = None, None
            return
        self.archivo = open(self.ruta, "r", encoding="utf-8", errors="replace", newline="\n")
        self.inodo = datos.st_ino
        self.campos = None
        self.pendiente = ""
        # La cabecera dice las columnas: se lee siempre, aunque se siga desde la mitad del archivo.
        while True:
            linea = self.archivo.readline()
            if not linea.startswith("#"):
                break
            self.campos = campos_de_cabecera(linea) or self.campos
        if desde_el_final:
            self.archivo.seek(0, os.SEEK_END)
        elif inodo == datos.st_ino and 0 < posicion <= datos.st_size:
            self.archivo.seek(posicion)
        else:
            self.archivo.seek(0)

    def posicion(self) -> int:
        return self.archivo.tell() if self.archivo else 0

    def _leer_disponible(self):
        """Las líneas completas que hay; una incompleta (Zeek a mitad de escribir) espera a la vuelta siguiente."""
        if self.archivo is None:
            return
        while True:
            trozo = self.archivo.readline()
            if not trozo:
                return
            if not trozo.endswith("\n"):
                self.pendiente += trozo
                return
            linea, self.pendiente = self.pendiente + trozo, ""
            cabecera = campos_de_cabecera(linea)
            if cabecera is not None:
                self.campos = cabecera
                continue
            registro = leer_registro(linea, self.campos)
            if registro is not None:
                yield registro

    def lineas_nuevas(self):
        """Lo nuevo del archivo actual y, si Zeek lo rotó, lo que quedaba del viejo y lo del nuevo."""
        yield from self._leer_disponible()
        try:
            datos = os.stat(self.ruta)
        except FileNotFoundError:
            return
        rotado = self.archivo is None or datos.st_ino != self.inodo or datos.st_size < self.posicion()
        if rotado:
            if self.archivo is not None:
                yield from self._leer_disponible()
                self.archivo.close()
            self._abrir(None, 0, False)
            yield from self._leer_disponible()


def fin_del_archivo(ruta: Path) -> datetime | None:
    """La hora en la que Zeek cerró un archivo viejo, por su nombre: 2026-10-30/conn.18:00:00-19:00:00.log.gz
    cierra a las 19 del 30 (hora del sensor); si cruza la medianoche, al día siguiente."""
    coincide = re.match(r"conn\.(\d\d):(\d\d):(\d\d)-(\d\d):(\d\d):(\d\d)\.log\.gz$", ruta.name)
    try:
        dia = datetime.strptime(ruta.parent.name, "%Y-%m-%d")
    except ValueError:
        return None
    if not coincide:
        return None
    h1, m1, s1, h2, m2, s2 = map(int, coincide.groups())
    inicio = dia.replace(hour=h1, minute=m1, second=s1)
    fin = dia.replace(hour=h2, minute=m2, second=s2)
    return fin if fin > inicio else fin + timedelta(days=1)


def archivos_viejos(carpeta: Path, horas: int, antes_de: datetime | None = None) -> list[Path]:
    """Los conn.*.log.gz que cerró Zeek en las últimas `horas` (y antes de `antes_de`, si se da), del más
    viejo al más nuevo."""
    desde = datetime.now() - timedelta(hours=horas)
    elegidos = []
    for ruta in sorted(carpeta.glob("[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9]/conn.*.log.gz")):
        fin = fin_del_archivo(ruta)
        if fin is None or fin < desde or (antes_de is not None and fin > antes_de):
            continue
        elegidos.append((fin, ruta))
    return [ruta for _, ruta in sorted(elegidos)]


def leer_archivo_viejo(ruta: Path):
    campos = None
    with gzip.open(ruta, "rt", encoding="utf-8", errors="replace") as archivo:
        for linea in archivo:
            cabecera = campos_de_cabecera(linea)
            if cabecera is not None:
                campos = cabecera
                continue
            registro = leer_registro(linea, campos)
            if registro is not None:
                yield registro


# ---- Envío al servidor ----


class Servidor:
    def __init__(self, url: str, token: str, espera: float = 5.0) -> None:
        self.url = url.rstrip("/") + "/ingesta/trafico"
        self.token = token
        self.espera = espera

    def mandar(self, cuerpo: dict) -> str:
        """'ok', 'reintentar' (no contestó, error 5xx, falta configurar el token) o 'descartar' (lo rechazó)."""
        pedido = urllib.request.Request(
            self.url,
            data=json.dumps(cuerpo, separators=(",", ":")).encode("utf-8"),
            headers={"Content-Type": "application/json", "X-Token-Ingesta": self.token},
            method="POST",
        )
        try:
            with urllib.request.urlopen(pedido, timeout=self.espera) as respuesta:
                respuesta.read()
            return "ok"
        except urllib.error.HTTPError as error:
            detalle = error.read()[:300].decode("utf-8", "replace")
            if error.code in (401, 404, 503) or error.code >= 500:
                registrar(f"El servidor contestó {error.code}: {detalle}. Se reintenta.")
                return "reintentar"
            registrar(f"El servidor rechazó el envío ({error.code}): {detalle}. Se descarta.")
            return "descartar"
        except (urllib.error.URLError, TimeoutError, ConnectionError, OSError) as error:
            registrar(f"No se pudo mandar al servidor ({error}). Se reintenta.")
            return "reintentar"


def mandar_todo(acumulado: Acumulador, servidor: Servidor) -> bool:
    """Manda lo acumulado y olvida cada envío que salió (o que el servidor rechazó). False si quedó algo
    para reintentar: lo que ya salió no se vuelve a mandar, así el servidor no lo suma dos veces."""
    for claves_tramos, claves_pares in acumulado.lotes():
        if servidor.mandar(acumulado.cuerpo(claves_tramos, claves_pares)) == "reintentar":
            return False
        acumulado.olvidar(claves_tramos, claves_pares)
    return True


# ---- Estado ----


def leer_estado(ruta: Path) -> dict:
    try:
        return json.loads(ruta.read_text(encoding="utf-8"))
    except (FileNotFoundError, ValueError):
        return {}


def guardar_estado(ruta: Path, estado: dict) -> None:
    temporal = ruta.with_suffix(ruta.suffix + ".tmp")
    temporal.write_text(json.dumps(estado), encoding="utf-8")
    os.replace(temporal, ruta)


# ---- Programa ----


def leer_token(argumentos) -> str:
    if argumentos.token:
        return argumentos.token.strip()
    if argumentos.token_archivo:
        return Path(argumentos.token_archivo).expanduser().read_text(encoding="utf-8").strip()
    return os.environ.get("ZONDA_INGESTA_TOKEN", "").strip()


def rellenar(argumentos, servidor: Servidor, estado: dict, ruta_estado: Path) -> None:
    """Manda el tráfico de los archivos viejos de Zeek, de a un archivo (una hora) por vez para no usar
    memoria. Solo los que Zeek cerró antes de que este programa empezara a seguir el conn.log en vivo: lo
    posterior ya lo mandó en vivo y el servidor lo sumaría dos veces. Si el servidor no contesta, lo deja
    para la próxima vez."""
    hechos = set(estado.get("rellenados", []))
    vivo_desde = datetime.fromtimestamp(estado["vivoDesde"]) if "vivoDesde" in estado else None
    for ruta in archivos_viejos(Path(argumentos.archivos_viejos), argumentos.rellenar_horas, antes_de=vivo_desde):
        if str(ruta) in hechos:
            continue
        acumulado = Acumulador()
        for registro in leer_archivo_viejo(ruta):
            acumulado.sumar(registro)
        conexiones = acumulado.conexiones
        if not mandar_todo(acumulado, servidor):
            registrar("El servidor no contesta: el relleno sigue la próxima vez")
            return
        hechos.add(str(ruta))
        estado["rellenados"] = sorted(hechos)
        guardar_estado(ruta_estado, estado)
        registrar(f"Rellenado {ruta.parent.name}/{ruta.name}: {conexiones} conexiones")


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="Resume el conn.log de Zeek para el tablero web de Zonda.")
    parser.add_argument("--archivo", default="/opt/zeek/spool/zeek/conn.log", help="el conn.log en vivo de Zeek")
    parser.add_argument("--servidor", default=os.environ.get("ZONDA_SERVIDOR", "http://10.0.2.2:8000"))
    parser.add_argument("--token", help="el token de la ingesta (mejor --token-archivo, para que no quede en el historial)")
    parser.add_argument("--token-archivo", help="archivo con el token de la ingesta")
    parser.add_argument("--cada", type=float, default=TRAMO, help="segundos entre envíos")
    parser.add_argument("--estado", default="~/.zonda_metricas_estado.json", help="dónde recuerda hasta dónde leyó")
    parser.add_argument("--desde-el-final", action="store_true", help="la primera vez, no cuenta lo que ya estaba en el archivo")
    parser.add_argument("--rellenar-horas", type=int, default=0, help="manda antes el tráfico de los archivos viejos de Zeek de estas horas")
    parser.add_argument("--archivos-viejos", default="/opt/zeek/logs", help="la carpeta donde Zeek archiva los registros")
    parser.add_argument("--una-vez", action="store_true", help="lee lo que hay, lo manda y termina")
    argumentos = parser.parse_args(argv)

    token = leer_token(argumentos)
    if not token:
        registrar("Falta el token de la ingesta (--token-archivo, --token o ZONDA_INGESTA_TOKEN).")
        return 2
    servidor = Servidor(argumentos.servidor, token)
    ruta_estado = Path(argumentos.estado).expanduser()
    estado = leer_estado(ruta_estado)
    if argumentos.rellenar_horas > 0:
        rellenar(argumentos, servidor, estado, ruta_estado)

    archivo = Path(argumentos.archivo)
    misma_ruta = estado.get("archivo") == str(archivo)
    seguidor = Seguidor(
        archivo,
        inodo=estado.get("inodo") if misma_ruta else None,
        posicion=estado.get("posicion", 0) if misma_ruta else 0,
        desde_el_final=argumentos.desde_el_final and not misma_ruta,
    )
    acumulado = Acumulador()
    if "vivoDesde" not in estado:
        estado["vivoDesde"] = time.time()
    registrar(f"Siguiendo {archivo} y mandando a {servidor.url} cada {argumentos.cada:g} s")
    ultimo_resumen = time.monotonic()
    enviadas = 0
    while True:
        for registro in seguidor.lineas_nuevas():
            acumulado.sumar(registro)
        if not acumulado.vacio():
            if mandar_todo(acumulado, servidor):
                enviadas += acumulado.conexiones
                acumulado.conexiones = 0
                estado.update({"archivo": str(archivo), "inodo": seguidor.inodo, "posicion": seguidor.posicion()})
                guardar_estado(ruta_estado, estado)
            else:
                perdidas = acumulado.descartar_viejo(int(time.time()) - RETENER)
                if perdidas:
                    registrar(f"Se descartaron {perdidas} filas de más de un día sin poder mandarlas")
        if argumentos.una_vez:
            registrar(f"Listo: {enviadas} conexiones mandadas")
            return 0 if acumulado.vacio() else 1
        if time.monotonic() - ultimo_resumen >= 300:
            registrar(f"En los últimos 5 minutos: {enviadas} conexiones mandadas")
            enviadas = 0
            ultimo_resumen = time.monotonic()
        time.sleep(argumentos.cada)


if __name__ == "__main__":
    sys.exit(main())
