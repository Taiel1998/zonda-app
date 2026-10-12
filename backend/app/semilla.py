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
from .modelos import CLAVE_SIN_TIPO, TIPOS_SEVERIDAD

SEVERIDAD_RECOMENDADA = {
    "fuerza_bruta": "alta",
    "sin_tipificar": "alta",
    "escaneo": "media",
    "ddos": "media",
    CLAVE_SIN_TIPO: "media",
    "probable_falsa_alarma": "baja",
}


def _fecha(texto: str) -> datetime:
    return datetime.fromisoformat(texto).astimezone(timezone.utc)


def cargar_si_vacia(con: sqlite3.Connection, ruta_semilla: Path, multiplicar: int = 1, ahora: datetime | None = None) -> int:
    """Si la tabla de alertas está vacía, carga la semilla. Devuelve cuántas alertas insertó."""
    if con.execute("SELECT COUNT(*) FROM alertas").fetchone()[0] > 0:
        return 0
    con.execute("INSERT OR IGNORE INTO meta (clave, valor) VALUES ('instancia', ?)", (uuid.uuid4().hex[:8],))
    for tipo in TIPOS_SEVERIDAD:
        con.execute("INSERT OR IGNORE INTO severidades (tipo_ataque, severidad) VALUES (?, ?)", (tipo, SEVERIDAD_RECOMENDADA[tipo]))
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
            con.execute("INSERT OR REPLACE INTO catalogo_equipos (ip, nombre) VALUES (?, ?)", (ip, nombre))
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
                id, creada_en, inicio, fin, cantidad_flujos, uid_referencia, origen_ip, destino_ip, servicio,
                puerto, puerto_referencia, puertos_distintos, tipo_ataque, confianza_tipo,
                veredicto_ia, error_reconstruccion, umbral_ia,
                veredicto_umbrales, contexto_60s, umbral_servicio, servicio_sin_linea_base,
                veredicto_reglas, par_destino, par_en_catalogo,
                estado_revision, version, actualizada_en, secuencia
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'sin_revisar', 1, ?, ?)
            """,
            (
                identificador, iso(creada_en), iso(inicio), iso(fin),
                int(alerta["cantidadFlujos"]), alerta["uidReferencia"], alerta["origenIp"], alerta["destinoIp"], alerta["servicio"],
                int(alerta["puerto"]), int(alerta.get("puertoReferencia", alerta["puerto"])), int(alerta.get("puertosDistintos", 1)),
                alerta.get("tipoAtaque"), alerta.get("confianzaTipo"),
                int(bool(alerta["veredictoIa"])), float(alerta["errorReconstruccion"]), float(alerta["umbralIa"]),
                int(bool(alerta["veredictoUmbrales"])), int(alerta["contexto60s"]), int(alerta["umbralServicio"]), int(alerta.get("umbralServicioLunes") is None),
                int(bool(alerta["veredictoReglas"])), alerta["parDestino"], int(bool(alerta["parEnCatalogo"])),
                iso(creada_en), secuencia,
            ),
        )
        insertadas += 1
    return insertadas


def _siguiente_secuencia(con: sqlite3.Connection) -> int:
    fila = con.execute("UPDATE meta SET valor = CAST(valor AS INTEGER) + 1 WHERE clave = 'secuencia' RETURNING valor").fetchone()
    return int(fila[0])
