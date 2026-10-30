"""Las rutas del contrato (informe, sección 13.3). Cada una dice qué recibe, qué devuelve y qué error da."""

import secrets
import sqlite3
from datetime import datetime, timedelta, timezone
from typing import Annotated, Literal

from fastapi import APIRouter, Depends, Header, HTTPException, Request, Response

from . import latido
from . import personas as gente
from . import registro
from . import trafico
from .asignaciones import asignaciones_de, asignar, responsable_de
from .auth import (
    administrador_actual,
    ahora,
    buscar_dispositivo,
    canjear_codigo_pareo,
    contenido_qr,
    crear_codigo_pareo,
    dispositivo_actual,
    iso,
    nuevo_codigo,
    token_de_cabecera,
)
from .db import Base
from .modelos import (
    MOTIVOS_POR_RESULTADO,
    TIPOS_SEVERIDAD,
    Alerta,
    AlertaIngesta,
    AsignacionEntrada,
    AvisosTelegram,
    CambioRegistro,
    Catalogo,
    CodigoPersona,
    CodigoPersonaEntrada,
    Conflicto,
    Contadores,
    Equipo,
    Estado,
    EstadoRevision,
    EstadoTelegram,
    ImportanciaEntrada,
    IngestaEntrada,
    IngestaResultado,
    LatidoEntrada,
    LatidoResultado,
    PaginaAlertas,
    ParDeTrafico,
    PareoEntrada,
    PareoSalida,
    ParesTrafico,
    PerfilEntrada,
    Persona,
    PuntoTrafico,
    Revision,
    RevisionEntrada,
    Salud,
    SerieTrafico,
    Severidad,
    SeveridadesEntrada,
    TraficoEntrada,
    TraficoResultado,
    Veredictos,
    VinculoEntrada,
    VinculoTelegram,
)
from .revisiones import aplicar_revision
from .semilla import SQL_SEVERIDAD, guardar_equipo, severidad_de
from .telegram import BotTelegram, ErrorTelegram

VERSION = "0.8.0"

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
        persona=fila["persona"],
    )


def alerta_dto(con: sqlite3.Connection, fila: sqlite3.Row) -> Alerta:
    asignaciones = asignaciones_de(con, fila["id"])
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
        severidad=severidad_de(con, fila),
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
        asignacion=responsable_de(asignaciones),
        asignaciones=asignaciones,
    )


@router.get("/salud", response_model=Salud)
def salud(request: Request, authorization: Annotated[str | None, Header()] = None) -> Salud:
    """Sin token sirve para verificar el servidor antes de parear. Con token, informa su vencimiento (401
    si venció) y cómo está la cadena: el sensor, el análisis en vivo y el servidor (Estado del sistema)."""
    base = _base(request)
    con = base.conexion()
    cfg = request.app.state.config
    token_vence = None
    sistema = None
    token = token_de_cabecera(authorization)
    if token is not None:
        dispositivo = buscar_dispositivo(con, token)
        if dispositivo is None:
            raise HTTPException(status_code=401, detail="El token venció o fue revocado")
        token_vence = dispositivo["vence_en"]
        sistema = latido.estado_del_sistema(base, cfg)
    cantidad = con.execute("SELECT COUNT(*) FROM alertas").fetchone()[0]
    return Salud(
        version=VERSION,
        hora=iso(ahora()),
        alertas=cantidad,
        red={"id": cfg.red_id, "nombre": cfg.red_nombre},
        tokenVence=token_vence,
        sistema=sistema,
    )


