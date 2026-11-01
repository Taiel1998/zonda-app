"""El programa del firewall que aplica los bloqueos aprobados en Zonda (herramientas/firewall, decisión 51):
cómo lee la tabla de Zonda en nftables, cómo valida lo que pide el servidor, qué cambia en la tabla y qué le
cuenta al servidor. nft y el núcleo están simulados (NftFalso), así que estas pruebas corren también en
Windows; la salida de nft tiene la forma que documenta libnftables-json(5)."""

import http.server
import importlib.util
import json
import os
import re
import shlex
import socket
import sys
import threading

import pytest

from app.config import RAIZ
from tests.test_bloqueos import FIREWALL, TOKEN_FIREWALL, bloquear, de_origenes_distintos, lo_que_bloquea_el_firewall
from tests.test_revision_y_severidades import revision

CARPETA = RAIZ / "herramientas" / "firewall"
TABLA_NFT = CARPETA / "zonda.nft"
_spec = importlib.util.spec_from_file_location("agente_firewall", CARPETA / "agente_firewall.py")
agente = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(agente)

RRHH, CONTABILIDAD, USUARIO = "192.168.30.50", "192.168.40.50", "192.168.10.50"
LEER = ["-j", "list", "table", "inet", "zonda"]
LINEA = re.compile(r"(add|delete) element inet zonda bloqueados \{ (\d{1,3}(?:\.\d{1,3}){3})(?: timeout ((?:\d+[dhms])+))? \}")


@pytest.fixture
def config(config):
    """La red de prueba con un firewall conectado: sin su token, los bloqueos están apagados."""
    config.firewall_token = TOKEN_FIREWALL
    return config


@pytest.fixture(autouse=True)
def registro_nuevo(monkeypatch):
    """Cada prueba empieza sin mensajes recordados: registrar() no calla lo que salió en otra prueba."""
    monkeypatch.setattr(agente, "_ultimos", {})


def segundos_de(texto: str) -> int:
    return sum(int(cantidad) * {"d": 86400, "h": 3600, "m": 60, "s": 1}[unidad] for cantidad, unidad in re.findall(r"(\d+)([dhms])", texto))


def regla(campo: str, veredicto: str = "drop", *otras: dict, protocolo: str = "ip") -> dict:
    """Una regla de la cadena como la muestra nft: «ip saddr @bloqueados counter drop»."""
    coincidencia = {"match": {"op": "==", "left": {"payload": {"protocol": protocolo, "field": campo}}, "right": "@bloqueados"}}
    expresiones = [coincidencia, *otras, {"counter": {"packets": 0, "bytes": 0}}, {veredicto: None}]
    return {"rule": {"family": "inet", "table": "zonda", "chain": "antes_del_filtro", "handle": 3, "expr": expresiones}}


def salida_nft(elementos=(), banderas=("timeout",), politica="accept", gancho="forward", reglas=None) -> dict:
    """Lo que muestra `nft -j list table inet zonda` con la tabla de zonda.nft."""
    conjunto = {"family": "inet", "name": "bloqueados", "table": "zonda", "type": "ipv4_addr", "handle": 1, "size": 256, "flags": banderas}
    if elementos:
        conjunto["elem"] = list(elementos)
    cadena = {"family": "inet", "table": "zonda", "name": "antes_del_filtro", "handle": 2, "type": "filter", "hook": gancho, "prio": -10, "policy": politica}
    return {
        "nftables": [
            {"metainfo": {"version": "1.1.3", "json_schema_version": 1}},
            {"table": {"family": "inet", "name": "zonda", "handle": 7}},
            {"set": conjunto},
            {"chain": cadena},
            *(reglas if reglas is not None else [regla("saddr"), regla("daddr")]),
        ]
    }


