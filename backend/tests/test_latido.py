"""Latido del análisis en vivo: POST /ingesta/latido, el bloque `sistema` de GET /salud (la tarjeta
"Estado del sistema" de la app) y el vigía que avisa por Telegram cuando el latido deja de llegar y
cuando vuelve.
"""

import asyncio
import json
import logging
from dataclasses import replace
from datetime import date, datetime, timedelta, timezone

import httpx
import pytest
from fastapi.testclient import TestClient

from app import latido
from app.config import Config
from app.db import Base
from app.main import crear_app
from app.telegram import BotTelegram, ClienteTelegram, ErrorTelegram
from tests.conftest import ARGENTINA, cabecera, parear

TOKEN_INGESTA = "token-de-ingesta-de-prueba-0123456789"
CABECERA = {"X-Token-Ingesta": TOKEN_INGESTA}


def un_latido(**cambios) -> dict:
    """El latido de un ciclo del pipeline en vivo; `cambios` pisa los campos que haga falta."""
    datos = {
        "hora": "2026-10-24T18:30:00-03:00",
        "flujos": 42,
        "ultimoFlujo": "2026-10-24T18:29:50-03:00",
        "zeekActivo": True,
        "episodios": 1,
        "intervalo": 20,
    }
    datos.update(cambios)
    return datos


@pytest.fixture
def cfg_latido(config):
    # El vigía de fondo no vuelve a correr durante el test: cada vuelta la pide el test.
    return replace(config, ingesta_token=TOKEN_INGESTA, latido_revisar_cada=3600)


@pytest.fixture
def cli(cfg_latido):
    with TestClient(crear_app(cfg_latido)) as cliente:
        yield cliente


@pytest.fixture
def bot_latido(cli, cfg_latido, telegram):
    """El bot con el Telegram falso, puesto en la app como si el servidor tuviera el token."""
    cliente_telegram = ClienteTelegram("123:token-de-prueba", "https://telegram.prueba", transporte=httpx.MockTransport(telegram))
    bot = BotTelegram(cli.app.state.base, cfg_latido, cliente_telegram, zona=ARGENTINA, pausa=0)
    cli.app.state.bot = bot
    return bot


def vincular(cli, auth, telegram, bot, chat_id=555) -> None:
    """La app pide el código, la persona toca Iniciar en Telegram y el bot lo lee."""
    codigo = cli.post("/telegram/vinculo", headers=auth).json()["codigo"]
    telegram.mensaje(f"/start {codigo}", chat_id)
    asyncio.run(bot.recibir())


def latir(cli, **cambios) -> dict:
    respuesta = cli.post("/ingesta/latido", headers=CABECERA, json=un_latido(**cambios))
    assert respuesta.status_code == 200, respuesta.text
    return respuesta.json()


def envejecer(cli, segundos: int) -> None:
    """Corre para atrás la llegada del último latido, como si hubieran pasado [segundos] sin otro."""
    base = cli.app.state.base
    recibido = datetime.fromisoformat(base.leer_meta("latido_recibido_en")) - timedelta(seconds=segundos)
    with base.transaccion() as con:
        Base.escribir_meta(con, "latido_recibido_en", recibido.isoformat(timespec="seconds"))


def sistema(cli, auth) -> dict:
    respuesta = cli.get("/salud", headers=auth)
    assert respuesta.status_code == 200, respuesta.text
    return respuesta.json()["sistema"]


def revisar(cli, cfg, bot) -> str | None:
    return asyncio.run(latido.revisar(cli.app.state.base, cfg, bot))


# ---- POST /ingesta/latido ----


def test_sin_token_configurado_el_latido_responde_503(cliente):
    # El `cliente` por defecto no tiene ZONDA_INGESTA_TOKEN.
    assert cliente.post("/ingesta/latido", headers=CABECERA, json=un_latido()).status_code == 503


def test_token_invalido_o_ausente_da_401(cli):
    assert cli.post("/ingesta/latido", headers={"X-Token-Ingesta": "otro"}, json=un_latido()).status_code == 401
    assert cli.post("/ingesta/latido", json=un_latido()).status_code == 401
    # El token de un teléfono no sirve para la ingesta.
    assert cli.post("/ingesta/latido", headers=cabecera(parear(cli)), json=un_latido()).status_code == 401


