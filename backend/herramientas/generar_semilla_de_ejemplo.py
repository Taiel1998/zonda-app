"""Genera la semilla DE EJEMPLO (backend/tests/semilla_de_prueba.json), con la misma forma que produce
la exportación de la plataforma, para poder desarrollar y probar la API y la app.

Los episodios están inventados a partir de lo que se sabe del banco de pruebas de CICIDS2017
(un atacante externo contra un servidor web, falsas alarmas hacia el controlador de dominio).
Desde el 16/10, la exportación real (516 episodios) vive en backend/alertas_semilla.json; este ejemplo
quedó como fixture de los tests, que dependen de su contenido, y por eso se escribe en tests/.

Uso (desde backend/): .venv\\Scripts\\python herramientas\\generar_semilla_de_ejemplo.py
"""

import hashlib
import json
import random
from datetime import datetime, timedelta, timezone
from pathlib import Path

TAU = 0.00306383
HALIFAX = timezone(timedelta(hours=-3))  # zona del dataset en julio
MARTES = datetime(2017, 7, 4, tzinfo=HALIFAX)
VIERNES = datetime(2017, 7, 7, tzinfo=HALIFAX)

# Los nombres de los equipos de la demo son los del Figma; las IP son las reales del dataset.
EQUIPOS = {
    "192.168.10.3": "srv-facturacion",
    "192.168.10.50": "srv-sueldos",
    "192.168.10.51": "srv-archivos",
    "192.168.10.5": "pc-05",
    "192.168.10.8": "pc-08",
    "192.168.10.9": "pc-09",
    "192.168.10.12": "pc-contabilidad",
    "192.168.10.14": "pc-14",
    "192.168.10.15": "pc-15",
    "192.168.10.16": "impresora-2",
    "192.168.10.17": "pc-17",
    "192.168.10.19": "pc-19",
    "192.168.10.25": "mac-25",
    "172.16.0.1": "pc-rrhh",
}
PARES_AUTORIZADOS = {
    "192.168.10.3:53", "192.168.10.3:88", "192.168.10.3:135", "192.168.10.3:389", "192.168.10.3:445", "192.168.10.3:3268",
    "192.168.10.50:21", "192.168.10.50:22", "192.168.10.50:137", "192.168.10.51:445", "192.168.10.51:139",
    "192.168.10.12:137", "192.168.10.14:445", "192.168.10.16:22", "192.168.10.17:445", "192.168.10.19:22",
}
UMBRALES_LUNES = {21: 1, 22: 2, 53: 14, 88: 6, 135: 3, 137: 9, 139: 4, 389: 8, 445: 11, 3268: 5}

azar = random.Random(7)
alertas: list[dict] = []


def episodio(dia: str, base: datetime, hora: str, minutos: int, origen: str, destino: str, puerto: int, servicio: str, flujos: int, *,
             tipo: str | None, confianza: float | None, ia: bool, umbrales: bool, reglas: bool,
             puertos_distintos: int = 1, error: float | None = None, contexto: int | None = None, verdad: str = "benigno",
             protocolo: str = "tcp", estado: str = "SF", history: str = "ShADadFf") -> None:
    hh, mm = (int(x) for x in hora.split(":"))
    inicio = base.replace(hour=hh, minute=mm)
    fin = inicio + timedelta(minutes=minutos)
    uid = "C" + hashlib.sha1(f"{dia}{origen}{destino}{hora}".encode()).hexdigest()[:17]
    ident = "a-" + hashlib.sha1(f"{dia}|{origen}|{destino}|{uid}".encode()).hexdigest()[:12]
    umbral_lunes = UMBRALES_LUNES.get(puerto)
    alertas.append({
        "id": ident, "dia": dia,
        "inicio": inicio.astimezone(timezone.utc).isoformat(), "fin": fin.astimezone(timezone.utc).isoformat(),
        "cantidadFlujos": flujos, "uidReferencia": uid, "origenIp": origen, "destinoIp": destino,
        "puertoReferencia": puerto, "puerto": puerto, "puertosDistintos": puertos_distintos,
        "servicio": servicio, "protocolo": protocolo, "estadoConexion": estado, "history": history,
        "veredictoIa": ia, "flujosIa": flujos if ia else 0,
        "errorReconstruccion": round(error if error is not None else (azar.uniform(0.004, 0.08) if ia else azar.uniform(0.0004, 0.0029)), 8),
        "umbralIa": TAU,
        "veredictoUmbrales": umbrales, "flujosUmbrales": flujos if umbrales else 0,
        "contexto60s": contexto if contexto is not None else (azar.randint(20, 90) if umbrales else azar.randint(1, max(1, umbral_lunes or 1))),
        "umbralServicio": max(umbral_lunes or 1, 1), "umbralServicioLunes": umbral_lunes,
        "veredictoReglas": reglas, "flujosReglas": flujos if reglas else 0,
        "parDestino": f"{destino}:{puerto}", "parEnCatalogo": f"{destino}:{puerto}" in PARES_AUTORIZADOS,
        "tipoAtaque": tipo, "confianzaTipo": confianza,
        "flujosPorTipo": {tipo: flujos} if tipo else {},
        "etiquetaVerdad": verdad,
    })


