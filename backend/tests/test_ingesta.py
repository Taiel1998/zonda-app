"""Ruta de ingesta de alertas desde la plataforma de detección (prueba de punta a punta, §21.6).

El pipeline en vivo del laboratorio manda episodios con el mismo formato de la semilla; la API los crea
o actualiza por id. El alta la avisa el bot de Telegram como cualquier alerta nueva; la actualización
mueve la secuencia para que la app la vuelva a bajar.
"""

import asyncio
from dataclasses import replace

import httpx
import pytest
from fastapi.testclient import TestClient

from app.main import crear_app
from app.telegram import BotTelegram, ClienteTelegram
from tests.conftest import ARGENTINA, TelegramFalso, cabecera, parear

TOKEN_INGESTA = "token-de-ingesta-de-prueba-0123456789"
CABECERA = {"X-Token-Ingesta": TOKEN_INGESTA}


def episodio(**cambios) -> dict:
    """Un episodio de fuerza bruta SSH con el formato de la semilla; `cambios` pisa los campos que haga falta."""
    base = {
        "id": "ep-lab-1",
        "inicio": "2026-10-16T18:00:00-03:00",
        "fin": "2026-10-16T18:05:00-03:00",
        "cantidadFlujos": 65,
        "uidReferencia": "CuidPrueba01",
        "origenIp": "192.168.30.10",
        "destinoIp": "192.168.20.10",
        "servicio": "ssh",
        "protocolo": "tcp",
        "puerto": 22,
        "puertoReferencia": 22,
        "puertosDistintos": 1,
        "tipoAtaque": "fuerza_bruta",
        "confianzaTipo": 0.95,
        "veredictoIa": True,
        "errorReconstruccion": 0.0473,
        "umbralIa": 0.00306383,
        "veredictoUmbrales": True,
        "contexto60s": 65,
        "umbralServicio": 2,
        "umbralServicioLunes": None,
        "veredictoReglas": False,
        "parDestino": "192.168.20.10:22",
        "parEnCatalogo": True,
    }
    base.update(cambios)
    return base


@pytest.fixture
def cfg_ingesta(config):
    return replace(config, ingesta_token=TOKEN_INGESTA)


@pytest.fixture
def cli_ingesta(cfg_ingesta):
    with TestClient(crear_app(cfg_ingesta)) as cliente:
        yield cliente


def drenar(cliente: TestClient, auth: dict, desde: str | None = None) -> tuple[list[dict], str]:
    """Baja todas las alertas desde el cursor, como la app, y devuelve también el cursor final."""
    alertas: list[dict] = []
    cursor = desde
    while True:
        pagina = cliente.get("/alertas", params={"desde": cursor} if cursor else None, headers=auth).json()
        alertas.extend(pagina["alertas"])
        cursor = pagina["cursorSiguiente"]
        if not pagina["hayMas"]:
            return alertas, cursor


def test_el_alta_crea_la_alerta_y_queda_consultable(cli_ingesta):
    auth = cabecera(parear(cli_ingesta))
    respuesta = cli_ingesta.post("/ingesta/alertas", headers=CABECERA, json={"alertas": [episodio()]})
    assert respuesta.status_code == 200, respuesta.text
    assert respuesta.json() == {"recibidas": 1, "creadas": 1, "actualizadas": 0}

    alerta = cli_ingesta.get("/alertas/ep-lab-1", headers=auth).json()
    assert alerta["servicio"] == "ssh" and alerta["puerto"] == 22 and alerta["protocolo"] == "tcp"
    assert alerta["tipoAtaque"] == "fuerza_bruta" and alerta["severidad"] == "alta"
    assert alerta["cantidadFlujos"] == 65 and alerta["estadoRevision"] == "sin_revisar"
    assert alerta["veredictos"]["ia"] is True and alerta["veredictos"]["umbrales"] is True
    assert alerta["veredictos"]["reglas"] is False
    # umbralServicioLunes None => el servicio no tenía línea base (igual que la semilla).
    assert alerta["veredictos"]["servicioSinLineaBase"] is True
    # Las fechas se guardan en UTC: -03:00 18:05 == 21:05Z.
    assert alerta["fin"] == "2026-10-16T21:05:00+00:00"


def test_la_reingesta_del_mismo_id_actualiza_y_la_app_la_vuelve_a_bajar(cli_ingesta):
    auth = cabecera(parear(cli_ingesta))
    _, cursor = drenar(cli_ingesta, auth)  # al día con la semilla de ejemplo

    assert cli_ingesta.post("/ingesta/alertas", headers=CABECERA, json={"alertas": [episodio(id="ep-sec")]}).json()["creadas"] == 1
    nuevas, cursor = drenar(cli_ingesta, auth, cursor)
    assert any(a["id"] == "ep-sec" for a in nuevas)

    # El mismo id con más flujos: actualiza (no crea) y mueve la secuencia.
    resultado = cli_ingesta.post("/ingesta/alertas", headers=CABECERA, json={"alertas": [episodio(id="ep-sec", cantidadFlujos=999)]}).json()
    assert resultado == {"recibidas": 1, "creadas": 0, "actualizadas": 1}
    rebajadas, _ = drenar(cli_ingesta, auth, cursor)
    cambiada = [a for a in rebajadas if a["id"] == "ep-sec"]
    assert cambiada and cambiada[0]["cantidadFlujos"] == 999


