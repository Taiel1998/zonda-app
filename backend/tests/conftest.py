import pytest
from fastapi.testclient import TestClient

from app.auth import crear_codigo_pareo
from app.config import RAIZ, Config
from app.main import crear_app


@pytest.fixture
def config(tmp_path):
    """Base nueva por test, con la semilla de ejemplo y páginas chicas para probar el cursor."""
    return Config(
        db_path=tmp_path / "zonda.db",
        semilla=RAIZ / "alertas_semilla.json",
        red_id="red-prueba",
        red_nombre="Red de prueba",
        url_publica="https://prueba.duckdns.org",
        tamanio_pagina=10,
    )


@pytest.fixture
def cliente(config):
    app = crear_app(config)
    with TestClient(app) as cliente:
        yield cliente


def parear(cliente: TestClient, nombre: str = "Teléfono de prueba") -> str:
    base = cliente.app.state.base
    with base.transaccion() as con:
        codigo, _ = crear_codigo_pareo(con, minutos=10)
    respuesta = cliente.post("/pareo", json={"codigo": codigo, "nombreDispositivo": nombre})
    assert respuesta.status_code == 200, respuesta.text
    return respuesta.json()["token"]


def cabecera(token: str) -> dict[str, str]:
    return {"Authorization": f"Bearer {token}"}


@pytest.fixture
def token(cliente):
    return parear(cliente)


@pytest.fixture
def auth(token):
    return cabecera(token)


def todas_las_alertas(cliente: TestClient, auth: dict[str, str]) -> list[dict]:
    """Recorre todas las páginas del cursor, como hace la app en la primera sincronización."""
    alertas: list[dict] = []
    cursor = None
    while True:
        respuesta = cliente.get("/alertas", params={"desde": cursor} if cursor else None, headers=auth)
        assert respuesta.status_code == 200, respuesta.text
        pagina = respuesta.json()
        alertas.extend(pagina["alertas"])
        cursor = pagina["cursorSiguiente"]
        if not pagina["hayMas"]:
            return alertas
