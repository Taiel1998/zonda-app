"""Carga de las alertas de muestra (alertas_semilla.json) en la base, con las fechas corridas.

Las fechas del dataset son de julio de 2017. Al cargar, se desplazan todas por igual para que el
episodio más reciente quede una hora antes de ahora; así los períodos de 24 horas, 7 y 30 días de la
app tienen datos. El README lo declara (docs/decisiones.md, decisión 7). Los identificadores no
cambian: son los estables que calculó la exportación.
"""

import json
import random
import sqlite3
import uuid
from datetime import datetime, timedelta, timezone
from pathlib import Path

from .auth import iso
from .modelos import CLAVE_REGLAS, CLAVE_SIN_TIPO_ANTERIOR, CLAVE_UMBRALES, TIPOS_SEVERIDAD

SEVERIDAD_RECOMENDADA = {
    "fuerza_bruta": "alta",
    "sin_tipificar": "alta",
    "escaneo": "media",
    "ddos": "media",
    CLAVE_REGLAS: "media",
    CLAVE_UMBRALES: "media",
    "probable_falsa_alarma": "baja",
}

ORDEN_SEVERIDAD = {"baja": 0, "media": 1, "alta": 2}
NIVELES = ["baja", "media", "alta"]

# La severidad del tipo de cada alerta en SQL, igual que severidad_del_tipo. Las claves del tipo vacío
# salen de los veredictos; sin ninguno marcado van las dos, como en claves_de.
SQL_SEVERIDAD_DEL_TIPO = f"""(CASE WHEN alertas.tipo_ataque IS NOT NULL
    THEN (SELECT s.severidad FROM severidades s WHERE s.tipo_ataque = alertas.tipo_ataque)
    ELSE (SELECT s.severidad FROM severidades s
          WHERE s.tipo_ataque IN (
              CASE WHEN alertas.veredicto_reglas = 1 OR alertas.veredicto_umbrales = 0 THEN '{CLAVE_REGLAS}' END,
              CASE WHEN alertas.veredicto_umbrales = 1 OR alertas.veredicto_reglas = 0 THEN '{CLAVE_UMBRALES}' END)
          ORDER BY CASE s.severidad WHEN 'alta' THEN 2 WHEN 'media' THEN 1 ELSE 0 END DESC LIMIT 1)
END)"""

# La severidad final en SQL, igual que severidad_de: la del tipo, un nivel más o menos según la
# importancia del equipo destino (decisión 41). Así el filtro de /alertas no deja páginas con huecos.
SQL_SEVERIDAD = f"""(CASE (SELECT e.importancia FROM catalogo_equipos e WHERE e.ip = alertas.destino_ip)
    WHEN 'critico' THEN (CASE {SQL_SEVERIDAD_DEL_TIPO} WHEN 'baja' THEN 'media' ELSE 'alta' END)
    WHEN 'poco_importante' THEN (CASE {SQL_SEVERIDAD_DEL_TIPO} WHEN 'alta' THEN 'media' ELSE 'baja' END)
    ELSE {SQL_SEVERIDAD_DEL_TIPO}
END)"""


def ajustar(severidad: str, importancia: str | None) -> str:
    """La severidad final (decisión 41): la del tipo de ataque, un nivel más si el equipo destino es
    crítico y uno menos si es poco importante, con tope en alta y piso en baja. Pesan lo mismo: un solo
    escalón, así una probable falsa alarma a un servidor crítico no sale en rojo.

        tipo   crítico  normal  poco importante
        alta   alta     alta    media
        media  alta     media   baja
        baja   media    baja    baja
    """
    nivel = NIVELES.index(severidad)
    if importancia == "critico":
        nivel = min(nivel + 1, len(NIVELES) - 1)
    elif importancia == "poco_importante":
        nivel = max(nivel - 1, 0)
    return NIVELES[nivel]


