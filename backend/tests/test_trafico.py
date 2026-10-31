"""Tráfico del sensor para el tablero web (decisión 49): POST /ingesta/trafico, que suma lo que manda
el sensor cada 5 segundos, y GET /trafico y GET /trafico/pares, que se lo dan al tablero."""

from dataclasses import replace

import pytest
from fastapi.testclient import TestClient

from app.auth import ahora
from app.main import crear_app
from tests.conftest import cabecera, parear

TOKEN_INGESTA = "token-de-ingesta-de-prueba-0123456789"
CABECERA = {"X-Token-Ingesta": TOKEN_INGESTA}
# Un momento redondo de la última hora: los tramos de prueba caen dentro de la ventana por defecto.
T0 = (int(ahora().timestamp()) // 3600) * 3600 - 3600


def tramo(t: int, servicio: str = "ssh", conexiones: int = 1, fallidas: int = 0, bytes: int = 100, paquetes: int = 4) -> dict:
    return {"t": t, "servicio": servicio, "conexiones": conexiones, "fallidas": fallidas, "bytes": bytes, "paquetes": paquetes}


def par(minuto: int, origen: str = "192.168.30.50", destino: str = "192.168.20.20", servicio: str = "ssh", conexiones: int = 1, fallidas: int = 0, bytes: int = 100) -> dict:
    return {"minuto": minuto, "origen": origen, "destino": destino, "servicio": servicio, "conexiones": conexiones, "fallidas": fallidas, "bytes": bytes}


@pytest.fixture
def cfg_trafico(config):
    return replace(config, ingesta_token=TOKEN_INGESTA)


@pytest.fixture
def cli(cfg_trafico):
    with TestClient(crear_app(cfg_trafico)) as cliente:
        yield cliente


@pytest.fixture
def auth_cli(cli):
    return cabecera(parear(cli, "Tablero web"))


def mandar(cli, tramos=(), pares=()) -> dict:
    respuesta = cli.post("/ingesta/trafico", headers=CABECERA, json={"tramos": list(tramos), "pares": list(pares)})
    assert respuesta.status_code == 200, respuesta.text
    return respuesta.json()


def test_la_ingesta_pide_el_token_de_ingesta(cli, auth_cli):
    cuerpo = {"tramos": [tramo(T0)]}
    assert cli.post("/ingesta/trafico", json=cuerpo).status_code == 401
    assert cli.post("/ingesta/trafico", headers={"X-Token-Ingesta": "otro"}, json=cuerpo).status_code == 401
    # El token de un teléfono no sirve para mandar tráfico.
    assert cli.post("/ingesta/trafico", headers=auth_cli, json=cuerpo).status_code == 401


def test_sin_token_de_ingesta_configurado_la_ingesta_no_anda(cliente):
    assert cliente.post("/ingesta/trafico", headers=CABECERA, json={"tramos": [tramo(T0)]}).status_code == 503


def test_los_tramos_se_suman_y_no_se_pisan(cli, auth_cli):
    # Una conexión larga llega cuando termina y completa un tramo que ya había llegado.
    assert mandar(cli, tramos=[tramo(T0, conexiones=2, bytes=300, paquetes=6)]) == {"tramos": 1, "pares": 0}
    mandar(cli, tramos=[tramo(T0, conexiones=1, fallidas=1, bytes=50, paquetes=2)])
    serie = cli.get("/trafico", params={"desde": T0, "hasta": T0 + 60, "paso": 5}, headers=auth_cli).json()
    assert serie["total"] == [{"t": T0, "conexiones": 3, "fallidas": 1, "bytes": 350, "paquetes": 8}]
    assert serie["servicios"]["ssh"] == serie["total"]


def test_la_serie_junta_por_paso_y_separa_por_servicio(cli, auth_cli):
    mandar(
        cli,
        tramos=[
            tramo(T0, "ssh", conexiones=1),
            tramo(T0 + 5, "ssh", conexiones=2),
            tramo(T0 + 30, "dns", conexiones=4, bytes=40, paquetes=2),
            tramo(T0 + 65, "ssh", conexiones=1),
        ],
    )
    serie = cli.get("/trafico", params={"desde": T0, "hasta": T0 + 120, "paso": 60}, headers=auth_cli).json()
    assert (serie["desde"], serie["hasta"], serie["paso"]) == (T0, T0 + 120, 60)
    assert [(p["t"], p["conexiones"]) for p in serie["total"]] == [(T0, 7), (T0 + 60, 1)]
    assert [(p["t"], p["conexiones"]) for p in serie["servicios"]["ssh"]] == [(T0, 3), (T0 + 60, 1)]
    assert [(p["t"], p["bytes"]) for p in serie["servicios"]["dns"]] == [(T0, 40)]
    assert serie["ultimoTramo"] == T0 + 65


def test_solo_vienen_los_tramos_con_trafico_dentro_de_la_ventana(cli, auth_cli):
    mandar(cli, tramos=[tramo(T0 - 5), tramo(T0 + 10), tramo(T0 + 60)])
    serie = cli.get("/trafico", params={"desde": T0, "hasta": T0 + 60, "paso": 5}, headers=auth_cli).json()
    assert [p["t"] for p in serie["total"]] == [T0 + 10]


def test_sin_ventana_es_la_ultima_hora_hasta_el_tramo_mas_nuevo(cli, auth_cli):
    mandar(cli, tramos=[tramo(T0 + 3600 - 5)])
    serie = cli.get("/trafico", headers=auth_cli).json()
    assert serie["hasta"] - serie["desde"] == 3600
    assert serie["desde"] <= T0 + 3600 - 5 < serie["hasta"]
    assert len(serie["total"]) == 1


def test_un_reloj_del_sensor_adelantado_no_deja_afuera_lo_mas_nuevo(cli, auth_cli):
    adelantado = (int(ahora().timestamp()) // 5) * 5 + 600
    mandar(cli, tramos=[tramo(adelantado)])
    serie = cli.get("/trafico", headers=auth_cli).json()
    assert serie["hasta"] > adelantado
    assert [p["t"] for p in serie["total"]] == [adelantado]


def test_la_serie_pide_el_token_de_un_dispositivo(cli):
    assert cli.get("/trafico").status_code == 401
    assert cli.get("/trafico", headers=CABECERA).status_code == 401
    assert cli.get("/trafico/pares").status_code == 401


@pytest.mark.parametrize(
    "parametros",
    [
        {"paso": 7},
        {"desde": T0 + 60, "hasta": T0, "paso": 5},
        {"desde": T0 - 7 * 86400, "hasta": T0, "paso": 5},
    ],
)
def test_una_ventana_que_no_se_puede_dibujar_da_422(cli, auth_cli, parametros):
    assert cli.get("/trafico", params=parametros, headers=auth_cli).status_code == 422


def test_los_pares_juntan_servicios_y_traen_los_nombres_del_catalogo(cli, auth_cli):
    with cli.app.state.base.transaccion() as con:
        con.execute("INSERT INTO catalogo_equipos (ip, nombre) VALUES ('192.168.30.50', 'pc-rrhh'), ('192.168.20.20', 'srv-sueldos')")
    mandar(
        cli,
        pares=[
            par(T0, servicio="ssh", conexiones=80, fallidas=0, bytes=9000),
            par(T0 + 60, servicio="otro", conexiones=1023, fallidas=1023, bytes=60000),
            # El minuto no redondo se redondea y se suma al mismo minuto.
            par(T0 + 61, servicio="ssh", conexiones=1),
            par(T0, origen="192.168.30.50", destino="192.168.20.1", servicio="dns", conexiones=2),
        ],
    )
    pares = cli.get("/trafico/pares", params={"desde": T0, "hasta": T0 + 3600}, headers=auth_cli).json()["pares"]
    assert len(pares) == 2
    principal = pares[0]
    assert (principal["origen"], principal["origenNombre"], principal["destino"], principal["destinoNombre"]) == (
        "192.168.30.50",
        "pc-rrhh",
        "192.168.20.20",
        "srv-sueldos",
    )
    assert (principal["conexiones"], principal["fallidas"], principal["bytes"]) == (1104, 1023, 69100)
    assert principal["servicios"] == ["otro", "ssh"]
    assert principal["ultimoMinuto"] == T0 + 60
    assert pares[1]["destinoNombre"] is None and pares[1]["servicios"] == ["dns"]


def test_los_pares_cuentan_el_minuto_de_desde_entero(cli, auth_cli):
    mandar(cli, pares=[par(T0)])
    pares = cli.get("/trafico/pares", params={"desde": T0 + 30, "hasta": T0 + 120}, headers=auth_cli).json()["pares"]
    assert [p["conexiones"] for p in pares] == [1]


@pytest.mark.parametrize(
    "cuerpo",
    [
        {"tramos": [tramo(T0, servicio="SSH; DROP TABLE")]},
        {"tramos": [tramo(T0, conexiones=-1)]},
        {"tramos": [tramo(12)]},
        {"pares": [par(T0, origen="no-es-una-ip")]},
    ],
)
def test_la_ingesta_rechaza_lo_que_no_es_trafico(cli, cuerpo):
    assert cli.post("/ingesta/trafico", headers=CABECERA, json=cuerpo).status_code == 422


def test_la_ingesta_borra_lo_que_tiene_mas_de_una_semana(cli, auth_cli):
    viejo = int(ahora().timestamp()) - 8 * 86400
    viejo -= viejo % 60
    mandar(cli, tramos=[tramo(viejo)], pares=[par(viejo)])
    # Lo viejo se borra al llegar el envío siguiente, junto con todo lo anterior a la semana.
    mandar(cli, tramos=[tramo(T0)])
    con = cli.app.state.base.conexion()
    assert con.execute("SELECT COUNT(*) FROM trafico WHERE t = ?", (viejo,)).fetchone()[0] == 0
    assert con.execute("SELECT COUNT(*) FROM trafico_pares").fetchone()[0] == 0
    assert con.execute("SELECT COUNT(*) FROM trafico WHERE t = ?", (T0,)).fetchone()[0] == 1
