"""El panel de redes de una consultora (decisión 64): lo sirve `zonda panel` en su computadora, sin base ni datos, y
le pregunta a cada servidor de empresa directo. Cada servidor lo deja leer solo si su dirección está en
ZONDA_ORIGENES_PANEL (CORS); el acceso es un token por red, del mismo pareo de un uso que la app y el tablero, y
la empresa se lo quita desde Administración → Navegadores como a cualquier navegador."""

import re

import pytest
from fastapi.testclient import TestClient

from app.auth import crear_codigo_pareo
from app.cli import main as cli
from app.config import RAIZ, Config
from app.main import crear_app
from app.panel import ARCHIVOS, CABECERAS_PANEL, crear_panel

from tests.conftest import cabecera, parear

WEB = RAIZ / "web"
PANEL = "http://127.0.0.1:8090"
OTRA = "https://pagina-cualquiera.example"
NOMBRE_PANEL = "Tablero web · Panel de redes · Chrome en Windows"


@pytest.fixture
def con_panel(config):
    """Un servidor de empresa que habilitó el panel de redes de su consultora."""
    config.origenes_panel = [PANEL]
    with TestClient(crear_app(config)) as cliente:
        yield cliente


def preflight(cliente: TestClient, ruta: str, origen: str, metodo: str = "GET"):
    return cliente.options(ruta, headers={
        "Origin": origen,
        "Access-Control-Request-Method": metodo,
        "Access-Control-Request-Headers": "authorization",
    })


def parear_panel(cliente: TestClient, persona: str = "Lucía", perfil: str = "analista") -> dict:
    """Lo que hace el panel al agregar una red: el código lo genera un administrador de la empresa."""
    with cliente.app.state.base.transaccion() as con:
        codigo, _ = crear_codigo_pareo(con, minutos=10, persona=persona, perfil=perfil)
    respuesta = cliente.post("/pareo", json={"codigo": codigo, "nombreDispositivo": NOMBRE_PANEL, "tipo": "navegador"}, headers={"Origin": PANEL})
    assert respuesta.status_code == 200, respuesta.text
    assert respuesta.headers["access-control-allow-origin"] == PANEL
    return respuesta.json()


# ---- CORS en el servidor de cada empresa ----


def test_sin_la_variable_el_servidor_no_deja_leerlo_desde_otra_direccion(cliente):
    respuesta = preflight(cliente, "/estado", PANEL)
    assert "access-control-allow-origin" not in respuesta.headers
    assert "access-control-allow-origin" not in cliente.get("/salud", headers={"Origin": PANEL}).headers


def test_con_la_variable_deja_leerlo_solo_desde_el_panel(con_panel):
    respuesta = preflight(con_panel, "/estado", PANEL)
    assert respuesta.status_code == 200
    assert respuesta.headers["access-control-allow-origin"] == PANEL
    assert "authorization" in respuesta.headers["access-control-allow-headers"].lower()
    assert "access-control-allow-credentials" not in respuesta.headers
    salud = con_panel.get("/salud", headers={"Origin": PANEL})
    assert salud.headers["access-control-allow-origin"] == PANEL
    # Otra página no: el navegador no le deja leer la respuesta.
    ajena = preflight(con_panel, "/estado", OTRA)
    assert ajena.status_code == 400
    assert "access-control-allow-origin" not in ajena.headers
    assert "access-control-allow-origin" not in con_panel.get("/salud", headers={"Origin": OTRA}).headers


def test_el_panel_no_puede_pedir_metodos_que_no_usa(con_panel):
    assert preflight(con_panel, "/severidades", PANEL, metodo="PUT").status_code == 400
    assert preflight(con_panel, "/dispositivos/actual", PANEL, metodo="DELETE").status_code == 200


def test_el_panel_se_parea_como_un_navegador_y_lee_el_estado(con_panel):
    salida = parear_panel(con_panel)
    assert salida["perfil"] == "analista" and salida["persona"] == "Lucía"
    token = salida["token"]
    salud = con_panel.get("/salud", headers={**cabecera(token), "Origin": PANEL})
    assert salud.status_code == 200
    datos = salud.json()
    assert datos["red"] == {"id": "red-prueba", "nombre": "Red de prueba"}
    assert datos["sistema"]["servidor"] == "funciona" and datos["tokenVence"]
    estado = con_panel.get("/estado", headers={**cabecera(token), "Origin": PANEL})
    assert estado.status_code == 200
    assert set(estado.json()["sinRevisar"]) == {"alta", "media", "baja"}
    # La empresa lo ve en Administración → Navegadores: un navegador, con el nombre que dice que es un panel.
    admin = parear(con_panel)
    gente = con_panel.get("/personas", headers=cabecera(admin)).json()
    panel = next(p for p in gente if p["id"] == salida["dispositivoId"])
    assert panel["tipo"] == "navegador" and panel["telefono"] == NOMBRE_PANEL and panel["nombre"] == "Lucía"


def test_la_empresa_le_quita_el_acceso_y_el_panel_lo_ve(con_panel):
    salida = parear_panel(con_panel)
    admin = parear(con_panel)
    assert con_panel.delete(f"/personas/{salida['dispositivoId']}", headers=cabecera(admin)).status_code == 204
    # El 401 también lleva el permiso de CORS: el panel lo lee y dice «Sin acceso», no «No responde».
    for ruta in ("/salud", "/estado"):
        respuesta = con_panel.get(ruta, headers={**cabecera(salida["token"]), "Origin": PANEL})
        assert respuesta.status_code == 401
        assert respuesta.headers["access-control-allow-origin"] == PANEL
    registro = con_panel.get("/registro", headers=cabecera(admin)).json()
    assert any(c["tipo"] == "persona_quitada" and c["objeto"] == salida["dispositivoId"] for c in registro)