def importancia_de(con: sqlite3.Connection, ip: str) -> str:
    """La importancia de un equipo; los que no están en el catálogo son normales."""
    fila = con.execute("SELECT importancia FROM catalogo_equipos WHERE ip = ?", (ip,)).fetchone()
    return fila["importancia"] if fila else "normal"


def claves_de(tipo_ataque: str | None, reglas: bool, umbrales: bool) -> list[str]:
    """Las filas de la tabla de severidades que le tocan a una alerta. Si la vio la IA, la de su tipo
    (decisión 4); si no, la de cada detector que la marcó (cambio 9). Una sin ningún veredicto no
    debería existir: si aparece, le tocan las dos."""
    if tipo_ataque:
        return [tipo_ataque]
    claves = ([CLAVE_REGLAS] if reglas else []) + ([CLAVE_UMBRALES] if umbrales else [])
    return claves or [CLAVE_REGLAS, CLAVE_UMBRALES]


def severidad_del_tipo(con: sqlite3.Connection, alerta: sqlite3.Row) -> str:
    """La severidad del tipo, según la tabla de la red (RF06). El tipo ordena, no filtra. Si la vieron
    las reglas y los umbrales sin la IA, le toca la más alta de las dos filas (cambio 9)."""
    severidades = []
    for clave in claves_de(alerta["tipo_ataque"], bool(alerta["veredicto_reglas"]), bool(alerta["veredicto_umbrales"])):
        fila = con.execute("SELECT severidad FROM severidades WHERE tipo_ataque = ?", (clave,)).fetchone()
        severidades.append(fila["severidad"] if fila else SEVERIDAD_RECOMENDADA[clave])
    return max(severidades, key=ORDEN_SEVERIDAD.__getitem__)


def severidad_de(con: sqlite3.Connection, alerta: sqlite3.Row) -> str:
    """La severidad final de una alerta: la de su tipo, ajustada por la importancia del equipo destino
    (decisión 41). La usan el detalle, la lista, Estado, los avisos de Telegram y zonda.cmd."""
    return ajustar(severidad_del_tipo(con, alerta), importancia_de(con, alerta["destino_ip"]))


def poner_al_dia_severidades(con: sqlite3.Connection) -> None:
    """La tabla de severidades con todas las claves de esta versión. En una base anterior a la 1.0.3,
    la severidad de `solo_reglas_o_umbrales` pasa a las dos claves nuevas (cambio 9); las claves que
    falten van con la recomendada."""
    anterior = con.execute("SELECT severidad FROM severidades WHERE tipo_ataque = ?", (CLAVE_SIN_TIPO_ANTERIOR,)).fetchone()
    if anterior is not None:
        for clave in (CLAVE_REGLAS, CLAVE_UMBRALES):
            con.execute("INSERT OR IGNORE INTO severidades (tipo_ataque, severidad) VALUES (?, ?)", (clave, anterior["severidad"]))
        con.execute("DELETE FROM severidades WHERE tipo_ataque = ?", (CLAVE_SIN_TIPO_ANTERIOR,))
    for tipo in TIPOS_SEVERIDAD:
        con.execute("INSERT OR IGNORE INTO severidades (tipo_ataque, severidad) VALUES (?, ?)", (tipo, SEVERIDAD_RECOMENDADA[tipo]))


def guardar_equipo(con: sqlite3.Connection, ip: str, nombre: str) -> None:
    """El nombre de un equipo del catálogo; si ya estaba, conserva la importancia que le dio un administrador."""
    con.execute(
        "INSERT INTO catalogo_equipos (ip, nombre) VALUES (?, ?) ON CONFLICT(ip) DO UPDATE SET nombre = excluded.nombre",
        (ip, nombre),
    )


def _fecha(texto: str) -> datetime:
    return datetime.fromisoformat(texto).astimezone(timezone.utc)


