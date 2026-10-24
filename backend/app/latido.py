"""Latido del análisis en vivo: el pipeline del laboratorio avisa al final de cada ciclo que sigue
andando, aunque no haya episodios, y el servidor se da cuenta si deja de hacerlo.

El último latido se guarda en la tabla meta, sin tablas nuevas: `latido` con lo que mandó el sensor y
`latido_recibido_en` con la hora del servidor, que es la que mide la frescura (el reloj de la VM puede
no coincidir con el de la notebook). GET /salud lo usa para la tarjeta "Estado del sistema" de la app.

El vigía revisa cada pocos segundos la edad del latido: si pasa del límite (2 minutos), avisa por
Telegram que el análisis se detuvo, una sola vez, y cuando vuelve, que volvió. Con el mismo criterio
avisa la pérdida de captura: el latido trae la última medición de Zeek (capture_loss.log, cada 15
minutos), y si pasa del 1 % el sensor ve parcialmente (el puerto espejo no le pasa todo, por ejemplo
porque se satura), así que puede haber ataques que Zonda no ve. Como los avisos de alertas, solo llevan
la hora, el porcentaje y la red: nunca direcciones IP ni nombres de equipos (RNF02).
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
TEXTO_PARCIAL = (
    "<b>Sensor viendo parcialmente</b>\nZeek perdió el {pct} % de lo que pasó por el puerto espejo en la medición "
    "de las {hora}: puede haber tráfico, y ataques, que Zonda no ve.\nRed: {red}"
)
TEXTO_NORMAL = "<b>Sensor viendo todo de nuevo</b>\nLa pérdida de captura bajó al {pct} % en la medición de las {hora}.\nRed: {red}"
# Con la pérdida simulada del laboratorio (vivo/.perdida_simulada), para que nadie la tome por real.
LINEA_SIMULADA = "\n(Prueba: pérdida simulada en el laboratorio.)"


def guardar(con: sqlite3.Connection, datos: dict) -> str:
    """Guarda el último latido (lo que mandó el sensor, con las fechas ya en UTC) y la hora del
    servidor en que llegó, que devuelve."""
    recibido = iso(ahora())
    Base.escribir_meta(con, "latido", json.dumps(datos))
    Base.escribir_meta(con, "latido_recibido_en", recibido)
    return recibido


def _ve_parcialmente(datos: dict, cfg: Config) -> bool:
    """Zeek anda pero perdió más que `perdida_limite` (1 %) en su última medición de pérdida de captura."""
    perdida = datos.get("perdidaCaptura")
    return bool(datos.get("zeekActivo")) and perdida is not None and perdida > cfg.perdida_limite


def estado_del_sistema(base: Base, cfg: Config) -> EstadoSistema:
    """El servidor funciona (respondió); el análisis en vivo funciona si su último latido tiene hasta
    `latido_limite` segundos; el sensor, según lo que dijo ese latido: detenido si Zeek no está vivo y
    parcial si perdió más del límite. Sin latido no hay datos de ninguno de los dos, y con el latido
    viejo, del sensor tampoco."""
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
            if not datos.get("zeekActivo"):
                sensor = "detenido"
            elif _ve_parcialmente(datos, cfg):
                sensor = "parcial"
            else:
                sensor = "funciona"
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
        perdidaCaptura=datos.get("perdidaCaptura"),
        perdidaCapturaEn=datos.get("perdidaCapturaEn"),
        acksCaptura=datos.get("acksCaptura"),
        perdidaSimulada=bool(datos.get("perdidaSimulada")),
        perdidaLimite=cfg.perdida_limite,
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


def _porcentaje(valor: float) -> str:
    """3,2: un decimal y coma decimal, como la app."""
    return f"{valor:.1f}".replace(".", ",")


def texto_parcial(
    perdida: float, medido: datetime, red: str, zona: tzinfo | None = None, hoy: date | None = None, simulada: bool = False
) -> str:
    """El aviso de que el sensor ve parcialmente, con el porcentaje y la hora de la medición de Zeek:

        Sensor viendo parcialmente
        Zeek perdió el 3,2 % de lo que pasó por el puerto espejo en la medición de las 18:45: puede haber tráfico, y ataques, que Zonda no ve.
        Red: Oficina central
    """
    texto = TEXTO_PARCIAL.format(pct=_porcentaje(perdida), hora=_hora(medido, zona, hoy), red=html.escape(red))
    return texto + LINEA_SIMULADA if simulada else texto


def texto_normal(
    perdida: float, medido: datetime, red: str, zona: tzinfo | None = None, hoy: date | None = None, simulada: bool = False
) -> str:
    """El aviso de que la pérdida de captura se normalizó, con la medición que lo dijo."""
    texto = TEXTO_NORMAL.format(pct=_porcentaje(perdida), hora=_hora(medido, zona, hoy), red=html.escape(red))
    return texto + LINEA_SIMULADA if simulada else texto


def _chats(con: sqlite3.Connection) -> list[str]:
    """Los chats vinculados a teléfonos que siguen en la red, una vez cada uno."""
    filas = con.execute(
        "SELECT DISTINCT telegram_chat_id FROM dispositivos "
        "WHERE telegram_chat_id IS NOT NULL AND revocado_en IS NULL AND vence_en > ? ORDER BY telegram_chat_id",
        (iso(ahora()),),
    ).fetchall()
    return [fila[0] for fila in filas]


async def _avisar(base: Base, bot: BotTelegram, texto: str) -> None:
    """Manda [texto] a cada chat vinculado. Si la persona bloqueó el bot, ese chat queda sin Telegram y se
    sigue con los demás; otro error de Telegram corta la vuelta, para reintentar en la siguiente."""
    for chat_id in _chats(base.conexion()):
        try:
            await bot.cliente.enviar(chat_id, texto)
        except ErrorTelegram as error:
            if not error.chat_perdido:
                raise
            bot.desvincular_chat(chat_id)


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
        await _avisar(base, bot, (texto_detenido if detenido else texto_volvio)(ultimo, cfg.red_nombre, bot.zona))
    nuevo = "detenido" if detenido else "funciona"
    with base.transaccion() as con:
        Base.escribir_meta(con, "latido_aviso", nuevo)
    return nuevo


async def revisar_perdida(base: Base, cfg: Config, bot: BotTelegram | None) -> str | None:
    """Una vuelta del vigía para la pérdida de captura, con el mismo criterio que la caída: si el sensor
    pasó a ver parcialmente (más de `perdida_limite`), avisa una vez a cada chat; si se normalizó, avisa
    que volvió a ver todo. Devuelve el estado nuevo ("parcial" o "normal"), o None si no cambió.

    Solo mira un latido al día, con Zeek vivo y con una medición: con el latido vencido avisa la caída
    del análisis, y con Zeek caído la medición es vieja (la tarjeta lo muestra detenido)."""
    recibido = base.leer_meta("latido_recibido_en")
    guardado = base.leer_meta("latido")
    if recibido is None or guardado is None:
        return None
    ultimo = datetime.fromisoformat(recibido)
    datos = json.loads(guardado)
    perdida = datos.get("perdidaCaptura")
    if (ahora() - ultimo).total_seconds() > cfg.latido_limite or perdida is None or not datos.get("zeekActivo"):
        return None
    parcial = _ve_parcialmente(datos, cfg)
    if parcial == (base.leer_meta("perdida_aviso", "normal") == "parcial"):
        return None
    if bot is not None:
        medido = datetime.fromisoformat(datos["perdidaCapturaEn"]) if datos.get("perdidaCapturaEn") else ultimo
        armar = texto_parcial if parcial else texto_normal
        await _avisar(base, bot, armar(perdida, medido, cfg.red_nombre, bot.zona, simulada=bool(datos.get("perdidaSimulada"))))
    nuevo = "parcial" if parcial else "normal"
    with base.transaccion() as con:
        Base.escribir_meta(con, "perdida_aviso", nuevo)
    return nuevo


async def vigilar(base: Base, cfg: Config, bot: BotTelegram | None) -> None:
    """Cada `latido_revisar_cada` segundos, la caída del análisis y la pérdida de captura. Un aviso que no
    sale no frena al otro."""
    while True:
        for revision in (revisar, revisar_perdida):
            try:
                await revision(base, cfg, bot)
            except ErrorTelegram as error:
                registro.warning("El aviso del análisis en vivo no salió, se reintenta: %s", error)
            except sqlite3.Error:
                registro.exception("No se pudo leer el latido del análisis en vivo")
        await asyncio.sleep(cfg.latido_revisar_cada)
