"""Llaves de acceso del tablero web (decisión 51): la computadora confirma con la huella, la cara o el PIN
(WebAuthn) y el servidor revisa la firma. Una llave de prueba hace de Windows Hello."""

import base64
import hashlib
import json
import secrets
import sys
from datetime import timedelta

import pytest

# Sin la librería (un entorno anterior a requirements.txt con webauthn), estas pruebas no corren; el servidor
# anda igual, con las llaves apagadas.
pytest.importorskip("webauthn")
cbor2 = pytest.importorskip("cbor2")

from cryptography.hazmat.primitives import hashes  # noqa: E402
from cryptography.hazmat.primitives.asymmetric import ec  # noqa: E402

from app.auth import ahora, iso  # noqa: E402
from tests.conftest import cabecera  # noqa: E402
from tests.test_bloqueos import MOTIVO, TOKEN_FIREWALL, bloquear, con_bloquea, de_origenes_distintos, lo_que_bloquea_el_firewall  # noqa: E402
from tests.test_perfiles_y_registro import sumar  # noqa: E402
from tests.test_revision_y_severidades import primera_alta  # noqa: E402

ORIGEN = "https://zonda.prueba"


@pytest.fixture
def config(config):
    """La red de prueba con un firewall conectado y el tablero en https://zonda.prueba."""
    config.firewall_token = TOKEN_FIREWALL
    config.url_publica = ORIGEN
    return config


def b64(datos: bytes) -> str:
    return base64.urlsafe_b64encode(datos).rstrip(b"=").decode()


class LlaveDePrueba:
    """Hace de Windows Hello: una llave P-256 que firma lo que le pide el servidor. Con [verifica] en False, la
    persona no puso la huella ni el PIN (solo tocó)."""

    def __init__(self, verifica: bool = True) -> None:
        self.privada = ec.generate_private_key(ec.SECP256R1())
        self.id = secrets.token_bytes(16)
        self.contador = 0
        self.verifica = verifica

    def _datos(self, rp_id: str, con_clave: bytes | None = None) -> bytes:
        # Presente (0x01), verificada (0x04) y, al crearla, con la clave (0x40).
        banderas = 0x01 | (0x04 if self.verifica else 0) | (0x40 if con_clave else 0)
        datos = hashlib.sha256(rp_id.encode()).digest() + bytes([banderas]) + self.contador.to_bytes(4, "big")
        if con_clave:
            datos += bytes(16) + len(self.id).to_bytes(2, "big") + self.id + con_clave
        return datos

    def crear(self, opciones: dict, origen: str = ORIGEN) -> dict:
        """Lo que devuelve navigator.credentials.create."""
        publica = self.privada.public_key().public_numbers()
        clave = cbor2.dumps({1: 2, 3: -7, -1: 1, -2: publica.x.to_bytes(32, "big"), -3: publica.y.to_bytes(32, "big")})
        cliente = json.dumps({"type": "webauthn.create", "challenge": opciones["challenge"], "origin": origen, "crossOrigin": False}).encode()
        objeto = cbor2.dumps({"fmt": "none", "attStmt": {}, "authData": self._datos(opciones["rp"]["id"], clave)})
        respuesta = {"clientDataJSON": b64(cliente), "attestationObject": b64(objeto)}
        return {"id": b64(self.id), "rawId": b64(self.id), "type": "public-key", "response": respuesta, "clientExtensionResults": {}}

    def firmar(self, opciones: dict, origen: str = ORIGEN) -> dict:
        """Lo que devuelve navigator.credentials.get."""
        self.contador += 1
        datos = self._datos(opciones["rpId"])
        cliente = json.dumps({"type": "webauthn.get", "challenge": opciones["challenge"], "origin": origen, "crossOrigin": False}).encode()
        firma = self.privada.sign(datos + hashlib.sha256(cliente).digest(), ec.ECDSA(hashes.SHA256()))
        respuesta = {"clientDataJSON": b64(cliente), "authenticatorData": b64(datos), "signature": b64(firma)}
        return {"id": b64(self.id), "rawId": b64(self.id), "type": "public-key", "response": respuesta, "clientExtensionResults": {}}


