"""Cómo queda guardada una revisión (RF04).

La usan la ruta POST /alertas/{id}/revision y el comando `zonda tranquila`: así una revisión hecha
desde la notebook queda igual que una que llega de un teléfono, con su autor y su dispositivo en el
historial, la versión siguiente y una secuencia nueva para que los teléfonos la bajen al sincronizar.
"""

import sqlite3
from datetime import datetime, timezone

from .auth import ahora, iso
from .db import Base
from .modelos import RevisionEntrada


def aplicar_revision(con: sqlite3.Connection, alerta: sqlite3.Row, entrada: RevisionEntrada, dispositivo: sqlite3.Row) -> None:
    """Guarda la revisión del dispositivo y deja la alerta con su resultado. No valida nada: el motivo,
    la idempotencia por accionId y el conflicto los resuelve antes quien la llama."""
    momento = ahora()
    nueva_version = alerta["version"] + 1
    # El autor es el teléfono y la persona, su nombre: el detalle muestra "Laura · Teléfono de Laura" (decisión 39).
    con.execute(
        "INSERT INTO revisiones (alerta_id, accion_id, resultado, motivo, comentario, autor, fecha, revisada_en, dispositivo_id, version_resultante, persona) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
        (alerta["id"], entrada.accionId, entrada.resultado, entrada.motivo, entrada.comentario.strip(), dispositivo["nombre"], iso(momento), revisada_en(entrada.revisadaEn, momento), dispositivo["id"], nueva_version, dispositivo["persona"]),
    )
    con.execute(
        "UPDATE alertas SET estado_revision = ?, version = ?, actualizada_en = ?, secuencia = ? WHERE id = ?",
        (entrada.resultado, nueva_version, iso(momento), Base.siguiente_secuencia(con), alerta["id"]),
    )


def revisada_en(valor: str | None, recibida: datetime) -> str:
    """La hora de revisión del teléfono, nunca posterior a la llegada: un reloj adelantado no la corre al futuro."""
    if valor is None:
        return iso(recibida)
    return iso(min(datetime.fromisoformat(valor), recibida).astimezone(timezone.utc))
