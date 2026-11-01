"""La gente de la red (decisión 39): cada persona es su teléfono pareado, con su nombre y su perfil.

Sin cuentas ni correos. Un administrador suma gente con un código de un uso que genera desde la app
(Ajustes → Personas → Agregar persona), le cambia el perfil o la quita del equipo. Siempre queda al
menos un administrador mientras haya gente: el servidor no deja pasar a analista ni quitar al último.
A un analista además le elige «Bloqueos» (decisión 51): «Bloquea», en el acto como un administrador, o
«Pide aprobación».
"""

import sqlite3

from fastapi import HTTPException

from .auth import ahora, iso, nombre_de
from .modelos import Persona
from .registro import anotar

SIN_ADMINISTRADOR = "Tiene que quedar al menos un administrador en el equipo"
# Cómo queda «Bloqueos» en el registro de cambios.
BLOQUEA = "bloquea"
PIDE_APROBACION = "pide_aprobacion"


def activos(con: sqlite3.Connection) -> list[sqlite3.Row]:
    """Los teléfonos que pueden usar la red: ni revocados ni vencidos, del primero que se sumó al último."""
    return con.execute(
        "SELECT * FROM dispositivos WHERE revocado_en IS NULL AND vence_en > ? ORDER BY creado_en, rowid",
        (iso(ahora()),),
    ).fetchall()


def activo(con: sqlite3.Connection, dispositivo_id: str) -> sqlite3.Row | None:
    return next((fila for fila in activos(con) if fila["id"] == dispositivo_id), None)


def puede_bloquear(fila: sqlite3.Row) -> bool:
    """Si bloquea en el acto: un administrador siempre; un analista, si un administrador lo pasó a «Bloquea»
    (decisión 51). Si no, pide aprobación."""
    return fila["perfil"] == "administrador" or bool(fila["puede_bloquear"])


def persona_dto(fila: sqlite3.Row, vos: bool) -> Persona:
    return Persona(
        id=fila["id"],
        nombre=fila["persona"],
        telefono=fila["nombre"],
        perfil=fila["perfil"],
        ultimoUso=fila["ultimo_uso"],
        vos=vos,
        tipo=fila["tipo"],
        puedeBloquear=puede_bloquear(fila),
    )


def deja_sin_administrador(con: sqlite3.Connection, dispositivo_id: str, quitar: bool) -> bool:
    """Si quitar a esa persona (o, con quitar en False, pasarla a analista) deja gente sin ningún
    administrador. Quitar al único teléfono de la red sí se puede: no queda nadie sin administrador."""
    quedan = [fila for fila in activos(con) if not (quitar and fila["id"] == dispositivo_id)]
    administradores = [fila for fila in quedan if fila["perfil"] == "administrador" and fila["id"] != dispositivo_id]
    return bool(quedan) and not administradores


def cambiar_perfil(con: sqlite3.Connection, persona: sqlite3.Row, perfil: str, autor: sqlite3.Row) -> None:
    """Hacer administrador o pasar a analista. 409 si deja al equipo sin administrador. «Bloqueos» vuelve a
    «Pide aprobación»: un administrador que pasa a analista no se lleva el bloqueo en el acto."""
    if persona["perfil"] == perfil:
        return
    if perfil == "analista" and deja_sin_administrador(con, persona["id"], quitar=False):
        raise HTTPException(status_code=409, detail=SIN_ADMINISTRADOR)
    con.execute("UPDATE dispositivos SET perfil = ?, puede_bloquear = 0 WHERE id = ?", (perfil, persona["id"]))
    anotar(con, "perfil", autor, objeto=persona["id"], nombre=nombre_de(persona), antes=persona["perfil"], despues=perfil)


def cambiar_permiso_bloqueo(con: sqlite3.Connection, persona: sqlite3.Row, puede: bool, autor: sqlite3.Row) -> None:
    """«Bloqueos» de un analista: «Bloquea» (en el acto, con su huella) o «Pide aprobación». Queda en el
    registro de cambios. 409 para un administrador, que siempre bloquea."""
    if persona["perfil"] == "administrador":
        raise HTTPException(status_code=409, detail="Un administrador siempre bloquea")
    if bool(persona["puede_bloquear"]) == puede:
        return
    con.execute("UPDATE dispositivos SET puede_bloquear = ? WHERE id = ?", (int(puede), persona["id"]))
    antes, despues = (PIDE_APROBACION, BLOQUEA) if puede else (BLOQUEA, PIDE_APROBACION)
    anotar(con, "permiso_bloqueo", autor, objeto=persona["id"], nombre=nombre_de(persona), antes=antes, despues=despues)


def quitar(con: sqlite3.Connection, persona: sqlite3.Row, autor: sqlite3.Row) -> None:
    """Quitar del equipo (o quitar el propio teléfono): revoca su token. 409 si deja gente sin administrador."""
    if deja_sin_administrador(con, persona["id"], quitar=True):
        raise HTTPException(status_code=409, detail=SIN_ADMINISTRADOR)
    con.execute("UPDATE dispositivos SET revocado_en = ? WHERE id = ?", (iso(ahora()), persona["id"]))
    anotar(con, "persona_quitada", autor, objeto=persona["id"], nombre=nombre_de(persona), antes=persona["perfil"])