def parear_navegador(cliente, auth, nombre: str = "Taiel", perfil: str = "administrador") -> dict:
    """Un navegador pareado con el tablero web, que entra por el nombre del tablero."""
    codigo = cliente.post("/personas/codigo", json={"nombre": nombre, "perfil": perfil}, headers=auth).json()["codigo"]
    pareo = cliente.post("/pareo", json={"codigo": codigo, "nombreDispositivo": "Tablero web · Chrome en Windows", "tipo": "navegador"})
    assert pareo.status_code == 200, pareo.text
    return {**cabecera(pareo.json()["token"]), "Origin": ORIGEN}


def crear_llave(cliente, navegador, llave: "LlaveDePrueba"):
    opciones = cliente.post("/llaves/opciones", headers=navegador)
    assert opciones.status_code == 200, opciones.text
    return cliente.post("/llaves", json={"desafioId": opciones.json()["desafioId"], "credencial": llave.crear(opciones.json()["opciones"])}, headers=navegador)


def tablero_con_llave(cliente, auth, nombre: str = "Taiel", perfil: str = "administrador") -> tuple[dict, LlaveDePrueba]:
    navegador = parear_navegador(cliente, auth, nombre, perfil)
    llave = LlaveDePrueba()
    assert crear_llave(cliente, navegador, llave).status_code == 201
    return navegador, llave


def con_llave(cliente, navegador: dict, llave: LlaveDePrueba, accion: dict) -> dict:
    """Las cabeceras del navegador con [accion] confirmada por la llave, como hace el tablero antes de mandarla."""
    pedido = cliente.post("/llaves/desafio", json={"accion": accion}, headers=navegador)
    assert pedido.status_code == 200, pedido.text
    confirmacion = {"desafio": pedido.json()["desafioId"], "credencial": llave.firmar(pedido.json()["opciones"])}
    return {**navegador, "X-Llave": b64(json.dumps(confirmacion).encode())}


def de_bloquear(alerta: dict, duracion: int | None = 3600) -> dict:
    return {"tipo": "bloquear", "alertaId": alerta["id"], "duracion": duracion}


def test_el_tablero_crea_su_llave_al_parearse_y_una_sola_vez(cliente, auth):
    navegador = parear_navegador(cliente, auth)
    assert cliente.get("/llaves", headers=navegador).json() == {"tiene": False, "puedeCrear": True}

    assert crear_llave(cliente, navegador, LlaveDePrueba()).status_code == 201

    assert cliente.get("/llaves", headers=navegador).json() == {"tiene": True, "puedeCrear": False}
    assert cliente.post("/llaves/opciones", headers=navegador).status_code == 409
    # El teléfono confirma con su huella: la llave es del tablero.
    assert cliente.post("/llaves/opciones", headers={**auth, "Origin": ORIGEN}).status_code == 403


def test_pasados_los_diez_minutos_del_pareo_ya_no_se_crea(cliente, auth):
    navegador = parear_navegador(cliente, auth)
    with cliente.app.state.base.transaccion() as con:
        con.execute("UPDATE dispositivos SET creado_en = ? WHERE tipo = 'navegador'", (iso(ahora() - timedelta(minutes=11)),))

    assert cliente.get("/llaves", headers=navegador).json() == {"tiene": False, "puedeCrear": False}
    assert cliente.post("/llaves/opciones", headers=navegador).status_code == 403


def test_la_llave_se_ata_al_nombre_del_tablero(cliente, auth):
    navegador = parear_navegador(cliente, auth)
    sin_origen = {clave: valor for clave, valor in navegador.items() if clave != "Origin"}
    cliente.app.state.config.origenes_llave = ["http://192.168.0.10:8000", "http://localhost:8001"]

    assert cliente.post("/llaves/opciones", headers=sin_origen).status_code == 403
    assert cliente.post("/llaves/opciones", headers={**navegador, "Origin": "https://otro.sitio"}).status_code == 403
    # Por la IP no sirve, aunque esté en la configuración: una llave se ata a un nombre.
    assert cliente.post("/llaves/opciones", headers={**navegador, "Origin": "http://192.168.0.10:8000"}).status_code == 403
    local = cliente.post("/llaves/opciones", headers={**navegador, "Origin": "http://localhost:8001"})
    assert local.json()["opciones"]["rp"]["id"] == "localhost"
    # La firma que dice venir de otra dirección no crea la llave.
    otra = cliente.post("/llaves/opciones", headers=navegador).json()
    credencial = LlaveDePrueba().crear(otra["opciones"], origen="https://otro.sitio")
    assert cliente.post("/llaves", json={"desafioId": otra["desafioId"], "credencial": credencial}, headers=navegador).status_code == 403