# Martes: fuerza bruta FTP y SSH desde el borde hacia el servidor web (partición de prueba: el tramo final).
episodio("martes", MARTES, "10:02", 18, "172.16.0.1", "192.168.10.50", 21, "ftp", 1198, tipo="fuerza_bruta", confianza=1.0, ia=True, umbrales=True, reglas=False, error=0.0412, contexto=65, verdad="ataque", history="ShAdDafrF")
episodio("martes", MARTES, "14:52", 19, "172.16.0.1", "192.168.10.50", 22, "ssh", 894, tipo="fuerza_bruta", confianza=0.99, ia=True, umbrales=True, reglas=False, error=0.0377, contexto=58, verdad="ataque", history="ShADadfF")
# Martes: falsas alarmas hacia el controlador de dominio (probable falsa alarma, severidad baja).
for hora, origen, puerto, servicio, flujos in [("09:14", "192.168.10.9", 445, "smb", 7), ("09:41", "192.168.10.14", 389, "ldap", 12), ("11:05", "192.168.10.5", 88, "kerberos", 4),
                                               ("13:30", "192.168.10.15", 53, "dns", 21), ("15:48", "192.168.10.17", 3268, "ldap", 9), ("16:20", "192.168.10.8", 445, "smb", 15)]:
    episodio("martes", MARTES, hora, azar.randint(1, 4), origen, "192.168.10.3", puerto, servicio, flujos, tipo="probable_falsa_alarma", confianza=round(azar.uniform(0.82, 0.99), 2), ia=True, umbrales=False, reglas=False)
# Martes: NetBIOS hacia el servidor web (falsa alarma), y detecciones solo de reglas o de umbrales (tipo vacío).
episodio("martes", MARTES, "10:35", 2, "192.168.10.12", "192.168.10.50", 137, "netbios", 6, tipo="probable_falsa_alarma", confianza=0.91, ia=True, umbrales=False, reglas=False, protocolo="udp", estado="S0", history="D")
episodio("martes", MARTES, "12:10", 1, "192.168.10.9", "192.168.10.25", 5900, "vnc", 4, tipo=None, confianza=None, ia=False, umbrales=False, reglas=True, history="S")
episodio("martes", MARTES, "12:47", 3, "192.168.10.12", "192.168.10.3", 53, "dns", 40, tipo=None, confianza=None, ia=False, umbrales=True, reglas=False, contexto=40, protocolo="udp", estado="SF", history="Dd")
episodio("martes", MARTES, "17:02", 1, "192.168.10.16", "192.168.10.51", 8080, "http", 3, tipo=None, confianza=None, ia=False, umbrales=False, reglas=True, history="ShR")
# Martes: tráfico anómalo sin tipo identificado (el clasificador no reunió mayoría).
episodio("martes", MARTES, "15:25", 2, "192.168.10.19", "192.168.10.51", 445, "smb", 33, tipo="sin_tipificar", confianza=0.44, ia=True, umbrales=True, reglas=True, contexto=19)

