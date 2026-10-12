"""API de alertas de Zonda (FastAPI).

Versión mínima de la fase 0: solo expone GET /salud, que sirve para probar la publicación con HTTPS
(Caddy + DuckDNS + Let's Encrypt) desde un teléfono. El resto del contrato (estado, alertas, detalle,
revisión, pareo, severidades, Telegram, catálogo) se agrega en las fases 3 y 7.
"""

from datetime import datetime, timezone

from fastapi import FastAPI

VERSION = "0.1.0"

app = FastAPI(title="API Zonda", version=VERSION)


@app.get("/salud")
def salud() -> dict:
    """Devuelve la versión de la API, la hora del servidor, la cantidad de alertas y el vencimiento del token.

    Sin token devuelve igual la versión y la hora: la pantalla de Conexión la usa para verificar que el
    servidor responde antes de enviar el código de pareo.
    """
    return {
        "version": VERSION,
        "hora": datetime.now(timezone.utc).isoformat(timespec="seconds"),
        "alertas": 0,
        "tokenVence": None,
    }
