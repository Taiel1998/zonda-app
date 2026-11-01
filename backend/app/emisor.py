"""Emisor para la demo: cada N segundos publica un episodio nuevo, copiado de uno existente.

Sirve para mostrar en vivo que la sincronización trae alertas nuevas y que llegan los avisos.
Se activa con ZONDA_EMITIR_CADA=<segundos> en .env; apagado por defecto. Para una alerta de un
tipo en el momento justo está `zonda emitir` (app/cli.py), que usa la misma función.
"""

import asyncio
import logging
import random
import sqlite3
import uuid
from datetime import timedelta

from .auth import ahora, iso
from .db import Base
from .modelos import CLAVE_REGLAS, CLAVE_UMBRALES

registro = logging.getLogger("zonda.emisor")

COLUMNAS_COPIADAS = (
    "cantidad_flujos, uid_referencia, origen_ip, destino_ip, servicio, protocolo, puerto, puerto_referencia, puertos_distintos, "
    "tipo_ataque, confianza_tipo, veredicto_ia, error_reconstruccion, umbral_ia, veredicto_umbrales, contexto_60s, "
    "umbral_servicio, servicio_sin_linea_base, veredicto_reglas, par_destino, par_en_catalogo"
)


def emitir_una(
    base: Base, aleatorio: random.Random | None = None, tipo: str | None = None, servicio: str | None = None
) -> str | None:
    """Inserta una alerta nueva con los datos de una existente elegida al azar. Devuelve su id.

    Con `tipo` o `servicio`, la elige entre las de ese tipo y ese servicio; el tipo es una clave de la
    tabla de severidades, así que las que la IA no vio se piden por el detector que las marcó:
    CLAVE_REGLAS o CLAVE_UMBRALES (cambio 9). Si no hay ninguna para copiar, devuelve None.
    """
    azar = aleatorio or random.Random()
    condiciones: list[str] = []
    parametros: list[str] = []
    if tipo == CLAVE_REGLAS:
        condiciones.append("tipo_ataque IS NULL AND veredicto_reglas = 1")
    elif tipo == CLAVE_UMBRALES:
        condiciones.append("tipo_ataque IS NULL AND veredicto_umbrales = 1")
    elif tipo is not None:
        condiciones.append("tipo_ataque = ?")
        parametros.append(tipo)
    if servicio is not None:
        condiciones.append("servicio = ?")
        parametros.append(servicio)
    filtro = " WHERE " + " AND ".join(condiciones) if condiciones else ""
    with base.transaccion() as con:
        ids = [f["id"] for f in con.execute(f"SELECT id FROM alertas{filtro}", parametros).fetchall()]
        if not ids:
            return None
        origen = con.execute(f"SELECT {COLUMNAS_COPIADAS} FROM alertas WHERE id = ?", (azar.choice(ids),)).fetchone()
        momento = ahora()
        fin = momento - timedelta(seconds=azar.randint(60, 300))
        inicio = fin - timedelta(seconds=azar.randint(30, 600))
        nuevo_id = "a-" + uuid.uuid4().hex[:12]
        con.execute(
            f"INSERT INTO alertas (id, creada_en, inicio, fin, {COLUMNAS_COPIADAS}, estado_revision, version, actualizada_en, secuencia) "
            f"VALUES (?, ?, ?, ?, {', '.join('?' for _ in origen.keys())}, 'sin_revisar', 1, ?, ?)",
            (nuevo_id, iso(momento), iso(inicio), iso(fin), *tuple(origen), iso(momento), Base.siguiente_secuencia(con)),
        )
        Base.escribir_meta(con, "ultimo_flujo", iso(fin))
        return nuevo_id


async def emitir_periodicamente(base: Base, cada_segundos: int) -> None:
    """Una alerta nueva cada [cada_segundos]. Con la base trabada se saltea esa vuelta: la demo sigue."""
    while True:
        await asyncio.sleep(cada_segundos)
        try:
            emitir_una(base)
        except sqlite3.Error:
            registro.exception("No se pudo emitir la alerta de la demo, se prueba en la vuelta siguiente")