@router.get("/estado", response_model=Estado)
def estado(request: Request, dispositivo: sqlite3.Row = Depends(dispositivo_actual)) -> Estado:
    """Alertas sin revisar por severidad final (la del tipo, ajustada por el equipo destino) y hora del
    último flujo procesado (RF01)."""
    base = _base(request)
    con = base.conexion()
    contadores = {"alta": 0, "media": 0, "baja": 0}
    for fila in con.execute(
        "SELECT tipo_ataque, veredicto_reglas, veredicto_umbrales, destino_ip, COUNT(*) AS n FROM alertas "
        "WHERE estado_revision = 'sin_revisar' GROUP BY tipo_ataque, veredicto_reglas, veredicto_umbrales, destino_ip"
    ):
        contadores[severidad_de(con, fila)] += fila["n"]
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
        sql += f" AND {SQL_SEVERIDAD} = ?"
        parametros.append(severidad)
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


def _marcar_modificadas(con: sqlite3.Connection, donde: str = "", parametros: tuple = ()) -> None:
    """Mueve la secuencia de esas alertas (todas, sin [donde]) para que los teléfonos las vuelvan a bajar."""
    momento = iso(ahora())
    for fila in con.execute(f"SELECT id FROM alertas {donde} ORDER BY secuencia", parametros).fetchall():
        con.execute("UPDATE alertas SET secuencia = ?, actualizada_en = ? WHERE id = ?", (Base.siguiente_secuencia(con), momento, fila["id"]))


@router.put("/severidades")
def guardar_severidades(entrada: SeveridadesEntrada, request: Request, administrador: sqlite3.Row = Depends(administrador_actual)) -> dict[str, str]:
    """Severidad por tipo para toda la red (RF06). Solo un administrador y con motivo (decisiones 39 y
    40): cada tipo que cambia queda en el registro con ese motivo. Marca todas las alertas como
    modificadas para que los teléfonos las vuelvan a bajar."""
    desconocidas = set(entrada.severidades) - set(TIPOS_SEVERIDAD)
    if desconocidas:
        raise HTTPException(status_code=422, detail=f"Tipos desconocidos: {', '.join(sorted(desconocidas))}")
    base = _base(request)
    with base.transaccion() as con:
        actuales = {f["tipo_ataque"]: f["severidad"] for f in con.execute("SELECT * FROM severidades")}
        cambios = {tipo: severidad for tipo, severidad in entrada.severidades.items() if actuales.get(tipo) != severidad}
        for tipo, severidad in cambios.items():
            con.execute("INSERT INTO severidades (tipo_ataque, severidad) VALUES (?, ?) ON CONFLICT(tipo_ataque) DO UPDATE SET severidad = excluded.severidad", (tipo, severidad))
            registro.anotar(con, "severidad", administrador, objeto=tipo, antes=actuales.get(tipo), despues=severidad, motivo=entrada.motivo)
        if cambios:
            _marcar_modificadas(con)
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
        perfil=datos["perfil"],
        persona=datos["persona"],
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
    """Revoca el token de este teléfono; la app borra sus datos locales. Queda en el registro de
    cambios. 409 si es el último administrador y queda más gente (decisión 39)."""
    with _base(request).transaccion() as con:
        gente.quitar(con, dispositivo, autor=dispositivo)
    return Response(status_code=204)


# ---- Personas, equipos y registro de cambios (decisiones 39, 40 y 41) ----


@router.get("/personas", response_model=list[Persona])
def personas(request: Request, dispositivo: sqlite3.Row = Depends(dispositivo_actual)) -> list[Persona]:
    """La gente de la red con su perfil, su teléfono y cuándo lo usó por última vez. La ven todos."""
    con = _base(request).conexion()
    return [gente.persona_dto(fila, vos=fila["id"] == dispositivo["id"]) for fila in gente.activos(con)]


@router.post("/personas/codigo", response_model=CodigoPersona)
def codigo_para_persona(entrada: CodigoPersonaEntrada, request: Request, administrador: sqlite3.Row = Depends(administrador_actual)) -> CodigoPersona:
    """Agregar persona: un código de un uso (10 minutos) con el nombre y el perfil de quien se suma, y lo
    que lleva su QR. Al usarlo, queda en el registro a nombre de este administrador."""
    cfg = request.app.state.config
    with _base(request).transaccion() as con:
        codigo, vence = crear_codigo_pareo(con, cfg.minutos_codigo, persona=entrada.nombre, perfil=entrada.perfil, creado_por=administrador["id"])
    return CodigoPersona(codigo=codigo, vence=iso(vence), contenido=contenido_qr(cfg.url_publica, codigo, vence), nombre=entrada.nombre, perfil=entrada.perfil)


