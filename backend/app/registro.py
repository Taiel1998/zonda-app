"""El registro de cambios de la red (decisión 40): quién cambió la configuración, cuándo y por qué.

Un solo registro con todo: los cambios de severidad y de importancia de un equipo, con su motivo, y
los de la gente (sumar a alguien, cambiarle el perfil, quitarlo), sin motivo porque la línea ya dice
qué pasó. Lo ve todo el equipo y desde la app nadie lo puede borrar ni editar: solo se agregan filas.
"""

import sqlite3

from .auth import ahora, iso, nombre_de
from .modelos import CambioRegistro

# Quién figura en los cambios hechos desde la terminal de la notebook (zonda pair, zonda revoke).
AUTOR_SERVIDOR = "Servidor"


def anotar(
    con: sqlite3.Connection,
    tipo: str,
    autor: sqlite3.Row | None,
    objeto: str,
    nombre: str | None = None,
    antes: str | None = None,
    despues: str | None = None,
    motivo: str | None = None,
) -> None:
    """Agrega una línea al registro, a nombre de [autor] (el teléfono que hizo el cambio, o None si fue la terminal)."""
    con.execute(
        "INSERT INTO registro_cambios (fecha, tipo, autor_id, autor, objeto, nombre, antes, despues, motivo) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
        (
            iso(ahora()),
            tipo,
            autor["id"] if autor is not None else None,
            nombre_de(autor) if autor is not None else AUTOR_SERVIDOR,
            objeto,
            nombre,
            antes,
            despues,
            motivo,
        ),
    )


def leer(con: sqlite3.Connection) -> list[CambioRegistro]:
    """De lo más nuevo a lo más viejo."""
    return [
        CambioRegistro(
            id=f["id"],
            fecha=f["fecha"],
            tipo=f["tipo"],
            autorId=f["autor_id"],
            autor=f["autor"],
            objeto=f["objeto"],
            nombre=f["nombre"],
            antes=f["antes"],
            despues=f["despues"],
            motivo=f["motivo"],
        )
        for f in con.execute("SELECT * FROM registro_cambios ORDER BY id DESC")
    ]
