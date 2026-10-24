"""Latido del análisis en vivo: POST /ingesta/latido, el bloque `sistema` de GET /salud (la tarjeta
"Estado del sistema" de la app) y el vigía que avisa por Telegram cuando el latido deja de llegar y
cuando vuelve, y cuando la pérdida de captura de Zeek pasa del 1 % y cuando se normaliza.
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
        "perdidaCaptura": None,
        "perdidaCapturaEn": None,
        "acksCaptura": None,
        "perdidaSimulada": False,
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
        "perdidaCaptura": None,
        "perdidaCapturaEn": None,
        "acksCaptura": None,
        "perdidaSimulada": False,
        "perdidaLimite": 1.0,
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


def test_si_el_servidor_estuvo_apagado_el_limite_se_cuenta_desde_que_arranco(cli, cfg_latido, telegram, bot_latido):
    # El servidor estuvo apagado 10 minutos: el último latido guardado es viejo, pero el análisis puede
    # seguir andando. Recién arrancado no avisa nada; si a los 2 minutos sigue sin latido, sí.
    vincular(cli, cabecera(parear(cli)), telegram, bot_latido)
    telegram.enviados.clear()
    latir(cli)
    envejecer(cli, 600)
    base = cli.app.state.base

    recien = datetime.now(timezone.utc)
    assert asyncio.run(latido.revisar(base, cfg_latido, bot_latido, arranque=recien)) is None
    assert telegram.enviados == []

    hace_rato = recien - timedelta(seconds=cfg_latido.latido_limite + 5)
    assert asyncio.run(latido.revisar(base, cfg_latido, bot_latido, arranque=hace_rato)) == "detenido"
    assert len(telegram.enviados) == 1


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
            # Un servidor que arrancó hace rato: el límite se cuenta desde el último latido.
            arranque = datetime.now(timezone.utc) - timedelta(hours=1)
            await asyncio.wait_for(latido.vigilar(cli.app.state.base, replace(cfg_latido, latido_revisar_cada=0), bot_latido, arranque), timeout=0.3)

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


# ---- Pérdida de captura de Zeek (capture_loss.log) ----


def con_perdida(porcentaje: float, **cambios) -> dict:
    """Lo que manda el latido con la última medición de capture_loss.log (cada 15 minutos)."""
    datos = {"perdidaCaptura": porcentaje, "perdidaCapturaEn": "2026-10-24T18:45:00-03:00", "acksCaptura": 1200}
    datos.update(cambios)
    return datos


def test_la_perdida_de_captura_se_guarda_en_utc_con_el_latido(cli):
    latir(cli, **con_perdida(3.25, perdidaSimulada=True))
    guardado = json.loads(cli.app.state.base.leer_meta("latido"))
    assert guardado["perdidaCaptura"] == 3.25
    assert guardado["perdidaCapturaEn"] == "2026-10-24T21:45:00+00:00"
    assert guardado["acksCaptura"] == 1200
    assert guardado["perdidaSimulada"] is True


def test_una_perdida_fuera_de_rango_o_una_fecha_mala_da_422(cli):
    for malo in (
        un_latido(**con_perdida(-0.1)),
        un_latido(**con_perdida(100.1)),
        un_latido(**con_perdida(2.0, perdidaCapturaEn="hace un rato")),
        un_latido(**con_perdida(2.0, perdidaCapturaEn="2026-10-24T18:45:00")),  # sin zona
        un_latido(**con_perdida(2.0, acksCaptura=-1)),
    ):
        assert cli.post("/ingesta/latido", headers=CABECERA, json=malo).status_code == 422


def test_con_mas_del_limite_de_perdida_el_sensor_ve_parcialmente(cli):
    auth = cabecera(parear(cli))
    latir(cli, **con_perdida(3.2))

    estado = sistema(cli, auth)
    assert estado["analisis"] == "funciona" and estado["sensor"] == "parcial"
    assert estado["perdidaCaptura"] == 3.2
    assert estado["perdidaCapturaEn"] == "2026-10-24T21:45:00+00:00"
    assert estado["acksCaptura"] == 1200
    assert estado["perdidaSimulada"] is False
    assert estado["perdidaLimite"] == 1.0


def test_con_la_perdida_en_el_limite_o_debajo_el_sensor_funciona(cli):
    auth = cabecera(parear(cli))
    for porcentaje in (0.0, 0.4, 1.0):
        latir(cli, **con_perdida(porcentaje))
        assert sistema(cli, auth)["sensor"] == "funciona"
    # Sin tráfico TCP, Zeek mide 0 % sobre 0 ACK: no hay pérdida que avisar.
    latir(cli, **con_perdida(0.0, acksCaptura=0))
    assert sistema(cli, auth)["sensor"] == "funciona"


def test_con_zeek_caido_el_sensor_esta_detenido_aunque_haya_perdida(cli):
    auth = cabecera(parear(cli))
    latir(cli, zeekActivo=False, **con_perdida(9.5))
    assert sistema(cli, auth)["sensor"] == "detenido"


def test_con_el_latido_vencido_del_sensor_no_se_sabe_aunque_haya_perdida(cli):
    auth = cabecera(parear(cli))
    latir(cli, **con_perdida(9.5))
    envejecer(cli, 200)
    estado = sistema(cli, auth)
    assert estado["analisis"] == "detenido" and estado["sensor"] == "sin_datos"
    # La última medición se sigue mostrando.
    assert estado["perdidaCaptura"] == 9.5


def test_el_limite_de_perdida_sale_de_la_configuracion(config, monkeypatch):
    with TestClient(crear_app(replace(config, ingesta_token=TOKEN_INGESTA, perdida_limite=5.0, latido_revisar_cada=3600))) as cli:
        auth = cabecera(parear(cli))
        latir(cli, **con_perdida(3.2))
        estado = sistema(cli, auth)
        assert estado["sensor"] == "funciona" and estado["perdidaLimite"] == 5.0

    monkeypatch.setattr("app.config.load_dotenv", lambda *args, **kwargs: None)
    monkeypatch.setenv("ZONDA_PERDIDA_LIMITE", "2,5")
    assert Config.desde_entorno().perdida_limite == 2.5
    monkeypatch.delenv("ZONDA_PERDIDA_LIMITE")
    assert Config.desde_entorno().perdida_limite == 1.0


def revisar_perdida(cli, cfg, bot) -> str | None:
    return asyncio.run(latido.revisar_perdida(cli.app.state.base, cfg, bot))


def test_el_vigia_avisa_una_vez_que_el_sensor_ve_parcialmente_y_otra_cuando_se_normaliza(cli, cfg_latido, telegram, bot_latido):
    vincular(cli, cabecera(parear(cli, "Teléfono de Taiel")), telegram, bot_latido, chat_id=555)
    vincular(cli, cabecera(parear(cli, "Tablet de Taiel")), telegram, bot_latido, chat_id=555)
    vincular(cli, cabecera(parear(cli, "Teléfono de Laura")), telegram, bot_latido, chat_id=777)
    telegram.enviados.clear()

    latir(cli, **con_perdida(0.3))
    assert revisar_perdida(cli, cfg_latido, bot_latido) is None  # debajo del límite: nada que avisar

    latir(cli, **con_perdida(3.2))
    assert revisar_perdida(cli, cfg_latido, bot_latido) == "parcial"
    assert sorted(m["chat_id"] for m in telegram.enviados) == ["555", "777"]
    aviso = telegram.enviados[0]
    hora = datetime(2026, 10, 24, 21, 45, tzinfo=timezone.utc).astimezone(ARGENTINA)
    hora_texto = hora.strftime("%H:%M") if hora.date() == datetime.now(ARGENTINA).date() else hora.strftime("%d/%m %H:%M")
    assert aviso["text"] == (
        f"<b>Sensor viendo parcialmente</b>\nZeek perdió el 3,2 % de lo que pasó por el puerto espejo en la medición "
        f"de las {hora_texto}: puede haber tráfico, y ataques, que Zonda no ve.\nRed: Red de prueba"
    )
    assert aviso["disable_notification"] is False and "reply_markup" not in aviso

    # Sigue alta en la medición siguiente: no se repite.
    latir(cli, **con_perdida(4.8))
    assert revisar_perdida(cli, cfg_latido, bot_latido) is None
    assert len(telegram.enviados) == 2
    assert cli.app.state.base.leer_meta("perdida_aviso") == "parcial"

    telegram.enviados.clear()
    latir(cli, **con_perdida(0.0))
    assert revisar_perdida(cli, cfg_latido, bot_latido) == "normal"
    assert sorted(m["chat_id"] for m in telegram.enviados) == ["555", "777"]
    assert telegram.enviados[0]["text"].startswith("<b>Sensor viendo todo de nuevo</b>\nLa pérdida de captura bajó al 0,0 % en la medición de las ")
    assert revisar_perdida(cli, cfg_latido, bot_latido) is None
    assert len(telegram.enviados) == 2


def test_el_vigia_no_avisa_la_perdida_con_el_latido_vencido_sin_medicion_o_con_zeek_caido(cli, cfg_latido, telegram, bot_latido):
    vincular(cli, cabecera(parear(cli)), telegram, bot_latido)
    telegram.enviados.clear()
    assert revisar_perdida(cli, cfg_latido, bot_latido) is None  # sin ningún latido

    latir(cli)  # un latido sin medición de pérdida
    assert revisar_perdida(cli, cfg_latido, bot_latido) is None

    latir(cli, zeekActivo=False, **con_perdida(7.0))
    assert revisar_perdida(cli, cfg_latido, bot_latido) is None

    latir(cli, **con_perdida(7.0))
    envejecer(cli, 200)  # de esto avisa la caída del análisis, no la pérdida
    assert revisar_perdida(cli, cfg_latido, bot_latido) is None
    assert telegram.enviados == []
    assert cli.app.state.base.leer_meta("perdida_aviso") is None


def test_la_perdida_simulada_lo_dice_en_el_aviso(cli, cfg_latido, telegram, bot_latido):
    vincular(cli, cabecera(parear(cli)), telegram, bot_latido)
    telegram.enviados.clear()

    latir(cli, **con_perdida(5.0, perdidaSimulada=True))
    assert revisar_perdida(cli, cfg_latido, bot_latido) == "parcial"
    assert telegram.enviados[0]["text"].endswith("Red: Red de prueba\n(Prueba: pérdida simulada en el laboratorio.)")

    latir(cli, **con_perdida(0.0, perdidaSimulada=True))
    assert revisar_perdida(cli, cfg_latido, bot_latido) == "normal"
    assert telegram.enviados[1]["text"].endswith("(Prueba: pérdida simulada en el laboratorio.)")


def test_sin_medicion_de_la_hora_el_aviso_usa_la_del_latido_y_sin_bot_solo_lleva_la_cuenta(cli, cfg_latido, telegram, bot_latido):
    vincular(cli, cabecera(parear(cli)), telegram, bot_latido)
    telegram.enviados.clear()

    latir(cli, perdidaCaptura=2.0)  # sin perdidaCapturaEn
    assert revisar_perdida(cli, cfg_latido, bot_latido) == "parcial"
    hora = datetime.fromisoformat(cli.app.state.base.leer_meta("latido_recibido_en")).astimezone(ARGENTINA).strftime("%H:%M")
    assert f"en la medición de las {hora}:" in telegram.enviados[0]["text"]

    latir(cli, **con_perdida(0.5))
    assert revisar_perdida(cli, cfg_latido, None) == "normal"
    assert len(telegram.enviados) == 1
    assert cli.app.state.base.leer_meta("perdida_aviso") == "normal"


def test_los_avisos_de_perdida_no_llevan_direcciones_ip_ni_nombres_de_equipos(cli, cfg_latido, telegram, bot_latido):
    vincular(cli, cabecera(parear(cli)), telegram, bot_latido)
    telegram.enviados.clear()
    con = cli.app.state.base.conexion()
    prohibidos = {fila[0] for fila in con.execute("SELECT origen_ip FROM alertas UNION SELECT destino_ip FROM alertas")}
    prohibidos |= {fila[0] for fila in con.execute("SELECT nombre FROM catalogo_equipos")}
    prohibidos |= {"192.168.30.50", "192.168.20.20", "10.0.2.2", "pc-rrhh", "srv-sueldos", "zeek-lab", "ovs-lab"}

    latir(cli, **con_perdida(12.0))
    revisar_perdida(cli, cfg_latido, bot_latido)
    latir(cli, **con_perdida(0.0))
    revisar_perdida(cli, cfg_latido, bot_latido)

    assert len(telegram.enviados) == 2
    for mensaje in telegram.enviados:
        for dato in prohibidos:
            assert dato not in mensaje["text"]


def test_si_telegram_falla_la_perdida_no_cambia_de_estado_y_la_caida_se_revisa_igual(cli, cfg_latido, telegram, bot_latido, caplog):
    vincular(cli, cabecera(parear(cli)), telegram, bot_latido)
    telegram.error = (500, "Internal Server Error")
    latir(cli, **con_perdida(6.0))

    async def unas_vueltas() -> None:
        with pytest.raises(asyncio.TimeoutError):
            await asyncio.wait_for(latido.vigilar(cli.app.state.base, replace(cfg_latido, latido_revisar_cada=0), bot_latido), timeout=0.3)

    with caplog.at_level(logging.WARNING, logger="zonda.latido"):
        asyncio.run(unas_vueltas())
    assert "El aviso del análisis en vivo no salió, se reintenta" in caplog.text
    assert cli.app.state.base.leer_meta("perdida_aviso") is None

    # Con el chat bloqueado, ese teléfono queda sin Telegram y el estado avanza.
    telegram.error = (403, "Forbidden: bot was blocked by the user")
    assert revisar_perdida(cli, cfg_latido, bot_latido) == "parcial"


def test_el_texto_de_la_perdida_lleva_coma_decimal_hora_local_y_fecha_si_no_es_de_hoy():
    medido = datetime(2026, 10, 24, 21, 45, 0, tzinfo=timezone.utc)  # 18:45 en Argentina
    assert latido.texto_parcial(3.25, medido, "Oficina central", ARGENTINA, hoy=date(2026, 10, 24)) == (
        "<b>Sensor viendo parcialmente</b>\nZeek perdió el 3,2 % de lo que pasó por el puerto espejo en la medición "
        "de las 18:45: puede haber tráfico, y ataques, que Zonda no ve.\nRed: Oficina central"
    )
    assert "de las 24/10 18:45:" in latido.texto_parcial(12.0, medido, "Oficina central", ARGENTINA, hoy=date(2026, 10, 25))
    assert latido.texto_normal(0.04, medido, "Oficina <central>", ARGENTINA, hoy=date(2026, 10, 24)) == (
        "<b>Sensor viendo todo de nuevo</b>\nLa pérdida de captura bajó al 0,0 % en la medición de las 18:45.\n"
        "Red: Oficina &lt;central&gt;"
    )