def test_el_latido_se_guarda_con_las_fechas_en_utc_y_la_hora_de_llegada(cli):
    antes = datetime.now(timezone.utc).replace(microsecond=0)
    resultado = latir(cli)

    base = cli.app.state.base
    recibido = datetime.fromisoformat(resultado["recibidoEn"])
    assert antes <= recibido <= datetime.now(timezone.utc)
    assert base.leer_meta("latido_recibido_en") == resultado["recibidoEn"]
    guardado = json.loads(base.leer_meta("latido"))
    # -03:00 18:30 == 21:30Z: como el resto de la base.
    assert guardado == {
        "hora": "2026-10-24T21:30:00+00:00",
        "flujos": 42,
        "ultimoFlujo": "2026-10-24T21:29:50+00:00",
        "zeekActivo": True,
        "episodios": 1,
        "intervalo": 20,
    }


def test_el_latido_sin_flujos_todavia_tambien_vale(cli):
    latir(cli, flujos=0, ultimoFlujo=None, episodios=0, intervalo=None)
    assert json.loads(cli.app.state.base.leer_meta("latido"))["ultimoFlujo"] is None


def test_formato_invalido_da_422(cli):
    for malo in (
        un_latido(hora="ayer"),
        un_latido(hora="2026-10-24T18:30:00"),  # sin zona horaria
        un_latido(ultimoFlujo="hace un rato"),
        un_latido(flujos=-1),
        un_latido(intervalo=0),
    ):
        assert cli.post("/ingesta/latido", headers=CABECERA, json=malo).status_code == 422
    incompleto = un_latido()
    del incompleto["zeekActivo"]
    assert cli.post("/ingesta/latido", headers=CABECERA, json=incompleto).status_code == 422


# ---- GET /salud: el bloque sistema ----


def test_sin_token_salud_no_dice_nada_del_sistema(cli):
    latir(cli)
    assert cli.get("/salud").json()["sistema"] is None
    # Con un token que no es de nadie sigue el 401 de siempre.
    assert cli.get("/salud", headers=cabecera("token-falso")).status_code == 401


def test_sin_ningun_latido_no_hay_datos_del_analisis_ni_del_sensor(cli):
    assert sistema(cli, cabecera(parear(cli))) == {
        "servidor": "funciona",
        "analisis": "sin_datos",
        "sensor": "sin_datos",
        "ultimaCorrida": None,
        "segundosDesdeUltimaCorrida": None,
        "ultimoFlujo": None,
        "flujosUltimaCorrida": None,
        "limiteSegundos": 120,
    }


def test_con_el_latido_fresco_funciona_todo(cli):
    auth = cabecera(parear(cli))
    resultado = latir(cli)

    estado = sistema(cli, auth)
    assert estado["servidor"] == estado["analisis"] == estado["sensor"] == "funciona"
    assert estado["ultimaCorrida"] == resultado["recibidoEn"]
    assert 0 <= estado["segundosDesdeUltimaCorrida"] <= 5
    assert estado["ultimoFlujo"] == "2026-10-24T21:29:50+00:00"
    assert estado["flujosUltimaCorrida"] == 42


def test_con_zeek_caido_el_sensor_figura_detenido(cli):
    auth = cabecera(parear(cli))
    latir(cli, zeekActivo=False)

    estado = sistema(cli, auth)
    assert estado["analisis"] == "funciona" and estado["sensor"] == "detenido"


def test_con_el_latido_viejo_el_analisis_esta_detenido_y_del_sensor_no_se_sabe(cli):
    auth = cabecera(parear(cli))
    latir(cli)
    envejecer(cli, 110)
    assert sistema(cli, auth)["analisis"] == "funciona"

    envejecer(cli, 20)  # 130 s, más que el límite de 120
    estado = sistema(cli, auth)
    assert estado["analisis"] == "detenido" and estado["sensor"] == "sin_datos"
    assert estado["segundosDesdeUltimaCorrida"] >= 130
    # Lo del último latido se sigue mostrando.
    assert estado["ultimoFlujo"] == "2026-10-24T21:29:50+00:00"

    latir(cli)
    assert sistema(cli, auth)["analisis"] == "funciona"


def test_el_limite_sale_de_la_configuracion(config):
    with TestClient(crear_app(replace(config, ingesta_token=TOKEN_INGESTA, latido_limite=30, latido_revisar_cada=3600))) as cli:
        auth = cabecera(parear(cli))
        latir(cli)
        envejecer(cli, 40)
        estado = sistema(cli, auth)
        assert estado["analisis"] == "detenido" and estado["limiteSegundos"] == 30


