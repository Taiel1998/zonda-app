"""Las rutas del contrato (informe, sección 13.3). Cada una dice qué recibe, qué devuelve y qué error da."""

import sqlite3
from datetime import timedelta
from typing import Annotated, Literal

from fastapi import APIRouter, Depends, Header, HTTPException, Request, Response

from .auth import (
    ahora,
    buscar_dispositivo,
    canjear_codigo_pareo,
    dispositivo_actual,
    iso,
    nuevo_codigo,
    token_de_cabecera,
)
from .db import Base
from .modelos import (
    CLAVE_SIN_TIPO,
    MOTIVOS_POR_RESULTADO,
    TIPOS_SEVERIDAD,
    Alerta,
    Catalogo,
    Conflicto,
    Contadores,
    Estado,
    EstadoRevision,
    PaginaAlertas,
    PareoEntrada,
    PareoSalida,
    Revision,
    RevisionEntrada,
    Salud,
    Severidad,
    Veredictos,
    VinculoTelegram,
)
from .semilla import SEVERIDAD_RECOMENDADA

VERSION = "0.3.0"

router = APIRouter()


def _base(request: Request) -> Base:
    return request.app.state.base


def severidad_de(con: sqlite3.Connection, tipo_ataque: str | None) -> str:
    """La severidad sale del tipo, según la tabla de la red (RF06). El tipo ordena, no filtra."""
    clave = tipo_ataque or CLAVE_SIN_TIPO
    fila = con.execute("SELECT severidad FROM severidades WHERE tipo_ataque = ?", (clave,)).fetchone()
    return fila["severidad"] if fila else SEVERIDAD_RECOMENDADA[clave]


def nombre_equipo(con: sqlite3.Connection, ip: str) -> str | None:
    fila = con.execute("SELECT nombre FROM catalogo_equipos WHERE ip = ?", (ip,)).fetchone()
    return fila["nombre"] if fila else None


def historial_de(con: sqlite3.Connection, alerta_id: str) -> list[Revision]:
    filas = con.execute("SELECT * FROM revisiones WHERE alerta_id = ? ORDER BY id ASC", (alerta_id,)).fetchall()
    return [
        Revision(resultado=f["resultado"], motivo=f["motivo"], comentario=f["comentario"], autor=f["autor"], fecha=f["fecha"])
        for f in filas
    ]


def alerta_dto(con: sqlite3.Connection, fila: sqlite3.Row) -> Alerta:
    return Alerta(
        id=fila["id"],
        creadaEn=fila["creada_en"],
        inicio=fila["inicio"],
        fin=fila["fin"],
        cantidadFlujos=fila["cantidad_flujos"],
        uidReferencia=fila["uid_referencia"],
        origenIp=fila["origen_ip"],
        origenNombre=nombre_equipo(con, fila["origen_ip"]),
        destinoIp=fila["destino_ip"],
        destinoNombre=nombre_equipo(con, fila["destino_ip"]),
        servicio=fila["servicio"],
        puerto=fila["puerto"],
        puertoReferencia=fila["puerto_referencia"],
        puertosDistintos=fila["puertos_distintos"],
        tipoAtaque=fila["tipo_ataque"],
        confianzaTipo=fila["confianza_tipo"],
        severidad=severidad_de(con, fila["tipo_ataque"]),
        veredictos=Veredictos(
            ia=bool(fila["veredicto_ia"]),
            errorReconstruccion=fila["error_reconstruccion"],
            umbralIa=fila["umbral_ia"],
            umbrales=bool(fila["veredicto_umbrales"]),
            contexto60s=fila["contexto_60s"],
            umbralServicio=fila["umbral_servicio"],
            servicioSinLineaBase=bool(fila["servicio_sin_linea_base"]),
            reglas=bool(fila["veredicto_reglas"]),
            parDestino=fila["par_destino"],
            parEnCatalogo=bool(fila["par_en_catalogo"]),
        ),
        estadoRevision=fila["estado_revision"],
        historial=historial_de(con, fila["id"]),
        version=fila["version"],
        actualizadaEn=fila["actualizada_en"],
    )


