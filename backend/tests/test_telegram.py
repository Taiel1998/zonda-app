import asyncio
import sqlite3
from datetime import date, datetime, timedelta, timezone

import httpx
import pytest

from app.emisor import emitir_una
from app.telegram import ClienteTelegram, ErrorTelegram, enlace_del_aviso, texto_del_aviso
from tests.conftest import ARGENTINA

AVISOS_DEL_FIGMA = {"alta": "sonido", "media": "vibracion", "baja": "sin_aviso"}


def vincular(cliente, auth, telegram, bot, avisos=None, chat_id=555) -> None:
    """Lo que pasa de verdad: la app pide el código, la persona toca Iniciar en Telegram y el bot lo lee."""
    codigo = cliente.post("/telegram/vinculo", json={"avisos": avisos} if avisos else {}, headers=auth).json()["codigo"]
    telegram.mensaje(f"/start {codigo}", chat_id)
    asyncio.run(bot.recibir())


def nueva_alerta(cliente, tipo: str) -> str:
    base = cliente.app.state.base
    alerta_id = emitir_una(base)
    with base.transaccion() as con:
        con.execute("UPDATE alertas SET tipo_ataque = ? WHERE id = ?", (tipo, alerta_id))
    return alerta_id


def alerta_del_boton(mensaje: dict) -> str:
    enlace = mensaje["reply_markup"]["inline_keyboard"][0][0]["url"]
    return enlace.split("?")[0].rsplit("/", 1)[-1]


def test_sin_el_bot_configurado_no_se_puede_vincular(cliente, auth):
    assert cliente.post("/telegram/vinculo", headers=auth).status_code == 503
    assert cliente.post("/telegram/prueba", headers=auth).status_code == 503
    assert cliente.get("/telegram", headers=auth).json() == {
        "vinculado": False,
        "bot": "zonda_alertas_bot",
        "vinculadoEn": None,
        "avisos": AVISOS_DEL_FIGMA,
    }


def test_iniciar_con_el_codigo_vincula_el_chat_con_el_telefono(cliente, auth, telegram, bot):
    asyncio.run(bot.presentarse())
    avisos = {"alta": "sonido", "media": "sin_aviso", "baja": "sin_aviso"}
    vinculo = cliente.post("/telegram/vinculo", json={"avisos": avisos}, headers=auth).json()
    # El usuario del bot sale de Telegram, no de la configuración.
    assert vinculo["enlaceBot"] == f"https://t.me/zonda_prueba_bot?start={vinculo['codigo']}"

    telegram.mensaje(f"/start {vinculo['codigo']}")
    asyncio.run(bot.recibir())

    estado = cliente.get("/telegram", headers=auth).json()
    assert estado["vinculado"] and estado["bot"] == "zonda_prueba_bot" and estado["vinculadoEn"]
    assert estado["avisos"] == avisos
    bienvenida = telegram.enviados[-1]
    assert bienvenida["chat_id"] == "555"
    assert "Red de prueba" in bienvenida["text"] and "nunca direcciones IP ni nombres de equipos" in bienvenida["text"]

    # El código es de un solo uso.
    telegram.mensaje(f"/start {vinculo['codigo']}", chat_id=777)
    asyncio.run(bot.recibir())
    assert telegram.enviados[-1]["text"].startswith("Ese código no sirve")


def test_el_bot_no_vincula_con_un_codigo_vencido_ni_en_un_grupo(cliente, auth, telegram, bot):
    codigo = cliente.post("/telegram/vinculo", headers=auth).json()["codigo"]
    telegram.mensaje(f"/start {codigo}", chat_id=-100, tipo="group")
    telegram.mensaje("/start ZZZZ-ZZZZ")
    telegram.mensaje("hola")
    asyncio.run(bot.recibir())
    textos = [mensaje["text"] for mensaje in telegram.enviados]
    assert textos[0].startswith("Los avisos de Zonda llegan a un chat privado")
    assert textos[1].startswith("Ese código no sirve")
    assert textos[2].startswith("Este bot manda los avisos de Zonda")

    with cliente.app.state.base.transaccion() as con:
        con.execute("UPDATE codigos_telegram SET vence_en = '2000-01-01T00:00:00+00:00'")
    telegram.mensaje(f"/start {codigo}")
    asyncio.run(bot.recibir())
    assert telegram.enviados[-1]["text"].startswith("Ese código no sirve")
    assert not cliente.get("/telegram", headers=auth).json()["vinculado"]


