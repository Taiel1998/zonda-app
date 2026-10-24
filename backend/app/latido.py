"""Latido del análisis en vivo: el pipeline del laboratorio avisa al final de cada ciclo que sigue
andando, aunque no haya episodios, y el servidor se da cuenta si deja de hacerlo.

El último latido se guarda en la tabla meta, sin tablas nuevas: `latido` con lo que mandó el sensor y
`latido_recibido_en` con la hora del servidor, que es la que mide la frescura (el reloj de la VM puede
no coincidir con el de la notebook). GET /salud lo usa para la tarjeta "Estado del sistema" de la app.

El vigía revisa cada pocos segundos la edad del latido: si pasa del límite (2 minutos), avisa por
Telegram que el análisis se detuvo, una sola vez, y cuando vuelve, que volvió. Como los avisos de
alertas, solo llevan la hora y la red: nunca direcciones IP ni nombres de equipos (RNF02).
"""

import asyncio
import html
import json
import logging
import sqlite3
from datetime import date, datetime, tzinfo

from .auth import ahora, iso
from .config import Config
from .db import Base
from .modelos import EstadoSistema
from .telegram import BotTelegram, ErrorTelegram

registro = logging.getLogger("zonda.latido")

TEXTO_DETENIDO = (
    "<b>Análisis en vivo detenido</b>\nZonda no recibe noticias del análisis en vivo desde las {hora}: "
    "las alertas nuevas no llegan hasta que vuelva.\nRed: {red}"
)
TEXTO_VOLVIO = "<b>Análisis en vivo funcionando de nuevo</b>\nZonda volvió a recibir el análisis en vivo a las {hora}.\nRed: {red}"


def guardar(con: sqlite3.Connection, datos: dict) -> str:
    """Guarda el último latido (lo que mandó el sensor, con las fechas ya en UTC) y la hora del
    servidor en que llegó, que devuelve."""
    recibido = iso(ahora())
    Base.escribir_meta(con, "latido", json.dumps(datos))
    Base.escribir_meta(con, "latido_recibido_en", recibido)
    return recibido


def estado_del_sistema(base: Base, cfg: Config) -> EstadoSistema:
    """El servidor funciona (respondió); el análisis en vivo funciona si su último latido tiene hasta
    `latido_limite` segundos; el sensor, según lo que dijo ese latido. Sin latido no hay datos de
    ninguno de los dos, y con el latido viejo, del sensor tampoco."""
    recibido = base.leer_meta("latido_recibido_en")
    guardado = base.leer_meta("latido")
    datos = json.loads(guardado) if guardado else {}
    segundos = None
    if recibido is None:
        analisis = sensor = "sin_datos"
    else:
        # Si el reloj de la notebook se atrasó, la edad no puede quedar negativa.
        segundos = max(0, int((ahora() - datetime.fromisoformat(recibido)).total_seconds()))
        if segundos <= cfg.latido_limite:
            analisis = "funciona"
            sensor = "funciona" if datos.get("zeekActivo") else "detenido"
        else:
            analisis, sensor = "detenido", "sin_datos"
    return EstadoSistema(
        servidor="funciona",
        analisis=analisis,
        sensor=sensor,
        ultimaCorrida=recibido,
        segundosDesdeUltimaCorrida=segundos,
        ultimoFlujo=datos.get("ultimoFlujo"),
        flujosUltimaCorrida=datos.get("flujos"),
        limiteSegundos=cfg.latido_limite,
    )


def _hora(momento: datetime, zona: tzinfo | None, hoy: date | None) -> str:
    """HH:MM en la hora local del servidor; si no es de hoy, también la fecha, como el aviso de alertas."""
    local = momento.astimezone(zona)
    if local.date() != (hoy or datetime.now(zona).date()):
        return local.strftime("%d/%m %H:%M")
    return local.strftime("%H:%M")


def texto_detenido(ultimo: datetime, red: str, zona: tzinfo | None = None, hoy: date | None = None) -> str:
    """El aviso de que el análisis se detuvo, con la hora del último latido que llegó:

        Análisis en vivo detenido
        Zonda no recibe noticias del análisis en vivo desde las 18:41: las alertas nuevas no llegan hasta que vuelva.
        Red: Oficina central
    """
    return TEXTO_DETENIDO.format(hora=_hora(ultimo, zona, hoy), red=html.escape(red))


def texto_volvio(momento: datetime, red: str, zona: tzinfo | None = None, hoy: date | None = None) -> str:
    """El aviso de que volvió, con la hora del latido que lo recuperó."""
    return TEXTO_VOLVIO.format(hora=_hora(momento, zona, hoy), red=html.escape(red))


def _chats(con: sqlite3.Connection) -> list[str]:
    """Los chats vinculados a teléfonos que siguen en la red, una vez cada uno."""
    filas = con.execute(
        "SELECT DISTINCT telegram_chat_id FROM dispositivos "
        "WHERE telegram_chat_id IS NOT NULL AND revocado_en IS NULL AND vence_en > ? ORDER BY telegram_chat_id",
        (iso(ahora()),),
    ).fetchall()
    return [fila[0] for fila in filas]


async def revisar(base: Base, cfg: Config, bot: BotTelegram | None) -> str | None:
    """Una vuelta del vigía. Si el análisis pasó de andando a detenido, o al revés, avisa a cada chat
    vinculado y guarda el estado nuevo, que devuelve ("detenido" o "funciona"); si no cambió, None. Sin
    bot solo guarda el estado. Si Telegram no responde, el estado no cambia y se reintenta en la vuelta
    siguiente; si la persona bloqueó el bot, ese chat queda sin Telegram y se sigue con los demás."""
    recibido = base.leer_meta("latido_recibido_en")
    if recibido is None:
        return None  # todavía no llegó ningún latido: no hay nada que vigilar
    ultimo = datetime.fromisoformat(recibido)
    detenido = (ahora() - ultimo).total_seconds() > cfg.latido_limite
    if detenido == (base.leer_meta("latido_aviso", "funciona") == "detenido"):
        return None
    if bot is not None:
        texto = (texto_detenido if detenido else texto_volvio)(ultimo, cfg.red_nombre, bot.zona)
        for chat_id in _chats(base.conexion()):
            try:
                await bot.cliente.enviar(chat_id, texto)
            except ErrorTelegram as error:
                if not error.chat_perdido:
                    raise
                bot.desvincular_chat(chat_id)
    nuevo = "detenido" if detenido else "funciona"
    with base.transaccion() as con:
        Base.escribir_meta(con, "latido_aviso", nuevo)
    return nuevo


async def vigilar(base: Base, cfg: Config, bot: BotTelegram | None) -> None:
    while True:
        try:
            await revisar(base, cfg, bot)
        except ErrorTelegram as error:
            registro.warning("El aviso del análisis en vivo no salió, se reintenta: %s", error)
        except sqlite3.Error:
            registro.exception("No se pudo leer el latido del análisis en vivo")
        await asyncio.sleep(cfg.latido_revisar_cada)