@router.get("/salud", response_model=Salud)
def salud(request: Request, authorization: Annotated[str | None, Header()] = None) -> Salud:
    """Sin token sirve para verificar el servidor antes de parear. Con token, informa su vencimiento (401 si venció)."""
    con = _base(request).conexion()
    token_vence = None
    token = token_de_cabecera(authorization)
    if token is not None:
        dispositivo = buscar_dispositivo(con, token)
        if dispositivo is None:
            raise HTTPException(status_code=401, detail="El token venció o fue revocado")
        token_vence = dispositivo["vence_en"]
    cfg = request.app.state.config
    cantidad = con.execute("SELECT COUNT(*) FROM alertas").fetchone()[0]
    return Salud(version=VERSION, hora=iso(ahora()), alertas=cantidad, red={"id": cfg.red_id, "nombre": cfg.red_nombre}, tokenVence=token_vence)


@router.get("/estado", response_model=Estado)
def estado(request: Request, dispositivo: sqlite3.Row = Depends(dispositivo_actual)) -> Estado:
    """Alertas sin revisar por severidad y hora del último flujo procesado (RF01)."""
    base = _base(request)
    con = base.conexion()
    contadores = {"alta": 0, "media": 0, "baja": 0}
    for fila in con.execute("SELECT tipo_ataque, COUNT(*) AS n FROM alertas WHERE estado_revision = 'sin_revisar' GROUP BY tipo_ataque"):
        contadores[severidad_de(con, fila["tipo_ataque"])] += fila["n"]
    return Estado(sinRevisar=Contadores(**contadores), ultimoFlujoProcesado=base.leer_meta("ultimo_flujo"), horaServidor=iso(ahora()))


@router.get("/alertas", response_model=PaginaAlertas)
def listar_alertas(
    request: Request,
    dispositivo: sqlite3.Row = Depends(dispositivo_actual),
    desde: str | None = None,
    severidad: Severidad | None = None,
    servicio: str | None = None,
    destino: str | None = None,
    metodo: Literal["ia", "umbrales", "reglas"] | None = None,
    estado: EstadoRevision | None = None,
) -> PaginaAlertas:
    """Alertas nuevas o modificadas desde el cursor, en orden de cambio. 410 si el cursor es de otra instalación."""
    base = _base(request)
    con = base.conexion()
    instancia = base.leer_meta("instancia", "")
    secuencia_desde = 0
    if desde:
        partes = desde.split(":")
        if len(partes) != 2 or partes[0] != instancia or not partes[1].isdigit():
            raise HTTPException(status_code=410, detail="El cursor ya no es válido: hay que sincronizar desde cero")
        secuencia_desde = int(partes[1])

    sql = "SELECT * FROM alertas WHERE secuencia > ?"
    parametros: list = [secuencia_desde]
    if servicio:
        sql += " AND servicio = ?"
        parametros.append(servicio)
    if destino:
        sql += " AND destino_ip = ?"
        parametros.append(destino)
    if estado:
        sql += " AND estado_revision = ?"
        parametros.append(estado)
    if metodo:
        sql += f" AND veredicto_{metodo} = 1"
    if severidad:
        # La severidad sale del tipo según la tabla de la red; se filtra en SQL para que cada página venga completa.
        sql += " AND COALESCE(tipo_ataque, ?) IN (SELECT tipo_ataque FROM severidades WHERE severidad = ?)"
        parametros.extend([CLAVE_SIN_TIPO, severidad])
    cfg = request.app.state.config
    total = None
    if not desde:
        # La primera sincronización trae lo que la app guarda (los últimos 30 días) y avisa cuántas son,
        # para la barra "120 de 213" de la descarga inicial.
        sql += " AND creada_en >= ?"
        parametros.append(iso(ahora() - timedelta(days=cfg.dias_cache)))
        total = con.execute(f"SELECT COUNT(*) FROM ({sql})", parametros).fetchone()[0]
    tamanio = cfg.tamanio_pagina
    sql += " ORDER BY secuencia ASC LIMIT ?"
    parametros.append(tamanio + 1)
    filas = con.execute(sql, parametros).fetchall()
    hay_mas = len(filas) > tamanio
    filas = filas[:tamanio]
    alertas = [alerta_dto(con, fila) for fila in filas]
    ultima = filas[-1]["secuencia"] if filas else secuencia_desde
    return PaginaAlertas(alertas=alertas, cursorSiguiente=f"{instancia}:{ultima}", hayMas=hay_mas, horaServidor=iso(ahora()), total=total)


