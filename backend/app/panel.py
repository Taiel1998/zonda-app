"""El panel de redes de una consultora (decisión 64): desde un mismo navegador, varias empresas, como la pantalla
Redes de la app.

Lo sirve `zonda panel` en la computadora de la consultora, en 127.0.0.1 (no se ve desde la red). No tiene base ni
datos: solo entrega las páginas. El navegador le pregunta a cada servidor de empresa directo, por su red o por su
VPN, con el token que le dio el pareo de esa red, y guarda los accesos en su almacenamiento local. Nada pasa por
un servidor central, y ningún servidor de empresa tiene el código que guarda los accesos a las otras.

Cada servidor tiene que dejar que el navegador lo lea desde esta dirección: ZONDA_ORIGENES_PANEL (CORS).
"""

from pathlib import Path

from fastapi import FastAPI, HTTPException
from fastapi.responses import FileResponse

from .config import RAIZ

WEB = RAIZ / "web"
PUERTO = 8090
# Solo lo que usa el panel. El tablero de cada red se abre en su propio servidor.
ARCHIVOS = {
    "redes.html",
    "estilos.css",
    "img/logo_zonda.png",
    "fuentes/inter-normal.ttf",
    "fuentes/inter-mediano.ttf",
    "fuentes/inter-seminegrita.ttf",
    "js/redes.js",
    "js/util.js",
}
# Como las del tablero (main.CABECERAS_TABLERO), salvo connect-src: el panel habla con los servidores de cada
# empresa, que pueden estar en cualquier dirección de su red o de su VPN.
CABECERAS_PANEL = {
    "Content-Security-Policy": (
        "default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data:; "
        "font-src 'self'; connect-src 'self' http: https:; object-src 'none'; base-uri 'none'; form-action 'none'; "
        "frame-ancestors 'none'"
    ),
    "X-Content-Type-Options": "nosniff",
    "X-Frame-Options": "DENY",
    "Referrer-Policy": "no-referrer",
    "Permissions-Policy": "camera=(), microphone=(), geolocation=()",
    "Cache-Control": "no-cache",
}


def crear_panel(web: Path = WEB) -> FastAPI:
    app = FastAPI(title="Panel de redes de Zonda", docs_url=None, redoc_url=None, openapi_url=None)

    @app.get("/")
    def inicio() -> FileResponse:
        return FileResponse(web / "redes.html", headers=CABECERAS_PANEL)

    @app.get("/{ruta:path}")
    def archivo(ruta: str) -> FileResponse:
        if ruta not in ARCHIVOS or not (web / ruta).is_file():
            raise HTTPException(status_code=404)
        return FileResponse(web / ruta, headers=CABECERAS_PANEL)

    return app


def servir(puerto: int = PUERTO) -> None:  # pragma: no cover - levanta el servidor de verdad
    import uvicorn

    print(f"Panel de redes: http://127.0.0.1:{puerto}/")
    print(f"Cada servidor de empresa necesita ZONDA_ORIGENES_PANEL=http://127.0.0.1:{puerto} en su .env.")
    uvicorn.run(crear_panel(), host="127.0.0.1", port=puerto, log_level="warning")
