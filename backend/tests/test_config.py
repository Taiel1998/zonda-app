import pytest

from app.config import Config


@pytest.fixture
def entorno(monkeypatch):
    """Sin el .env de la máquina y sin las variables de la dirección: cada prueba pone las suyas."""
    monkeypatch.setattr("app.config.load_dotenv", lambda *args, **kwargs: None)
    for variable in ("ZONDA_URL_PUBLICA", "ZONDA_DOMINIO"):
        monkeypatch.delenv(variable, raising=False)
    return monkeypatch


def test_con_el_nombre_de_duckdns_el_qr_lleva_https_con_ese_nombre(entorno):
    entorno.setenv("ZONDA_DOMINIO", "zonda-equipo.duckdns.org")

    assert Config.desde_entorno().url_publica == "https://zonda-equipo.duckdns.org"


def test_una_direccion_escrita_a_mano_gana_sobre_el_nombre(entorno):
    entorno.setenv("ZONDA_DOMINIO", "zonda-equipo.duckdns.org")
    entorno.setenv("ZONDA_URL_PUBLICA", "https://zonda.empresa.com.ar")

    assert Config.desde_entorno().url_publica == "https://zonda.empresa.com.ar"


def test_sin_nombre_ni_direccion_el_qr_lleva_la_api_local(entorno):
    assert Config.desde_entorno().url_publica == "http://127.0.0.1:8000"

    entorno.setenv("ZONDA_URL_PUBLICA", "")
    assert Config.desde_entorno().url_publica == "http://127.0.0.1:8000"