@router.get("/alertas/{alerta_id}", response_model=Alerta)
def detalle_alerta(alerta_id: str, request: Request, dispositivo: sqlite3.Row = Depends(dispositivo_actual)) -> Alerta:
    con = _base(request).conexion()
    fila = con.execute("SELECT * FROM alertas WHERE id = ?", (alerta_id,)).fetchone()
    if fila is None:
        raise HTTPException(status_code=404, detail="No existe esa alerta")
    return alerta_dto(con, fila)


@router.post("/alertas/{alerta_id}/revision", response_model=Alerta, responses={409: {"model": Conflicto}})
def revisar_alerta(
    alerta_id: str,
    entrada: RevisionEntrada,
    request: Request,
    dispositivo: sqlite3.Row = Depends(dispositivo_actual),
) -> Alerta:
    """Registra la revisión (RF04). Idempotente por accionId; 409 si otra persona revisó después de lo que vio el teléfono."""
    if entrada.motivo not in MOTIVOS_POR_RESULTADO[entrada.resultado]:
        raise HTTPException(status_code=422, detail="El motivo no corresponde al resultado")
    base = _base(request)
    with base.transaccion() as con:
        fila = con.execute("SELECT * FROM alertas WHERE id = ?", (alerta_id,)).fetchone()
        if fila is None:
            raise HTTPException(status_code=404, detail="No existe esa alerta")
        if con.execute("SELECT 1 FROM revisiones WHERE accion_id = ?", (entrada.accionId,)).fetchone():
            return alerta_dto(con, fila)
        if entrada.versionVista is not None and entrada.versionVista < fila["version"] and not entrada.forzar:
            ajena = con.execute("SELECT * FROM revisiones WHERE alerta_id = ? ORDER BY id DESC LIMIT 1", (alerta_id,)).fetchone()
            if ajena is not None:
                conflicto = Conflicto(
                    mensaje="Otra persona revisó esta alerta después de tu última sincronización",
                    revisionAjena=Revision(resultado=ajena["resultado"], motivo=ajena["motivo"], comentario=ajena["comentario"], autor=ajena["autor"], fecha=ajena["fecha"]),
                    alerta=alerta_dto(con, fila),
                )
                raise HTTPException(status_code=409, detail=conflicto.model_dump())
        momento = ahora()
        nueva_version = fila["version"] + 1
        con.execute(
            "INSERT INTO revisiones (alerta_id, accion_id, resultado, motivo, comentario, autor, fecha, dispositivo_id, version_resultante) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
            (alerta_id, entrada.accionId, entrada.resultado, entrada.motivo, entrada.comentario.strip(), dispositivo["nombre"], iso(momento), dispositivo["id"], nueva_version),
        )
        con.execute(
            "UPDATE alertas SET estado_revision = ?, version = ?, actualizada_en = ?, secuencia = ? WHERE id = ?",
            (entrada.resultado, nueva_version, iso(momento), Base.siguiente_secuencia(con), alerta_id),
        )
        return alerta_dto(con, con.execute("SELECT * FROM alertas WHERE id = ?", (alerta_id,)).fetchone())


@router.get("/severidades")
def leer_severidades(request: Request, dispositivo: sqlite3.Row = Depends(dispositivo_actual)) -> dict[str, str]:
    con = _base(request).conexion()
    return {f["tipo_ataque"]: f["severidad"] for f in con.execute("SELECT * FROM severidades ORDER BY tipo_ataque")}