def _persona_activa(con: sqlite3.Connection, persona_id: str) -> sqlite3.Row:
    fila = gente.activo(con, persona_id)
    if fila is None:
        raise HTTPException(status_code=404, detail="Esa persona no está en el equipo")
    return fila


@router.put("/personas/{persona_id}/perfil", response_model=Persona)
def cambiar_perfil(persona_id: str, entrada: PerfilEntrada, request: Request, administrador: sqlite3.Row = Depends(administrador_actual)) -> Persona:
    """Hacer administrador o pasar a analista. 409 si deja al equipo sin administrador."""
    with _base(request).transaccion() as con:
        gente.cambiar_perfil(con, _persona_activa(con, persona_id), entrada.perfil, autor=administrador)
        return gente.persona_dto(_persona_activa(con, persona_id), vos=persona_id == administrador["id"])


@router.delete("/personas/{persona_id}", status_code=204)
def quitar_persona(persona_id: str, request: Request, administrador: sqlite3.Row = Depends(administrador_actual)) -> Response:
    """Quitar del equipo: revoca el token de ese teléfono. 409 si deja gente sin administrador."""
    with _base(request).transaccion() as con:
        gente.quitar(con, _persona_activa(con, persona_id), autor=administrador)
    return Response(status_code=204)


def _equipo_dto(fila: sqlite3.Row) -> Equipo:
    return Equipo(ip=fila["ip"], nombre=fila["nombre"], importancia=fila["importancia"])


@router.get("/equipos", response_model=list[Equipo])
def equipos(request: Request, dispositivo: sqlite3.Row = Depends(dispositivo_actual)) -> list[Equipo]:
    """Los equipos de la red con nombre en el catálogo y su importancia (Ajustes → Equipos)."""
    con = _base(request).conexion()
    return [_equipo_dto(fila) for fila in con.execute("SELECT * FROM catalogo_equipos ORDER BY nombre, ip")]


@router.put("/equipos/{ip}", response_model=Equipo)
def cambiar_importancia(ip: str, entrada: ImportanciaEntrada, request: Request, administrador: sqlite3.Row = Depends(administrador_actual)) -> Equipo:
    """La importancia de un equipo (decisión 41), con motivo. Recategoriza sus alertas, las viejas y las
    nuevas: se mueven para que los teléfonos las vuelvan a bajar con la severidad nueva."""
    with _base(request).transaccion() as con:
        fila = con.execute("SELECT * FROM catalogo_equipos WHERE ip = ?", (ip,)).fetchone()
        if fila is None:
            raise HTTPException(status_code=404, detail="Ese equipo no está en el catálogo")
        if fila["importancia"] != entrada.importancia:
            con.execute("UPDATE catalogo_equipos SET importancia = ? WHERE ip = ?", (entrada.importancia, ip))
            registro.anotar(con, "importancia", administrador, objeto=ip, nombre=fila["nombre"], antes=fila["importancia"], despues=entrada.importancia, motivo=entrada.motivo)
            _marcar_modificadas(con, "WHERE destino_ip = ?", (ip,))
        return _equipo_dto(con.execute("SELECT * FROM catalogo_equipos WHERE ip = ?", (ip,)).fetchone())


@router.get("/registro", response_model=list[CambioRegistro])
def registro_de_cambios(request: Request, dispositivo: sqlite3.Row = Depends(dispositivo_actual)) -> list[CambioRegistro]:
    """Quién cambió la configuración de la red, cuándo y por qué, de lo más nuevo a lo más viejo. Lo ve todo el equipo."""
    return registro.leer(_base(request).conexion())