def test_las_variables_del_latido_se_leen_del_entorno(monkeypatch):
    monkeypatch.setattr("app.config.load_dotenv", lambda *args, **kwargs: None)
    monkeypatch.setenv("ZONDA_LATIDO_LIMITE", "90")
    monkeypatch.setenv("ZONDA_LATIDO_REVISAR_CADA", "5")
    cfg = Config.desde_entorno()
    assert (cfg.latido_limite, cfg.latido_revisar_cada) == (90, 5)

    monkeypatch.delenv("ZONDA_LATIDO_LIMITE")
    monkeypatch.delenv("ZONDA_LATIDO_REVISAR_CADA")
    cfg = Config.desde_entorno()
    assert (cfg.latido_limite, cfg.latido_revisar_cada) == (120, 15)


# ---- El vigía ----


def test_sin_latido_el_vigia_no_avisa_nada(cli, cfg_latido, telegram, bot_latido):
    vincular(cli, cabecera(parear(cli)), telegram, bot_latido)
    telegram.enviados.clear()

    assert revisar(cli, cfg_latido, bot_latido) is None
    assert telegram.enviados == []
    assert cli.app.state.base.leer_meta("latido_aviso") is None


def test_el_vigia_avisa_una_sola_vez_la_caida_y_despues_la_vuelta(cli, cfg_latido, telegram, bot_latido):
    # Tres teléfonos: dos comparten el chat 555 (un solo aviso) y el tercero tiene el 777.
    vincular(cli, cabecera(parear(cli, "Teléfono de Taiel")), telegram, bot_latido, chat_id=555)
    vincular(cli, cabecera(parear(cli, "Tablet de Taiel")), telegram, bot_latido, chat_id=555)
    vincular(cli, cabecera(parear(cli, "Teléfono de Laura")), telegram, bot_latido, chat_id=777)
    telegram.enviados.clear()

    latir(cli)
    assert revisar(cli, cfg_latido, bot_latido) is None  # anda: no hay nada que avisar

    envejecer(cli, 200)
    assert revisar(cli, cfg_latido, bot_latido) == "detenido"
    assert sorted(m["chat_id"] for m in telegram.enviados) == ["555", "777"]
    caida = telegram.enviados[0]
    assert caida["text"].startswith("<b>Análisis en vivo detenido</b>\nZonda no recibe noticias del análisis en vivo desde las ")
    assert caida["text"].endswith(": las alertas nuevas no llegan hasta que vuelva.\nRed: Red de prueba")
    # Con sonido y sin botón: no hay una alerta que abrir.
    assert caida["disable_notification"] is False and "reply_markup" not in caida

    # Una segunda vuelta con el análisis todavía detenido no repite el aviso.
    assert revisar(cli, cfg_latido, bot_latido) is None
    assert len(telegram.enviados) == 2
    assert cli.app.state.base.leer_meta("latido_aviso") == "detenido"

    telegram.enviados.clear()
    latir(cli)
    assert revisar(cli, cfg_latido, bot_latido) == "funciona"
    assert sorted(m["chat_id"] for m in telegram.enviados) == ["555", "777"]
    hora = datetime.fromisoformat(cli.app.state.base.leer_meta("latido_recibido_en")).astimezone(ARGENTINA).strftime("%H:%M")
    assert telegram.enviados[0]["text"] == (
        f"<b>Análisis en vivo funcionando de nuevo</b>\nZonda volvió a recibir el análisis en vivo a las {hora}.\nRed: Red de prueba"
    )
    assert revisar(cli, cfg_latido, bot_latido) is None
    assert len(telegram.enviados) == 2


def test_un_telefono_revocado_no_recibe_el_aviso(cli, cfg_latido, telegram, bot_latido):
    queda = cabecera(parear(cli, "Teléfono de Taiel"))
    se_va = cabecera(parear(cli, "Teléfono perdido"))
    vincular(cli, queda, telegram, bot_latido, chat_id=555)
    vincular(cli, se_va, telegram, bot_latido, chat_id=999)
    assert cli.delete("/dispositivos/actual", headers=se_va).status_code == 204
    telegram.enviados.clear()

    latir(cli)
    envejecer(cli, 200)
    assert revisar(cli, cfg_latido, bot_latido) == "detenido"
    assert [m["chat_id"] for m in telegram.enviados] == ["555"]