class NftFalso:
    """Hace de nft y del núcleo: la tabla de Zonda en memoria y un reloj que se adelanta a mano. Entiende solo
    las tres órdenes que usa el programa; cualquier otra hace fallar la prueba."""

    def __init__(self) -> None:
        self.reloj = 0
        self.existe = False
        # IP → (el vencimiento con que se puso, cuándo vence); (None, None) es hasta desbloquear.
        self.elementos: dict[str, tuple[int | None, int | None]] = {}
        self.reglas = [regla("saddr"), regla("daddr")]
        self.ordenes: list[list[str]] = []
        self.transacciones: list[str] = []
        self.sin_permiso = False
        self.fallan_transacciones = False
        # Armarla con zonda.nft no le arregla las reglas (como un nft que las muestra de otra forma).
        self.queda_rara = False
        # Lo que pasa justo antes de la próxima transacción (por ejemplo, que el reloj avance).
        self.antes_de_la_proxima: list = []

    def poner(self, ip: str, segundos: int | None) -> None:
        """Un equipo que ya estaba bloqueado, como si lo hubiera puesto una vuelta anterior."""
        self.existe = True
        self.elementos[ip] = (segundos, None if segundos is None else self.reloj + segundos)

    def pasar(self, segundos: int) -> None:
        self.reloj += segundos

    def quedan(self) -> dict[str, int | None]:
        """IP → segundos que le quedan (None: sin vencimiento)."""
        self._vencer()
        return {ip: None if vence is None else vence - self.reloj for ip, (_, vence) in self.elementos.items()}

    def _vencer(self) -> None:
        self.elementos = {ip: e for ip, e in self.elementos.items() if e[1] is None or e[1] > self.reloj}

    def __call__(self, argumentos: list[str], entrada: str | None = None) -> tuple[int, str, str]:
        assert argumentos[0] == "nft"
        orden = argumentos[1:]
        self.ordenes.append(orden)
        self._vencer()
        if self.sin_permiso:
            return 1, "", "Error: Could not process rule: Operation not permitted\n"
        if orden == LEER:
            if not self.existe:
                return 1, "", "Error: No such file or directory\nlist table inet zonda\n                 ^^^^^\n"
            elementos = [
                ip if timeout is None else {"elem": {"val": ip, "timeout": timeout, "expires": vence - self.reloj}}
                for ip, (timeout, vence) in sorted(self.elementos.items())
            ]
            return 0, json.dumps(salida_nft(elementos, reglas=self.reglas)), ""
        if orden == ["-f", str(TABLA_NFT)]:
            assert "delete table inet zonda" in TABLA_NFT.read_text(encoding="utf-8")
            self.existe, self.elementos = True, {}
            if not self.queda_rara:
                self.reglas = [regla("saddr"), regla("daddr")]
            return 0, "", ""
        assert orden == ["-f", "/dev/stdin"], f"Una orden de nft que el programa no usa: {orden}"
        return self._transaccion(entrada)

    def _transaccion(self, guion: str) -> tuple[int, str, str]:
        self.transacciones.append(guion)
        while self.antes_de_la_proxima:
            self.antes_de_la_proxima.pop(0)()
        self._vencer()
        if self.fallan_transacciones or not self.existe:
            return 1, "", "Error: Could not process rule: Device or resource busy\n"
        elementos = dict(self.elementos)
        for linea in guion.splitlines():
            partes = LINEA.fullmatch(linea)
            assert partes, f"Una línea que el programa no debería mandarle a nft: {linea!r}"
            accion, ip, tiempo = partes.groups()
            if accion == "delete":
                if ip not in elementos:
                    # Se cae la transacción entera, como en nft.
                    return 1, "", "Error: Could not process rule: No such file or directory\n"
                del elementos[ip]
            elif ip not in elementos:
                # Agregar uno que ya está no le cambia el vencimiento: para eso hay que sacarlo y ponerlo.
                timeout = None if tiempo is None else segundos_de(tiempo)
                elementos[ip] = (timeout, None if timeout is None else self.reloj + timeout)
        self.elementos = elementos
        return 0, "", ""


def pedido(bloqueo_id, ip, segundos=3600) -> dict:
    return {"id": bloqueo_id, "ip": ip, "segundos": segundos}


class ServidorFalso:
    """Hace del servidor de Zonda: contesta los bloqueos que se le cargan y guarda los informes."""

    url = "http://10.0.2.2:8000"

    def __init__(self, *bloqueos) -> None:
        self.bloqueos = list(bloqueos)
        self.informes: list[dict] = []
        self.caido = False
        self.informe_falla = False
        # Para contestar otra cosa en lugar de la lista.
        self.contestar = None

    def pedir(self, metodo: str, ruta: str, cuerpo: dict | None = None):
        if self.caido:
            raise agente.SinConexion("[Errno 111] Connection refused")
        if (metodo, ruta) == ("GET", "/firewall/bloqueos"):
            if self.contestar is not None:
                return self.contestar()
            return {"bloqueos": self.bloqueos, "horaServidor": "2026-10-31T12:00:00-03:00"}
        assert (metodo, ruta) == ("POST", "/firewall/informe")
        if self.informe_falla:
            raise agente.ErrorDelServidor("el servidor contestó 500: Internal Server Error")
        self.informes.append(cuerpo)
        return {"aplicados": len(cuerpo["aplicados"]), "rechazados": len(cuerpo["rechazados"])}


class ServidorDeZonda:
    """El servidor de verdad (TestClient), con el token del firewall."""

    url = "http://testserver"

    def __init__(self, cliente) -> None:
        self.cliente = cliente

    def pedir(self, metodo: str, ruta: str, cuerpo: dict | None = None):
        respuesta = self.cliente.request(metodo, ruta, json=cuerpo, headers=FIREWALL)
        if respuesta.status_code >= 400:
            raise agente.ErrorDelServidor(f"el servidor contestó {respuesta.status_code}: {respuesta.text}")
        return respuesta.json()


def un_firewall(nft: NftFalso, servidor=None, nunca: str = "192.168.20.0/24", tope: int = 20, probar: bool = False, del_servidor: str = ""):
    """El programa del firewall con nft simulado; `del_servidor` hace de las direcciones del servidor de Zonda."""
    return agente.Agente(
        servidor if servidor is not None else ServidorFalso(),
        agente.Nft(TABLA_NFT, correr=nft),
        agente.redes(nunca),
        tope,
        probar=probar,
        direcciones=lambda url: agente.redes(del_servidor),
    )


# ---- La tabla de Zonda ----


def test_lee_la_tabla_de_zonda_como_la_muestra_nft():
    elementos = [CONTABILIDAD, {"elem": {"val": RRHH, "timeout": 3600, "expires": 3542}}]
    assert agente.leer_tabla(json.dumps(salida_nft(elementos))) == {CONTABILIDAD: None, RRHH: 3542}
    # Vacía, nft no muestra la lista de elementos; con una sola bandera, la puede mostrar como texto.
    assert agente.leer_tabla(json.dumps(salida_nft())) == {}
    assert agente.leer_tabla(json.dumps(salida_nft(banderas="timeout"))) == {}
    # Lo que no entiende de más, lo saltea.
    con_algo_de_mas = salida_nft()
    con_algo_de_mas["nftables"].append("algo de una versión nueva")
    assert agente.leer_tabla(json.dumps(con_algo_de_mas)) == {}