@router.put("/alertas/{alerta_id}/asignacion", response_model=Alerta)
def asignar_alerta(alerta_id: str, entrada: AsignacionEntrada, request: Request, dispositivo: sqlite3.Row = Depends(dispositivo_actual)) -> Alerta:
    """Asigna la alerta a una persona o la deja sin asignar (decisión 42). Idempotente por accionId. 403
    si a un analista no le toca, 409 si un analista se asigna una que ya tiene otra persona."""
    with _base(request).transaccion() as con:
        fila = con.execute("SELECT * FROM alertas WHERE id = ?", (alerta_id,)).fetchone()
        if fila is None:
            raise HTTPException(status_code=404, detail="No existe esa alerta")
        if con.execute("SELECT 1 FROM asignaciones WHERE accion_id = ?", (entrada.accionId,)).fetchone() is None:
            asignar(con, fila, entrada, dispositivo)
        return alerta_dto(con, con.execute("SELECT * FROM alertas WHERE id = ?", (alerta_id,)).fetchone())


@router.get("/catalogo", response_model=Catalogo)
def catalogo(request: Request, dispositivo: sqlite3.Row = Depends(dispositivo_actual)) -> Catalogo:
    con = _base(request).conexion()
    return Catalogo(
        equipos={f["ip"]: f["nombre"] for f in con.execute("SELECT * FROM catalogo_equipos ORDER BY ip")},
        servicios=[f["servicio"] for f in con.execute("SELECT * FROM catalogo_servicios ORDER BY servicio")],
    )


# ---- Ingesta de alertas desde la plataforma de detección (prueba de punta a punta, §21.6) ----


def verificar_token_ingesta(request: Request, x_token_ingesta: Annotated[str | None, Header()] = None) -> None:
    """La plataforma de detección se autentica con un token propio (ZONDA_INGESTA_TOKEN), distinto del de
    los teléfonos, en la cabecera X-Token-Ingesta. 503 si el servidor no tiene uno; 401 si no coincide."""
    esperado = request.app.state.config.ingesta_token
    if not esperado:
        raise HTTPException(status_code=503, detail="La ingesta de alertas no está configurada")
    if not x_token_ingesta or not secrets.compare_digest(x_token_ingesta, esperado):
        raise HTTPException(status_code=401, detail="Token de ingesta inválido")


def _a_utc(valor: str) -> str:
    """Fecha ISO 8601 (con zona) a ISO en UTC con segundos, como guarda el resto de la base."""
    return iso(datetime.fromisoformat(valor).astimezone(timezone.utc))


def _guardar_ingesta(con: sqlite3.Connection, a: AlertaIngesta) -> bool:
    """Crea la alerta si el id del episodio es nuevo, o actualiza sus datos si ya existe. Devuelve True si
    la creó. En los dos casos mueve la secuencia y actualizadaEn para que la app la vuelva a bajar; el
    aviso de Telegram de una alerta nueva lo manda el vigía del bot (una sola vez, por su dedup). La
    revisión de una alerta ya existente (estado y versión) se conserva."""
    momento = iso(ahora())
    datos = {
        "inicio": _a_utc(a.inicio),
        "fin": _a_utc(a.fin),
        "cantidad_flujos": a.cantidadFlujos,
        "uid_referencia": a.uidReferencia,
        "origen_ip": a.origenIp,
        "destino_ip": a.destinoIp,
        "servicio": a.servicio,
        "protocolo": a.protocolo,
        "puerto": a.puerto,
        "puerto_referencia": a.puertoReferencia if a.puertoReferencia is not None else a.puerto,
        "puertos_distintos": a.puertosDistintos,
        "tipo_ataque": a.tipoAtaque,
        "confianza_tipo": a.confianzaTipo,
        "veredicto_ia": int(a.veredictoIa),
        "error_reconstruccion": a.errorReconstruccion,
        "umbral_ia": a.umbralIa,
        "veredicto_umbrales": int(a.veredictoUmbrales),
        "contexto_60s": a.contexto60s,
        "umbral_servicio": a.umbralServicio,
        "servicio_sin_linea_base": int(a.umbralServicioLunes is None),
        "veredicto_reglas": int(a.veredictoReglas),
        "par_destino": a.parDestino,
        "par_en_catalogo": int(a.parEnCatalogo),
    }
    existe = con.execute("SELECT 1 FROM alertas WHERE id = ?", (a.id,)).fetchone() is not None
    secuencia = Base.siguiente_secuencia(con)
    if existe:
        asignaciones = ", ".join(f"{col} = ?" for col in datos)
        con.execute(
            f"UPDATE alertas SET {asignaciones}, actualizada_en = ?, secuencia = ? WHERE id = ?",
            (*datos.values(), momento, secuencia, a.id),
        )
        return False
    columnas = ", ".join(datos)
    marcadores = ", ".join("?" for _ in datos)
    con.execute(
        f"INSERT INTO alertas (id, creada_en, {columnas}, estado_revision, version, actualizada_en, secuencia) "
        f"VALUES (?, ?, {marcadores}, 'sin_revisar', 1, ?, ?)",
        (a.id, momento, *datos.values(), momento, secuencia),
    )
    return True


