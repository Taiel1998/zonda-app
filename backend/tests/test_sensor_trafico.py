"""El programa del sensor que resume el conn.log de Zeek para el tablero (herramientas/sensor,
decisión 49): cómo lee el registro, cómo suma y reparte, cómo sigue el archivo cuando Zeek lo rota y que
lo que manda es lo que el servidor acepta."""

import gzip
import importlib.util
import json
import os
from dataclasses import replace
from datetime import datetime, timedelta
from pathlib import PurePosixPath

import pytest
from fastapi.testclient import TestClient

from app.config import RAIZ
from app.main import crear_app
from tests.conftest import cabecera, parear

_spec = importlib.util.spec_from_file_location("metricas_trafico", RAIZ / "herramientas" / "sensor" / "metricas_trafico.py")
sensor = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(sensor)

CAMPOS = [
    "ts", "uid", "id.orig_h", "id.orig_p", "id.resp_h", "id.resp_p", "proto", "service", "duration",
    "orig_bytes", "resp_bytes", "conn_state", "local_orig", "local_resp", "missed_bytes", "history",
    "orig_pkts", "orig_ip_bytes", "resp_pkts", "resp_ip_bytes", "tunnel_parents",
]
CABECERA_ZEEK = (
    "#separator \\x09\n#set_separator\t,\n#empty_field\t(empty)\n#unset_field\t-\n#path\tconn\n"
    "#open\t2026-10-30-18-00-00\n#fields\t" + "\t".join(CAMPOS) + "\n#types\t" + "\t".join(["string"] * len(CAMPOS)) + "\n"
)
# Un momento del 30/10/2026; BASE es el comienzo de su minuto.
T0 = 1_793_390_000


def linea(ts, orig="192.168.30.50", resp="192.168.20.20", puerto=22, proto="tcp", servicio="-", duracion="-",
          estado="SF", ip_bytes=(0, 0), paquetes=(0, 0)) -> str:
    valores = {
        "ts": f"{ts:.6f}", "uid": "C1", "id.orig_h": orig, "id.orig_p": "50000", "id.resp_h": resp,
        "id.resp_p": str(puerto), "proto": proto, "service": servicio, "duration": str(duracion),
        "orig_bytes": "-", "resp_bytes": "-", "conn_state": estado, "local_orig": "-", "local_resp": "-",
        "missed_bytes": "0", "history": "ShADadFf", "orig_pkts": str(paquetes[0]), "orig_ip_bytes": str(ip_bytes[0]),
        "resp_pkts": str(paquetes[1]), "resp_ip_bytes": str(ip_bytes[1]), "tunnel_parents": "-",
    }
    return "\t".join(valores[c] for c in CAMPOS) + "\n"


