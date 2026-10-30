"""El tablero web en /panel (decisión 49): lo sirve la misma API, con cabeceras que no dejan cargar nada
de afuera ni meterlo en otra página, y sin dependencias de internet."""

import re

from app.config import RAIZ

WEB = RAIZ / "web"


def test_el_tablero_se_sirve_con_sus_cabeceras(cliente):
    respuesta = cliente.get("/panel/")
    assert respuesta.status_code == 200
    assert respuesta.headers["content-type"].startswith("text/html")
    politica = respuesta.headers["content-security-policy"]
    assert "default-src 'self'" in politica and "script-src 'self'" in politica and "frame-ancestors 'none'" in politica
    assert respuesta.headers["x-content-type-options"] == "nosniff"
    assert respuesta.headers["x-frame-options"] == "DENY"
    assert respuesta.headers["referrer-policy"] == "no-referrer"


def test_sin_la_barra_final_lleva_al_tablero(cliente):
    respuesta = cliente.get("/panel", follow_redirects=False)
    assert respuesta.status_code in (301, 302, 307, 308)
    assert respuesta.headers["location"].endswith("/panel/")


def test_las_rutas_de_la_api_no_llevan_las_cabeceras_del_tablero(cliente):
    respuesta = cliente.get("/salud")
    assert respuesta.status_code == 200
    assert "content-security-policy" not in respuesta.headers


def test_lo_que_no_existe_da_404(cliente):
    assert cliente.get("/panel/no-existe.js").status_code == 404


def test_lo_que_pide_la_pagina_existe_y_se_sirve(cliente):
    html = (WEB / "index.html").read_text(encoding="utf-8")
    rutas = re.findall(r"""(?:src|href)\s*=\s*["']([^"'#]+)["']""", html)
    assert {"estilos.css", "vendor/echarts.min.js", "js/tablero.js"} <= set(rutas)
    for ruta in rutas:
        assert (WEB / ruta).is_file(), ruta
        assert cliente.get(f"/panel/{ruta}").status_code == 200, ruta
    for modulo in (WEB / "js").glob("*.js"):
        for importado in re.findall(r"""from\s+["'](\./[^"']+)["']""", modulo.read_text(encoding="utf-8")):
            assert (modulo.parent / importado).is_file(), f"{modulo.name} importa {importado}, que no existe"


def test_la_pagina_no_tiene_codigo_en_linea():
    """La política de contenido solo deja correr los scripts que sirve el servidor: un <script> sin src o un
    onclick en el HTML no correrían, y además serían una puerta para inyectar código."""
    html = (WEB / "index.html").read_text(encoding="utf-8")
    assert not re.search(r"<script(?![^>]*\bsrc=)[^>]*>", html)
    assert not re.search(r"\son[a-z]+\s*=", html, re.IGNORECASE)


def test_el_tablero_no_carga_nada_de_internet():
    """Todo lo que usa el tablero (scripts, estilos, letras, imágenes) está en backend/web: anda sin
    internet y la política de contenido no se puede aflojar sin que falle este test."""
    externos = re.compile(r"""(?:src|href)\s*=\s*["']\s*(?:https?:)?//""", re.IGNORECASE)
    importados = re.compile(r"""@import\s+url\(\s*["']?(?:https?:)?//|url\(\s*["']?(?:https?:)?//""", re.IGNORECASE)
    for archivo in list(WEB.rglob("*.html")) + list(WEB.rglob("*.css")):
        texto = archivo.read_text(encoding="utf-8")
        assert not externos.search(texto), f"{archivo.name} carga algo de afuera"
        assert not importados.search(texto), f"{archivo.name} importa algo de afuera"
