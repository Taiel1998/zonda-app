"""Emisor para la demo: cada N segundos publica un episodio nuevo, copiado de uno existente.

Sirve para mostrar en vivo que la sincronización trae alertas nuevas y que llegan los avisos.
Se activa con ZONDA_EMITIR_CADA=<segundos> en .env; apagado por defecto. Para una alerta de un
tipo en el momento justo está `zonda emitir` (app/cli.py), que usa la misma función.
"""

import asyncio
import random
import uuid
from datetime import timedelta

from .auth import ahora, iso
from .db import Base
from .modelos import CLAVE_SIN_TIPO

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
    tabla de severidades, así que las que detectaron solo las reglas o los umbrales son CLAVE_SIN_TIPO.
    Si no hay ninguna para copiar, devuelve None.
    """
    azar = aleatorio or random.Random()
    condiciones: list[str] = []
    parametros: list[str] = []
    if tipo is not None:
        condiciones.append("COALESCE(tipo_ataque, ?) = ?")
        parametros += [CLAVE_SIN_TIPO, tipo]
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
    while True:
        await asyncio.sleep(cada_segundos)
        emitir_una(base)
