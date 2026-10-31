"""Llaves de acceso del tablero web (decisión 51): la huella, la cara o el PIN de la computadora, con WebAuthn
(Windows Hello), para lo que da poder o corta tráfico desde un navegador.

El teléfono pide su huella antes de mandar el pedido. En la computadora, la confirmación la revisa el servidor:
la llave vive en el chip de seguridad de la computadora y nunca sale de ahí; el servidor guarda solo la parte
pública y, en cada acción, revisa la firma, la dirección del tablero y que la persona se haya verificado. Cada
desafío sirve para una sola acción, la que se dijo al pedirlo, y vence a los dos minutos.

La llave se crea al parear el navegador, en los 10 minutos siguientes y una sola vez: así la crea solo quien
tenía el código de un uso, que hizo un administrador confirmando con su huella o con su llave. Sin la librería
webauthn, las llaves están apagadas y el tablero hace lo de antes: mirar, revisar, asignar y pedir.
"""

import ipaddress
import json
import secrets
import sqlite3
from base64 import urlsafe_b64decode
from datetime import datetime, timedelta
from urllib.parse import urlsplit

from fastapi import APIRouter, Depends, HTTPException, Request

from .auth import ahora, dispositivo_actual, iso, nombre_de
from .config import Config
from .db import Base
from .modelos import AccionLlave, EstadoLlave, LlaveNueva, OpcionesLlave

router = APIRouter()

# Cuánto después del pareo se puede crear la llave, y cuánto vale un desafío.
VENTANA_PARA_CREARLA = timedelta(minutes=10)
VIGENCIA_DESAFIO = timedelta(minutes=2)
# Lo que muestra Windows Hello como el sitio que pide la llave.
NOMBRE_DEL_SERVIDOR = "Zonda"

SIN_LIBRERIA = "Este servidor no tiene las llaves de acceso: falta la librería webauthn"
FALTA_LLAVE = "Confirmalo con la llave de esta computadora"
NO_CONFIRMO = "La llave de esta computadora no confirmó esta acción"
POR_EL_NOMBRE = "Las llaves funcionan entrando al tablero por su nombre, no por la IP"


def _base(request: Request) -> Base:
    return request.app.state.base


def _config(request: Request) -> Config:
    return request.app.state.config


def _webauthn():
    """La librería, o 503 si el servidor no la tiene: el resto del servidor anda igual, como el QR sin qrcode."""
    try:
        import webauthn
    except ImportError:
        raise HTTPException(status_code=503, detail=SIN_LIBRERIA) from None
    return webauthn


def origen_de(url: str) -> str:
    """https://zonda-equipo.duckdns.org/panel → https://zonda-equipo.duckdns.org."""
    partes = urlsplit(url.strip())
    return f"{partes.scheme}://{partes.netloc}"


def origenes(cfg: Config) -> list[str]:
    """Las direcciones del tablero desde las que vale una llave: la pública del servidor y las de prueba."""
    return [origen_de(cfg.url_publica), *(origen_de(origen) for origen in cfg.origenes_llave)]


def _origen(request: Request) -> tuple[str, str]:
    """La dirección del tablero que hace el pedido (la cabecera Origin del navegador) y su nombre, que es el
    rp_id de WebAuthn. 403 si no es una del servidor o si es una IP: una llave se ata a un nombre."""
    origen = (request.headers.get("origin") or "").rstrip("/")
    nombre = urlsplit(origen).hostname or ""
    if origen not in origenes(_config(request)):
        raise HTTPException(status_code=403, detail=POR_EL_NOMBRE)
    try:
        ipaddress.ip_address(nombre)
    except ValueError:
        return origen, nombre
    raise HTTPException(status_code=403, detail=POR_EL_NOMBRE)


def canonica(accion: dict) -> str:
    """La acción en JSON con las claves ordenadas: así se compara lo que se confirmó con lo que se hace."""
    return json.dumps(accion, sort_keys=True, separators=(",", ":"), ensure_ascii=False)


