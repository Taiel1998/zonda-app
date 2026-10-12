"""Configuración de la API, leída del entorno y del archivo backend/.env (que no se commitea)."""

import os
from dataclasses import dataclass
from pathlib import Path

from dotenv import load_dotenv

RAIZ = Path(__file__).resolve().parent.parent


@dataclass
class Config:
    db_path: Path = RAIZ / "zonda.db"
    semilla: Path = RAIZ / "alertas_semilla.json"
    red_id: str = "red-oficina"
    red_nombre: str = "Oficina"
    url_publica: str = "http://127.0.0.1:8000"
    bot_usuario: str = "zonda_alertas_bot"
    emitir_cada: int = 0
    multiplicar: int = 1
    dias_token: int = 90
    minutos_codigo: int = 10
    tamanio_pagina: int = 200

    @classmethod
    def desde_entorno(cls) -> "Config":
        load_dotenv(RAIZ / ".env")
        leer = os.environ.get
        return cls(
            db_path=Path(leer("ZONDA_DB_PATH", str(RAIZ / "zonda.db"))),
            semilla=Path(leer("ZONDA_SEMILLA", str(RAIZ / "alertas_semilla.json"))),
            red_id=leer("ZONDA_RED_ID", "red-oficina"),
            red_nombre=leer("ZONDA_RED_NOMBRE", "Oficina"),
            url_publica=leer("ZONDA_URL_PUBLICA", "http://127.0.0.1:8000"),
            bot_usuario=leer("ZONDA_TELEGRAM_BOT", "zonda_alertas_bot"),
            emitir_cada=int(leer("ZONDA_EMITIR_CADA", "0")),
            multiplicar=int(leer("ZONDA_MULTIPLICAR", "1")),
        )