def test_el_panel_quita_su_propio_acceso(con_panel):
    salida = parear_panel(con_panel)
    parear(con_panel)
    respuesta = con_panel.delete("/dispositivos/actual", headers={**cabecera(salida["token"]), "Origin": PANEL})
    assert respuesta.status_code == 204
    assert respuesta.headers["access-control-allow-origin"] == PANEL
    assert con_panel.get("/estado", headers=cabecera(salida["token"])).status_code == 401


def test_la_variable_admite_varias_direcciones(monkeypatch):
    monkeypatch.setenv("ZONDA_ORIGENES_PANEL", " http://127.0.0.1:8090/ ,http://localhost:8090,, ")
    assert Config.desde_entorno().origenes_panel == ["http://127.0.0.1:8090", "http://localhost:8090"]


# ---- Lo que sirve el servidor de cada empresa ----


def test_un_servidor_de_empresa_no_sirve_el_panel_de_redes(cliente):
    for ruta in ("/panel/redes.html", "/panel/js/redes.js"):
        respuesta = cliente.get(ruta)
        assert respuesta.status_code == 404, ruta
        assert respuesta.headers["x-frame-options"] == "DENY"
    # El tablero sigue igual.
    assert cliente.get("/panel/js/tablero.js").status_code == 200


# ---- Lo que sirve `zonda panel` ----


@pytest.fixture
def panel():
    with TestClient(crear_panel()) as cliente:
        yield cliente


def test_el_panel_se_sirve_con_sus_cabeceras(panel):
    respuesta = panel.get("/")
    assert respuesta.status_code == 200
    assert respuesta.headers["content-type"].startswith("text/html")
    assert 'src="js/redes.js"' in respuesta.text
    politica = respuesta.headers["content-security-policy"]
    # Habla con los servidores de cada empresa, pero solo corre sus propios scripts y nadie lo mete en otra página.
    assert "connect-src 'self' http: https:" in politica
    assert "script-src 'self'" in politica and "frame-ancestors 'none'" in politica
    assert respuesta.headers["x-frame-options"] == "DENY"
    assert respuesta.headers["referrer-policy"] == "no-referrer"
    assert respuesta.headers == {**respuesta.headers, **{k.lower(): v for k, v in CABECERAS_PANEL.items()}}


def test_el_panel_sirve_solo_lo_suyo(panel):
    html = (WEB / "redes.html").read_text(encoding="utf-8")
    rutas = set(re.findall(r"""(?:src|href)\s*=\s*["']([^"'#]+)["']""", html))
    assert {"estilos.css", "js/redes.js", "img/logo_zonda.png"} <= rutas
    for ruta in rutas | ARCHIVOS:
        assert (WEB / ruta).is_file(), ruta
        assert panel.get(f"/{ruta}").status_code == 200, ruta
    # Los módulos que importa el panel también los sirve: si no, la página no arranca.
    for modulo in ("js/redes.js", "js/util.js"):
        for importado in re.findall(r"""from\s+["']\./([^"']+)["']""", (WEB / modulo).read_text(encoding="utf-8")):
            assert f"js/{importado}" in ARCHIVOS, f"{modulo} importa {importado}, que el panel no sirve"
    # Ni el tablero, ni la API, ni nada fuera de backend/web.
    for ruta in ("/index.html", "/js/tablero.js", "/salud", "/alertas", "/../app/config.py", "/%2e%2e/app/config.py", "/zonda.db"):
        assert panel.get(ruta).status_code == 404, ruta


def test_la_pagina_del_panel_no_tiene_codigo_en_linea_ni_carga_nada_de_afuera():
    html = (WEB / "redes.html").read_text(encoding="utf-8")
    assert not re.search(r"<script(?![^>]*\bsrc=)[^>]*>", html)
    assert not re.search(r"\son[a-z]+\s*=", html, re.IGNORECASE)
    assert not re.search(r"""(?:src|href)\s*=\s*["']\s*(?:https?:)?//""", html, re.IGNORECASE)


def test_el_panel_le_pasa_el_acceso_solo_al_servidor_de_esa_red():
    """El token viaja por postMessage al origen exacto del servidor, nunca en la dirección."""
    redes = (WEB / "js" / "redes.js").read_text(encoding="utf-8")
    assert "ventana.postMessage({" in redes and "}, origen);" in redes
    assert "evento.origin !== origen" in redes and "evento.source !== ventana" in redes
    assert not re.search(r"panel/#[^'`]*token", redes)
    tablero = (WEB / "js" / "tablero.js").read_text(encoding="utf-8")
    assert "evento.source !== panel" in tablero and "window.opener = null" in tablero


def test_zonda_panel_levanta_el_panel_en_el_puerto_pedido(monkeypatch):
    pedidos = []
    monkeypatch.setattr("app.panel.servir", lambda puerto: pedidos.append(puerto))
    assert cli(["panel"]) == 0
    assert cli(["panel", "--puerto", "8099"]) == 0
    assert pedidos == [8090, 8099]