def _llaves_de(con: sqlite3.Connection, dispositivo: sqlite3.Row) -> list[sqlite3.Row]:
    return con.execute("SELECT * FROM llaves WHERE dispositivo_id = ? ORDER BY creada_en", (dispositivo["id"],)).fetchall()


def _puede_crearla(dispositivo: sqlite3.Row, tiene: bool) -> bool:
    """Un navegador sin llave, en los 10 minutos después de parearse."""
    pareado = datetime.fromisoformat(dispositivo["creado_en"])
    return dispositivo["tipo"] == "navegador" and not tiene and ahora() - pareado <= VENTANA_PARA_CREARLA


def _nuevo_desafio(con: sqlite3.Connection, dispositivo: sqlite3.Row, proposito: str, origen: str, accion: str | None = None) -> tuple[str, bytes]:
    momento = ahora()
    con.execute("DELETE FROM desafios_llave WHERE vence_en < ?", (iso(momento - timedelta(days=1)),))
    desafio_id = "q-" + secrets.token_hex(12)
    desafio = secrets.token_bytes(32)
    con.execute(
        "INSERT INTO desafios_llave (id, dispositivo_id, desafio, proposito, accion, origen, vence_en) VALUES (?, ?, ?, ?, ?, ?, ?)",
        (desafio_id, dispositivo["id"], desafio, proposito, accion, origen, iso(momento + VIGENCIA_DESAFIO)),
    )
    return desafio_id, desafio


def _usar_desafio(base: Base, desafio_id: str, dispositivo: sqlite3.Row, proposito: str, origen: str) -> sqlite3.Row:
    """El desafío, ya gastado aunque después la firma no valga: cada uno sirve una sola vez. 403 si no es de este
    navegador, es de otra cosa, vino de otra dirección, ya se usó o venció."""
    with base.transaccion() as con:
        fila = con.execute("SELECT * FROM desafios_llave WHERE id = ?", (desafio_id,)).fetchone()
        vale = (
            fila is not None
            and fila["dispositivo_id"] == dispositivo["id"]
            and fila["proposito"] == proposito
            and fila["origen"] == origen
            and fila["usado_en"] is None
            and fila["vence_en"] > iso(ahora())
        )
        if vale:
            con.execute("UPDATE desafios_llave SET usado_en = ? WHERE id = ?", (iso(ahora()), desafio_id))
    if not vale:
        raise HTTPException(status_code=403, detail=NO_CONFIRMO)
    return fila


# ---- Crear la llave, al parear el navegador ----


@router.get("/llaves", response_model=EstadoLlave)
def estado(request: Request, dispositivo: sqlite3.Row = Depends(dispositivo_actual)) -> EstadoLlave:
    """Si este navegador tiene su llave y si todavía la puede crear (en los 10 minutos después de parearse)."""
    tiene = bool(_llaves_de(_base(request).conexion(), dispositivo))
    return EstadoLlave(tiene=tiene, puedeCrear=_puede_crearla(dispositivo, tiene))


