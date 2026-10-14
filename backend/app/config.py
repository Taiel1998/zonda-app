"""Configuración de la API, leída del entorno y del archivo backend/.env (que no se commitea)."""

import os
from dataclasses import dataclass, field
from pathlib import Path

from dotenv import load_dotenv

RAIZ = Path(__file__).resolve().parent.parent


@dataclass
class Config:
    db_path: Path = RAIZ / "zonda.db"
    semilla: Path = RAIZ / "alertas_semilla.json"
    red_id: str = "red-oficina"
    red_nombre: str = "Oficina central"
    url_publica: str = "http://127.0.0.1:8000"
    bot_usuario: str = "zonda_alertas_bot"
    # El token del bot es una contraseña: repr=False para que no aparezca si se imprime la configuración.
    telegram_token: str = field(default="", repr=False)
    telegram_api: str = "https://api.telegram.org"
    enlace_aviso: str = "https://taiel1998.github.io/a/"
    emitir_cada: int = 0
    multiplicar: int = 1
    revisar_semilla: bool = False
    dias_token: int = 90
    minutos_codigo: int = 10
    dias_cache: int = 30
    tamanio_pagina: int = 200

    @classmethod
    def desde_entorno(cls) -> "Config":
        load_dotenv(RAIZ / ".env")
        leer = os.environ.get
        # Con el nombre de DuckDNS, el QR lleva https://<nombre>, que es lo que publica Caddy; así la
        # API y `zonda pair` no dependen de que alguien se acuerde de escribir la misma dirección dos veces.
        dominio = leer("ZONDA_DOMINIO", "").strip()
        return cls(
            db_path=Path(leer("ZONDA_DB_PATH", str(RAIZ / "zonda.db"))),
            semilla=Path(leer("ZONDA_SEMILLA", str(RAIZ / "alertas_semilla.json"))),
            red_id=leer("ZONDA_RED_ID", "red-oficina"),
            red_nombre=leer("ZONDA_RED_NOMBRE", "Oficina central"),
            url_publica=leer("ZONDA_URL_PUBLICA", "").strip() or (f"https://{dominio}" if dominio else "http://127.0.0.1:8000"),
            bot_usuario=leer("ZONDA_TELEGRAM_BOT", "zonda_alertas_bot"),
            telegram_token=leer("TELEGRAM_BOT_TOKEN", "").strip(),
            telegram_api=leer("ZONDA_TELEGRAM_API", "https://api.telegram.org").rstrip("/"),
            enlace_aviso=leer("ZONDA_ENLACE_AVISO", "https://taiel1998.github.io/a/"),
            emitir_cada=int(leer("ZONDA_EMITIR_CADA", "0")),
            multiplicar=int(leer("ZONDA_MULTIPLICAR", "1")),
            revisar_semilla=leer("ZONDA_REVISAR_SEMILLA", "0").strip().lower() in ("1", "true", "si", "sí"),
            tamanio_pagina=int(leer("ZONDA_TAMANIO_PAGINA", "200")),
        )