@router.post("/ingesta/alertas", response_model=IngestaResultado)
def ingesta_alertas(entrada: IngestaEntrada, request: Request, _: None = Depends(verificar_token_ingesta)) -> IngestaResultado:
    """Recibe episodios de la plataforma de detección (el pipeline en vivo del laboratorio) con el mismo
    formato de la semilla y los crea o actualiza por id (§21.6). El alta la avisa el bot de Telegram como
    cualquier alerta nueva; la actualización mueve la secuencia para que la app la vuelva a bajar."""
    base = _base(request)
    creadas = 0
    fin_mas_nuevo: str | None = None
    with base.transaccion() as con:
        if entrada.catalogo is not None:
            for ip, nombre in entrada.catalogo.equipos.items():
                if nombre:
                    guardar_equipo(con, ip, nombre)
            for servicio in entrada.catalogo.servicios:
                con.execute("INSERT OR IGNORE INTO catalogo_servicios (servicio) VALUES (?)", (servicio,))
        for alerta in entrada.alertas:
            if _guardar_ingesta(con, alerta):
                creadas += 1
            fin = _a_utc(alerta.fin)
            if fin_mas_nuevo is None or fin > fin_mas_nuevo:
                fin_mas_nuevo = fin
        if fin_mas_nuevo is not None:
            ultimo = base.leer_meta("ultimo_flujo")
            if ultimo is None or ultimo < fin_mas_nuevo:
                Base.escribir_meta(con, "ultimo_flujo", fin_mas_nuevo)
    return IngestaResultado(recibidas=len(entrada.alertas), creadas=creadas, actualizadas=len(entrada.alertas) - creadas)


@router.post("/ingesta/latido", response_model=LatidoResultado)
def ingesta_latido(entrada: LatidoEntrada, request: Request, _: None = Depends(verificar_token_ingesta)) -> LatidoResultado:
    """El latido del análisis en vivo al final de cada ciclo, aunque no haya episodios, con el mismo token
    que la ingesta. Se guarda el último: GET /salud dice con él cómo está la cadena y el vigía
    (app/latido.py) avisa por Telegram si deja de llegar."""
    datos = entrada.model_dump()
    datos["hora"] = _a_utc(entrada.hora)
    for campo in ("ultimoFlujo", "perdidaCapturaEn"):
        if datos[campo] is not None:
            datos[campo] = _a_utc(datos[campo])
    with _base(request).transaccion() as con:
        recibido = latido.guardar(con, datos)
    return LatidoResultado(recibidoEn=recibido)


# ---- Tráfico del sensor para el tablero web (decisión 49) ----