ROTAS = {
    "no es la salida de nft": "Error: syntax error",
    "nftables no es una lista": json.dumps({"nftables": {"set": {}}}),
    "sin el conjunto": json.dumps({"nftables": [o for o in salida_nft()["nftables"] if "set" not in o]}),
    "el conjunto sin vencimientos": json.dumps(salida_nft(banderas=[])),
    "la cadena descarta todo": json.dumps(salida_nft(politica="drop")),
    "la cadena en otro gancho": json.dumps(salida_nft(gancho="input")),
    "una regla que acepta": json.dumps(salida_nft(reglas=[regla("saddr", "accept"), regla("daddr")])),
    "le falta una regla": json.dumps(salida_nft(reglas=[regla("saddr")])),
    "una regla de más": json.dumps(salida_nft(reglas=[regla("saddr"), regla("daddr"), regla("daddr")])),
    "una regla con otra condición": json.dumps(
        salida_nft(reglas=[regla("saddr", "drop", {"match": {"op": "==", "left": {"payload": {"protocol": "tcp", "field": "dport"}}, "right": 22}}), regla("daddr")])
    ),
    "una regla sobre IPv6": json.dumps(salida_nft(reglas=[regla("saddr", protocolo="ip6"), regla("daddr")])),
    "una regla con algo que no es una expresión": json.dumps(
        salida_nft(reglas=[{"rule": {"chain": "antes_del_filtro", "expr": ["ip saddr @bloqueados", {"drop": None}]}}, regla("daddr")])
    ),
}


@pytest.mark.parametrize("salida", list(ROTAS.values()), ids=list(ROTAS))
def test_una_tabla_que_no_es_la_de_zonda_no_se_acepta(salida):
    with pytest.raises(agente.TablaRota):
        agente.leer_tabla(salida)


def test_si_la_tabla_falta_la_arma_y_pone_lo_que_pide_el_servidor(capsys):
    nft = NftFalso()
    servidor = ServidorFalso(pedido("b-1", RRHH), pedido("b-2", CONTABILIDAD, None))

    assert un_firewall(nft, servidor).vuelta()

    assert nft.ordenes[:2] == [LEER, ["-f", str(TABLA_NFT)]]
    assert nft.transacciones == [
        "add element inet zonda bloqueados { 192.168.30.50 timeout 1h }\nadd element inet zonda bloqueados { 192.168.40.50 }\n"
    ]
    assert nft.quedan() == {RRHH: 3600, CONTABILIDAD: None}
    assert servidor.informes == [{"aplicados": ["b-1", "b-2"], "rechazados": [], "nuncaBloquear": ["192.168.20.0/24"]}]
    salida = capsys.readouterr().out
    assert "La tabla de Zonda no está" in salida
    assert "Bloqueado 192.168.30.50 por 1h (b-1)" in salida and "Bloqueado 192.168.40.50 hasta desbloquear (b-2)" in salida


def test_si_alguien_recarga_las_reglas_o_cambia_la_tabla_la_arma_de_nuevo(capsys):
    nft = NftFalso()
    firewall = un_firewall(nft, ServidorFalso(pedido("b-1", RRHH)))
    assert firewall.vuelta()

    # systemctl restart nftables: nftables.conf empieza con flush ruleset y se lleva la tabla de Zonda.
    nft.existe, nft.elementos = False, {}
    assert firewall.vuelta()
    assert nft.quedan() == {RRHH: 3600}

    # Alguien cambia una regla para que acepte: se arma de nuevo, con el bloqueo.
    nft.reglas = [regla("saddr", "accept"), regla("daddr")]
    assert firewall.vuelta()
    assert nft.reglas == [regla("saddr"), regla("daddr")]
    assert nft.quedan() == {RRHH: 3600}
    assert "La tabla de Zonda tiene las reglas cambiadas: se armó de nuevo" in capsys.readouterr().out


def test_si_la_tabla_no_queda_bien_ni_armandola_no_bloquea_nada(capsys):
    # Por ejemplo, un nft que muestra la tabla de otra forma: el programa no la reconoce y no pone nada.
    nft = NftFalso()
    nft.existe, nft.queda_rara = True, True
    nft.reglas = [regla("saddr", "accept"), regla("daddr")]
    servidor = ServidorFalso(pedido("b-1", RRHH))

    assert not un_firewall(nft, servidor).vuelta()

    assert (nft.transacciones, servidor.informes[0]["aplicados"]) == ([], [])
    assert "No se pudo cambiar la tabla de Zonda: la tabla de Zonda tiene las reglas cambiadas, también después de armarla" in capsys.readouterr().out


# ---- Lo que cambia en la tabla ----


def test_agrega_lo_que_falta_saca_lo_que_ya_no_esta_y_no_toca_lo_demas(capsys):
    nft = NftFalso()
    nft.poner(RRHH, 3600)
    nft.pasar(100)
    servidor = ServidorFalso(pedido("b-1", RRHH, 3500), pedido("b-2", CONTABILIDAD, 28800))
    firewall = un_firewall(nft, servidor)

    assert firewall.vuelta()
    assert nft.transacciones == ["add element inet zonda bloqueados { 192.168.40.50 timeout 8h }\n"]
    assert nft.quedan() == {RRHH: 3500, CONTABILIDAD: 28800}

    # Alguien desbloqueó pc-rrhh en Zonda: sale en la vuelta siguiente.
    servidor.bloqueos = [pedido("b-2", CONTABILIDAD, 28790)]
    assert firewall.vuelta()
    assert nft.transacciones[-1] == "delete element inet zonda bloqueados { 192.168.30.50 }\n"
    assert nft.quedan() == {CONTABILIDAD: 28800}

    # Sin cambios no hay transacción, pero el informe sale igual: así el servidor sabe que el firewall está.
    assert firewall.vuelta()
    assert len(nft.transacciones) == 2
    assert [informe["aplicados"] for informe in servidor.informes] == [["b-1", "b-2"], ["b-2"], ["b-2"]]
    assert "Desbloqueado 192.168.30.50" in capsys.readouterr().out


