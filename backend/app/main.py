"""API de alertas de Zonda (FastAPI).

Hace de simulador de la plataforma de detección: sirve alertas de muestra construidas a partir
del dataset público CICIDS2017 (ver alertas_semilla.json) y recibe las revisiones de la app.
El contrato es el definitivo (informe, sección 13.3): después se conecta a la plataforma real
sin tocar la app.

Arranque: uvicorn app.main:app --host 127.0.0.1 --port 8000
"""

import asyncio
from contextlib import asynccontextmanager

from fastapi import FastAPI

from .config import Config
from .db import Base
from .emisor import emitir_periodicamente
from .rutas import VERSION, router
from .semilla import cargar_si_vacia


def crear_app(config: Config | None = None) -> FastAPI:
    cfg = config or Config.desde_entorno()

    @asynccontextmanager
    async def ciclo_de_vida(app: FastAPI):
        base = Base(cfg.db_path)
        base.iniciar()
        with base.transaccion() as con:
            insertadas = cargar_si_vacia(con, cfg.semilla, cfg.multiplicar)
        app.state.base = base
        app.state.config = cfg
        app.state.alertas_cargadas = insertadas
        tarea = asyncio.create_task(emitir_periodicamente(base, cfg.emitir_cada)) if cfg.emitir_cada > 0 else None
        yield
        if tarea is not None:
            tarea.cancel()

    app = FastAPI(
        title="API Zonda",
        version=VERSION,
        description="Alertas de anomalías de red para la app Zonda. Datos de muestra de CICIDS2017 con las fechas corridas al presente.",
        lifespan=ciclo_de_vida,
    )
    app.include_router(router)
    return app


app = crear_app()
