from datetime import datetime, timedelta, timezone

from app.auth import crear_codigo_pareo
from app.rutas import VERSION
from tests.conftest import cabecera, parear


def test_salud_responde_sin_token(cliente):
    respuesta = cliente.get("/salud")

    assert respuesta.status_code == 200
    cuerpo = respuesta.json()
    assert cuerpo["version"] == VERSION
    assert cuerpo["alertas"] > 0
    assert cuerpo["red"] == {"id": "red-prueba", "nombre": "Red de prueba"}
    assert cuerpo["tokenVence"] is None


def test_salud_con_token_informa_el_vencimiento_y_rechaza_uno_invalido(cliente, token):
    con_token = cliente.get("/salud", headers=cabecera(token)).json()
    assert con_token["tokenVence"] is not None

    assert cliente.get("/salud", headers=cabecera("token-falso")).status_code == 401


def test_pareo_entrega_token_y_el_codigo_se_consume(cliente):
    base = cliente.app.state.base
    with base.transaccion() as con:
        codigo, _ = crear_codigo_pareo(con, minutos=10)

    primera = cliente.post("/pareo", json={"codigo": codigo.lower(), "nombreDispositivo": "Pixel de Martín"})
    segunda = cliente.post("/pareo", json={"codigo": codigo, "nombreDispositivo": "Otro teléfono"})

    assert primera.status_code == 200
    cuerpo = primera.json()
    assert cuerpo["red"]["id"] == "red-prueba"
    assert len(cuerpo["token"]) > 30
    assert cuerpo["dispositivoId"].startswith("d-")
    assert cuerpo["emitido"] < cuerpo["vence"]
    assert segunda.status_code == 410


def test_pareo_rechaza_codigos_invalidos_o_vencidos(cliente):
    assert cliente.post("/pareo", json={"codigo": "ZZZZ-ZZZZ", "nombreDispositivo": "x"}).status_code == 400

    base = cliente.app.state.base
    with base.transaccion() as con:
        codigo, _ = crear_codigo_pareo(con, minutos=10)
        vencido = (datetime.now(timezone.utc) - timedelta(minutes=1)).isoformat(timespec="seconds")
        con.execute("UPDATE codigos_pareo SET vence_en = ? WHERE codigo = ?", (vencido, codigo))

    assert cliente.post("/pareo", json={"codigo": codigo, "nombreDispositivo": "x"}).status_code == 410


def test_el_token_se_guarda_como_hash_y_vence_a_los_90_dias(cliente):
    token = parear(cliente)
    fila = cliente.app.state.base.conexion().execute("SELECT * FROM dispositivos").fetchone()

    assert token not in fila["token_hash"]
    vence = datetime.fromisoformat(fila["vence_en"])
    assert timedelta(days=89) < vence - datetime.now(timezone.utc) <= timedelta(days=90)
