"""El tráfico que ve el sensor, para los gráficos del tablero web (decisión 49).

El sensor (herramientas/sensor/metricas_trafico.py, en zeek-lab) lee el conn.log de Zeek y cada 5
segundos manda a POST /ingesta/trafico lo que sumó: por tramo de 5 segundos y servicio, las conexiones
que empezaron, cuántas no llegaron a establecerse y los bytes y paquetes, repartidos entre los tramos que
duró cada conexión; y por minuto, quién habló con quién. Zeek escribe una conexión cuando termina, así
que una larga completa tramos que ya habían llegado: por eso todo se suma y nada se pisa.

Los tiempos van en segundos desde 1970 (UTC), con el reloj del sensor, que es el mismo de las alertas.
"""

import sqlite3
from collections import defaultdict

from .modelos import ParTrafico, TramoTrafico

# Los pasos que acepta GET /trafico: de 5 segundos (en vivo) a una hora (la semana).
PASOS = (5, 10, 15, 30, 60, 300, 900, 3600)
# Puntos como mucho por serie: una semana en pasos de 5 minutos son 2.016.
MAX_PUNTOS = 5000
# Pares como mucho en el mapa: un escaneo desde muchos orígenes no puede llenar la pantalla.
MAX_PARES = 200

_SUMAR_TRAMO = """
INSERT INTO trafico (t, servicio, conexiones, fallidas, bytes, paquetes) VALUES (?, ?, ?, ?, ?, ?)
ON CONFLICT (t, servicio) DO UPDATE SET
    conexiones = conexiones + excluded.conexiones,
    fallidas = fallidas + excluded.fallidas,
    bytes = bytes + excluded.bytes,
    paquetes = paquetes + excluded.paquetes
"""

_SUMAR_PAR = """
INSERT INTO trafico_pares (minuto, origen, destino, servicio, conexiones, fallidas, bytes) VALUES (?, ?, ?, ?, ?, ?, ?)
ON CONFLICT (minuto, origen, destino, servicio) DO UPDATE SET
    conexiones = conexiones + excluded.conexiones,
    fallidas = fallidas + excluded.fallidas,
    bytes = bytes + excluded.bytes
"""


def guardar(con: sqlite3.Connection, tramos: list[TramoTrafico], pares: list[ParTrafico]) -> None:
    """Suma lo que mandó el sensor a lo que ya había en cada tramo y en cada minuto."""
    con.executemany(_SUMAR_TRAMO, [(x.t, x.servicio, x.conexiones, x.fallidas, x.bytes, x.paquetes) for x in tramos])
    con.executemany(
        _SUMAR_PAR,
        [(p.minuto - p.minuto % 60, p.origen, p.destino, p.servicio, p.conexiones, p.fallidas, p.bytes) for p in pares],
    )


def podar(con: sqlite3.Connection, limite: int) -> None:
    """Borra el tráfico anterior a `limite` (segundos desde 1970): el servidor guarda una semana."""
    con.execute("DELETE FROM trafico WHERE t < ?", (limite,))
    con.execute("DELETE FROM trafico_pares WHERE minuto < ?", (limite,))


def ultimo_tramo(con: sqlite3.Connection) -> int | None:
    return con.execute("SELECT MAX(t) FROM trafico").fetchone()[0]


def serie(con: sqlite3.Connection, desde: int, hasta: int, paso: int) -> tuple[list[dict], dict[str, list[dict]]]:
    """El total y cada servicio en tramos de `paso` segundos, solo los tramos con tráfico."""
    total: dict[int, dict] = {}
    servicios: dict[str, list[dict]] = defaultdict(list)
    filas = con.execute(
        "SELECT (t / ?) * ? AS tramo, servicio, SUM(conexiones) AS conexiones, SUM(fallidas) AS fallidas, "
        "SUM(bytes) AS bytes, SUM(paquetes) AS paquetes FROM trafico WHERE t >= ? AND t < ? "
        "GROUP BY tramo, servicio ORDER BY tramo, servicio",
        (paso, paso, desde, hasta),
    )
    for fila in filas:
        punto = {"t": fila["tramo"], "conexiones": fila["conexiones"], "fallidas": fila["fallidas"], "bytes": fila["bytes"], "paquetes": fila["paquetes"]}
        servicios[fila["servicio"]].append(punto)
        suma = total.setdefault(fila["tramo"], {"t": fila["tramo"], "conexiones": 0, "fallidas": 0, "bytes": 0, "paquetes": 0})
        for campo in ("conexiones", "fallidas", "bytes", "paquetes"):
            suma[campo] += punto[campo]
    return list(total.values()), dict(servicios)


def pares(con: sqlite3.Connection, desde: int, hasta: int) -> list[dict]:
    """Quién habló con quién entre `desde` y `hasta`: un par por origen y destino, con sus servicios de
    más a menos conexiones. Cuenta el minuto en el que cae `desde` entero."""
    juntos: dict[tuple[str, str], dict] = {}
    filas = con.execute(
        "SELECT origen, destino, servicio, SUM(conexiones) AS conexiones, SUM(fallidas) AS fallidas, SUM(bytes) AS bytes, "
        "MAX(minuto) AS ultimo FROM trafico_pares WHERE minuto >= ? AND minuto < ? GROUP BY origen, destino, servicio",
        (desde - desde % 60, hasta),
    )
    for fila in filas:
        par = juntos.setdefault(
            (fila["origen"], fila["destino"]),
            {"origen": fila["origen"], "destino": fila["destino"], "conexiones": 0, "fallidas": 0, "bytes": 0, "ultimoMinuto": 0, "porServicio": {}},
        )
        par["conexiones"] += fila["conexiones"]
        par["fallidas"] += fila["fallidas"]
        par["bytes"] += fila["bytes"]
        par["ultimoMinuto"] = max(par["ultimoMinuto"], fila["ultimo"])
        par["porServicio"][fila["servicio"]] = fila["conexiones"]
    ordenados = sorted(juntos.values(), key=lambda p: (-p["conexiones"], -p["bytes"], p["origen"], p["destino"]))[:MAX_PARES]
    for par in ordenados:
        por_servicio = par.pop("porServicio")
        par["servicios"] = sorted(por_servicio, key=lambda s: (-por_servicio[s], s))
    return ordenados
