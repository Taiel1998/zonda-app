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
    # Token con el que la plataforma de detección manda alertas a POST /ingesta/alertas; también es secreto.
    ingesta_token: str = field(default="", repr=False)
    # Token con el que el agente del firewall pide los bloqueos aprobados (decisión 51); también es secreto.
    # Sin él, los bloqueos están apagados y la red queda en modo de observación.
    firewall_token: str = field(default="", repr=False)
    telegram_api: str = "https://api.telegram.org"
    enlace_aviso: str = "https://taiel1998.github.io/a/"
    emitir_cada: int = 0
    multiplicar: int = 1
    revisar_semilla: bool = False
    dias_token: int = 90
    minutos_codigo: int = 10
    dias_cache: int = 30
    tamanio_pagina: int = 200
    # Segundos sin latido del análisis en vivo para darlo por detenido, y cada cuántos los revisa el vigía.
    latido_limite: int = 120
    latido_revisar_cada: int = 15
    # Porcentaje de pérdida de captura de Zeek desde el que el sensor ve parcialmente (y se avisa).
    perdida_limite: float = 1.0
    # Días de tráfico que guarda el servidor para el tablero web (decisión 49); lo anterior se borra.
    dias_trafico: int = 7
    # Segundos sin que el firewall pregunte para darlo por desconectado, y los topes de los bloqueos: activos
    # a la vez en la red y pedidos por dispositivo en una hora (decisión 51).
    firewall_limite: int = 60
    max_bloqueos: int = 20
    bloqueos_por_hora: int = 10
    # Direcciones del tablero, además de url_publica, desde las que vale una llave de acceso (decisión 51); por
    # ejemplo http://localhost:8001 para probar. Una llave se ata al nombre: con una IP no funciona.
    origenes_llave: list[str] = field(default_factory=list)

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
            ingesta_token=leer("ZONDA_INGESTA_TOKEN", "").strip(),
            firewall_token=leer("ZONDA_FIREWALL_TOKEN", "").strip(),
            telegram_api=leer("ZONDA_TELEGRAM_API", "https://api.telegram.org").rstrip("/"),
            enlace_aviso=leer("ZONDA_ENLACE_AVISO", "https://taiel1998.github.io/a/"),
            emitir_cada=int(leer("ZONDA_EMITIR_CADA", "0")),
            multiplicar=int(leer("ZONDA_MULTIPLICAR", "1")),
            revisar_semilla=leer("ZONDA_REVISAR_SEMILLA", "0").strip().lower() in ("1", "true", "si", "sí"),
            tamanio_pagina=int(leer("ZONDA_TAMANIO_PAGINA", "200")),
            latido_limite=int(leer("ZONDA_LATIDO_LIMITE", "120")),
            latido_revisar_cada=int(leer("ZONDA_LATIDO_REVISAR_CADA", "15")),
            perdida_limite=float(leer("ZONDA_PERDIDA_LIMITE", "1.0").replace(",", ".")),
            dias_trafico=int(leer("ZONDA_DIAS_TRAFICO", "7")),
            firewall_limite=int(leer("ZONDA_FIREWALL_LIMITE", "60")),
            max_bloqueos=int(leer("ZONDA_MAX_BLOQUEOS", "20")),
            bloqueos_por_hora=int(leer("ZONDA_BLOQUEOS_POR_HORA", "10")),
            origenes_llave=[origen.strip() for origen in leer("ZONDA_ORIGENES_LLAVE", "").split(",") if origen.strip()],
        )