@router.post("/llaves/opciones", response_model=OpcionesLlave)
def opciones_para_crearla(request: Request, dispositivo: sqlite3.Row = Depends(dispositivo_actual)) -> OpcionesLlave:
    """Lo que el navegador le pasa a navigator.credentials.create: la llave pide la verificación de la persona
    (huella, cara o PIN) y se ata al nombre del tablero. 403 a un teléfono (confirma con su huella), fuera de los
    10 minutos o desde una dirección que no es la del tablero; 409 si ya tiene llave; 503 sin la librería."""
    webauthn = _webauthn()
    from webauthn.helpers.cose import COSEAlgorithmIdentifier
    from webauthn.helpers.structs import AuthenticatorSelectionCriteria, ResidentKeyRequirement, UserVerificationRequirement

    origen, rp_id = _origen(request)
    if dispositivo["tipo"] != "navegador":
        raise HTTPException(status_code=403, detail="La llave es del tablero web: el teléfono confirma con su huella")
    with _base(request).transaccion() as con:
        tiene = bool(_llaves_de(con, dispositivo))
        if tiene:
            raise HTTPException(status_code=409, detail="Esta computadora ya tiene su llave")
        if not _puede_crearla(dispositivo, tiene):
            raise HTTPException(status_code=403, detail="La llave se crea al parear este navegador, en los 10 minutos siguientes")
        desafio_id, desafio = _nuevo_desafio(con, dispositivo, "registro", origen)
    opciones = webauthn.generate_registration_options(
        rp_id=rp_id,
        rp_name=NOMBRE_DEL_SERVIDOR,
        user_id=dispositivo["id"].encode(),
        user_name=dispositivo["nombre"],
        user_display_name=nombre_de(dispositivo),
        challenge=desafio,
        timeout=int(VIGENCIA_DESAFIO.total_seconds() * 1000),
        authenticator_selection=AuthenticatorSelectionCriteria(
            resident_key=ResidentKeyRequirement.DISCOURAGED,
            user_verification=UserVerificationRequirement.REQUIRED,
        ),
        supported_pub_key_algs=[COSEAlgorithmIdentifier.ECDSA_SHA_256, COSEAlgorithmIdentifier.RSASSA_PKCS1_v1_5_SHA_256],
    )
    return OpcionesLlave(desafioId=desafio_id, opciones=json.loads(webauthn.options_to_json(opciones)))


@router.post("/llaves", response_model=EstadoLlave, status_code=201)
def crearla(entrada: LlaveNueva, request: Request, dispositivo: sqlite3.Row = Depends(dispositivo_actual)) -> EstadoLlave:
    """Guarda la parte pública de la llave que creó la computadora, si el desafío, la dirección, la firma y la
    verificación de la persona están bien (403 si algo no coincide). 409 si ya tiene llave."""
    webauthn = _webauthn()
    from webauthn.helpers import bytes_to_base64url
    from webauthn.helpers.cose import COSEAlgorithmIdentifier
    from webauthn.helpers.exceptions import WebAuthnException

    origen, rp_id = _origen(request)
    base = _base(request)
    if not _puede_crearla(dispositivo, bool(_llaves_de(base.conexion(), dispositivo))):
        raise HTTPException(status_code=409, detail="Esta computadora ya tiene su llave, o ya pasaron los 10 minutos")
    desafio = _usar_desafio(base, entrada.desafioId, dispositivo, "registro", origen)
    try:
        hecha = webauthn.verify_registration_response(
            credential=entrada.credencial,
            expected_challenge=desafio["desafio"],
            expected_rp_id=rp_id,
            expected_origin=origen,
            require_user_verification=True,
            supported_pub_key_algs=[COSEAlgorithmIdentifier.ECDSA_SHA_256, COSEAlgorithmIdentifier.RSASSA_PKCS1_v1_5_SHA_256],
        )
    except WebAuthnException:
        raise HTTPException(status_code=403, detail="La computadora no pudo crear la llave") from None
    with base.transaccion() as con:
        con.execute(
            "INSERT INTO llaves (id, dispositivo_id, clave_publica, contador, rp_id, creada_en) VALUES (?, ?, ?, ?, ?, ?)",
            (bytes_to_base64url(hecha.credential_id), dispositivo["id"], hecha.credential_public_key, hecha.sign_count, rp_id, iso(ahora())),
        )
    return EstadoLlave(tiene=True, puedeCrear=False)


# ---- Confirmar una acción con la llave ----