def test_si_cambia_la_duracion_lo_saca_y_lo_vuelve_a_poner():
    nft = NftFalso()
    nft.poner(RRHH, 3600)
    nft.pasar(600)
    # 3.000 segundos en el núcleo y 3.010 en el servidor: una diferencia chica no cambia nada.
    servidor = ServidorFalso(pedido("b-1", RRHH, 3010))
    firewall = un_firewall(nft, servidor)
    assert firewall.vuelta()
    assert nft.transacciones == []

    # Un administrador lo lleva a 8 horas desde ahora.
    servidor.bloqueos = [pedido("b-1", RRHH, 28800)]
    assert firewall.vuelta()
    assert nft.transacciones[-1] == (
        "delete element inet zonda bloqueados { 192.168.30.50 }\nadd element inet zonda bloqueados { 192.168.30.50 timeout 8h }\n"
    )
    assert nft.quedan() == {RRHH: 28800}

    # Y después lo deja hasta desbloquear.
    servidor.bloqueos = [pedido("b-1", RRHH, None)]
    assert firewall.vuelta()
    assert nft.quedan() == {RRHH: None}
    assert [informe["aplicados"] for informe in servidor.informes] == [["b-1"]] * 3


def test_hasta_desbloquear_no_vence_y_lo_demas_vence_solo_aunque_el_servidor_este_apagado(capsys):
    nft = NftFalso()
    servidor = ServidorFalso(pedido("b-1", RRHH, 3600), pedido("b-2", CONTABILIDAD, None))
    firewall = un_firewall(nft, servidor)
    assert firewall.vuelta()
    ordenes = len(nft.ordenes)

    servidor.caido = True
    nft.pasar(3601)
    assert not firewall.vuelta()
    assert not firewall.vuelta()

    # Sin el servidor no toca nada: lo de una hora venció solo, lo otro sigue.
    assert len(nft.ordenes) == ordenes
    assert nft.quedan() == {CONTABILIDAD: None}
    nft.pasar(10 * 86400)
    assert nft.quedan() == {CONTABILIDAD: None}
    assert len(servidor.informes) == 1
    # El aviso sale una vez, no en cada vuelta.
    assert capsys.readouterr().out.count("No se pudo preguntar al servidor") == 1


def test_si_un_equipo_vence_justo_al_cambiar_la_tabla_lee_de_nuevo_y_reintenta():
    nft = NftFalso()
    nft.poner(RRHH, 5)
    # pc-rrhh ya se desbloqueó en Zonda; pc-contabilidad se acaba de bloquear.
    servidor = ServidorFalso(pedido("b-2", CONTABILIDAD))
    nft.antes_de_la_proxima.append(lambda: nft.pasar(10))

    assert un_firewall(nft, servidor).vuelta()

    # La primera transacción quería sacar a pc-rrhh, que venció en el medio, y nft la rechazó entera.
    assert nft.transacciones == [
        "delete element inet zonda bloqueados { 192.168.30.50 }\nadd element inet zonda bloqueados { 192.168.40.50 timeout 1h }\n",
        "add element inet zonda bloqueados { 192.168.40.50 timeout 1h }\n",
    ]
    assert nft.quedan() == {CONTABILIDAD: 3600}
    assert servidor.informes[0]["aplicados"] == ["b-2"]


def test_si_lo_unico_que_habia_que_sacar_vencio_justo_no_hace_falta_otra_transaccion():
    nft = NftFalso()
    nft.poner(RRHH, 5)
    nft.antes_de_la_proxima.append(lambda: nft.pasar(10))

    assert un_firewall(nft, ServidorFalso()).vuelta()

    assert (len(nft.transacciones), nft.quedan()) == (1, {})


@pytest.mark.parametrize("falla", ["sin_permiso", "fallan_transacciones"])
def test_lo_que_nft_no_pudo_poner_no_cuenta_como_aplicado(falla, capsys):
    nft = NftFalso()
    nft.poner(RRHH, 3600)
    setattr(nft, falla, True)
    servidor = ServidorFalso(pedido("b-2", CONTABILIDAD), pedido("b-3", USUARIO), pedido("b-4", "desconocido"))

    assert not un_firewall(nft, servidor, tope=1).vuelta()

    assert nft.quedan() == {RRHH: 3600}
    # Lo inválido se rechaza igual; el tope, sin saber qué tiene la tabla, espera a la vuelta siguiente.
    assert servidor.informes == [{"aplicados": [], "rechazados": [{"id": "b-4", "motivo": "invalido"}], "nuncaBloquear": ["192.168.20.0/24"]}]
    assert "No se pudo cambiar la tabla de Zonda: Error: Could not process rule" in capsys.readouterr().out


# ---- Lo que valida el firewall por su cuenta ----


INVALIDOS = [
    ("2001:db8::1", 3600),
    ("224.0.0.251", 3600),
    ("255.255.255.255", 3600),
    ("127.0.0.1", 3600),
    ("0.0.0.0", 3600),
    ("169.254.10.1", 3600),
    ("desconocido", 3600),
    (None, 3600),
    ("192.168.30.50 }; flush ruleset; {", 3600),
    ("192.168.030.050", 3600),
    (RRHH, 0),
    (RRHH, -60),
    (RRHH, 86401),
    (RRHH, "3600"),
    (RRHH, True),
    (RRHH, 3600.0),
]


@pytest.mark.parametrize("ip, segundos", INVALIDOS)
def test_rechaza_lo_que_no_es_la_ip_de_un_equipo_o_no_tiene_una_duracion_permitida(ip, segundos):
    nft = NftFalso()
    servidor = ServidorFalso(pedido("b-1", ip, segundos))

    assert un_firewall(nft, servidor).vuelta()

    assert (nft.transacciones, nft.quedan()) == ([], {})
    assert (servidor.informes[0]["aplicados"], servidor.informes[0]["rechazados"]) == ([], [{"id": "b-1", "motivo": "invalido"}])


