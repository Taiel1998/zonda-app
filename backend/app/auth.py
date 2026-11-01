"""Tokens por dispositivo y códigos de un solo uso (informe, sección 13.4).

El token se entrega una sola vez al parear y la API guarda solo su hash SHA-256: si alguien lee la
base del servidor no obtiene tokens válidos. Vence a los 90 días y se puede revocar.
"""

import hashlib
import secrets
import sqlite3
import uuid
from datetime import datetime, timedelta, timezone
from typing import Annotated

from fastapi import Depends, Header, HTTPException, Request

ALFABETO_CODIGO = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"  # sin 0, O, 1 ni I, que se confunden al escribir
# Cómo se llaman los navegadores pareados con el tablero web (decisión 50): «Tablero web · Chrome en Windows».
PREFIJO_TABLERO = "Tablero web"


def ahora() -> datetime:
    return datetime.now(timezone.utc)


def iso(momento: datetime) -> str:
    return momento.isoformat(timespec="seconds")


def hash_token(token: str) -> str:
    return hashlib.sha256(token.encode("utf-8")).hexdigest()


def nuevo_codigo() -> str:
    """Ocho caracteres en dos grupos, por ejemplo K7M2-QX9P."""
    letras = "".join(secrets.choice(ALFABETO_CODIGO) for _ in range(8))
    return letras[:4] + "-" + letras[4:]


def normalizar_codigo(codigo: str) -> str:
    limpio = codigo.strip().upper().replace("-", "")
    return limpio[:4] + "-" + limpio[4:] if len(limpio) == 8 else codigo.strip().upper()


def contenido_qr(url_publica: str, codigo: str, vence: datetime) -> str:
    """Lo que lleva el QR: la dirección, el código y cuándo vence (la app muestra "válido hasta las HH:MM")."""
    return f"zonda:{url_publica}#{codigo};vence={iso(vence)}"


def crear_codigo_pareo(
    con: sqlite3.Connection,
    minutos: int,
    persona: str | None = None,
    perfil: str = "administrador",
    creado_por: str | None = None,
) -> tuple[str, datetime]:
    """Un código de un uso. Los de la terminal (zonda pair) son de administrador; los que genera un
    administrador desde la app llevan el nombre y el perfil de quien se suma (decisión 39)."""
    codigo = nuevo_codigo()
    vence = ahora() + timedelta(minutes=minutos)
    con.execute(
        "INSERT INTO codigos_pareo (codigo, creado_en, vence_en, persona, perfil, creado_por) VALUES (?, ?, ?, ?, ?, ?)",
        (codigo, iso(ahora()), iso(vence), persona, perfil, creado_por),
    )
    return codigo, vence


def canjear_codigo_pareo(con: sqlite3.Connection, codigo: str, nombre_dispositivo: str, dias_token: int, tipo: str | None = None) -> dict:
    """Invalida el código y crea el dispositivo con su token, con la persona y el perfil del código, y lo
    anota en el registro de cambios. Lanza 400 si no existe, 410 si venció o se usó. Sin [tipo] (la app y el
    tablero anteriores a la API 0.9.0), el tablero web se reconoce por su nombre."""
    from .registro import anotar

    fila = con.execute("SELECT * FROM codigos_pareo WHERE codigo = ?", (normalizar_codigo(codigo),)).fetchone()
    if fila is None:
        raise HTTPException(status_code=400, detail="El código no es válido")
    if fila["usado_en"] is not None or datetime.fromisoformat(fila["vence_en"]) < ahora():
        raise HTTPException(status_code=410, detail="El código venció o ya se usó")
    token = secrets.token_urlsafe(32)
    dispositivo_id = "d-" + uuid.uuid4().hex[:12]
    emitido = ahora()
    vence = emitido + timedelta(days=dias_token)
    nombre = nombre_dispositivo.strip()
    tipo = tipo or ("navegador" if nombre.startswith(PREFIJO_TABLERO) else "telefono")
    con.execute(
        "INSERT INTO dispositivos (id, nombre, token_hash, creado_en, vence_en, perfil, persona, ultimo_uso, tipo) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
        (dispositivo_id, nombre, hash_token(token), iso(emitido), iso(vence), fila["perfil"], fila["persona"], iso(emitido), tipo),
    )
    con.execute(
        "UPDATE codigos_pareo SET usado_en = ?, dispositivo_id = ? WHERE codigo = ?",
        (iso(ahora()), dispositivo_id, fila["codigo"]),
    )
    # "Sumó a Laura al equipo como analista", a nombre de quien generó el código (o del servidor).
    autor = con.execute("SELECT * FROM dispositivos WHERE id = ?", (fila["creado_por"],)).fetchone() if fila["creado_por"] else None
    anotar(con, "persona_nueva", autor, objeto=dispositivo_id, nombre=fila["persona"] or nombre_dispositivo.strip(), despues=fila["perfil"])
    return {
        "token": token,
        "dispositivoId": dispositivo_id,
        "emitido": iso(emitido),
        "vence": iso(vence),
        "perfil": fila["perfil"],
        "persona": fila["persona"],
    }


def nombre_de(dispositivo: sqlite3.Row) -> str:
    """Cómo se nombra a una persona: su nombre o, en los teléfonos pareados antes de la 1.0.5, el del teléfono."""
    return dispositivo["persona"] or dispositivo["nombre"]


def buscar_dispositivo(con: sqlite3.Connection, token: str) -> sqlite3.Row | None:
    fila = con.execute("SELECT * FROM dispositivos WHERE token_hash = ?", (hash_token(token),)).fetchone()
    if fila is None or fila["revocado_en"] is not None:
        return None
    if datetime.fromisoformat(fila["vence_en"]) < ahora():
        return None
    return fila


def token_de_cabecera(authorization: str | None) -> str | None:
    if not authorization:
        return None
    partes = authorization.split(" ", 1)
    if len(partes) != 2 or partes[0].lower() != "bearer":
        return None
    return partes[1].strip()


def dispositivo_actual(request: Request, authorization: Annotated[str | None, Header()] = None) -> sqlite3.Row:
    """Dependencia de FastAPI: el dispositivo dueño del token, o 401 (la app pide un pareo nuevo). De
    paso anota cuándo lo usó la persona por última vez (Ajustes → Personas), una vez por minuto."""
    token = token_de_cabecera(authorization)
    if token is None:
        raise HTTPException(status_code=401, detail="Falta el token")
    base = request.app.state.base
    fila = buscar_dispositivo(base.conexion(), token)
    if fila is None:
        raise HTTPException(status_code=401, detail="El token venció o fue revocado")
    momento = ahora()
    if fila["ultimo_uso"] is None or datetime.fromisoformat(fila["ultimo_uso"]) < momento - USO_CADA:
        with base.transaccion() as con:
            con.execute("UPDATE dispositivos SET ultimo_uso = ? WHERE id = ?", (iso(momento), fila["id"]))
    return fila


# Cada cuánto, como mucho, se anota el último uso de un teléfono.
USO_CADA = timedelta(minutes=1)


def administrador_actual(dispositivo: Annotated[sqlite3.Row, Depends(dispositivo_actual)]) -> sqlite3.Row:
    """Dependencia de FastAPI para lo que cambia la configuración de la red: 403 a un analista (decisión 39).
    La app además esconde esos botones; el que controla es el servidor."""
    if dispositivo["perfil"] != "administrador":
        raise HTTPException(status_code=403, detail="Lo cambia un administrador")
    return dispositivo