BASE = (T0 // 60) * 60


def test_lee_el_registro_de_zeek_en_tsv_y_en_json():
    registro = sensor.leer_registro(linea(BASE + 1.5, servicio="ssh"), CAMPOS)
    assert registro["id.orig_h"] == "192.168.30.50" and registro["service"] == "ssh"
    assert sensor.leer_registro("#close\t2026-10-30\n", CAMPOS) is None
    assert sensor.leer_registro("cortada\ta\n", CAMPOS) is None
    json_ = sensor.leer_registro(json.dumps({"ts": BASE, "id.orig_h": "10.0.0.1", "id.resp_h": "10.0.0.2"}) + "\n", None)
    assert json_["id.resp_h"] == "10.0.0.2"


@pytest.mark.parametrize(
    "registro, esperado",
    [
        ({"service": "ssh", "proto": "tcp", "id.resp_p": "22"}, "ssh"),
        ({"service": "gssapi,smb,krb", "proto": "tcp", "id.resp_p": "445"}, "gssapi"),
        ({"service": "-", "proto": "tcp", "id.resp_p": "22"}, "ssh"),
        ({"service": "-", "proto": "udp", "id.resp_p": "53"}, "dns"),
        ({"service": "-", "proto": "icmp", "id.resp_p": "0"}, "icmp"),
        ({"service": "-", "proto": "tcp", "id.resp_p": "31337"}, "otro"),
        ({"service": "Raro;DROP", "proto": "tcp", "id.resp_p": "1"}, "rarodrop"),
    ],
)
def test_el_servicio_es_el_de_zeek_o_el_del_puerto(registro, esperado):
    assert sensor.servicio_de(registro) == esperado


def test_suma_conexiones_fallidas_y_quien_hablo_con_quien():
    acumulado = sensor.Acumulador()
    acumulado.sumar(sensor.leer_registro(linea(BASE + 1, servicio="ssh", ip_bytes=(300, 700), paquetes=(5, 5)), CAMPOS))
    acumulado.sumar(sensor.leer_registro(linea(BASE + 2, puerto=8081, estado="S0", ip_bytes=(60, 0), paquetes=(1, 0)), CAMPOS))
    acumulado.sumar(sensor.leer_registro(linea(BASE + 3, servicio="ssh", estado="REJ", ip_bytes=(60, 40), paquetes=(1, 1)), CAMPOS))
    assert acumulado.tramos[(BASE, "ssh")] == [2, 1, 1100, 12]
    assert acumulado.tramos[(BASE, "otro")] == [1, 1, 60, 1]
    assert acumulado.pares[(BASE, "192.168.30.50", "192.168.20.20", "ssh")] == [2, 1, 1100]
    # Sin hora o sin direcciones no se cuenta.
    assert not acumulado.sumar({"ts": "-", "id.orig_h": "1.1.1.1", "id.resp_h": "2.2.2.2"})


def test_reparte_bytes_y_paquetes_entre_los_tramos_que_duro_sin_perder_nada():
    partes = sensor.repartir(BASE + 2.5, 10.0, 1000, 21)
    assert [t for t, _, _ in partes] == [BASE, BASE + 5, BASE + 10]
    assert sum(b for _, b, _ in partes) == 1000 and sum(p for _, _, p in partes) == 21
    assert partes[0][1] == 250 and partes[1][1] == 500
    assert sensor.repartir(BASE, 0, 50, 2) == [(BASE, 50, 2)]
    assert sensor.repartir(BASE, 3.0, 0, 0) == []


def test_sigue_el_archivo_con_lineas_a_medio_escribir(tmp_path):
    ruta = tmp_path / "conn.log"
    ruta.write_text(CABECERA_ZEEK + linea(BASE + 1), encoding="utf-8")
    seguidor = sensor.Seguidor(ruta)
    assert len(list(seguidor.lineas_nuevas())) == 1
    completa = linea(BASE + 2)
    with open(ruta, "a", encoding="utf-8", newline="\n") as archivo:
        archivo.write(completa[:20])
        archivo.flush()
        assert list(seguidor.lineas_nuevas()) == []
        archivo.write(completa[20:])
    assert [r["ts"] for r in seguidor.lineas_nuevas()] == [f"{BASE + 2:.6f}"]


def test_sigue_desde_donde_quedo_si_se_reinicia(tmp_path):
    ruta = tmp_path / "conn.log"
    ruta.write_text(CABECERA_ZEEK + linea(BASE + 1) + linea(BASE + 2), encoding="utf-8")
    primero = sensor.Seguidor(ruta)
    assert len(list(primero.lineas_nuevas())) == 2
    inodo, posicion = primero.inodo, primero.posicion()
    primero.archivo.close()
    with open(ruta, "a", encoding="utf-8", newline="\n") as archivo:
        archivo.write(linea(BASE + 3))
    segundo = sensor.Seguidor(ruta, inodo=inodo, posicion=posicion)
    assert [r["ts"] for r in segundo.lineas_nuevas()] == [f"{BASE + 3:.6f}"]
    # La primera vez, desde el final: no cuenta lo que ya estaba.
    assert list(sensor.Seguidor(ruta, desde_el_final=True).lineas_nuevas()) == []


def test_si_zeek_empieza_el_archivo_de_nuevo_lo_lee_desde_el_principio(tmp_path):
    ruta = tmp_path / "conn.log"
    ruta.write_text(CABECERA_ZEEK + linea(BASE + 1) + linea(BASE + 2), encoding="utf-8")
    seguidor = sensor.Seguidor(ruta)
    list(seguidor.lineas_nuevas())
    ruta.write_text(CABECERA_ZEEK + linea(BASE + 9), encoding="utf-8")
    assert [r["ts"] for r in seguidor.lineas_nuevas()] == [f"{BASE + 9:.6f}"]


@pytest.mark.skipif(os.name == "nt", reason="Windows no deja renombrar un archivo abierto; Zeek corre en Linux")
def test_cuando_zeek_rota_el_archivo_termina_el_viejo_y_sigue_con_el_nuevo(tmp_path):
    ruta = tmp_path / "conn.log"
    ruta.write_text(CABECERA_ZEEK + linea(BASE + 1), encoding="utf-8")
    seguidor = sensor.Seguidor(ruta)
    list(seguidor.lineas_nuevas())
    with open(ruta, "a", encoding="utf-8") as archivo:
        archivo.write(linea(BASE + 2))
    os.rename(ruta, tmp_path / "conn.viejo.log")
    ruta.write_text(CABECERA_ZEEK + linea(BASE + 3), encoding="utf-8")
    assert [r["ts"] for r in seguidor.lineas_nuevas()] == [f"{BASE + 2:.6f}", f"{BASE + 3:.6f}"]


def test_la_hora_en_que_zeek_cerro_un_archivo_viejo_sale_del_nombre():
    assert sensor.fin_del_archivo(PurePosixPath("2026-10-29/conn.10:00:00-11:00:00.log.gz")) == datetime(2026, 10, 29, 11)
    # El de las 23 cierra a la medianoche, que ya es el día siguiente.
    assert sensor.fin_del_archivo(PurePosixPath("2026-10-29/conn.23:00:00-00:00:00.log.gz")) == datetime(2026, 10, 30)
    assert sensor.fin_del_archivo(PurePosixPath("2026-10-29/dns.10:00:00-11:00:00.log.gz")) is None
    assert sensor.fin_del_archivo(PurePosixPath("otra-carpeta/conn.10:00:00-11:00:00.log.gz")) is None


@pytest.mark.skipif(os.name == "nt", reason="Windows no deja dos puntos en los nombres de archivo; Zeek corre en Linux")
def test_elige_los_archivos_viejos_por_la_hora_en_que_zeek_los_cerro(tmp_path):
    ayer = (datetime.now() - timedelta(days=1)).strftime("%Y-%m-%d")
    hace_mucho = (datetime.now() - timedelta(days=30)).strftime("%Y-%m-%d")
    for dia, nombre in [(ayer, "conn.10:00:00-11:00:00.log.gz"), (ayer, "conn.23:00:00-00:00:00.log.gz"), (hace_mucho, "conn.10:00:00-11:00:00.log.gz"), (ayer, "dns.10:00:00-11:00:00.log.gz")]:
        (tmp_path / dia).mkdir(exist_ok=True)
        (tmp_path / dia / nombre).write_bytes(b"")
    elegidos = sensor.archivos_viejos(tmp_path, 72)
    assert [r.name for r in elegidos] == ["conn.10:00:00-11:00:00.log.gz", "conn.23:00:00-00:00:00.log.gz"]
    assert sensor.fin_del_archivo(elegidos[1]).date() == (datetime.now()).date()
    corte = datetime.strptime(ayer, "%Y-%m-%d").replace(hour=12)
    assert [r.name for r in sensor.archivos_viejos(tmp_path, 72, antes_de=corte)] == ["conn.10:00:00-11:00:00.log.gz"]


def test_lee_un_archivo_viejo_comprimido(tmp_path):
    ruta = tmp_path / "conn.log.gz"
    with gzip.open(ruta, "wt", encoding="utf-8") as archivo:
        archivo.write(CABECERA_ZEEK + linea(BASE + 1) + linea(BASE + 2))
    assert len(list(sensor.leer_archivo_viejo(ruta))) == 2


class ServidorDePrueba:
    """Hace de servidor: manda cada envío a la API de verdad (TestClient), o falla a partir de un envío."""

    def __init__(self, cliente: TestClient | None = None, falla_desde: int | None = None) -> None:
        self.cliente = cliente
        self.falla_desde = falla_desde
        self.cuerpos: list[dict] = []

    def mandar(self, cuerpo: dict) -> str:
        if self.falla_desde is not None and len(self.cuerpos) >= self.falla_desde:
            return "reintentar"
        self.cuerpos.append(cuerpo)
        if self.cliente is None:
            return "ok"
        respuesta = self.cliente.post("/ingesta/trafico", headers={"X-Token-Ingesta": "token-de-prueba-del-sensor"}, json=cuerpo)
        assert respuesta.status_code == 200, respuesta.text
        return "ok"


def test_si_se_corta_a_mitad_no_vuelve_a_mandar_lo_que_ya_salio(monkeypatch):
    monkeypatch.setattr(sensor, "FILAS_POR_ENVIO", 2)
    acumulado = sensor.Acumulador()
    for i in range(5):
        acumulado.sumar(sensor.leer_registro(linea(BASE + 5 * i, servicio="ssh", ip_bytes=(10, 10), paquetes=(1, 1)), CAMPOS))
    servidor = ServidorDePrueba(falla_desde=1)
    assert not sensor.mandar_todo(acumulado, servidor)
    assert len(acumulado.tramos) == 3
    servidor.falla_desde = None
    assert sensor.mandar_todo(acumulado, servidor)
    enviados = [t["t"] for cuerpo in servidor.cuerpos for t in cuerpo["tramos"]]
    assert sorted(enviados) == [BASE + 5 * i for i in range(5)]


def test_lo_que_manda_el_sensor_lo_acepta_el_servidor_y_lo_suma(config):
    cfg = replace(config, ingesta_token="token-de-prueba-del-sensor")
    with TestClient(crear_app(cfg)) as cliente:
        auth = cabecera(parear(cliente, "Tablero web"))
        acumulado = sensor.Acumulador()
        ahora = (int(datetime.now().timestamp()) // 60) * 60 - 600
        registros = [
            linea(ahora + 1, servicio="ssh", duracion=12.0, ip_bytes=(4000, 6000), paquetes=(30, 30)),
            linea(ahora + 2, puerto=445, estado="S0", ip_bytes=(60, 0), paquetes=(1, 0)),
            linea(ahora + 61, resp="192.168.20.1", puerto=53, proto="udp", servicio="dns", ip_bytes=(70, 120), paquetes=(1, 1)),
        ]
        for texto in registros:
            acumulado.sumar(sensor.leer_registro(texto, CAMPOS))
        assert sensor.mandar_todo(acumulado, ServidorDePrueba(cliente))
        serie = cliente.get("/trafico", params={"desde": ahora, "hasta": ahora + 120, "paso": 60}, headers=auth).json()
        assert [(p["t"], p["conexiones"], p["fallidas"]) for p in serie["total"]] == [(ahora, 2, 1), (ahora + 60, 1, 0)]
        assert sum(p["bytes"] for p in serie["total"]) == 10000 + 60 + 190
        assert set(serie["servicios"]) == {"ssh", "smb", "dns"}
        pares = cliente.get("/trafico/pares", params={"desde": ahora, "hasta": ahora + 120}, headers=auth).json()["pares"]
        assert [(p["destino"], p["servicios"]) for p in pares] == [("192.168.20.20", ["smb", "ssh"]), ("192.168.20.1", ["dns"])]
