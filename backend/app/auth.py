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

from fastapi import Header, HTTPException, Request

ALFABETO_CODIGO = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"  # sin 0, O, 1 ni I, que se confunden al escribir


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


def crear_codigo_pareo(con: sqlite3.Connection, minutos: int) -> tuple[str, datetime]:
    codigo = nuevo_codigo()
    vence = ahora() + timedelta(minutes=minutos)
    con.execute(
        "INSERT INTO codigos_pareo (codigo, creado_en, vence_en) VALUES (?, ?, ?)",
        (codigo, iso(ahora()), iso(vence)),
    )
    return codigo, vence


def canjear_codigo_pareo(con: sqlite3.Connection, codigo: str, nombre_dispositivo: str, dias_token: int) -> dict:
    """Invalida el código y crea el dispositivo con su token. Lanza 400 si no existe, 410 si venció o se usó."""
    fila = con.execute("SELECT * FROM codigos_pareo WHERE codigo = ?", (normalizar_codigo(codigo),)).fetchone()
    if fila is None:
        raise HTTPException(status_code=400, detail="El código no es válido")
    if fila["usado_en"] is not None or datetime.fromisoformat(fila["vence_en"]) < ahora():
        raise HTTPException(status_code=410, detail="El código venció o ya se usó")
    token = secrets.token_urlsafe(32)
    dispositivo_id = "d-" + uuid.uuid4().hex[:12]
    emitido = ahora()
    vence = emitido + timedelta(days=dias_token)
    con.execute(
        "INSERT INTO dispositivos (id, nombre, token_hash, creado_en, vence_en) VALUES (?, ?, ?, ?, ?)",
        (dispositivo_id, nombre_dispositivo.strip(), hash_token(token), iso(emitido), iso(vence)),
    )
    con.execute(
        "UPDATE codigos_pareo SET usado_en = ?, dispositivo_id = ? WHERE codigo = ?",
        (iso(ahora()), dispositivo_id, fila["codigo"]),
    )
    return {"token": token, "dispositivoId": dispositivo_id, "emitido": iso(emitido), "vence": iso(vence)}


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
    """Dependencia de FastAPI: el dispositivo dueño del token, o 401 (la app pide un pareo nuevo)."""
    token = token_de_cabecera(authorization)
    if token is None:
        raise HTTPException(status_code=401, detail="Falta el token")
    fila = buscar_dispositivo(request.app.state.base.conexion(), token)
    if fila is None:
        raise HTTPException(status_code=401, detail="El token venció o fue revocado")
    return fila
