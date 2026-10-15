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
    AvisosTelegram,
    Catalogo,
    Conflicto,
    Contadores,
    Estado,
    EstadoRevision,
    EstadoTelegram,
    PaginaAlertas,
    PareoEntrada,
    PareoSalida,
    Revision,
    RevisionEntrada,
    Salud,
    Severidad,
    Veredictos,
    VinculoEntrada,
    VinculoTelegram,
)
from .revisiones import aplicar_revision
from .semilla import severidad_de
from .telegram import BotTelegram, ErrorTelegram

VERSION = "0.4.0"

router = APIRouter()


def _base(request: Request) -> Base:
    return request.app.state.base


def nombre_equipo(con: sqlite3.Connection, ip: str) -> str | None:
    fila = con.execute("SELECT nombre FROM catalogo_equipos WHERE ip = ?", (ip,)).fetchone()
    return fila["nombre"] if fila else None


def historial_de(con: sqlite3.Connection, alerta_id: str) -> list[Revision]:
    filas = con.execute("SELECT * FROM revisiones WHERE alerta_id = ? ORDER BY id ASC", (alerta_id,)).fetchall()
    return [
        revision_dto(f)
        for f in filas
    ]


def revision_dto(fila: sqlite3.Row) -> Revision:
    return Revision(
        resultado=fila["resultado"],
        motivo=fila["motivo"],
        comentario=fila["comentario"],
        autor=fila["autor"],
        fecha=fila["fecha"],
        revisadaEn=fila["revisada_en"],
    )


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
        protocolo=fila["protocolo"],
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
        avisoTelegram=fila["aviso_telegram_en"],
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
                    revisionAjena=revision_dto(ajena),
                    alerta=alerta_dto(con, fila),
                )
                raise HTTPException(status_code=409, detail=conflicto.model_dump())
        aplicar_revision(con, fila, entrada, dispositivo)
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


def _bot(request: Request) -> BotTelegram | None:
    """El bot de la red, o None si el servidor no tiene TELEGRAM_BOT_TOKEN."""
    return getattr(request.app.state, "bot", None)


def _bot_andando(request: Request) -> BotTelegram:
    bot = _bot(request)
    if bot is None:
        raise HTTPException(status_code=503, detail="Este servidor no tiene el bot de Telegram configurado")
    return bot


def _avisos_de(dispositivo: sqlite3.Row) -> AvisosTelegram:
    return AvisosTelegram(alta=dispositivo["aviso_alta"], media=dispositivo["aviso_media"], baja=dispositivo["aviso_baja"])


def _guardar_avisos(con: sqlite3.Connection, dispositivo_id: str, avisos: AvisosTelegram) -> None:
    con.execute(
        "UPDATE dispositivos SET aviso_alta = ?, aviso_media = ?, aviso_baja = ? WHERE id = ?",
        (avisos.alta, avisos.media, avisos.baja, dispositivo_id),
    )


@router.get("/telegram", response_model=EstadoTelegram)
def estado_telegram(request: Request, dispositivo: sqlite3.Row = Depends(dispositivo_actual)) -> EstadoTelegram:
    """Si este teléfono tiene un chat vinculado, con qué bot y cómo avisa cada severidad. La app lo
    consulta al volver de Telegram y en cada sincronización."""
    bot = _bot(request)
    return EstadoTelegram(
        vinculado=dispositivo["telegram_chat_id"] is not None,
        bot=bot.usuario if bot is not None else request.app.state.config.bot_usuario,
        vinculadoEn=dispositivo["telegram_vinculado_en"],
        avisos=_avisos_de(dispositivo),
    )


@router.post("/telegram/vinculo", response_model=VinculoTelegram)
def vincular_telegram(
    request: Request,
    entrada: VinculoEntrada | None = None,
    dispositivo: sqlite3.Row = Depends(dispositivo_actual),
) -> VinculoTelegram:
    """Código de un uso (10 minutos) y el enlace que abre el chat con el bot: al tocar Iniciar, el bot
    recibe el código y asocia el chat con este teléfono (RF05). Guarda los avisos que eligió la persona.
    503 si el servidor no tiene el bot."""
    bot = _bot_andando(request)
    cfg = request.app.state.config
    codigo = nuevo_codigo()
    vence = ahora() + timedelta(minutes=cfg.minutos_codigo)
    with _base(request).transaccion() as con:
        con.execute(
            "INSERT INTO codigos_telegram (codigo, dispositivo_id, creado_en, vence_en) VALUES (?, ?, ?, ?)",
            (codigo, dispositivo["id"], iso(ahora()), iso(vence)),
        )
        if entrada is not None and entrada.avisos is not None:
            _guardar_avisos(con, dispositivo["id"], entrada.avisos)
    return VinculoTelegram(codigo=codigo, enlaceBot=bot.enlace_de_vinculo(codigo), vence=iso(vence))


@router.delete("/telegram/vinculo", status_code=204)
async def desvincular_telegram(request: Request, dispositivo: sqlite3.Row = Depends(dispositivo_actual)) -> Response:
    """El chat deja de recibir los avisos de este teléfono ("Desvincular Telegram", Figma 66-3484)."""
    chat_id = dispositivo["telegram_chat_id"]
    with _base(request).transaccion() as con:
        con.execute("UPDATE dispositivos SET telegram_chat_id = NULL, telegram_vinculado_en = NULL WHERE id = ?", (dispositivo["id"],))
        sigue = chat_id is not None and con.execute(
            "SELECT 1 FROM dispositivos WHERE telegram_chat_id = ? AND revocado_en IS NULL", (chat_id,)
        ).fetchone()
    bot = _bot(request)
    if chat_id is not None and not sigue and bot is not None:
        try:
            await bot.despedir(chat_id)
        except ErrorTelegram:
            pass  # el desvínculo vale igual; solo falta la despedida
    return Response(status_code=204)


@router.post("/telegram/prueba", status_code=204)
async def aviso_de_prueba(request: Request, dispositivo: sqlite3.Row = Depends(dispositivo_actual)) -> Response:
    """Manda un aviso de prueba al chat de este teléfono ("Enviar un aviso de prueba", Figma 66-3484).
    409 si no tiene Telegram vinculado, 503 si el servidor no tiene el bot, 502 si Telegram no respondió."""
    bot = _bot_andando(request)
    chat_id = dispositivo["telegram_chat_id"]
    if chat_id is None:
        raise HTTPException(status_code=409, detail="Este teléfono no tiene Telegram vinculado")
    try:
        await bot.enviar_prueba(chat_id)
    except ErrorTelegram as error:
        if error.chat_perdido:
            bot.desvincular_chat(chat_id)
            raise HTTPException(status_code=409, detail="Este teléfono no tiene Telegram vinculado") from None
        raise HTTPException(status_code=502, detail="Telegram no respondió") from None
    return Response(status_code=204)


@router.put("/telegram/avisos", response_model=AvisosTelegram)
def guardar_avisos_telegram(entrada: AvisosTelegram, request: Request, dispositivo: sqlite3.Row = Depends(dispositivo_actual)) -> AvisosTelegram:
    """Cómo avisa Telegram cada severidad en este teléfono (Figma 51-1688). Vale aunque todavía no haya
    un chat vinculado."""
    with _base(request).transaccion() as con:
        _guardar_avisos(con, dispositivo["id"], entrada)
    return entrada


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
