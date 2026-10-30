"""API de alertas de Zonda (FastAPI).

Hace de simulador de la plataforma de detección: sirve alertas de muestra construidas a partir
del dataset público CICIDS2017 (ver alertas_semilla.json) y recibe las revisiones de la app.
El contrato es el definitivo (informe, sección 13.3): después se conecta a la plataforma real
sin tocar la app.

Si TELEGRAM_BOT_TOKEN está en .env, el bot de Telegram corre en el mismo proceso (app/telegram.py).
El vigía del latido del análisis en vivo (app/latido.py) corre siempre; sin bot no avisa.

El tablero web (backend/web, decisión 49) se sirve en /panel desde el mismo proceso: misma dirección,
mismo HTTPS de Caddy y el mismo token por dispositivo que la app.

Arranque: uvicorn app.main:app --host 127.0.0.1 --port 8000
"""

import asyncio
from contextlib import asynccontextmanager

from fastapi import FastAPI, Request
from fastapi.staticfiles import StaticFiles

from .config import RAIZ, Config
from .db import Base
from .emisor import emitir_periodicamente
from .latido import vigilar as vigilar_latido
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
    app.mount("/panel", StaticFiles(directory=RAIZ / "web", html=True), name="panel")

    @app.middleware("http")
    async def cabeceras_del_tablero(request: Request, siguiente):
        respuesta = await siguiente(request)
        if es_del_tablero(request.url.path):
            respuesta.headers.update(CABECERAS_TABLERO)
        return respuesta

    return app


app = crear_app()