def test_cada_severidad_avisa_como_eligio_la_persona(cliente, auth, telegram, bot):
    vincular(cliente, auth, telegram, bot)  # los del Figma: alta con sonido, media con vibración, baja sin aviso
    telegram.enviados.clear()
    alta = nueva_alerta(cliente, "fuerza_bruta")
    media = nueva_alerta(cliente, "escaneo")
    baja = nueva_alerta(cliente, "probable_falsa_alarma")

    assert asyncio.run(bot.avisar_nuevas()) == 2

    por_alerta = {alerta_del_boton(mensaje): mensaje for mensaje in telegram.enviados}
    assert por_alerta[alta]["disable_notification"] is False
    # "Con vibración" es un mensaje sin sonido: el bot no puede pedir solo vibración.
    assert por_alerta[media]["disable_notification"] is True
    assert baja not in por_alerta
    assert por_alerta[alta]["reply_markup"]["inline_keyboard"][0][0]["text"] == "Ver el detalle en Zonda"
    # Nada sale dos veces, tampoco lo que quedó sin aviso.
    assert asyncio.run(bot.avisar_nuevas()) == 0
    assert len(telegram.enviados) == 2


def test_ningun_aviso_lleva_direcciones_ip_ni_nombres_de_equipos(cliente, auth, telegram, bot):
    vincular(cliente, auth, telegram, bot, avisos={"alta": "sonido", "media": "sonido", "baja": "sonido"})
    telegram.enviados.clear()
    base = cliente.app.state.base
    with base.transaccion() as con:
        # Como si el chat estuviera vinculado desde antes de la semilla: se avisan todas las alertas.
        con.execute("UPDATE dispositivos SET telegram_vinculado_en = '2000-01-01T00:00:00+00:00'")
        prohibidos = {fila[0] for fila in con.execute("SELECT origen_ip FROM alertas UNION SELECT destino_ip FROM alertas")}
        prohibidos |= {fila[0] for fila in con.execute("SELECT nombre FROM catalogo_equipos")}
    assert "srv-sueldos" in prohibidos

    enviados = 0
    while (vuelta := asyncio.run(bot.avisar_nuevas())) > 0:
        enviados += vuelta

    assert enviados == cliente.get("/salud").json()["alertas"] == len(telegram.enviados)
    for mensaje in telegram.enviados:
        enlace = mensaje["reply_markup"]["inline_keyboard"][0][0]["url"]
        for dato in prohibidos:
            assert dato not in mensaje["text"] and dato not in enlace


def test_texto_y_enlace_del_aviso():
    deteccion = datetime(2026, 9, 27, 5, 9, 12, tzinfo=timezone.utc)  # 02:09 en Argentina

    texto = texto_del_aviso("alta", "ssh", deteccion, "Oficina <central>", ARGENTINA, hoy=date(2026, 9, 27))

    assert texto == "<b>Alerta ALTA · SSH · 02:09</b>\nTocá para ver el detalle en Zonda.\nRed: Oficina &lt;central&gt;"
    # Una alerta de otro día lleva también la fecha.
    assert "Alerta MEDIA · FTP · 27/09 02:09" in texto_del_aviso("media", "ftp", deteccion, "Oficina", ARGENTINA, hoy=date(2026, 9, 28))
    enlace = enlace_del_aviso(
        "https://taiel1998.github.io/a/", "a-1", "red-oficina", "alta", "ssh", deteccion, deteccion + timedelta(seconds=51)
    )
    assert enlace == (
        "https://taiel1998.github.io/a/a-1?red=red-oficina&severidad=alta&servicio=ssh"
        "&deteccion=2026-09-27T05:09:12Z&aviso=2026-09-27T05:10:03Z"
    )


def test_solo_avisa_alertas_nuevas_y_nunca_a_un_telefono_revocado(cliente, auth, telegram, bot):
    vincular(cliente, auth, telegram, bot)
    telegram.enviados.clear()
    # La semilla es anterior al vínculo: no se avisa lo viejo.
    assert asyncio.run(bot.avisar_nuevas()) == 0

    nueva_alerta(cliente, "fuerza_bruta")
    assert cliente.delete("/dispositivos/actual", headers=auth).status_code == 204

    assert asyncio.run(bot.avisar_nuevas()) == 0
    assert telegram.enviados == []


