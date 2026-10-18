"""A quién está asignada cada alerta (decisión 42; corrección 1 de Peña).

El administrador asigna a cualquiera de Personas, también a sí mismo, o saca la asignación. El
analista se asigna una alerta sin asignar ("Me la asigno") y suelta la suya ("Soltarla"); no le puede
sacar una alerta a otra persona. Asignar no traba nada: cualquiera la puede revisar. Si dos asignan a
la vez queda la última. Cada cambio queda en la línea de tiempo de la alerta y mueve su secuencia, así
llega a todos los teléfonos con la sincronización; no cambia la versión de la revisión.
"""

import sqlite3

from fastapi import HTTPException

from .auth import ahora, iso, nombre_de
from .db import Base
from .modelos import Asignacion, AsignacionEntrada
from .personas import activo


def asignacion_dto(fila: sqlite3.Row) -> Asignacion:
    return Asignacion(
        responsableId=fila["responsable_id"],
        responsable=fila["responsable"],
        porId=fila["por_id"],
        por=fila["por"],
        fecha=fila["fecha"],
    )


def asignaciones_de(con: sqlite3.Connection, alerta_id: str) -> list[Asignacion]:
    """Cada cambio de responsable de la alerta, del primero al último."""
    return [asignacion_dto(f) for f in con.execute("SELECT * FROM asignaciones WHERE alerta_id = ? ORDER BY id", (alerta_id,))]


def responsable_de(eventos: list[Asignacion]) -> Asignacion | None:
    """El último cambio manda; si dejó la alerta sin asignar, no hay responsable."""
    ultima = eventos[-1] if eventos else None
    return ultima if ultima is not None and ultima.responsableId is not None else None


def asignar(con: sqlite3.Connection, alerta: sqlite3.Row, entrada: AsignacionEntrada, dispositivo: sqlite3.Row) -> None:
    """Aplica el cambio de responsable con las reglas de cada perfil. Si no cambia nada, no anota nada."""
    actual = responsable_de(asignaciones_de(con, alerta["id"]))
    actual_id = actual.responsableId if actual is not None else None
    nuevo_id = entrada.responsableId
    if nuevo_id == actual_id:
        return
    if dispositivo["perfil"] != "administrador":
        if nuevo_id is None and actual_id != dispositivo["id"]:
            raise HTTPException(status_code=403, detail="Solo podés soltar una alerta asignada a vos")
        if nuevo_id is not None and nuevo_id != dispositivo["id"]:
            raise HTTPException(status_code=403, detail="Solo un administrador asigna alertas a otra persona")
        if nuevo_id is not None and actual_id is not None:
            raise HTTPException(status_code=409, detail="Ya está asignada a otra persona")
    responsable = None
    if nuevo_id is not None:
        responsable = activo(con, nuevo_id)
        if responsable is None:
            raise HTTPException(status_code=422, detail="Esa persona no está en el equipo")
    momento = iso(ahora())
    con.execute(
        "INSERT INTO asignaciones (alerta_id, accion_id, responsable_id, responsable, por_id, por, fecha) VALUES (?, ?, ?, ?, ?, ?, ?)",
        (
            alerta["id"],
            entrada.accionId,
            nuevo_id,
            nombre_de(responsable) if responsable is not None else None,
            dispositivo["id"],
            nombre_de(dispositivo),
            momento,
        ),
    )
    con.execute(
        "UPDATE alertas SET actualizada_en = ?, secuencia = ? WHERE id = ?",
        (momento, Base.siguiente_secuencia(con), alerta["id"]),
    )