@pytest.mark.parametrize("ip", ["192.168.20.20", "10.0.2.2"])
def test_nunca_bloquea_lo_de_su_lista_ni_al_servidor_de_zonda(ip):
    nft = NftFalso()
    servidor = ServidorFalso(pedido("b-1", ip))

    assert un_firewall(nft, servidor, nunca="192.168.20.0/24", del_servidor="10.0.2.2").vuelta()

    assert nft.quedan() == {}
    assert servidor.informes[0]["rechazados"] == [{"id": "b-1", "motivo": "nunca_bloquear"}]
    # El servidor se entera de las dos cosas, y deja de aceptar esos equipos.
    assert servidor.informes[0]["nuncaBloquear"] == ["192.168.20.0/24", "10.0.2.2/32"]


def test_la_direccion_del_servidor_se_suma_a_la_lista_si_no_esta_en_ella():
    respuestas = [agente.redes("10.0.2.2"), agente.redes("10.0.2.2"), []]
    firewall = agente.Agente(ServidorFalso(), agente.Nft(TABLA_NFT, correr=NftFalso()), agente.redes("10.0.2.0/24"), direcciones=lambda url: respuestas.pop(0))
    assert firewall.protegidas() == agente.redes("10.0.2.0/24")
    firewall.nunca = agente.redes("192.168.20.0/24")
    assert firewall.protegidas() == agente.redes("192.168.20.0/24 10.0.2.2")
    # Si ahora no se puede resolver el nombre, queda la última dirección que se conoció.
    assert firewall.protegidas() == agente.redes("192.168.20.0/24 10.0.2.2")


def test_las_direcciones_del_servidor_salen_de_su_nombre(monkeypatch):
    assert agente.direcciones_del_servidor("http://10.0.2.2:8000") == agente.redes("10.0.2.2")
    assert agente.direcciones_del_servidor("https://[::1]:8443/") == agente.redes("::1")
    assert agente.direcciones_del_servidor("sin-esquema") == []

    def sin_dns(*argumentos, **nombrados):
        raise socket.gaierror("Name or service not known")

    monkeypatch.setattr(agente.socket, "getaddrinfo", sin_dns)
    assert agente.direcciones_del_servidor("https://zonda-equipo.duckdns.org") == []


def test_respeta_el_tope_y_siguen_los_que_ya_estaban():
    nft = NftFalso()
    nft.poner(USUARIO, 3600)
    servidor = ServidorFalso(pedido("b-1", RRHH), pedido("b-2", CONTABILIDAD), pedido("b-3", USUARIO))

    assert un_firewall(nft, servidor, tope=2).vuelta()

    assert nft.quedan() == {USUARIO: 3600, RRHH: 3600}
    informe = servidor.informes[0]
    assert (informe["aplicados"], informe["rechazados"]) == (["b-3", "b-1"], [{"id": "b-2", "motivo": "tope"}])


def test_un_equipo_pedido_dos_veces_queda_con_lo_mas_largo():
    nft = NftFalso()
    servidor = ServidorFalso(pedido("b-1", RRHH, 900), pedido("b-2", RRHH, None), pedido("b-3", CONTABILIDAD, 900), pedido("b-4", CONTABILIDAD, 3600))

    assert un_firewall(nft, servidor).vuelta()

    assert nft.quedan() == {RRHH: None, CONTABILIDAD: 3600}
    assert servidor.informes[0]["aplicados"] == ["b-1", "b-2", "b-3", "b-4"]


def test_lo_que_no_trae_un_id_no_se_aplica_ni_se_cuenta():
    nft = NftFalso()
    servidor = ServidorFalso({"ip": RRHH, "segundos": 60}, pedido("b" * 65, RRHH), pedido(7, RRHH), "b-1", None)

    assert un_firewall(nft, servidor).vuelta()

    assert nft.quedan() == {}
    assert (servidor.informes[0]["aplicados"], servidor.informes[0]["rechazados"]) == ([], [])


def _token_invalido():
    raise agente.ErrorDelServidor("el servidor contestó 401: Token del firewall inválido")


@pytest.mark.parametrize("contestar", [lambda: {"error": "x"}, lambda: ["b-1"], lambda: {"bloqueos": "b-1"}, lambda: None, _token_invalido])
def test_si_el_servidor_contesta_otra_cosa_no_toca_nada(contestar):
    nft = NftFalso()
    nft.poner(RRHH, 3600)
    servidor = ServidorFalso()
    servidor.contestar = contestar

    assert not un_firewall(nft, servidor).vuelta()

    assert (nft.ordenes, nft.quedan(), servidor.informes) == ([], {RRHH: 3600}, [])


def test_si_el_informe_no_llega_lo_dice_y_lo_manda_en_la_vuelta_siguiente(capsys):
    nft = NftFalso()
    servidor = ServidorFalso(pedido("b-1", RRHH))
    servidor.informe_falla = True
    firewall = un_firewall(nft, servidor)

    assert not firewall.vuelta()
    assert nft.quedan() == {RRHH: 3600}
    servidor.informe_falla = False
    assert firewall.vuelta()
    assert servidor.informes == [{"aplicados": ["b-1"], "rechazados": [], "nuncaBloquear": ["192.168.20.0/24"]}]
    assert "No se pudo contarle al servidor qué se aplicó" in capsys.readouterr().out