def test_un_lote_con_varios_episodios(cli_ingesta):
    auth = cabecera(parear(cli_ingesta))
    lote = [episodio(id="ep-a"), episodio(id="ep-b", servicio="http", puerto=80, tipoAtaque="ddos"), episodio(id="ep-a", cantidadFlujos=70)]
    resultado = cli_ingesta.post("/ingesta/alertas", headers=CABECERA, json={"alertas": lote}).json()
    # ep-a aparece dos veces en el lote: la segunda es una actualización.
    assert resultado == {"recibidas": 3, "creadas": 2, "actualizadas": 1}
    assert cli_ingesta.get("/alertas/ep-a", headers=auth).json()["cantidadFlujos"] == 70
    assert cli_ingesta.get("/alertas/ep-b", headers=auth).json()["severidad"] == "media"


def test_el_catalogo_pone_los_nombres_de_los_equipos(cli_ingesta):
    auth = cabecera(parear(cli_ingesta))
    cuerpo = {
        "alertas": [episodio(id="ep-cat")],
        "catalogo": {"equipos": {"192.168.30.10": "pc-rrhh", "192.168.20.10": "srv-sueldos"}, "servicios": ["ssh"]},
    }
    assert cli_ingesta.post("/ingesta/alertas", headers=CABECERA, json=cuerpo).status_code == 200
    alerta = cli_ingesta.get("/alertas/ep-cat", headers=auth).json()
    assert alerta["origenNombre"] == "pc-rrhh" and alerta["destinoNombre"] == "srv-sueldos"
    assert "ssh" in cli_ingesta.get("/catalogo", headers=auth).json()["servicios"]


def test_el_alta_dispara_un_aviso_de_telegram_y_la_reingesta_no(cfg_ingesta):
    telegram = TelegramFalso()
    with TestClient(crear_app(cfg_ingesta)) as cli:
        cliente_telegram = ClienteTelegram("123:token-de-prueba", "https://telegram.prueba", transporte=httpx.MockTransport(telegram))
        bot = BotTelegram(cli.app.state.base, cfg_ingesta, cliente_telegram, zona=ARGENTINA, pausa=0)
        cli.app.state.bot = bot
        auth = cabecera(parear(cli))
        codigo = cli.post("/telegram/vinculo", headers=auth).json()["codigo"]
        telegram.mensaje(f"/start {codigo}")
        asyncio.run(bot.recibir())
        telegram.enviados.clear()

        assert cli.post("/ingesta/alertas", headers=CABECERA, json={"alertas": [episodio(id="ep-aviso")]}).json()["creadas"] == 1
        # Una alta: el vigía del bot manda un aviso (con sonido, el modo por defecto de las altas).
        assert asyncio.run(bot.avisar_nuevas()) == 1
        aviso = telegram.enviados[-1]
        assert aviso["chat_id"] == "555" and "SSH" in aviso["text"]
        # Ni IP ni nombres de equipos en el aviso (RNF02).
        assert "192.168" not in aviso["text"]

        # La actualización del mismo episodio no vuelve a avisar.
        assert cli.post("/ingesta/alertas", headers=CABECERA, json={"alertas": [episodio(id="ep-aviso", cantidadFlujos=120)]}).json()["actualizadas"] == 1
        assert asyncio.run(bot.avisar_nuevas()) == 0


def test_sin_token_configurado_la_ruta_responde_503(cliente):
    # El `cliente` por defecto no tiene ZONDA_INGESTA_TOKEN.
    assert cliente.post("/ingesta/alertas", headers=CABECERA, json={"alertas": [episodio()]}).status_code == 503


def test_token_invalido_o_ausente_da_401(cli_ingesta):
    assert cli_ingesta.post("/ingesta/alertas", headers={"X-Token-Ingesta": "otro"}, json={"alertas": [episodio()]}).status_code == 401
    assert cli_ingesta.post("/ingesta/alertas", json={"alertas": [episodio()]}).status_code == 401


def test_formato_invalido_da_422(cli_ingesta):
    # Falta un campo obligatorio.
    incompleto = episodio()
    del incompleto["servicio"]
    assert cli_ingesta.post("/ingesta/alertas", headers=CABECERA, json={"alertas": [incompleto]}).status_code == 422
    # Fecha sin zona horaria.
    assert cli_ingesta.post("/ingesta/alertas", headers=CABECERA, json={"alertas": [episodio(inicio="2026-10-16T18:00:00")]}).status_code == 422
    # Lista vacía.
    assert cli_ingesta.post("/ingesta/alertas", headers=CABECERA, json={"alertas": []}).status_code == 422