# Viernes: escaneo de puertos y denegación de servicio contra el servidor web (tramo final de cada ataque).
episodio("viernes", VIERNES, "14:55", 28, "172.16.0.1", "192.168.10.50", 80, "http", 48123, tipo="escaneo", confianza=0.98, ia=True, umbrales=True, reglas=True, puertos_distintos=1061, error=0.0129, contexto=612, verdad="ataque", estado="REJ", history="Sr")
episodio("viernes", VIERNES, "16:10", 6, "172.16.0.1", "192.168.10.50", 80, "http", 28705, tipo="ddos", confianza=1.0, ia=True, umbrales=True, reglas=True, error=0.0211, contexto=840, verdad="ataque", estado="RSTO", history="ShAR")
# Viernes: falsas alarmas, reglas y umbrales solos, y otro sin tipificar.
for hora, origen, destino, puerto, servicio, flujos in [("08:52", "192.168.10.5", "192.168.10.3", 445, "smb", 9), ("09:30", "192.168.10.14", "192.168.10.3", 389, "ldap", 5),
                                                        ("10:12", "192.168.10.16", "192.168.10.50", 137, "netbios", 11), ("11:45", "192.168.10.19", "192.168.10.3", 88, "kerberos", 6),
                                                        ("13:02", "192.168.10.8", "192.168.10.3", 3268, "ldap", 8), ("13:40", "192.168.10.15", "192.168.10.50", 137, "netbios", 14),
                                                        ("15:30", "192.168.10.17", "192.168.10.3", 53, "dns", 18), ("16:40", "192.168.10.9", "192.168.10.3", 445, "smb", 10)]:
    episodio("viernes", VIERNES, hora, azar.randint(1, 4), origen, destino, puerto, servicio, flujos, tipo="probable_falsa_alarma", confianza=round(azar.uniform(0.8, 0.99), 2), ia=True, umbrales=False, reglas=False, protocolo="udp" if puerto in (53, 137) else "tcp")
episodio("viernes", VIERNES, "09:58", 1, "192.168.10.25", "192.168.10.51", 3389, "rdp", 5, tipo=None, confianza=None, ia=False, umbrales=False, reglas=True, history="ShADad")
episodio("viernes", VIERNES, "11:20", 2, "192.168.10.9", "192.168.10.16", 22, "ssh", 3, tipo=None, confianza=None, ia=False, umbrales=False, reglas=True)
episodio("viernes", VIERNES, "12:33", 2, "192.168.10.14", "192.168.10.3", 88, "kerberos", 27, tipo=None, confianza=None, ia=False, umbrales=True, reglas=False, contexto=27)
episodio("viernes", VIERNES, "14:05", 4, "192.168.10.12", "192.168.10.50", 22, "ssh", 61, tipo="sin_tipificar", confianza=0.47, ia=True, umbrales=True, reglas=False, contexto=41)
episodio("viernes", VIERNES, "17:15", 1, "192.168.10.5", "192.168.10.17", 445, "smb", 12, tipo="probable_falsa_alarma", confianza=0.86, ia=True, umbrales=False, reglas=False)

semilla = {
    "generadoEn": datetime.now(timezone.utc).isoformat(timespec="seconds"),
    "fuente": "EJEMPLO armado a mano con la forma de la exportación de CICIDS2017 (martes y viernes). Se reemplaza por la exportación real de la plataforma.",
    "zonaHorariaOriginal": "America/Halifax",
    "nota": "Las fechas son las del dataset (julio de 2017); el backend las corre para la demo.",
    "parametros": {"gapSegundos": 300, "tau": TAU, "theta": 0.5, "umbralCatalogo": 50, "agrupacion": "dia, origen, destino", "particionTipos": "prueba del clasificador (simulada)"},
    "catalogo": {
        "equipos": {ip: EQUIPOS.get(ip) for ip in sorted({a["origenIp"] for a in alertas} | {a["destinoIp"] for a in alertas})},
        "servicios": sorted({a["servicio"] for a in alertas}),
        "paresAutorizados": sorted(PARES_AUTORIZADOS),
    },
    "alertas": sorted(alertas, key=lambda a: a["inicio"]),
}

destino = Path(__file__).resolve().parent.parent / "tests" / "semilla_de_prueba.json"
destino.write_text(json.dumps(semilla, ensure_ascii=False, indent=1), encoding="utf-8")
print(f"{len(alertas)} episodios de ejemplo guardados en {destino}")