def test_la_computadora_tiene_que_verificar_a_la_persona(cliente, auth):
    navegador = parear_navegador(cliente, auth)

    # Tocar sin la huella ni el PIN no alcanza.
    assert crear_llave(cliente, navegador, LlaveDePrueba(verifica=False)).status_code == 403
    assert cliente.get("/llaves", headers=navegador).json()["tiene"] is False


def test_con_la_llave_el_administrador_bloquea_desde_el_tablero(cliente, auth):
    navegador, llave = tablero_con_llave(cliente, auth)
    primera, segunda = de_origenes_distintos(cliente, auth, 2)

    hecho = bloquear(cliente, con_llave(cliente, navegador, llave, de_bloquear(primera)), primera["id"])

    assert hecho.status_code == 201, hecho.text
    assert (hecho.json()["estado"], hecho.json()["aprobadoPor"]) == ("aprobado", "Taiel")
    assert primera["origenIp"] in lo_que_bloquea_el_firewall(cliente)
    cambio = cliente.get("/registro", params={"bloqueos": "true"}, headers=auth).json()[0]
    assert (cambio["tipo"], cambio["autor"], cambio["motivo"]) == ("bloqueo", "Taiel", MOTIVO)
    # Sin la llave, el tablero lo pide.
    assert bloquear(cliente, navegador, segunda["id"], accion="2").json()["estado"] == "pedido"


def test_cada_confirmacion_sirve_para_una_sola_accion(cliente, auth):
    navegador, llave = tablero_con_llave(cliente, auth)
    primera, segunda = de_origenes_distintos(cliente, auth, 2)

    # La que confirmó la primera no bloquea la segunda, ni por otra duración.
    assert bloquear(cliente, con_llave(cliente, navegador, llave, de_bloquear(primera)), segunda["id"]).status_code == 403
    assert bloquear(cliente, con_llave(cliente, navegador, llave, de_bloquear(primera)), primera["id"], duracion=None).status_code == 403
    confirmada = con_llave(cliente, navegador, llave, de_bloquear(primera))
    assert bloquear(cliente, confirmada, primera["id"], accion="2").status_code == 201
    # Y no se usa dos veces.
    assert bloquear(cliente, confirmada, segunda["id"], accion="3").status_code == 403
    assert lo_que_bloquea_el_firewall(cliente).keys() == {primera["origenIp"]}


def test_un_desafio_vencido_o_de_otra_computadora_no_sirve(cliente, auth):
    navegador, llave = tablero_con_llave(cliente, auth)
    otro, otra_llave = tablero_con_llave(cliente, auth, nombre="Meli")
    primera = primera_alta(cliente, auth)
    vencida = con_llave(cliente, navegador, llave, de_bloquear(primera))
    with cliente.app.state.base.transaccion() as con:
        con.execute("UPDATE desafios_llave SET vence_en = ?", (iso(ahora() - timedelta(seconds=1)),))

    assert bloquear(cliente, vencida, primera["id"]).status_code == 403
    # La confirmación de una computadora no le sirve a otra, aunque las dos tengan llave.
    ajena = con_llave(cliente, navegador, llave, de_bloquear(primera))
    assert bloquear(cliente, {**otro, "X-Llave": ajena["X-Llave"]}, primera["id"], accion="2").status_code == 403
    assert bloquear(cliente, con_llave(cliente, otro, otra_llave, de_bloquear(primera)), primera["id"], accion="3").json()["aprobadoPor"] == "Meli"


