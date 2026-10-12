"""Acceso a SQLite con la biblioteca estándar: una conexión por hilo y un candado para las escrituras.

Se eligió sqlite3 directo, sin ORM, porque el esquema es chico y así cada consulta se puede leer
tal cual en la defensa.
"""

import sqlite3
import threading
from contextlib import contextmanager
from pathlib import Path

ESQUEMA = """
CREATE TABLE IF NOT EXISTS meta (
    clave TEXT PRIMARY KEY,
    valor TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS dispositivos (
    id TEXT PRIMARY KEY,
    nombre TEXT NOT NULL,
    token_hash TEXT NOT NULL UNIQUE,
    creado_en TEXT NOT NULL,
    vence_en TEXT NOT NULL,
    revocado_en TEXT,
    telegram_chat_id TEXT
);

CREATE TABLE IF NOT EXISTS codigos_pareo (
    codigo TEXT PRIMARY KEY,
    creado_en TEXT NOT NULL,
    vence_en TEXT NOT NULL,
    usado_en TEXT,
    dispositivo_id TEXT
);

CREATE TABLE IF NOT EXISTS codigos_telegram (
    codigo TEXT PRIMARY KEY,
    dispositivo_id TEXT NOT NULL,
    creado_en TEXT NOT NULL,
    vence_en TEXT NOT NULL,
    usado_en TEXT
);

CREATE TABLE IF NOT EXISTS alertas (
    id TEXT PRIMARY KEY,
    creada_en TEXT NOT NULL,
    inicio TEXT NOT NULL,
    fin TEXT NOT NULL,
    cantidad_flujos INTEGER NOT NULL,
    uid_referencia TEXT NOT NULL,
    origen_ip TEXT NOT NULL,
    destino_ip TEXT NOT NULL,
    servicio TEXT NOT NULL,
    puerto INTEGER NOT NULL,
    puerto_referencia INTEGER NOT NULL,
    puertos_distintos INTEGER NOT NULL,
    tipo_ataque TEXT,
    confianza_tipo REAL,
    veredicto_ia INTEGER NOT NULL,
    error_reconstruccion REAL NOT NULL,
    umbral_ia REAL NOT NULL,
    veredicto_umbrales INTEGER NOT NULL,
    contexto_60s INTEGER NOT NULL,
    umbral_servicio INTEGER NOT NULL,
    servicio_sin_linea_base INTEGER NOT NULL,
    veredicto_reglas INTEGER NOT NULL,
    par_destino TEXT NOT NULL,
    par_en_catalogo INTEGER NOT NULL,
    estado_revision TEXT NOT NULL DEFAULT 'sin_revisar',
    version INTEGER NOT NULL DEFAULT 1,
    actualizada_en TEXT NOT NULL,
    secuencia INTEGER NOT NULL
);
CREATE INDEX IF NOT EXISTS alertas_secuencia ON alertas (secuencia);
CREATE INDEX IF NOT EXISTS alertas_estado ON alertas (estado_revision);

CREATE TABLE IF NOT EXISTS revisiones (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    alerta_id TEXT NOT NULL REFERENCES alertas (id) ON DELETE CASCADE,
    accion_id TEXT NOT NULL UNIQUE,
    resultado TEXT NOT NULL,
    motivo TEXT NOT NULL,
    comentario TEXT NOT NULL,
    autor TEXT NOT NULL,
    fecha TEXT NOT NULL,
    dispositivo_id TEXT NOT NULL,
    version_resultante INTEGER NOT NULL
);
CREATE INDEX IF NOT EXISTS revisiones_alerta ON revisiones (alerta_id);

CREATE TABLE IF NOT EXISTS severidades (
    tipo_ataque TEXT PRIMARY KEY,
    severidad TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS catalogo_equipos (
    ip TEXT PRIMARY KEY,
    nombre TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS catalogo_servicios (
    servicio TEXT PRIMARY KEY
);
"""


class Base:
    def __init__(self, ruta: Path) -> None:
        self.ruta = str(ruta)
        self._hilo = threading.local()
        self._candado = threading.Lock()

    def conexion(self) -> sqlite3.Connection:
        con = getattr(self._hilo, "con", None)
        if con is None:
            con = sqlite3.connect(self.ruta, check_same_thread=False)
            con.row_factory = sqlite3.Row
            con.execute("PRAGMA foreign_keys = ON")
            con.execute("PRAGMA journal_mode = WAL")
            self._hilo.con = con
        return con

    def iniciar(self) -> None:
        con = self.conexion()
        con.executescript(ESQUEMA)
        con.execute("INSERT OR IGNORE INTO meta (clave, valor) VALUES ('secuencia', '0')")
        con.commit()

    @contextmanager
    def transaccion(self):
        """Una escritura por vez: SQLite serializa igual, pero así la secuencia nunca se repite."""
        with self._candado:
            con = self.conexion()
            try:
                con.execute("BEGIN IMMEDIATE")
                yield con
                con.commit()
            except Exception:
                con.rollback()
                raise

    def leer_meta(self, clave: str, por_defecto: str | None = None) -> str | None:
        fila = self.conexion().execute("SELECT valor FROM meta WHERE clave = ?", (clave,)).fetchone()
        return fila["valor"] if fila else por_defecto

    @staticmethod
    def escribir_meta(con: sqlite3.Connection, clave: str, valor: str) -> None:
        con.execute("INSERT INTO meta (clave, valor) VALUES (?, ?) ON CONFLICT(clave) DO UPDATE SET valor = excluded.valor", (clave, valor))

    @staticmethod
    def siguiente_secuencia(con: sqlite3.Connection) -> int:
        """Número creciente que ordena los cambios; es lo que viaja en el cursor de sincronización."""
        fila = con.execute("UPDATE meta SET valor = CAST(valor AS INTEGER) + 1 WHERE clave = 'secuencia' RETURNING valor").fetchone()
        return int(fila["valor"])
