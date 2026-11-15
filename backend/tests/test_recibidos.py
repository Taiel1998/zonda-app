"""Lo que le llega al servidor desde la red, por minuto (decisión 60): la tabla `recibidos`, que también
dice en qué minutos anduvo el servidor; GET /recibidos, para los gráficos del servidor y del firewall del
tablero web; y GET /latidos en tramos de más de un minuto, para ver la semana.
"""

import asyncio
import logging
import sqlite3
from dataclasses import replace

import pytest
from fastapi.testclient import TestClient

from app import recibidos
from app.auth import ahora
from app.main import crear_app
from tests.conftest import cabecera, parear
from tests.test_ingesta import episodio
from tests.test_latido import un_latido
from tests.test_trafico import tramo

TOKEN_INGESTA = "token-de-ingesta-de-prueba-0123456789"
INGESTA = {"X-Token-Ingesta": TOKEN_INGESTA}
TOKEN_FIREWALL = "token-del-firewall-de-prueba"
FIREWALL = {"X-Token-Firewall": TOKEN_FIREWALL}
# Una hora redonda ya pasada: lo que anota el servidor mientras corre la prueba cae después.
T0 = (int(ahora().timestamp()) // 3600) * 3600 - 3600


@pytest.fixture
def cli(config):
    cfg = replace(config, ingesta_token=TOKEN_INGESTA, firewall_token=TOKEN_FIREWALL, latido_revisar_cada=3600)
    with TestClient(crear_app(cfg)) as cliente:
        yield cliente


@pytest.fixture
def auth_cli(cli):
    return cabecera(parear(cli))


def poner(cli, minuto: int, sensor: int = 0, analisis: int = 0, firewall: int = 0) -> None:
    """Una fila ya escrita, como si el servidor hubiera andado en ese minuto."""
    with cli.app.state.base.transaccion() as con:
        con.execute("INSERT INTO recibidos (minuto, sensor, analisis, firewall) VALUES (?, ?, ?, ?)", (minuto, sensor, analisis, firewall))


def recibido(cli, auth, **parametros) -> dict:
    respuesta = cli.get("/recibidos", headers=auth, params=parametros)
    assert respuesta.status_code == 200, respuesta.text
    return respuesta.json()


def suma(serie: dict, origen: str) -> int:
    return sum(t[origen] for t in serie["tramos"])


def test_cada_envio_de_la_red_suma_a_su_origen(cli, auth_cli):
    for _ in range(2):
        assert cli.post("/ingesta/trafico", headers=INGESTA, json={"tramos": [tramo(T0)], "pares": []}).status_code == 200
    assert cli.post("/ingesta/latido", headers=INGESTA, json=un_latido()).status_code == 200
    assert cli.post("/ingesta/alertas", headers=INGESTA, json={"alertas": [episodio()]}).status_code == 200
    for _ in range(3):
        assert cli.get("/firewall/bloqueos", headers=FIREWALL).status_code == 200
    serie = recibido(cli, auth_cli)
    # Los envíos pueden caer en dos minutos si la prueba cruza el cambio de minuto.
    assert (suma(serie, "sensor"), suma(serie, "analisis"), suma(serie, "firewall")) == (2, 2, 3)
    assert serie["paso"] == 60
    assert all(t["t"] % 60 == 0 and t["minutos"] == 1 for t in serie["tramos"])


def test_lo_que_piden_la_app_y_el_tablero_no_cuenta(cli, auth_cli):
    for ruta in ("/salud", "/estado", "/alertas", "/latidos", "/recibidos"):
        assert cli.get(ruta, headers=auth_cli).status_code == 200
    serie = recibido(cli, auth_cli)
    assert (suma(serie, "sensor"), suma(serie, "analisis"), suma(serie, "firewall")) == (0, 0, 0)


def test_un_envio_rechazado_no_cuenta(cli, auth_cli):
    assert cli.post("/ingesta/latido", headers={"X-Token-Ingesta": "otro"}, json=un_latido()).status_code == 401
    assert cli.get("/firewall/bloqueos", headers={"X-Token-Firewall": "otro"}).status_code == 401
    serie = recibido(cli, auth_cli)
    assert (suma(serie, "analisis"), suma(serie, "firewall")) == (0, 0)


def test_el_servidor_anota_el_minuto_en_que_anduvo_aunque_no_le_llegue_nada(cli):
    base = cli.app.state.base
    with base.transaccion() as con:
        minuto = recibidos.anotar(con)
        recibidos.anotar(con)
        fila = con.execute("SELECT * FROM recibidos WHERE minuto = ?", (minuto,)).fetchone()
    assert minuto % 60 == 0
    assert (fila["sensor"], fila["analisis"], fila["firewall"]) == (0, 0, 0)


def test_anotar_que_anduvo_no_pisa_lo_que_ya_llego(cli):
    with cli.app.state.base.transaccion() as con:
        minuto = recibidos.anotar(con, "sensor")
        recibidos.anotar(con)
        recibidos.anotar(con, "sensor")
        assert con.execute("SELECT sensor FROM recibidos WHERE minuto = ?", (minuto,)).fetchone()[0] == 2


def test_un_minuto_prendido_sin_datos_viene_en_cero_y_uno_apagado_no_viene(cli, auth_cli):
    poner(cli, T0, sensor=12, analisis=3, firewall=6)
    poner(cli, T0 + 60)
    # En T0 + 120 el servidor estuvo apagado: no hay fila.
    poner(cli, T0 + 180, sensor=11)
    serie = recibido(cli, auth_cli, desde=T0, hasta=T0 + 240)
    assert [t["t"] for t in serie["tramos"]] == [T0, T0 + 60, T0 + 180]
    assert serie["tramos"][0] == {"t": T0, "minutos": 1, "sensor": 12, "analisis": 3, "firewall": 6}
    assert serie["tramos"][1] == {"t": T0 + 60, "minutos": 1, "sensor": 0, "analisis": 0, "firewall": 0}
    assert (serie["desde"], serie["hasta"]) == (T0, T0 + 240)


def test_con_un_paso_mas_largo_junta_los_minutos_de_cada_tramo(cli, auth_cli):
    poner(cli, T0, sensor=12, firewall=6)
    poner(cli, T0 + 60, sensor=10, analisis=3, firewall=6)
    poner(cli, T0 + 300, analisis=1)
    # Con `desde` en el medio de un tramo, la ventana empieza donde empieza ese tramo.
    serie = recibido(cli, auth_cli, desde=T0 + 30, hasta=T0 + 600, paso=300)
    assert serie["paso"] == 300
    assert serie["desde"] == T0
    assert serie["tramos"] == [
        {"t": T0, "minutos": 2, "sensor": 22, "analisis": 3, "firewall": 12},
        {"t": T0 + 300, "minutos": 1, "sensor": 0, "analisis": 1, "firewall": 0},
    ]


def test_sin_ventana_es_la_ultima_hora(cli, auth_cli):
    poner(cli, T0 - 3600, sensor=5)
    with cli.app.state.base.transaccion() as con:
        minuto = recibidos.anotar(con, "firewall")
    serie = recibido(cli, auth_cli)
    assert minuto in [t["t"] for t in serie["tramos"]]
    assert suma(serie, "sensor") == 0
    assert serie["hasta"] - serie["desde"] <= 3600 + 60


def test_borra_lo_de_mas_de_una_semana(cli):
    base = cli.app.state.base
    viejo = T0 - 8 * 86400
    poner(cli, viejo, sensor=1)
    poner(cli, T0, sensor=1)
    assert cli.post("/ingesta/latido", headers=INGESTA, json=un_latido()).status_code == 200
    con = base.conexion()
    assert con.execute("SELECT COUNT(*) FROM recibidos WHERE minuto = ?", (viejo,)).fetchone()[0] == 0
    assert con.execute("SELECT COUNT(*) FROM recibidos WHERE minuto = ?", (T0,)).fetchone()[0] == 1


def test_pide_un_dispositivo_pareado(cli):
    assert cli.get("/recibidos").status_code == 401
    assert cli.get("/recibidos", headers=cabecera("token-falso")).status_code == 401
    assert cli.get("/recibidos", headers=INGESTA).status_code == 401


@pytest.mark.parametrize("ruta", ["/recibidos", "/latidos"])
@pytest.mark.parametrize("parametros", ["desde=2000&hasta=1000", f"desde=0&hasta={8 * 86400}", "paso=7", "paso=5", "paso=0"])
def test_una_ventana_que_no_se_puede_dibujar_da_422(cli, auth_cli, ruta, parametros):
    assert cli.get(f"{ruta}?{parametros}", headers=auth_cli).status_code == 422


def test_los_latidos_tambien_se_leen_en_tramos(cli, auth_cli):
    with cli.app.state.base.transaccion() as con:
        con.executemany(
            "INSERT INTO latidos (t, flujos, zeek_activo, perdida) VALUES (?, ?, ?, ?)",
            [(T0 + 10, 5, 1, 0.5), (T0 + 70, 7, 1, 2.0), (T0 + 310, 1, 0, None)],
        )
    respuesta = cli.get("/latidos", headers=auth_cli, params={"desde": T0, "hasta": T0 + 600, "paso": 300})
    assert respuesta.status_code == 200, respuesta.text
    serie = respuesta.json()
    assert serie["paso"] == 300
    assert serie["minutos"] == [
        {"minuto": T0, "latidos": 2, "flujos": 12, "perdidaCaptura": 2.0, "zeekActivo": True},
        {"minuto": T0 + 300, "latidos": 1, "flujos": 1, "perdidaCaptura": None, "zeekActivo": False},
    ]
    # Sin paso, como siempre: por minuto.
    por_minuto = cli.get("/latidos", headers=auth_cli, params={"desde": T0, "hasta": T0 + 600}).json()
    assert por_minuto["paso"] == 60
    assert [m["minuto"] for m in por_minuto["minutos"]] == [T0, T0 + 60, T0 + 300]


def test_el_servidor_deja_la_fila_de_cada_minuto_mientras_anda(cli):
    base = cli.app.state.base
    cfg = cli.app.state.config
    with base.transaccion() as con:
        con.execute("DELETE FROM recibidos")

    async def una_vuelta() -> None:
        tarea = asyncio.create_task(recibidos.anotar_cada_minuto(base, cfg, cada=3600))
        await asyncio.sleep(0)
        tarea.cancel()

    asyncio.run(una_vuelta())
    filas = base.conexion().execute("SELECT * FROM recibidos").fetchall()
    assert len(filas) == 1
    assert (filas[0]["sensor"], filas[0]["analisis"], filas[0]["firewall"]) == (0, 0, 0)


def test_si_una_vuelta_falla_el_servidor_sigue_anotando(cli, monkeypatch, caplog):
    base = cli.app.state.base
    cfg = cli.app.state.config
    with base.transaccion() as con:
        con.execute("DELETE FROM recibidos")
    de_verdad = recibidos.anotar
    vueltas = []

    def anotar_con_una_falla(con, origen=None, dias=7):
        vueltas.append(1)
        if len(vueltas) == 1:
            raise sqlite3.OperationalError("database is locked")
        return de_verdad(con, origen, dias)

    monkeypatch.setattr(recibidos, "anotar", anotar_con_una_falla)

    async def dos_vueltas() -> None:
        tarea = asyncio.create_task(recibidos.anotar_cada_minuto(base, cfg, cada=0))
        while len(vueltas) < 2:
            await asyncio.sleep(0)
        tarea.cancel()

    with caplog.at_level(logging.ERROR, logger="zonda.recibidos"):
        asyncio.run(dos_vueltas())
    assert "No se pudo anotar el minuto del servidor" in caplog.text
    assert base.conexion().execute("SELECT COUNT(*) FROM recibidos").fetchone()[0] == 1
