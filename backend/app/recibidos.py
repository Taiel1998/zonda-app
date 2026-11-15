"""Lo que le llega al servidor desde la red, por minuto (decisión 60).

El servidor anota en la tabla `recibidos` cada envío que le llega de la cadena: el tráfico del sensor
(POST /ingesta/trafico, cada 5 segundos), los latidos y las alertas del análisis en vivo (POST
/ingesta/latido y POST /ingesta/alertas) y las consultas del firewall (GET /firewall/bloqueos, cada 10
segundos). Lo que piden la app y el tablero no cuenta: el historial no depende de que alguien los tenga
abiertos.

Además, cada pocos segundos anota que anduvo en ese minuto, aunque no le haya llegado nada. Por eso un
minuto con la fila en cero es un rato en que estuvo prendido sin recibir datos, y un minuto sin fila, un
rato en que estuvo apagado. GET /recibidos lo da por tramo para los gráficos del servidor y del firewall
en "Estado del sistema" del tablero web. Se guarda lo mismo que el tráfico (dias_trafico).

Los minutos van en segundos desde 1970 (UTC), con el reloj del servidor.
"""

import asyncio
import logging
import sqlite3

from .auth import ahora
from .config import Config
from .db import Base

registro = logging.getLogger("zonda.recibidos")

# Quién manda: una columna de la tabla por cada uno.
ORIGENES = ("sensor", "analisis", "firewall")
# Los pasos que acepta GET /recibidos (y GET /latidos): del minuto a la hora, para una semana.
PASOS = (60, 300, 900, 3600)
# Cada cuánto anota el servidor que sigue andando: varias veces por minuto, así ninguno queda sin fila.
ANOTAR_CADA = 20

_ANDUVO = "INSERT OR IGNORE INTO recibidos (minuto) VALUES (?)"
_SUMAR = {
    origen: f"INSERT INTO recibidos (minuto, {origen}) VALUES (?, 1) ON CONFLICT (minuto) DO UPDATE SET {origen} = {origen} + 1"
    for origen in ORIGENES
}


def anotar(con: sqlite3.Connection, origen: str | None = None, dias: int = 7) -> int:
    """Suma un envío de `origen` al minuto de ahora, que devuelve; sin origen, solo anota que el servidor
    anduvo en ese minuto. De paso borra lo de más de `dias` días."""
    minuto = int(ahora().timestamp()) // 60 * 60
    con.execute(_ANDUVO if origen is None else _SUMAR[origen], (minuto,))
    con.execute("DELETE FROM recibidos WHERE minuto < ?", (minuto - dias * 86400,))
    return minuto


def serie(con: sqlite3.Connection, desde: int, hasta: int, paso: int = 60) -> list[dict]:
    """Lo recibido en [desde, hasta) en tramos de `paso` segundos: cuántos minutos del tramo anduvo el
    servidor y cuántos envíos le llegaron de cada origen. Solo los tramos en que anduvo."""
    filas = con.execute(
        "SELECT (minuto / ?) * ? AS tramo, COUNT(*) AS minutos, SUM(sensor) AS sensor, SUM(analisis) AS analisis, "
        "SUM(firewall) AS firewall FROM recibidos WHERE minuto >= ? AND minuto < ? GROUP BY tramo ORDER BY tramo",
        (paso, paso, desde, hasta),
    )
    return [
        {"t": f["tramo"], "minutos": f["minutos"], "sensor": f["sensor"], "analisis": f["analisis"], "firewall": f["firewall"]}
        for f in filas
    ]


async def anotar_cada_minuto(base: Base, cfg: Config, cada: float = ANOTAR_CADA) -> None:
    """Mientras el servidor anda, deja la fila de cada minuto. Si una vuelta falla, sigue con la próxima."""
    while True:
        try:
            with base.transaccion() as con:
                anotar(con, dias=cfg.dias_trafico)
        except sqlite3.Error:
            registro.exception("No se pudo anotar el minuto del servidor")
        await asyncio.sleep(cada)