def test_probar_muestra_lo_que_haria_sin_tocar_nada(capsys):
    nft = NftFalso()
    servidor = ServidorFalso(pedido("b-1", RRHH), pedido("b-2", "desconocido"))

    assert un_firewall(nft, servidor, probar=True).vuelta()

    assert (nft.ordenes, servidor.informes) == ([LEER], [])
    salida = capsys.readouterr().out
    assert "la armaría con" in salida and "Bloquearía 192.168.30.50 por 1h" in salida
    assert "Rechazaría el bloqueo b-2 (desconocido)" in salida

    # Con la tabla armada: lo que sacaría, lo que volvería a poner y cuándo no cambiaría nada.
    nft.poner(RRHH, 60)
    nft.poner(CONTABILIDAD, 3600)
    servidor.bloqueos = [pedido("b-1", RRHH)]
    assert un_firewall(nft, servidor, probar=True).vuelta()
    salida = capsys.readouterr().out
    assert "Sacaría 192.168.40.50" in salida and "Volvería a poner 192.168.30.50 por 1h" in salida
    nft.elementos, servidor.bloqueos = {}, []
    assert un_firewall(nft, servidor, probar=True).vuelta()
    assert "No cambiaría nada" in capsys.readouterr().out
    assert (nft.transacciones, servidor.informes) == ([], [])


@pytest.mark.parametrize(
    "segundos, texto",
    [(1, "1s"), (59, "59s"), (60, "1m"), (900, "15m"), (3542, "59m2s"), (3600, "1h"), (28800, "8h"), (86400, "1d"), (90061, "1d1h1m1s")],
)
def test_escribe_la_duracion_como_nft(segundos, texto):
    assert agente.duracion_nft(segundos) == texto
    assert segundos_de(texto) == segundos


def test_la_lista_de_nunca_bloquear_lleva_redes_o_direcciones():
    assert [str(red) for red in agente.redes("192.168.10.1, 192.168.20.0/24 10.0.2.5/24,,")] == ["192.168.10.1/32", "192.168.20.0/24", "10.0.2.0/24"]
    assert agente.redes("") == []
    with pytest.raises(ValueError):
        agente.redes("192.168.300.1")


# ---- Con el servidor de verdad ----


def test_con_el_servidor_de_zonda_aplica_rechaza_y_desbloquea(cliente, auth):
    alerta, protegida = de_origenes_distintos(cliente, auth, 2)
    nft = NftFalso()
    firewall = agente.Agente(ServidorDeZonda(cliente), agente.Nft(TABLA_NFT, correr=nft), agente.redes(protegida["origenIp"]), direcciones=lambda url: [])

    # Con otro token, el servidor no le contesta y el firewall no toca nada.
    cliente.app.state.config.firewall_token = "otro-token"
    assert not firewall.vuelta()
    assert nft.ordenes == []
    cliente.app.state.config.firewall_token = TOKEN_FIREWALL

    # Antes de que el firewall pregunte, el servidor no conoce su lista y acepta bloquear un equipo que está en ella.
    en_la_lista = bloquear(cliente, auth, protegida["id"], accion="lista")
    bloqueo = bloquear(cliente, auth, alerta["id"])
    assert (en_la_lista.status_code, bloqueo.status_code) == (201, 201)
    en_la_lista, bloqueo = en_la_lista.json(), bloqueo.json()
    assert firewall.vuelta()

    # El firewall pone uno y rechaza el otro; el servidor se entera de su lista y ya no acepta ese equipo.
    assert list(nft.quedan()) == [alerta["origenIp"]] and 3590 <= nft.quedan()[alerta["origenIp"]] <= 3600
    lista = cliente.get("/bloqueos", headers=auth).json()
    estados = {b["id"]: (b["estado"], b["terminadoPor"], b["detalle"]) for b in lista["bloqueos"]}
    assert estados == {bloqueo["id"]: ("aplicado", None, None), en_la_lista["id"]: ("rechazado", "Firewall", "nunca_bloquear")}
    assert (lista["firewall"]["conectado"], lista["firewall"]["nuncaBloquear"]) == (True, [protegida["origenIp"] + "/32"])
    assert bloquear(cliente, auth, protegida["id"], accion="otra-vez").status_code == 422

    # Con la alerta revisada como ataque real, un administrador lo deja hasta desbloquear: el firewall lo vuelve a
    # poner sin vencimiento.
    cliente.post(f"/alertas/{alerta['id']}/revision", json=revision(motivo="bloqueado_manualmente", comentario="Se bloqueó el acceso SSH."), headers=auth)
    permanente = {"duracion": None, "motivo": "pc-rrhh queda aislada hasta formatearla."}
    assert cliente.put(f"/bloqueos/{bloqueo['id']}/duracion", json=permanente, headers=auth).json()["estado"] == "aprobado"
    assert firewall.vuelta()
    assert nft.quedan() == {alerta["origenIp"]: None}
    assert next(b["estado"] for b in cliente.get("/bloqueos", headers=auth).json()["bloqueos"] if b["id"] == bloqueo["id"]) == "aplicado"

    # Desbloquear lo saca en la vuelta siguiente.
    assert cliente.post(f"/bloqueos/{bloqueo['id']}/desbloqueo", json={"motivo": "Se formateó pc-rrhh."}, headers=auth).status_code == 200
    assert firewall.vuelta()
    assert nft.quedan() == {}
    assert lo_que_bloquea_el_firewall(cliente) == {}


# ---- Cómo le habla al servidor ----