@router.post("/ingesta/trafico", response_model=TraficoResultado)
def ingesta_trafico(entrada: TraficoEntrada, request: Request, _: None = Depends(verificar_token_ingesta)) -> TraficoResultado:
    """El resumen del tráfico que manda el sensor cada 5 segundos, con el mismo token que la ingesta de
    alertas. Se suma a lo que ya había (una conexión larga completa tramos viejos) y, de paso, se borra lo
    que tiene más de `dias_trafico` días."""
    cfg = request.app.state.config
    limite = int(ahora().timestamp()) - cfg.dias_trafico * 86400
    with _base(request).transaccion() as con:
        trafico.guardar(con, entrada.tramos, entrada.pares)
        trafico.podar(con, limite)
    return TraficoResultado(tramos=len(entrada.tramos), pares=len(entrada.pares))


def _ventana(con: sqlite3.Connection, desde: int | None, hasta: int | None, paso: int, por_puntos: bool = True) -> tuple[int, int]:
    """La ventana pedida, redondeada al paso. Sin `hasta`, llega hasta ahora o hasta el último tramo si el
    reloj del sensor va adelantado; sin `desde`, es la última hora. 422 si está al revés o, en una serie,
    si tiene demasiados puntos."""
    if hasta is None:
        ultimo = trafico.ultimo_tramo(con)
        hasta = max(int(ahora().timestamp()), (ultimo or 0) + 1)
    hasta = -(-hasta // paso) * paso
    desde = hasta - 3600 if desde is None else desde - desde % paso
    if desde >= hasta:
        raise HTTPException(status_code=422, detail="La ventana está al revés: desde tiene que ser anterior a hasta")
    if por_puntos and (hasta - desde) // paso > trafico.MAX_PUNTOS:
        raise HTTPException(status_code=422, detail=f"Demasiados puntos: con ese paso, la ventana pasa de {trafico.MAX_PUNTOS}")
    return desde, hasta


@router.get("/trafico", response_model=SerieTrafico)
def ver_trafico(
    request: Request,
    dispositivo: sqlite3.Row = Depends(dispositivo_actual),
    desde: int | None = None,
    hasta: int | None = None,
    paso: int = 5,
) -> SerieTrafico:
    """El tráfico total y por servicio en tramos de `paso` segundos, para los gráficos del tablero.
    `desde` y `hasta` en segundos desde 1970; solo vienen los tramos con tráfico."""
    if paso not in trafico.PASOS:
        raise HTTPException(status_code=422, detail=f"El paso tiene que ser uno de {', '.join(map(str, trafico.PASOS))} segundos")
    con = _base(request).conexion()
    desde, hasta = _ventana(con, desde, hasta, paso)
    total, servicios = trafico.serie(con, desde, hasta, paso)
    return SerieTrafico(
        desde=desde,
        hasta=hasta,
        paso=paso,
        horaServidor=iso(ahora()),
        ultimoTramo=trafico.ultimo_tramo(con),
        total=[PuntoTrafico(**p) for p in total],
        servicios={s: [PuntoTrafico(**p) for p in puntos] for s, puntos in servicios.items()},
    )


@router.get("/trafico/pares", response_model=ParesTrafico)
def ver_pares(
    request: Request,
    dispositivo: sqlite3.Row = Depends(dispositivo_actual),
    desde: int | None = None,
    hasta: int | None = None,
) -> ParesTrafico:
    """Quién habló con quién en la ventana (por defecto, la última hora), con el nombre de cada equipo del
    catálogo: el mapa de la red del tablero."""
    con = _base(request).conexion()
    desde, hasta = _ventana(con, desde, hasta, 60, por_puntos=False)
    return ParesTrafico(
        desde=desde,
        hasta=hasta,
        pares=[
            ParDeTrafico(**p, origenNombre=nombre_equipo(con, p["origen"]), destinoNombre=nombre_equipo(con, p["destino"]))
            for p in trafico.pares(con, desde, hasta)
        ],
    )
