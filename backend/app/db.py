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

-- Cada teléfono pareado es una persona del equipo, con su nombre y su perfil (decisión 39).
CREATE TABLE IF NOT EXISTS dispositivos (
    id TEXT PRIMARY KEY,
    -- El nombre del teléfono ("moto g(9) plus").
    nombre TEXT NOT NULL,
    token_hash TEXT NOT NULL UNIQUE,
    creado_en TEXT NOT NULL,
    vence_en TEXT NOT NULL,
    revocado_en TEXT,
    telegram_chat_id TEXT,
    -- Desde cuándo el chat recibe los avisos: las alertas anteriores al vínculo no se mandan.
    telegram_vinculado_en TEXT,
    -- Cómo avisa Telegram cada severidad en este teléfono: sonido, vibracion o sin_aviso (Figma 51-1688).
    aviso_alta TEXT NOT NULL DEFAULT 'sonido',
    aviso_media TEXT NOT NULL DEFAULT 'vibracion',
    aviso_baja TEXT NOT NULL DEFAULT 'sin_aviso',
    -- administrador o analista. Los pareados antes de la 1.0.5 quedan como administradores.
    perfil TEXT NOT NULL DEFAULT 'administrador',
    -- El nombre de la persona ("Laura"); null en los pareados antes de la 1.0.5.
    persona TEXT,
    -- Cuándo usó la app por última vez (Ajustes → Personas).
    ultimo_uso TEXT,
    -- telefono o navegador (el tablero web): un bloqueo solo lo hace efectivo un teléfono (decisión 51).
    tipo TEXT NOT NULL DEFAULT 'telefono',
    -- «Bloqueos» de un analista (decisión 51): 1 si bloquea en el acto, como un administrador (se lo da un
    -- administrador a quien hace guardia); 0 si pide aprobación. Vuelve a 0 cuando le cambian el perfil.
    puede_bloquear INTEGER NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS codigos_pareo (
    codigo TEXT PRIMARY KEY,
    creado_en TEXT NOT NULL,
    vence_en TEXT NOT NULL,
    usado_en TEXT,
    dispositivo_id TEXT,
    -- A quién y con qué perfil suma el código. Los de la terminal (zonda pair) son de administrador.
    persona TEXT,
    perfil TEXT NOT NULL DEFAULT 'administrador',
    -- El administrador que lo generó desde la app; null si salió de la terminal.
    creado_por TEXT
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
    protocolo TEXT NOT NULL DEFAULT 'tcp',
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
    secuencia INTEGER NOT NULL,
    -- Cuándo salió el primer aviso de Telegram de esta alerta (el historial del detalle lo muestra).
    aviso_telegram_en TEXT
);
CREATE INDEX IF NOT EXISTS alertas_secuencia ON alertas (secuencia);
CREATE INDEX IF NOT EXISTS alertas_estado ON alertas (estado_revision);

-- Un aviso por alerta y por chat: el bot nunca manda dos veces el mismo.
CREATE TABLE IF NOT EXISTS avisos_telegram (
    alerta_id TEXT NOT NULL REFERENCES alertas (id) ON DELETE CASCADE,
    chat_id TEXT NOT NULL,
    -- Cuándo se mandó; NULL si esa severidad está en "Sin aviso" y no se mandó nada.
    enviado_en TEXT,
    PRIMARY KEY (alerta_id, chat_id)
);

CREATE TABLE IF NOT EXISTS revisiones (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    alerta_id TEXT NOT NULL REFERENCES alertas (id) ON DELETE CASCADE,
    accion_id TEXT NOT NULL UNIQUE,
    resultado TEXT NOT NULL,
    motivo TEXT NOT NULL,
    comentario TEXT NOT NULL,
    autor TEXT NOT NULL,
    fecha TEXT NOT NULL,
    -- Cuándo se revisó en el teléfono; fecha es cuándo llegó al servidor (decisión 21).
    revisada_en TEXT,
    dispositivo_id TEXT NOT NULL,
    version_resultante INTEGER NOT NULL,
    -- El nombre de la persona que la revisó ("Laura"); autor es el de su teléfono.
    persona TEXT
);
CREATE INDEX IF NOT EXISTS revisiones_alerta ON revisiones (alerta_id);

CREATE TABLE IF NOT EXISTS severidades (
    tipo_ataque TEXT PRIMARY KEY,
    severidad TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS catalogo_equipos (
    ip TEXT PRIMARY KEY,
    nombre TEXT NOT NULL,
    -- critico, normal o poco_importante: sube o baja un nivel la severidad de sus alertas (decisión 41).
    importancia TEXT NOT NULL DEFAULT 'normal'
);

-- Quién cambió la configuración de la red, cuándo y por qué (decisión 40). Nadie lo edita ni lo borra
-- desde la app: solo se agregan filas.
CREATE TABLE IF NOT EXISTS registro_cambios (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    fecha TEXT NOT NULL,
    -- severidad, importancia, persona_nueva, perfil o persona_quitada.
    tipo TEXT NOT NULL,
    -- El teléfono que hizo el cambio; null si fue desde la terminal del servidor.
    autor_id TEXT,
    autor TEXT NOT NULL,
    -- Lo que cambió: el tipo de ataque, la IP del equipo o el teléfono de la persona.
    objeto TEXT NOT NULL,
    nombre TEXT,
    antes TEXT,
    despues TEXT,
    motivo TEXT
);

-- A quién está asignada cada alerta, como una línea de tiempo: la última fila manda (decisión 42).
CREATE TABLE IF NOT EXISTS asignaciones (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    alerta_id TEXT NOT NULL REFERENCES alertas (id) ON DELETE CASCADE,
    -- Como las revisiones: la misma acción reenviada desde la cola no se aplica dos veces.
    accion_id TEXT NOT NULL UNIQUE,
    -- null: quedó sin asignar.
    responsable_id TEXT,
    responsable TEXT,
    por_id TEXT NOT NULL,
    por TEXT NOT NULL,
    fecha TEXT NOT NULL,
    -- Cuándo salió el aviso de Telegram a quien se la asignaron.
    aviso_telegram_en TEXT
);
CREATE INDEX IF NOT EXISTS asignaciones_alerta ON asignaciones (alerta_id);

CREATE TABLE IF NOT EXISTS catalogo_servicios (
    servicio TEXT PRIMARY KEY
);

-- El tráfico que ve el sensor, para el tablero web (decisión 49): por tramo de 5 segundos y servicio.
-- t es el comienzo del tramo en segundos desde 1970 (UTC), con el reloj del sensor. Las filas se suman:
-- Zeek escribe una conexión cuando termina, y una larga completa tramos que ya habían llegado.
CREATE TABLE IF NOT EXISTS trafico (
    t INTEGER NOT NULL,
    servicio TEXT NOT NULL,
    conexiones INTEGER NOT NULL DEFAULT 0,
    -- Las que no llegaron a establecerse: sin respuesta, rechazadas o cortadas en el saludo.
    fallidas INTEGER NOT NULL DEFAULT 0,
    bytes INTEGER NOT NULL DEFAULT 0,
    paquetes INTEGER NOT NULL DEFAULT 0,
    PRIMARY KEY (t, servicio)
);

-- Quién habló con quién, por minuto y servicio (el mapa de la red del tablero).
CREATE TABLE IF NOT EXISTS trafico_pares (
    minuto INTEGER NOT NULL,
    origen TEXT NOT NULL,
    destino TEXT NOT NULL,
    servicio TEXT NOT NULL,
    conexiones INTEGER NOT NULL DEFAULT 0,
    fallidas INTEGER NOT NULL DEFAULT 0,
    bytes INTEGER NOT NULL DEFAULT 0,
    PRIMARY KEY (minuto, origen, destino, servicio)
);

-- El bloqueo del origen de una alerta en el firewall (decisión 51). Lo hace efectivo un administrador desde
-- su teléfono y lo aplica el agente del firewall cuando pregunta. Ninguna fila se borra.
CREATE TABLE IF NOT EXISTS bloqueos (
    id TEXT PRIMARY KEY,
    alerta_id TEXT NOT NULL REFERENCES alertas (id) ON DELETE CASCADE,
    ip TEXT NOT NULL,
    -- Como las revisiones: la misma acción reenviada desde la cola no se aplica dos veces.
    accion_id TEXT NOT NULL UNIQUE,
    -- pedido, aprobado, aplicado, rechazado, desbloqueado o vencido.
    estado TEXT NOT NULL,
    -- Segundos; null: hasta desbloquear. Manda vence_en.
    duracion INTEGER,
    motivo TEXT NOT NULL,
    -- 1 si lo pidió un analista o el tablero web y lo tuvo que aprobar un administrador.
    fue_pedido INTEGER NOT NULL DEFAULT 0,
    pedido_por_id TEXT NOT NULL,
    pedido_por TEXT NOT NULL,
    pedido_en TEXT NOT NULL,
    aprobado_por_id TEXT,
    aprobado_por TEXT,
    aprobado_en TEXT,
    vence_en TEXT,
    -- Cuándo dijo el firewall que lo aplicó.
    aplicado_en TEXT,
    -- Quién lo rechazó o lo desbloqueó ("Firewall" si lo rechazó el firewall), cuándo y por qué.
    terminado_por_id TEXT,
    terminado_por TEXT,
    terminado_en TEXT,
    detalle TEXT
);
CREATE INDEX IF NOT EXISTS bloqueos_estado ON bloqueos (estado);

-- Un aviso de Telegram por bloqueo, momento y chat: el bot nunca manda dos veces el mismo.
CREATE TABLE IF NOT EXISTS avisos_bloqueos (
    bloqueo_id TEXT NOT NULL REFERENCES bloqueos (id) ON DELETE CASCADE,
    -- pedido, aprobado, desbloqueado o rechazado (por el firewall).
    evento TEXT NOT NULL,
    chat_id TEXT NOT NULL,
    enviado_en TEXT NOT NULL,
    PRIMARY KEY (bloqueo_id, evento, chat_id)
);

-- Un latido del análisis en vivo por ciclo, para el gráfico del estado del sistema del tablero
-- (decisión 52). t es la hora del servidor en que llegó, en segundos desde 1970 (UTC); se guarda lo
-- mismo que el tráfico (dias_trafico). El último latido sigue también en meta, para GET /salud.
CREATE TABLE IF NOT EXISTS latidos (
    t INTEGER NOT NULL,
    flujos INTEGER NOT NULL DEFAULT 0,
    zeek_activo INTEGER NOT NULL DEFAULT 0,
    perdida REAL
);
CREATE INDEX IF NOT EXISTS latidos_t ON latidos (t);

-- La llave de acceso de cada computadora con el tablero web (decisión 51): solo la parte pública. La privada
-- vive en el chip de seguridad de la computadora (Windows Hello) y nunca sale de ahí.
CREATE TABLE IF NOT EXISTS llaves (
    -- El id de la credencial, en base64url.
    id TEXT PRIMARY KEY,
    -- El navegador que la creó al parearse.
    dispositivo_id TEXT NOT NULL REFERENCES dispositivos (id) ON DELETE CASCADE,
    -- La clave pública en COSE, con la que el servidor revisa cada firma.
    clave_publica BLOB NOT NULL,
    -- El contador de firmas de la computadora (Windows Hello suele dejarlo en 0).
    contador INTEGER NOT NULL DEFAULT 0,
    -- El nombre del tablero con el que se creó (zonda-equipo.duckdns.org): la llave solo vale ahí.
    rp_id TEXT NOT NULL,
    creada_en TEXT NOT NULL,
    usada_en TEXT
);

-- Cada desafío sirve para una sola cosa: crear la llave o confirmar una acción, la que se dijo al pedirlo.
-- Vence a los dos minutos.
CREATE TABLE IF NOT EXISTS desafios_llave (
    id TEXT PRIMARY KEY,
    dispositivo_id TEXT NOT NULL,
    desafio BLOB NOT NULL,
    -- registro o accion.
    proposito TEXT NOT NULL,
    -- La acción que confirma, en JSON con las claves ordenadas ({"alertaId": "a-1", "duracion": 3600, "tipo": "bloquear"}).
    accion TEXT,
    -- La dirección del tablero que lo pidió: la firma tiene que venir de la misma.
    origen TEXT NOT NULL,
    vence_en TEXT NOT NULL,
    usado_en TEXT
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
        self._actualizar(con)
        con.commit()

    @staticmethod
    def _actualizar(con: sqlite3.Connection) -> None:
        """Pone al día una base creada con una versión anterior: el protocolo, la hora de revisión en el
        teléfono, los motivos nuevos, lo que guarda el bot de Telegram, las severidades del tipo vacío
        (cambio 9) y, desde la 1.0.5, la persona y el perfil de cada teléfono y la importancia de cada
        equipo. Los teléfonos ya pareados quedan como administradores: hasta la 1.0.4 todos podían
        cambiar las severidades. Desde la API 0.9.0, si cada dispositivo es un teléfono o un navegador."""
        from .modelos import MOTIVOS_ANTERIORES
        from .semilla import poner_al_dia_severidades

        columnas = {fila[1] for fila in con.execute("PRAGMA table_info(alertas)")}
        if "protocolo" not in columnas:
            con.execute("ALTER TABLE alertas ADD COLUMN protocolo TEXT NOT NULL DEFAULT 'tcp'")
        if "aviso_telegram_en" not in columnas:
            con.execute("ALTER TABLE alertas ADD COLUMN aviso_telegram_en TEXT")
        columnas = {fila[1] for fila in con.execute("PRAGMA table_info(revisiones)")}
        if "revisada_en" not in columnas:
            con.execute("ALTER TABLE revisiones ADD COLUMN revisada_en TEXT")
        if "persona" not in columnas:
            con.execute("ALTER TABLE revisiones ADD COLUMN persona TEXT")
        columnas = {fila[1] for fila in con.execute("PRAGMA table_info(dispositivos)")}
        if "telegram_vinculado_en" not in columnas:
            con.execute("ALTER TABLE dispositivos ADD COLUMN telegram_vinculado_en TEXT")
            con.execute("ALTER TABLE dispositivos ADD COLUMN aviso_alta TEXT NOT NULL DEFAULT 'sonido'")
            con.execute("ALTER TABLE dispositivos ADD COLUMN aviso_media TEXT NOT NULL DEFAULT 'vibracion'")
            con.execute("ALTER TABLE dispositivos ADD COLUMN aviso_baja TEXT NOT NULL DEFAULT 'sin_aviso'")
        if "perfil" not in columnas:
            con.execute("ALTER TABLE dispositivos ADD COLUMN perfil TEXT NOT NULL DEFAULT 'administrador'")
            con.execute("ALTER TABLE dispositivos ADD COLUMN persona TEXT")
            con.execute("ALTER TABLE dispositivos ADD COLUMN ultimo_uso TEXT")
            con.execute("UPDATE dispositivos SET ultimo_uso = creado_en")
        if "tipo" not in columnas:
            con.execute("ALTER TABLE dispositivos ADD COLUMN tipo TEXT NOT NULL DEFAULT 'telefono'")
            # Los navegadores pareados antes de este campo se llaman «Tablero web · Chrome en Windows».
            con.execute("UPDATE dispositivos SET tipo = 'navegador' WHERE nombre LIKE 'Tablero web%'")
        if "puede_bloquear" not in columnas:
            con.execute("ALTER TABLE dispositivos ADD COLUMN puede_bloquear INTEGER NOT NULL DEFAULT 0")
        if "perfil" not in {fila[1] for fila in con.execute("PRAGMA table_info(codigos_pareo)")}:
            con.execute("ALTER TABLE codigos_pareo ADD COLUMN persona TEXT")
            con.execute("ALTER TABLE codigos_pareo ADD COLUMN perfil TEXT NOT NULL DEFAULT 'administrador'")
            con.execute("ALTER TABLE codigos_pareo ADD COLUMN creado_por TEXT")
        if "importancia" not in {fila[1] for fila in con.execute("PRAGMA table_info(catalogo_equipos)")}:
            con.execute("ALTER TABLE catalogo_equipos ADD COLUMN importancia TEXT NOT NULL DEFAULT 'normal'")
        for viejo, nuevo in MOTIVOS_ANTERIORES.items():
            con.execute("UPDATE revisiones SET motivo = ? WHERE motivo = ?", (nuevo, viejo))
        poner_al_dia_severidades(con)

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