@router.post("/llaves/desafio", response_model=OpcionesLlave)
def desafio_para(entrada: AccionLlave, request: Request, dispositivo: sqlite3.Row = Depends(dispositivo_actual)) -> OpcionesLlave:
    """Lo que el navegador le pasa a navigator.credentials.get para confirmar [entrada.accion]: el desafío sirve
    solo para esa acción y vence a los dos minutos. 403 si esta computadora no tiene llave para este tablero."""
    webauthn = _webauthn()
    from webauthn.helpers import base64url_to_bytes
    from webauthn.helpers.structs import PublicKeyCredentialDescriptor, UserVerificationRequirement

    origen, rp_id = _origen(request)
    with _base(request).transaccion() as con:
        llaves = [fila for fila in _llaves_de(con, dispositivo) if fila["rp_id"] == rp_id]
        if not llaves:
            raise HTTPException(status_code=403, detail="Esta computadora no tiene su llave: se crea al parear el tablero")
        desafio_id, desafio = _nuevo_desafio(con, dispositivo, "accion", origen, canonica(entrada.accion))
    opciones = webauthn.generate_authentication_options(
        rp_id=rp_id,
        challenge=desafio,
        timeout=int(VIGENCIA_DESAFIO.total_seconds() * 1000),
        allow_credentials=[PublicKeyCredentialDescriptor(id=base64url_to_bytes(fila["id"])) for fila in llaves],
        user_verification=UserVerificationRequirement.REQUIRED,
    )
    return OpcionesLlave(desafioId=desafio_id, opciones=json.loads(webauthn.options_to_json(opciones)))


def confirmada(request: Request, dispositivo: sqlite3.Row, accion: dict) -> bool:
    """Si el navegador confirmó [accion] con su llave, en la cabecera X-Llave (base64url de
    {"desafio": "q-...", "credencial": <lo que devolvió navigator.credentials.get>}). False si no la mandó; 403
    si la mandó y no vale: otra acción, otra computadora, desafío usado o vencido, firma mala o la persona sin
    verificarse. Se llama antes de abrir la transacción de la acción: la revisión usa las suyas."""
    crudo = request.headers.get("x-llave")
    if not crudo:
        return False
    webauthn = _webauthn()
    from webauthn.helpers.exceptions import WebAuthnException

    try:
        datos = json.loads(urlsafe_b64decode(crudo + "=" * (-len(crudo) % 4)))
        desafio_id, credencial = str(datos["desafio"]), datos["credencial"]
        credencial_id = str(credencial["id"])
    except (ValueError, KeyError, TypeError):
        raise HTTPException(status_code=403, detail=NO_CONFIRMO) from None
    origen, rp_id = _origen(request)
    base = _base(request)
    desafio = _usar_desafio(base, desafio_id, dispositivo, "accion", origen)
    llave = base.conexion().execute("SELECT * FROM llaves WHERE id = ? AND dispositivo_id = ?", (credencial_id, dispositivo["id"])).fetchone()
    if desafio["accion"] != canonica(accion) or llave is None or llave["rp_id"] != rp_id:
        raise HTTPException(status_code=403, detail=NO_CONFIRMO)
    try:
        hecha = webauthn.verify_authentication_response(
            credential=credencial,
            expected_challenge=desafio["desafio"],
            expected_rp_id=rp_id,
            expected_origin=origen,
            credential_public_key=llave["clave_publica"],
            credential_current_sign_count=llave["contador"],
            require_user_verification=True,
        )
    except WebAuthnException:
        raise HTTPException(status_code=403, detail=NO_CONFIRMO) from None
    with base.transaccion() as con:
        con.execute("UPDATE llaves SET contador = ?, usada_en = ? WHERE id = ?", (hecha.new_sign_count, iso(ahora()), llave["id"]))
    return True


def exigir(request: Request, dispositivo: sqlite3.Row, accion: dict) -> None:
    """Desde un navegador, lo que da poder o corta tráfico se confirma con la llave (403 sin ella). Un teléfono
    confirma con su huella o su PIN, en la app."""
    if dispositivo["tipo"] == "navegador" and not confirmada(request, dispositivo, accion):
        raise HTTPException(status_code=403, detail=FALTA_LLAVE)
