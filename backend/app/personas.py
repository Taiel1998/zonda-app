"""La gente de la red (decisión 39): cada persona es su teléfono pareado, con su nombre y su perfil.

Sin cuentas ni correos. Un administrador suma gente con un código de un uso que genera desde la app
(Ajustes → Personas → Agregar persona), le cambia el perfil o la quita del equipo. Siempre queda al
menos un administrador mientras haya gente: el servidor no deja pasar a analista ni quitar al último.
"""

import sqlite3

from fastapi import HTTPException

from .auth import ahora, iso, nombre_de
from .modelos import Persona
from .registro import anotar

SIN_ADMINISTRADOR = "Tiene que quedar al menos un administrador en el equipo"


def activos(con: sqlite3.Connection) -> list[sqlite3.Row]:
    """Los teléfonos que pueden usar la red: ni revocados ni vencidos, del primero que se sumó al último."""
    return con.execute(
        "SELECT * FROM dispositivos WHERE revocado_en IS NULL AND vence_en > ? ORDER BY creado_en, rowid",
        (iso(ahora()),),
    ).fetchall()


def activo(con: sqlite3.Connection, dispositivo_id: str) -> sqlite3.Row | None:
    return next((fila for fila in activos(con) if fila["id"] == dispositivo_id), None)


def persona_dto(fila: sqlite3.Row, vos: bool) -> Persona:
    return Persona(
        id=fila["id"],
        nombre=fila["persona"],
        telefono=fila["nombre"],
        perfil=fila["perfil"],
        ultimoUso=fila["ultimo_uso"],
        vos=vos,
    )


def deja_sin_administrador(con: sqlite3.Connection, dispositivo_id: str, quitar: bool) -> bool:
    """Si quitar a esa persona (o, con quitar en False, pasarla a analista) deja gente sin ningún
    administrador. Quitar al único teléfono de la red sí se puede: no queda nadie sin administrador."""
    quedan = [fila for fila in activos(con) if not (quitar and fila["id"] == dispositivo_id)]
    administradores = [fila for fila in quedan if fila["perfil"] == "administrador" and fila["id"] != dispositivo_id]
    return bool(quedan) and not administradores


def cambiar_perfil(con: sqlite3.Connection, persona: sqlite3.Row, perfil: str, autor: sqlite3.Row) -> None:
    """Hacer administrador o pasar a analista. 409 si deja al equipo sin administrador."""
    if persona["perfil"] == perfil:
        return
    if perfil == "analista" and deja_sin_administrador(con, persona["id"], quitar=False):
        raise HTTPException(status_code=409, detail=SIN_ADMINISTRADOR)
    con.execute("UPDATE dispositivos SET perfil = ? WHERE id = ?", (perfil, persona["id"]))
    anotar(con, "perfil", autor, objeto=persona["id"], nombre=nombre_de(persona), antes=persona["perfil"], despues=perfil)


def quitar(con: sqlite3.Connection, persona: sqlite3.Row, autor: sqlite3.Row) -> None:
    """Quitar del equipo (o quitar el propio teléfono): revoca su token. 409 si deja gente sin administrador."""
    if deja_sin_administrador(con, persona["id"], quitar=True):
        raise HTTPException(status_code=409, detail=SIN_ADMINISTRADOR)
    con.execute("UPDATE dispositivos SET revocado_en = ? WHERE id = ?", (iso(ahora()), persona["id"]))
    anotar(con, "persona_quitada", autor, objeto=persona["id"], nombre=nombre_de(persona), antes=persona["perfil"])
