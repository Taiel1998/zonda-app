import json
from datetime import timedelta, timezone

import httpx
import pytest
from fastapi.testclient import TestClient

from app.auth import crear_codigo_pareo
from app.cli import main as cli
from app.config import RAIZ, Config
from app.main import crear_app
from app.telegram import BotTelegram, ClienteTelegram

ARGENTINA = timezone(timedelta(hours=-3))


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


@pytest.fixture
def zonda(config, monkeypatch):
    """Los comandos de zonda.cmd como se corren en la notebook, pero sobre la base nueva de este test."""
    monkeypatch.setattr("app.cli.Config.desde_entorno", classmethod(lambda cls: config))
    return cli


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


class TelegramFalso:
    """Hace de api.telegram.org: guarda lo que manda el bot y le entrega los mensajes que se le cargan."""

    def __init__(self) -> None:
        self.enviados: list[dict] = []
        self.novedades: list[dict] = []
        # (código, descripción) para que sendMessage falle, como cuando la persona bloqueó el bot.
        self.error: tuple[int, str] | None = None
        self.urls: list[str] = []

    def mensaje(self, texto: str, chat_id: int = 555, tipo: str = "private") -> None:
        self.novedades.append(
            {"update_id": len(self.novedades) + 1, "message": {"message_id": 1, "chat": {"id": chat_id, "type": tipo}, "text": texto}}
        )

    def __call__(self, pedido: httpx.Request) -> httpx.Response:
        self.urls.append(str(pedido.url))
        metodo = pedido.url.path.rsplit("/", 1)[-1]
        datos = json.loads(pedido.content or b"{}")
        if metodo == "getMe":
            return httpx.Response(200, json={"ok": True, "result": {"id": 1, "is_bot": True, "username": "zonda_prueba_bot"}})
        if metodo == "getUpdates":
            nuevas = [n for n in self.novedades if n["update_id"] >= datos.get("offset", 0)]
            return httpx.Response(200, json={"ok": True, "result": nuevas})
        if metodo == "sendMessage":
            if self.error is not None:
                codigo, descripcion = self.error
                return httpx.Response(codigo, json={"ok": False, "error_code": codigo, "description": descripcion})
            self.enviados.append(datos)
            return httpx.Response(200, json={"ok": True, "result": {"message_id": len(self.enviados)}})
        return httpx.Response(404, json={"ok": False, "error_code": 404, "description": "Not Found"})


@pytest.fixture
def telegram():
    return TelegramFalso()


@pytest.fixture
def bot(cliente, config, telegram):
    """El bot con el Telegram falso, puesto en la app como si el servidor tuviera el token."""
    cliente_telegram = ClienteTelegram("123:token-de-prueba", "https://telegram.prueba", transporte=httpx.MockTransport(telegram))
    bot = BotTelegram(cliente.app.state.base, config, cliente_telegram, zona=ARGENTINA, pausa=0)
    cliente.app.state.bot = bot
    return bot


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