def test_los_avisos_no_llevan_direcciones_ip_ni_nombres_de_equipos(cli, cfg_latido, telegram, bot_latido):
    vincular(cli, cabecera(parear(cli)), telegram, bot_latido)
    telegram.enviados.clear()
    base = cli.app.state.base
    con = base.conexion()
    prohibidos = {fila[0] for fila in con.execute("SELECT origen_ip FROM alertas UNION SELECT destino_ip FROM alertas")}
    prohibidos |= {fila[0] for fila in con.execute("SELECT nombre FROM catalogo_equipos")}
    # Los del laboratorio, que el pipeline en vivo manda en su catálogo.
    prohibidos |= {"192.168.30.50", "192.168.20.20", "10.0.2.2", "pc-rrhh", "srv-sueldos", "zeek-lab"}

    latir(cli)
    envejecer(cli, 200)
    revisar(cli, cfg_latido, bot_latido)
    latir(cli)
    revisar(cli, cfg_latido, bot_latido)

    assert len(telegram.enviados) == 2
    for mensaje in telegram.enviados:
        for dato in prohibidos:
            assert dato not in mensaje["text"]


def test_sin_bot_el_vigia_solo_lleva_la_cuenta(cli, cfg_latido):
    latir(cli)
    envejecer(cli, 200)
    assert revisar(cli, cfg_latido, None) == "detenido"
    assert cli.app.state.base.leer_meta("latido_aviso") == "detenido"

    latir(cli)
    assert revisar(cli, cfg_latido, None) == "funciona"
    assert cli.app.state.base.leer_meta("latido_aviso") == "funciona"


def test_si_bloquearon_el_bot_ese_chat_queda_sin_telegram(cli, cfg_latido, telegram, bot_latido):
    auth = cabecera(parear(cli))
    vincular(cli, auth, telegram, bot_latido)
    telegram.error = (403, "Forbidden: bot was blocked by the user")

    latir(cli)
    envejecer(cli, 200)
    assert revisar(cli, cfg_latido, bot_latido) == "detenido"

    assert not cli.get("/telegram", headers=auth).json()["vinculado"]


def test_si_telegram_no_responde_el_estado_no_cambia_y_se_reintenta(cli, cfg_latido, telegram, bot_latido):
    vincular(cli, cabecera(parear(cli)), telegram, bot_latido)
    telegram.enviados.clear()
    telegram.error = (500, "Internal Server Error")

    latir(cli)
    envejecer(cli, 200)
    with pytest.raises(ErrorTelegram):
        revisar(cli, cfg_latido, bot_latido)
    assert cli.app.state.base.leer_meta("latido_aviso") is None

    telegram.error = None
    assert revisar(cli, cfg_latido, bot_latido) == "detenido"
    assert len(telegram.enviados) == 1


def test_el_vigia_sigue_andando_aunque_telegram_falle(cli, cfg_latido, telegram, bot_latido, caplog):
    vincular(cli, cabecera(parear(cli)), telegram, bot_latido)
    telegram.error = (500, "Internal Server Error")
    latir(cli)
    envejecer(cli, 200)

    async def unas_vueltas() -> None:
        with pytest.raises(asyncio.TimeoutError):
            await asyncio.wait_for(latido.vigilar(cli.app.state.base, replace(cfg_latido, latido_revisar_cada=0), bot_latido), timeout=0.3)

    with caplog.at_level(logging.WARNING, logger="zonda.latido"):
        asyncio.run(unas_vueltas())
    assert "El aviso del análisis en vivo no salió, se reintenta" in caplog.text
    assert cli.app.state.base.leer_meta("latido_aviso") is None


def test_el_texto_lleva_la_hora_local_y_la_fecha_si_no_es_de_hoy():
    ultimo = datetime(2026, 10, 24, 21, 41, 5, tzinfo=timezone.utc)  # 18:41 en Argentina
    assert latido.texto_detenido(ultimo, "Oficina central", ARGENTINA, hoy=date(2026, 10, 24)) == (
        "<b>Análisis en vivo detenido</b>\nZonda no recibe noticias del análisis en vivo desde las 18:41: "
        "las alertas nuevas no llegan hasta que vuelva.\nRed: Oficina central"
    )
    assert "desde las 24/10 18:41:" in latido.texto_detenido(ultimo, "Oficina central", ARGENTINA, hoy=date(2026, 10, 25))
    assert latido.texto_volvio(ultimo, "Oficina <central>", ARGENTINA, hoy=date(2026, 10, 24)) == (
        "<b>Análisis en vivo funcionando de nuevo</b>\nZonda volvió a recibir el análisis en vivo a las 18:41.\n"
        "Red: Oficina &lt;central&gt;"
    )
