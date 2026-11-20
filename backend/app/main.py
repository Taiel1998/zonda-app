"""API de alertas de Zonda (FastAPI).

Hace de simulador de la plataforma de detección: sirve alertas de muestra construidas a partir
del dataset público CICIDS2017 (ver alertas_semilla.json) y recibe las revisiones de la app.
El contrato es el definitivo (informe, sección 13.3): después se conecta a la plataforma real
sin tocar la app.

Si TELEGRAM_BOT_TOKEN está en .env, el bot de Telegram corre en el mismo proceso (app/telegram.py).
El vigía del latido del análisis en vivo (app/latido.py) corre siempre; sin bot no avisa. También corre
siempre la anotación de cada minuto en que el servidor anduvo (app/recibidos.py, decisión 60).

El tablero web (backend/web, decisión 49) se sirve en /panel desde el mismo proceso: misma dirección,
mismo HTTPS de Caddy y el mismo token por dispositivo que la app.

Los bloqueos del firewall (app/bloqueos.py, decisión 51) tienen su propio router: los pide la gente con el
token de su dispositivo y los lee el agente del firewall con el suyo.

El panel de redes de una consultora (app/panel.py, decisión 64) no lo sirve este servidor: corre en la
computadora de la consultora y le pregunta a cada servidor directo, con el token que le dio el pareo. Para que
el navegador la deje leer esta API, su dirección tiene que estar en ZONDA_ORIGENES_PANEL (CORS).

Arranque: uvicorn app.main:app --host 127.0.0.1 --port 8000
"""

import asyncio
from contextlib import asynccontextmanager

from fastapi import FastAPI, Request
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse
from fastapi.staticfiles import StaticFiles

from .bloqueos import router as router_bloqueos
from .config import RAIZ, Config
from .db import Base
from .emisor import emitir_periodicamente
from .latido import vigilar as vigilar_latido
from .llaves import router as router_llaves
from .recibidos import anotar_cada_minuto
from .rutas import VERSION, router
from .semilla import cargar_si_vacia
from .telegram import BotTelegram, ClienteTelegram

# Las cabeceras del tablero (decisión 49): solo carga lo que sirve este servidor, sin CDN ni nada de
# afuera; nadie lo puede meter en un marco de otra página; el navegador no adivina tipos de archivo y no
# guarda copias viejas. Los estilos en línea los ponen los globitos de ECharts.
CABECERAS_TABLERO = {
    "Content-Security-Policy": (
        "default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data: blob:; "
        "font-src 'self'; connect-src 'self'; object-src 'none'; base-uri 'none'; form-action 'none'; frame-ancestors 'none'"
    ),
    "X-Content-Type-Options": "nosniff",
    "X-Frame-Options": "DENY",
    "Referrer-Policy": "no-referrer",
    "Permissions-Policy": "camera=(), microphone=(), geolocation=()",
    "Cache-Control": "no-cache",
}


# Las páginas del panel de redes (decisión 64) están en backend/web porque usan los mismos estilos, pero las
# sirve solo `zonda panel`, en la computadora de la consultora. Un servidor de empresa no las sirve: así nunca
# tiene el código que guarda los accesos a las otras redes.
SOLO_DEL_PANEL_DE_REDES = {"/panel/redes.html", "/panel/js/redes.js"}
# Los métodos y las cabeceras que usa el panel de redes: lee el estado, parea y quita su propio acceso.
METODOS_DEL_PANEL = ["GET", "POST", "DELETE"]
CABECERAS_DEL_PANEL = ["Authorization", "Content-Type", "Accept"]


def es_del_tablero(ruta: str) -> bool:
    return ruta == "/panel" or ruta.startswith("/panel/")


def crear_app(config: Config | None = None) -> FastAPI:
    cfg = config or Config.desde_entorno()

    @asynccontextmanager
    async def ciclo_de_vida(app: FastAPI):
        base = Base(cfg.db_path)
        base.iniciar()
        with base.transaccion() as con:
            insertadas = cargar_si_vacia(con, cfg.semilla, cfg.multiplicar, revisar=cfg.revisar_semilla)
        app.state.base = base
        app.state.config = cfg
        app.state.alertas_cargadas = insertadas
        tareas: list[asyncio.Task] = []
        bot = None
        if cfg.telegram_token:
            bot = BotTelegram(base, cfg, ClienteTelegram(cfg.telegram_token, cfg.telegram_api))
            tareas += bot.arrancar()
        app.state.bot = bot
        tareas.append(asyncio.create_task(vigilar_latido(base, cfg, bot)))
        tareas.append(asyncio.create_task(anotar_cada_minuto(base, cfg)))
        if cfg.emitir_cada > 0:
            tareas.append(asyncio.create_task(emitir_periodicamente(base, cfg.emitir_cada)))
        yield
        for tarea in tareas:
            tarea.cancel()
        if bot is not None:
            await bot.cliente.cerrar()

    app = FastAPI(
        title="API Zonda",
        version=VERSION,
        description="Alertas de anomalías de red para la app Zonda. Datos de muestra de CICIDS2017 con las fechas corridas al presente.",
        lifespan=ciclo_de_vida,
    )
    app.include_router(router)
    app.include_router(router_bloqueos)
    app.include_router(router_llaves)
    app.mount("/panel", StaticFiles(directory=RAIZ / "web", html=True), name="panel")

    @app.middleware("http")
    async def cabeceras_del_tablero(request: Request, siguiente):
        if request.url.path in SOLO_DEL_PANEL_DE_REDES:
            return JSONResponse({"detail": "Not Found"}, status_code=404, headers=CABECERAS_TABLERO)
        respuesta = await siguiente(request)
        if es_del_tablero(request.url.path):
            respuesta.headers.update(CABECERAS_TABLERO)
        return respuesta

    # CORS solo para las direcciones del panel de redes que eligió la empresa (decisión 64). Sin cookies: el acceso
    # va en la cabecera Authorization, así que otra página no puede usar el de nadie. Sin la variable, como antes.
    if cfg.origenes_panel:
        app.add_middleware(
            CORSMiddleware,
            allow_origins=cfg.origenes_panel,
            allow_methods=METODOS_DEL_PANEL,
            allow_headers=CABECERAS_DEL_PANEL,
            allow_credentials=False,
            max_age=600,
        )

    return app


app = crear_app()