@pytest.fixture
def servidor_http():
    """Un servidor HTTP de verdad en 127.0.0.1, para probar cómo pregunta el programa (urllib)."""
    vistos: list[tuple] = []

    class Manejador(http.server.BaseHTTPRequestHandler):
        def _contestar(self):
            largo = int(self.headers.get("Content-Length") or 0)
            vistos.append((self.command, self.path, self.headers, self.rfile.read(largo)))
            codigo, cabeceras, cuerpo = 200, {}, b""
            if self.path == "/firewall/bloqueos":
                cuerpo = json.dumps({"bloqueos": [pedido("b-1", RRHH)], "horaServidor": "2026-10-31T12:00:00-03:00"}).encode()
            elif self.path == "/firewall/informe":
                cuerpo = b'{"aplicados": 0, "rechazados": 0}'
            elif self.path == "/redirige":
                codigo, cabeceras = 307, {"Location": "/firewall/bloqueos"}
            elif self.path == "/grande":
                cuerpo = b" " * 2000
            elif self.path == "/texto":
                cuerpo = b"<html>hola</html>"
            else:
                codigo, cuerpo = 401, '{"detail": "Token del firewall inválido"}'.encode()
            self.send_response(codigo)
            for nombre, valor in cabeceras.items():
                self.send_header(nombre, valor)
            self.send_header("Content-Length", str(len(cuerpo)))
            self.end_headers()
            self.wfile.write(cuerpo)

        do_GET = do_POST = _contestar

        def log_message(self, *argumentos):
            pass

    servidor = http.server.ThreadingHTTPServer(("127.0.0.1", 0), Manejador)
    threading.Thread(target=servidor.serve_forever, daemon=True).start()
    yield f"http://127.0.0.1:{servidor.server_address[1]}", vistos
    servidor.shutdown()
    servidor.server_close()


def test_le_pregunta_al_servidor_con_su_token_sin_proxies_ni_redirecciones(servidor_http, monkeypatch):
    url, vistos = servidor_http
    # Un proxy en el entorno no se usa: el token va derecho al servidor.
    monkeypatch.setenv("HTTP_PROXY", "http://127.0.0.1:9")
    servidor = agente.Servidor(url + "/", "token-de-prueba")

    assert servidor.pedir("GET", "/firewall/bloqueos")["bloqueos"] == [pedido("b-1", RRHH)]
    assert servidor.pedir("POST", "/firewall/informe", {"aplicados": ["b-1"]}) == {"aplicados": 0, "rechazados": 0}

    (_, _, cabeceras_get, _), (_, _, cabeceras_post, cuerpo) = vistos
    assert cabeceras_get["X-Token-Firewall"] == cabeceras_post["X-Token-Firewall"] == "token-de-prueba"
    assert cabeceras_post["Content-Type"] == "application/json"
    assert json.loads(cuerpo) == {"aplicados": ["b-1"]}
    with pytest.raises(agente.ErrorDelServidor, match="307"):
        servidor.pedir("GET", "/redirige")
    # No siguió la redirección: el token no fue a ningún otro lado.
    assert [ruta for _, ruta, _, _ in vistos] == ["/firewall/bloqueos", "/firewall/informe", "/redirige"]
    with pytest.raises(agente.ErrorDelServidor, match="401: .*inválido"):
        servidor.pedir("GET", "/otra")
    with pytest.raises(agente.ErrorDelServidor, match="no es JSON"):
        servidor.pedir("GET", "/texto")
    monkeypatch.setattr(agente, "MAXIMO_RESPUESTA", 1000)
    with pytest.raises(agente.ErrorDelServidor, match="demasiado grande"):
        servidor.pedir("GET", "/grande")


def test_si_el_servidor_no_esta_es_sin_conexion():
    with socket.socket() as libre:
        libre.bind(("127.0.0.1", 0))
        puerto = libre.getsockname()[1]
    with pytest.raises(agente.SinConexion):
        agente.Servidor(f"http://127.0.0.1:{puerto}", "token-de-prueba", espera=2).pedir("GET", "/firewall/bloqueos")


def test_nft_corre_sin_shell_y_si_no_esta_lo_dice():
    codigo, salida, error = agente.ejecutar(["programa-que-no-existe-en-zonda", "-j", "list", "ruleset"])
    assert (codigo, salida) == (127, "") and error


def test_los_cambios_le_llegan_a_nft_por_la_entrada(monkeypatch):
    # Acá hace de nft el mismo Python, que en Windows necesita su entorno para arrancar; en el firewall, nft corre
    # solo con el PATH fijo.
    monkeypatch.setattr(agente, "ENTORNO_NFT", dict(os.environ))
    programa = "import sys; print(sys.stdin.read().upper(), end=''); print('aviso', file=sys.stderr); sys.exit(3)"
    assert agente.ejecutar([sys.executable, "-c", programa], "add element\n") == (3, "ADD ELEMENT\n", "aviso\n")


# ---- El programa ----


def test_sin_token_o_con_la_configuracion_mal_no_arranca(tmp_path, monkeypatch, capsys):
    monkeypatch.delenv("ZONDA_FIREWALL_TOKEN", raising=False)
    monkeypatch.delenv("CREDENTIALS_DIRECTORY", raising=False)
    assert agente.main(["--una-vez"]) == 2
    assert agente.main(["--token-archivo", str(tmp_path / "no-esta")]) == 2

    archivo = tmp_path / "token"
    archivo.write_text("  token-de-prueba\n", encoding="utf-8")
    con_token = ["--token-archivo", str(archivo), "--una-vez"]
    assert agente.main([*con_token, "--nunca-bloquear", "192.168.300.1"]) == 2
    assert agente.main([*con_token, "--tope", "0"]) == 2
    assert agente.main([*con_token, "--tope", "257"]) == 2
    assert agente.main([*con_token, "--cada", "0.5"]) == 2
    assert agente.main([*con_token, "--servidor", "ftp://10.0.2.2"]) == 2
    salida = capsys.readouterr().out
    assert "Falta el token del firewall" in salida and "No se pudo leer el token" in salida and "no es una red" in salida