def cargar_si_vacia(
    con: sqlite3.Connection,
    ruta_semilla: Path,
    multiplicar: int = 1,
    ahora: datetime | None = None,
    revisar: bool = False,
) -> int:
    """Si la tabla de alertas está vacía, carga la semilla. Devuelve cuántas alertas insertó.

    Con [revisar], deja revisada una parte, como si el equipo ya hubiera trabajado con la app
    (ZONDA_REVISAR_SEMILLA, para la demo y las capturas del Resumen).
    """
    if con.execute("SELECT COUNT(*) FROM alertas").fetchone()[0] > 0:
        return 0
    con.execute("INSERT OR IGNORE INTO meta (clave, valor) VALUES ('instancia', ?)", (uuid.uuid4().hex[:8],))
    poner_al_dia_severidades(con)
    if not ruta_semilla.exists():
        return 0

    semilla = json.loads(ruta_semilla.read_text(encoding="utf-8"))
    alertas = semilla.get("alertas", [])
    if not alertas:
        return 0
    gap = int(semilla.get("parametros", {}).get("gapSegundos", 300))
    momento = ahora or datetime.now(timezone.utc)
    fin_mas_reciente = max(_fecha(a["fin"]) for a in alertas)
    corrimiento = (momento - timedelta(hours=1)) - fin_mas_reciente

    # Catálogo: nombres de equipos y servicios que la app muestra y usa en los filtros.
    for ip, nombre in semilla.get("catalogo", {}).get("equipos", {}).items():
        if nombre:
            guardar_equipo(con, ip, nombre)
    for servicio in semilla.get("catalogo", {}).get("servicios", []):
        con.execute("INSERT OR IGNORE INTO catalogo_servicios (servicio) VALUES (?)", (servicio,))

    filas = []
    aleatorio = random.Random(7)
    for copia in range(max(1, multiplicar)):
        for alerta in alertas:
            inicio = _fecha(alerta["inicio"]) + corrimiento
            fin = _fecha(alerta["fin"]) + corrimiento
            identificador = alerta["id"]
            if copia > 0:
                # Solo para la prueba de rendimiento (RF02, 1.000 alertas): copias con otro id y un poco corridas.
                identificador = f"{alerta['id']}-c{copia}"
                desplazamiento = timedelta(minutes=aleatorio.randint(1, 60 * 24 * 20))
                inicio -= desplazamiento
                fin -= desplazamiento
            filas.append((identificador, alerta, inicio, fin, fin + timedelta(seconds=gap)))

    # Orden cronológico de creación: así la primera sincronización las recibe en orden.
    filas.sort(key=lambda fila: fila[4])
    insertadas = 0
    for identificador, alerta, inicio, fin, creada_en in filas:
        secuencia = _siguiente_secuencia(con)
        ultimo = con.execute("SELECT valor FROM meta WHERE clave = 'ultimo_flujo'").fetchone()
        if ultimo is None or datetime.fromisoformat(ultimo["valor"]) < fin:
            con.execute("INSERT INTO meta (clave, valor) VALUES ('ultimo_flujo', ?) ON CONFLICT(clave) DO UPDATE SET valor = excluded.valor", (iso(fin),))
        con.execute(
            """
            INSERT INTO alertas (
                id, creada_en, inicio, fin, cantidad_flujos, uid_referencia, origen_ip, destino_ip, servicio, protocolo,
                puerto, puerto_referencia, puertos_distintos, tipo_ataque, confianza_tipo,
                veredicto_ia, error_reconstruccion, umbral_ia,
                veredicto_umbrales, contexto_60s, umbral_servicio, servicio_sin_linea_base,
                veredicto_reglas, par_destino, par_en_catalogo,
                estado_revision, version, actualizada_en, secuencia
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'sin_revisar', 1, ?, ?)
            """,
            (
                identificador, iso(creada_en), iso(inicio), iso(fin),
                int(alerta["cantidadFlujos"]), alerta["uidReferencia"], alerta["origenIp"], alerta["destinoIp"], alerta["servicio"],
                alerta.get("protocolo", "tcp"),
                int(alerta["puerto"]), int(alerta.get("puertoReferencia", alerta["puerto"])), int(alerta.get("puertosDistintos", 1)),
                alerta.get("tipoAtaque"), alerta.get("confianzaTipo"),
                int(bool(alerta["veredictoIa"])), float(alerta["errorReconstruccion"]), float(alerta["umbralIa"]),
                int(bool(alerta["veredictoUmbrales"])), int(alerta["contexto60s"]), int(alerta["umbralServicio"]), int(alerta.get("umbralServicioLunes") is None),
                int(bool(alerta["veredictoReglas"])), alerta["parDestino"], int(bool(alerta["parEnCatalogo"])),
                iso(creada_en), secuencia,
            ),
        )
        insertadas += 1
    if revisar:
        _revisar_parte(con, filas, momento)
    return insertadas