def test_desde_el_tablero_aprobar_la_duracion_y_desbloquear_piden_la_llave(cliente, auth):
    navegador, llave = tablero_con_llave(cliente, auth)
    _, laura = sumar(cliente, auth)
    pedido = bloquear(cliente, laura, primera_alta(cliente, auth)["id"]).json()

    assert cliente.post(f"/bloqueos/{pedido['id']}/aprobacion", headers=navegador).status_code == 403
    aprobar = con_llave(cliente, navegador, llave, {"tipo": "aprobar", "bloqueoId": pedido["id"]})
    assert cliente.post(f"/bloqueos/{pedido['id']}/aprobacion", headers=aprobar).json()["aprobadoPor"] == "Taiel"
    hasta = {"duracion": None, "motivo": "Queda hasta formatearla."}
    assert cliente.put(f"/bloqueos/{pedido['id']}/duracion", json=hasta, headers=navegador).status_code == 403
    duracion = con_llave(cliente, navegador, llave, {"tipo": "duracion", "bloqueoId": pedido["id"], "duracion": None})
    assert cliente.put(f"/bloqueos/{pedido['id']}/duracion", json=hasta, headers=duracion).json()["duracion"] is None
    motivo = {"motivo": "Formateada."}
    assert cliente.post(f"/bloqueos/{pedido['id']}/desbloqueo", json=motivo, headers=navegador).status_code == 403
    soltar = con_llave(cliente, navegador, llave, {"tipo": "desbloquear", "bloqueoId": pedido["id"]})
    assert cliente.post(f"/bloqueos/{pedido['id']}/desbloqueo", json=motivo, headers=soltar).json()["estado"] == "desbloqueado"


def test_desde_el_tablero_la_gente_se_suma_y_se_cambia_con_la_llave(cliente, auth):
    navegador, llave = tablero_con_llave(cliente, auth)
    laura_id, _ = sumar(cliente, auth)
    meli = {"nombre": "Meli", "perfil": "administrador"}

    # Sin la llave, nada de lo que da poder: ni sumar a alguien, ni el perfil, ni el permiso de bloquear, ni quitar.
    assert cliente.post("/personas/codigo", json=meli, headers=navegador).status_code == 403
    assert cliente.put(f"/personas/{laura_id}/perfil", json={"perfil": "administrador"}, headers=navegador).status_code == 403
    assert con_bloquea(cliente, navegador, laura_id).status_code == 403
    assert cliente.delete(f"/personas/{laura_id}", headers=navegador).status_code == 403

    sumar_meli = con_llave(cliente, navegador, llave, {"tipo": "sumar", **meli})
    assert cliente.post("/personas/codigo", json=meli, headers=sumar_meli).status_code == 200
    permiso = con_llave(cliente, navegador, llave, {"tipo": "permiso_bloqueo", "personaId": laura_id, "puedeBloquear": True})
    assert con_bloquea(cliente, permiso, laura_id).json()["puedeBloquear"] is True
    perfil = con_llave(cliente, navegador, llave, {"tipo": "perfil", "personaId": laura_id, "perfil": "administrador"})
    assert cliente.put(f"/personas/{laura_id}/perfil", json={"perfil": "administrador"}, headers=perfil).json()["perfil"] == "administrador"
    quitar = con_llave(cliente, navegador, llave, {"tipo": "quitar", "personaId": laura_id})
    assert cliente.delete(f"/personas/{laura_id}", headers=quitar).status_code == 204


def test_el_analista_de_guardia_bloquea_desde_el_tablero_con_su_llave(cliente, auth):
    navegador, llave = tablero_con_llave(cliente, auth, nombre="Laura", perfil="analista")
    laura_id = next(p["id"] for p in cliente.get("/personas", headers=auth).json() if p["nombre"] == "Laura")
    primera, segunda = de_origenes_distintos(cliente, auth, 2)

    # Con «Pide aprobación», la llave no alcanza: lo pide.
    assert bloquear(cliente, con_llave(cliente, navegador, llave, de_bloquear(primera)), primera["id"]).json()["estado"] == "pedido"
    con_bloquea(cliente, auth, laura_id)

    hecho = bloquear(cliente, con_llave(cliente, navegador, llave, de_bloquear(segunda)), segunda["id"], accion="2").json()

    assert (hecho["estado"], hecho["aprobadoPor"]) == ("aprobado", "Laura")


def test_sin_la_libreria_el_tablero_hace_lo_de_antes(cliente, auth, monkeypatch):
    navegador = parear_navegador(cliente, auth)
    monkeypatch.setitem(sys.modules, "webauthn", None)

    assert cliente.post("/llaves/opciones", headers=navegador).status_code == 503
    # Sin llaves, el tablero mira y pide, como antes.
    assert bloquear(cliente, navegador, primera_alta(cliente, auth)["id"]).json()["estado"] == "pedido"