def test_el_token_sale_del_archivo_que_pasa_systemd_o_del_entorno(tmp_path, monkeypatch):
    (tmp_path / "token").write_text("token-de-systemd\n", encoding="utf-8")
    monkeypatch.setenv("CREDENTIALS_DIRECTORY", str(tmp_path))
    assert agente.leer_token(None) == "token-de-systemd"
    monkeypatch.delenv("CREDENTIALS_DIRECTORY")
    monkeypatch.setenv("ZONDA_FIREWALL_TOKEN", " token-del-entorno ")
    assert agente.leer_token(None) == "token-del-entorno"


def test_de_punta_a_punta_con_un_servidor_http_y_sin_nft(servidor_http, monkeypatch, capsys):
    url, vistos = servidor_http
    monkeypatch.setenv("ZONDA_FIREWALL_TOKEN", "token-de-prueba")
    monkeypatch.delenv("CREDENTIALS_DIRECTORY", raising=False)
    comunes = ["--servidor", url, "--nft", "programa-que-no-existe-en-zonda", "--tabla", str(TABLA_NFT), "--nunca-bloquear", "192.168.20.0/24"]

    # Para probar, muestra lo que haría y no le cuenta nada al servidor.
    assert agente.main([*comunes, "--probar"]) == 0
    assert [ruta for _, ruta, _, _ in vistos] == ["/firewall/bloqueos"]
    salida = capsys.readouterr().out
    assert "sin cifrar" in salida and "Bloquearía 192.168.30.50 por 1h" in salida

    # De verdad, sin nft no puede aplicar nada: lo cuenta igual y termina con error.
    assert agente.main([*comunes, "--una-vez"]) == 1
    assert json.loads(vistos[-1][3]) == {"aplicados": [], "rechazados": [], "nuncaBloquear": ["192.168.20.0/24", "127.0.0.1/32"]}


def test_da_una_vuelta_cada_tantos_segundos(servidor_http, monkeypatch):
    url, vistos = servidor_http
    monkeypatch.setenv("ZONDA_FIREWALL_TOKEN", "token-de-prueba")
    monkeypatch.delenv("CREDENTIALS_DIRECTORY", raising=False)
    pausas = []

    class Basta(Exception):
        pass

    def dormir(segundos):
        pausas.append(segundos)
        if len(pausas) == 2:
            raise Basta

    monkeypatch.setattr(agente.time, "sleep", dormir)
    with pytest.raises(Basta):
        agente.main(["--servidor", url, "--nft", "programa-que-no-existe-en-zonda", "--cada", "3"])
    assert pausas == [3, 3]
    assert [ruta for _, ruta, _, _ in vistos].count("/firewall/bloqueos") == 2


# ---- Los archivos que van al firewall ----


def _valor(unidad: str, clave: str) -> str:
    """El valor de una clave de la unidad de systemd, con las líneas que siguen con barra ya unidas."""
    return next(linea.split("=", 1)[1] for linea in unidad.replace("\\\n", " ").splitlines() if linea.startswith(clave + "="))


def test_el_servicio_y_el_instalador_usan_lo_que_entiende_el_programa():
    servicio = (CARPETA / "zonda-firewall.service").read_text(encoding="utf-8")
    instalador = (CARPETA / "instalar.sh").read_text(encoding="utf-8")

    comando = shlex.split(_valor(servicio, "ExecStart"))
    assert comando[:4] == ["/usr/bin/python3", "-I", "-u", "/opt/zonda-firewall/agente_firewall.py"]
    argumentos = agente.leer_argumentos([parte.replace("%d", "/run/credentials/zonda-firewall.service") for parte in comando[4:]])
    assert (argumentos.servidor, argumentos.una_vez, argumentos.probar) == ("http://10.0.2.2:8000", False, False)
    # Lo que nunca se bloquea en el laboratorio: las cuatro puertas de enlace, los servidores y la red de VirtualBox.
    assert [str(red) for red in agente.redes(argumentos.nunca_bloquear)] == [
        "192.168.10.1/32", "192.168.20.1/32", "192.168.30.1/32", "192.168.40.1/32", "192.168.20.0/24", "10.0.2.0/24",
    ]
    # Corre sin root, con un solo permiso del núcleo, y el token le llega por systemd.
    assert _valor(servicio, "DynamicUser") == "yes"
    assert _valor(servicio, "AmbientCapabilities") == _valor(servicio, "CapabilityBoundingSet") == "CAP_NET_ADMIN"
    assert _valor(servicio, "LoadCredential") == "token:/etc/zonda-firewall/token"
    assert argumentos.token_archivo == "/run/credentials/zonda-firewall.service/token"
    # El instalador pone cada archivo donde lo buscan el servicio y el programa.
    for destino in ("/opt/zonda-firewall/agente_firewall.py", argumentos.tabla, "/etc/systemd/system/zonda-firewall.service"):
        assert f" {destino}\n" in instalador


def test_la_tabla_es_la_que_verifica_el_programa_y_todo_va_con_fin_de_linea_de_linux():
    tabla = TABLA_NFT.read_text(encoding="utf-8")
    for linea in (
        "add table inet zonda",
        "delete table inet zonda",
        "set bloqueados {",
        "type ipv4_addr",
        "flags timeout",
        "chain antes_del_filtro {",
        "type filter hook forward priority filter - 10; policy accept;",
        "ip saddr @bloqueados counter drop",
        "ip daddr @bloqueados counter drop",
    ):
        assert linea in tabla
    for archivo in ("agente_firewall.py", "zonda.nft", "zonda-firewall.service", "instalar.sh"):
        assert b"\r" not in (CARPETA / archivo).read_bytes(), archivo