def _siguiente_secuencia(con: sqlite3.Connection) -> int:
    fila = con.execute("UPDATE meta SET valor = CAST(valor AS INTEGER) + 1 WHERE clave = 'secuencia' RETURNING valor").fetchone()
    return int(fila[0])


# Un comentario por motivo, como los que propone la app pero sin nombres de equipos.
COMENTARIO_POR_MOTIVO = {
    "escalado_al_proveedor": "Avisé al proveedor de seguridad; están revisando el servidor.",
    "bloqueado_manualmente": "Bloqueé el acceso desde ese equipo y avisé al proveedor.",
    "contrasena_cambiada": "Cambié la contraseña de la cuenta usada y revisé los accesos.",
    "investigando": "Estoy revisando los registros del servidor; sigo mañana.",
    "tarea_programada": "Es la copia nocturna que corre todos los domingos.",
    "backup": "Es el backup semanal entre esos dos equipos.",
    "equipo_nuevo": "Es un equipo nuevo que se configuró esta semana.",
    "usuario_autorizado": "Es un acceso autorizado del administrador.",
}
MOTIVOS_SEMILLA = {
    "ataque_real": ["bloqueado_manualmente", "contrasena_cambiada", "escalado_al_proveedor", "investigando"],
    "falso_positivo": ["tarea_programada", "backup", "equipo_nuevo", "usuario_autorizado"],
}
AUTOR_SEMILLA = "Teléfono de Martín"
PARTE_REVISADA = 0.75


def _revisar_parte(con: sqlite3.Connection, filas: list, momento: datetime) -> None:
    """Revisa tres de cada cuatro alertas de más de dos horas; las más nuevas quedan sin revisar.

    El resultado sale de la etiqueta del dataset (benigno es falso positivo) y la hora de revisión,
    de unos minutos a un día después de la alerta, la mayoría dentro de la primera hora. Todo con una
    semilla fija, así la demo da siempre las mismas cifras.
    """
    aleatorio = random.Random(11)
    for identificador, alerta, _inicio, _fin, creada_en in filas:
        if creada_en > momento - timedelta(hours=2) or aleatorio.random() >= PARTE_REVISADA:
            continue
        resultado = "falso_positivo" if alerta.get("etiquetaVerdad") == "benigno" else "ataque_real"
        motivo = aleatorio.choice(MOTIVOS_SEMILLA[resultado])
        sorteo = aleatorio.random()
        if sorteo < 0.6:
            demora = timedelta(minutes=aleatorio.randint(3, 55))
        elif sorteo < 0.9:
            demora = timedelta(minutes=aleatorio.randint(61, 6 * 60))
        else:
            demora = timedelta(minutes=aleatorio.randint(6 * 60, 30 * 60))
        revisada = min(creada_en + demora, momento - timedelta(minutes=5))
        con.execute(
            "INSERT INTO revisiones (alerta_id, accion_id, resultado, motivo, comentario, autor, fecha, revisada_en, dispositivo_id, version_resultante) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
            (identificador, f"semilla-{identificador}", resultado, motivo, COMENTARIO_POR_MOTIVO[motivo], AUTOR_SEMILLA, iso(revisada), iso(revisada), "semilla", 2),
        )
        con.execute(
            "UPDATE alertas SET estado_revision = ?, version = 2, actualizada_en = ? WHERE id = ?",
            (resultado, iso(revisada), identificador),
        )