def test_si_bloquearon_el_bot_el_telefono_queda_sin_telegram(cliente, auth, telegram, bot):
    vincular(cliente, auth, telegram, bot)
    nueva_alerta(cliente, "fuerza_bruta")
    telegram.error = (403, "Forbidden: bot was blocked by the user")

    assert asyncio.run(bot.avisar_nuevas()) == 0

    assert not cliente.get("/telegram", headers=auth).json()["vinculado"]


def test_la_alerta_avisada_vuelve_a_bajar_con_la_hora_del_aviso(cliente, auth, telegram, bot):
    vincular(cliente, auth, telegram, bot)
    alerta_id = nueva_alerta(cliente, "fuerza_bruta")
    cursor = None
    while True:
        pagina = cliente.get("/alertas", params={"desde": cursor} if cursor else None, headers=auth).json()
        cursor = pagina["cursorSiguiente"]
        if not pagina["hayMas"]:
            break
    assert all(alerta["avisoTelegram"] is None for alerta in pagina["alertas"])

    asyncio.run(bot.avisar_nuevas())

    novedades = cliente.get("/alertas", params={"desde": cursor}, headers=auth).json()["alertas"]
    assert [alerta["id"] for alerta in novedades] == [alerta_id]
    assert novedades[0]["avisoTelegram"] is not None


def test_aviso_de_prueba_y_desvincular(cliente, auth, telegram, bot):
    assert cliente.post("/telegram/prueba", headers=auth).status_code == 409
    vincular(cliente, auth, telegram, bot)

    assert cliente.post("/telegram/prueba", headers=auth).status_code == 204
    assert telegram.enviados[-1]["text"] == "<b>Aviso de prueba</b>\nLos avisos de Red de prueba llegan a este chat."

    assert cliente.delete("/telegram/vinculo", headers=auth).status_code == 204
    assert telegram.enviados[-1]["text"].startswith("Este chat ya no recibe los avisos de Red de prueba")
    assert not cliente.get("/telegram", headers=auth).json()["vinculado"]
    assert cliente.post("/telegram/prueba", headers=auth).status_code == 409

    vincular(cliente, auth, telegram, bot)
    telegram.error = (500, "Internal Server Error")
    assert cliente.post("/telegram/prueba", headers=auth).status_code == 502


def test_guardar_los_avisos_del_telefono(cliente, auth):
    nuevos = {"alta": "vibracion", "media": "sin_aviso", "baja": "sonido"}

    assert cliente.put("/telegram/avisos", json=nuevos, headers=auth).json() == nuevos

    assert cliente.get("/telegram", headers=auth).json()["avisos"] == nuevos
    assert cliente.put("/telegram/avisos", json={"alta": "fuerte"}, headers=auth).status_code == 422
    assert cliente.put("/telegram/avisos", json=nuevos).status_code == 401


def test_el_token_no_aparece_en_los_errores_ni_en_la_configuracion(config):
    def sin_red(pedido: httpx.Request) -> httpx.Response:
        raise httpx.ConnectError(f"no se pudo conectar con {pedido.url}", request=pedido)

    cliente = ClienteTelegram("123:secreto", "https://telegram.prueba", transporte=httpx.MockTransport(sin_red))
    with pytest.raises(ErrorTelegram) as error:
        asyncio.run(cliente.enviar("1", "hola"))

    assert "secreto" not in str(error.value)
    # La excepción de httpx, que tiene la URL con el token, no queda encadenada.
    assert error.value.__suppress_context__
    config.telegram_token = "123:secreto"
    assert "secreto" not in repr(config)


def test_la_escucha_del_bot_sigue_si_la_base_esta_trabada(bot, monkeypatch):
    """Una base trabada (por ejemplo, con un comando de zonda.cmd escribiendo) no corta la escucha: el bot
    espera y vuelve a preguntar. Antes la tarea se cortaba sin avisar y el bot dejaba de leer los mensajes."""
    vueltas: list[int] = []

    async def recibir_con_la_base_trabada() -> None:
        vueltas.append(1)
        raise sqlite3.OperationalError("database is locked")

    class FinDeLaPrueba(Exception):
        pass

    esperas: list[float] = []

    async def esperar(segundos: float) -> None:
        esperas.append(segundos)
        if len(esperas) == 2:
            raise FinDeLaPrueba

    monkeypatch.setattr(bot, "recibir", recibir_con_la_base_trabada)
    monkeypatch.setattr("app.telegram.asyncio.sleep", esperar)
    with pytest.raises(FinDeLaPrueba):
        asyncio.run(bot.escuchar())

    assert len(vueltas) == 2
    # Espera cada vez más, como cuando Telegram no responde.
    assert esperas == [1, 2]