@router.put("/severidades")
def guardar_severidades(entrada: dict[str, Severidad], request: Request, dispositivo: sqlite3.Row = Depends(dispositivo_actual)) -> dict[str, str]:
    """Severidad por tipo para toda la red (RF06). Marca todas las alertas como modificadas para que los teléfonos las vuelvan a bajar."""
    desconocidas = set(entrada) - set(TIPOS_SEVERIDAD)
    if desconocidas:
        raise HTTPException(status_code=422, detail=f"Tipos desconocidos: {', '.join(sorted(desconocidas))}")
    base = _base(request)
    with base.transaccion() as con:
        for tipo, severidad in entrada.items():
            con.execute("INSERT INTO severidades (tipo_ataque, severidad) VALUES (?, ?) ON CONFLICT(tipo_ataque) DO UPDATE SET severidad = excluded.severidad", (tipo, severidad))
        momento = iso(ahora())
        for fila in con.execute("SELECT id FROM alertas ORDER BY secuencia").fetchall():
            con.execute("UPDATE alertas SET secuencia = ?, actualizada_en = ? WHERE id = ?", (Base.siguiente_secuencia(con), momento, fila["id"]))
        return {f["tipo_ataque"]: f["severidad"] for f in con.execute("SELECT * FROM severidades ORDER BY tipo_ataque")}


@router.post("/pareo", response_model=PareoSalida)
def parear(entrada: PareoEntrada, request: Request) -> PareoSalida:
    """Canjea el código de un uso por el token del teléfono (400 inválido, 410 vencido o usado)."""
    cfg = request.app.state.config
    limite = iso(ahora() - timedelta(days=cfg.dias_cache))
    with _base(request).transaccion() as con:
        datos = canjear_codigo_pareo(con, entrada.codigo, entrada.nombreDispositivo, cfg.dias_token)
        recientes = con.execute("SELECT COUNT(*) FROM alertas WHERE creada_en >= ?", (limite,)).fetchone()[0]
    return PareoSalida(
        token=datos["token"],
        red={"id": cfg.red_id, "nombre": cfg.red_nombre},
        emitido=datos["emitido"],
        vence=datos["vence"],
        dispositivoId=datos["dispositivoId"],
        alertas30Dias=recientes,
    )


@router.post("/telegram/vinculo", response_model=VinculoTelegram)
def vincular_telegram(request: Request, dispositivo: sqlite3.Row = Depends(dispositivo_actual)) -> VinculoTelegram:
    """Código de un uso para que el bot asocie el chat con este dispositivo (lo consume el bot en la fase 10)."""
    cfg = request.app.state.config
    codigo = nuevo_codigo()
    vence = ahora() + timedelta(minutes=cfg.minutos_codigo)
    with _base(request).transaccion() as con:
        con.execute(
            "INSERT INTO codigos_telegram (codigo, dispositivo_id, creado_en, vence_en) VALUES (?, ?, ?, ?)",
            (codigo, dispositivo["id"], iso(ahora()), iso(vence)),
        )
    return VinculoTelegram(codigo=codigo, enlaceBot=f"https://t.me/{cfg.bot_usuario}?start={codigo}", vence=iso(vence))


@router.delete("/dispositivos/actual", status_code=204)
def quitar_dispositivo(request: Request, dispositivo: sqlite3.Row = Depends(dispositivo_actual)) -> Response:
    """Revoca el token de este teléfono; la app borra sus datos locales."""
    with _base(request).transaccion() as con:
        con.execute("UPDATE dispositivos SET revocado_en = ? WHERE id = ?", (iso(ahora()), dispositivo["id"]))
    return Response(status_code=204)


@router.get("/catalogo", response_model=Catalogo)
def catalogo(request: Request, dispositivo: sqlite3.Row = Depends(dispositivo_actual)) -> Catalogo:
    con = _base(request).conexion()
    return Catalogo(
        equipos={f["ip"]: f["nombre"] for f in con.execute("SELECT * FROM catalogo_equipos ORDER BY ip")},
        servicios=[f["servicio"] for f in con.execute("SELECT * FROM catalogo_servicios ORDER BY servicio")],
    )
