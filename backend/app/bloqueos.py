"""Bloqueos del origen de una alerta en el firewall (decisión 51), siempre con una persona que decide.

Un administrador bloquea desde su teléfono el origen de una alerta, con un motivo y una duración; un analista
o el tablero web lo piden, y lo aprueba un administrador desde su teléfono. El servidor no guarda ninguna clave
del firewall: el agente del firewall pregunta cada pocos segundos qué tiene que bloquear (GET
/firewall/bloqueos, con su propio token), lo valida contra su lista de «nunca bloquear» y sus topes, lo aplica
y cuenta qué hizo (POST /firewall/informe). Un bloqueo dura 1 o 24 horas, o hasta que alguien lo desbloquea;
siempre se puede desbloquear antes. Sin ZONDA_FIREWALL_TOKEN los bloqueos están apagados y la red queda en
modo de observación.
"""

import ipaddress
import json
import math
import secrets
import sqlite3
import uuid
from datetime import datetime, timedelta
from typing import Annotated

from fastapi import APIRouter, Depends, Header, HTTPException, Request

from . import registro
from .auth import administrador_actual, ahora, dispositivo_actual, iso, nombre_de
from .config import Config
from .db import Base
from .modelos import (
    Bloqueo,
    BloqueoEntrada,
    BloqueoFirewall,
    DuracionEntrada,
    EstadoFirewall,
    InformeFirewall,
    InformeResultado,
    ListaBloqueos,
    ListaFirewall,
    MotivoEntrada,
)

router = APIRouter()

# Lo que está en el firewall: aprobado (todavía no dijo que lo aplicó) y aplicado. Un pedido espera a un
# administrador; rechazado, desbloqueado y vencido ya terminaron.
EN_EL_FIREWALL = ("aprobado", "aplicado")
# Un pedido que ningún administrador aprobó en una hora deja de valer: el ataque pudo haber cambiado.
PEDIDO_VALE = timedelta(hours=1)
# Quién figura cuando el que rechaza un bloqueo es el firewall.
AUTOR_FIREWALL = "Firewall"

APAGADOS = "Los bloqueos están apagados: esta red no tiene un firewall conectado"
EN_LA_LISTA = "El firewall nunca bloquea ese equipo: está en su lista de «nunca bloquear»"
NO_PENDIENTE = "Ese pedido ya no está pendiente"


def _base(request: Request) -> Base:
    return request.app.state.base


def _config(request: Request) -> Config:
    return request.app.state.config


def _exigir_prendidos(request: Request) -> None:
    if not _config(request).firewall_token:
        raise HTTPException(status_code=503, detail=APAGADOS)


def _nombre_equipo(con: sqlite3.Connection, ip: str) -> str | None:
    fila = con.execute("SELECT nombre FROM catalogo_equipos WHERE ip = ?", (ip,)).fetchone()
    return fila["nombre"] if fila else None


def bloqueo_dto(con: sqlite3.Connection, fila: sqlite3.Row) -> Bloqueo:
    return Bloqueo(
        id=fila["id"],
        alertaId=fila["alerta_id"],
        ip=fila["ip"],
        nombre=_nombre_equipo(con, fila["ip"]),
        estado=fila["estado"],
        duracion=fila["duracion"],
        motivo=fila["motivo"],
        pedidoPorId=fila["pedido_por_id"],
        pedidoPor=fila["pedido_por"],
        pedidoEn=fila["pedido_en"],
        aprobadoPorId=fila["aprobado_por_id"],
        aprobadoPor=fila["aprobado_por"],
        aprobadoEn=fila["aprobado_en"],
        venceEn=fila["vence_en"],
        aplicadoEn=fila["aplicado_en"],
        terminadoPorId=fila["terminado_por_id"],
        terminadoPor=fila["terminado_por"],
        terminadoEn=fila["terminado_en"],
        detalle=fila["detalle"],
    )


def poner_al_dia(con: sqlite3.Connection) -> None:
    """Da por vencidos los bloqueos cuyo vencimiento pasó (el firewall ya los borró solo) y los pedidos que
    nadie aprobó en una hora."""
    momento = ahora()
    con.execute(
        "UPDATE bloqueos SET estado = 'vencido', terminado_en = vence_en "
        "WHERE estado IN ('aprobado', 'aplicado') AND vence_en IS NOT NULL AND vence_en <= ?",
        (iso(momento),),
    )
    con.execute(
        "UPDATE bloqueos SET estado = 'vencido', terminado_en = ? WHERE estado = 'pedido' AND pedido_en <= ?",
        (iso(momento), iso(momento - PEDIDO_VALE)),
    )


def nunca_bloquear(base: Base) -> list[str]:
    """Las redes que el firewall dijo en su último informe que nunca bloquea."""
    return json.loads(base.leer_meta("firewall_nunca_bloquear", "[]"))


def _en_la_lista(ip: str, redes: list[str]) -> bool:
    direccion = ipaddress.ip_address(ip)
    return any(direccion in ipaddress.ip_network(red) for red in redes)


def ip_bloqueable(texto: str) -> str | None:
    """La IP de un equipo, que tiene sentido bloquear: IPv4 (IPv6 queda afuera, como en la detección), y ni
    difusión, ni multidifusión, ni la dirección vacía, ni la propia máquina, ni una de enlace local."""
    try:
        ip = ipaddress.ip_address(texto)
    except ValueError:
        return None
    if ip.version != 4 or ip.is_multicast or ip.is_unspecified or ip.is_loopback or ip.is_link_local or ip.is_reserved:
        return None
    return str(ip)


def estado_firewall(base: Base, cfg: Config) -> EstadoFirewall:
    ultima = base.leer_meta("firewall_consulta")
    conectado = ultima is not None and ahora() - datetime.fromisoformat(ultima) <= timedelta(seconds=cfg.firewall_limite)
    return EstadoFirewall(habilitado=bool(cfg.firewall_token), conectado=conectado, ultimaConsulta=ultima, nuncaBloquear=nunca_bloquear(base))


def lo_hace_efectivo(dispositivo: sqlite3.Row) -> bool:
    """Un bloqueo solo lo hace efectivo un administrador desde su teléfono. El tablero web y los analistas lo
    piden: bloquear es lo único que corta tráfico, y el acceso de un navegador dura 90 días en esa computadora."""
    return dispositivo["perfil"] == "administrador" and dispositivo["tipo"] == "telefono"


def texto_duracion(duracion: int | None) -> str:
    """Cómo queda la duración en el registro de cambios: los segundos, o hasta_desbloquear."""
    return "hasta_desbloquear" if duracion is None else str(duracion)


def _vence(desde: datetime, duracion: int | None) -> str | None:
    return None if duracion is None else iso(desde + timedelta(seconds=duracion))


def _bloqueo(con: sqlite3.Connection, bloqueo_id: str) -> sqlite3.Row:
    fila = con.execute("SELECT * FROM bloqueos WHERE id = ?", (bloqueo_id,)).fetchone()
    if fila is None:
        raise HTTPException(status_code=404, detail="No existe ese bloqueo")
    return fila


def _terminar(con: sqlite3.Connection, bloqueo_id: str, estado: str, autor: sqlite3.Row, detalle: str) -> None:
    con.execute(
        "UPDATE bloqueos SET estado = ?, terminado_por_id = ?, terminado_por = ?, terminado_en = ?, detalle = ? WHERE id = ?",
        (estado, autor["id"], nombre_de(autor), iso(ahora()), detalle, bloqueo_id),
    )


# ---- Lo que hace la gente, con el token de su dispositivo ----


@router.get("/bloqueos", response_model=ListaBloqueos)
def listar(request: Request, dispositivo: sqlite3.Row = Depends(dispositivo_actual), alerta: str | None = None) -> ListaBloqueos:
    """Los bloqueos de la red (o los de una alerta), de lo más nuevo a lo más viejo, y cómo está el firewall:
    si los bloqueos están prendidos, si el firewall pregunta y qué nunca bloquea. Lo ve todo el equipo."""
    base = _base(request)
    with base.transaccion() as con:
        poner_al_dia(con)
        sql = "SELECT * FROM bloqueos" + (" WHERE alerta_id = ?" if alerta is not None else "") + " ORDER BY pedido_en DESC, rowid DESC"
        filas = con.execute(sql, (alerta,) if alerta is not None else ()).fetchall()
        bloqueos = [bloqueo_dto(con, fila) for fila in filas]
    return ListaBloqueos(bloqueos=bloqueos, firewall=estado_firewall(base, _config(request)))


@router.post("/bloqueos", response_model=Bloqueo, status_code=201)
def bloquear(entrada: BloqueoEntrada, request: Request, dispositivo: sqlite3.Row = Depends(dispositivo_actual)) -> Bloqueo:
    """Bloquea el origen de una alerta. Un administrador desde su teléfono lo deja aprobado en el acto; un
    analista o el tablero web lo piden. Idempotente por accionId. 404 sin la alerta; 409 si ese equipo ya
    tiene un bloqueo o un pedido, o con el tope de bloqueos activos; 422 si el origen no es un equipo o está en
    la lista de «nunca bloquear»; 429 con demasiados en una hora; 503 si están apagados."""
    _exigir_prendidos(request)
    cfg = _config(request)
    base = _base(request)
    with base.transaccion() as con:
        poner_al_dia(con)
        repetido = con.execute("SELECT * FROM bloqueos WHERE accion_id = ?", (entrada.accionId,)).fetchone()
        if repetido is not None:
            return bloqueo_dto(con, repetido)
        alerta = con.execute("SELECT * FROM alertas WHERE id = ?", (entrada.alertaId,)).fetchone()
        if alerta is None:
            raise HTTPException(status_code=404, detail="No existe esa alerta")
        ip = ip_bloqueable(alerta["origen_ip"])
        if ip is None:
            raise HTTPException(status_code=422, detail="Ese origen no es la dirección de un equipo: no se puede bloquear")
        if _en_la_lista(ip, nunca_bloquear(base)):
            raise HTTPException(status_code=422, detail=EN_LA_LISTA)
        if con.execute("SELECT 1 FROM bloqueos WHERE ip = ? AND estado IN ('pedido', 'aprobado', 'aplicado')", (ip,)).fetchone():
            raise HTTPException(status_code=409, detail="Ese equipo ya tiene un bloqueo o un pedido de bloqueo")
        activos = con.execute("SELECT COUNT(*) FROM bloqueos WHERE estado IN ('pedido', 'aprobado', 'aplicado')").fetchone()[0]
        if activos >= cfg.max_bloqueos:
            raise HTTPException(status_code=409, detail=f"Ya hay {activos} bloqueos activos, el tope de esta red")
        momento = ahora()
        recientes = con.execute(
            "SELECT COUNT(*) FROM bloqueos WHERE pedido_por_id = ? AND pedido_en > ?",
            (dispositivo["id"], iso(momento - timedelta(hours=1))),
        ).fetchone()[0]
        if recientes >= cfg.bloqueos_por_hora:
            raise HTTPException(status_code=429, detail="Demasiados bloqueos en una hora desde este dispositivo")
        efectivo = lo_hace_efectivo(dispositivo)
        bloqueo_id = "b-" + uuid.uuid4().hex[:12]
        persona = nombre_de(dispositivo)
        aprobado = (dispositivo["id"], persona, iso(momento), _vence(momento, entrada.duracion)) if efectivo else (None, None, None, None)
        con.execute(
            "INSERT INTO bloqueos (id, alerta_id, ip, accion_id, estado, duracion, motivo, fue_pedido, pedido_por_id, pedido_por, "
            "pedido_en, aprobado_por_id, aprobado_por, aprobado_en, vence_en) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
            (
                bloqueo_id,
                alerta["id"],
                ip,
                entrada.accionId,
                "aprobado" if efectivo else "pedido",
                entrada.duracion,
                entrada.motivo,
                int(not efectivo),
                dispositivo["id"],
                persona,
                iso(momento),
                *aprobado,
            ),
        )
        if efectivo:
            registro.anotar(con, "bloqueo", dispositivo, objeto=ip, nombre=_nombre_equipo(con, ip), despues=texto_duracion(entrada.duracion), motivo=entrada.motivo)
        return bloqueo_dto(con, _bloqueo(con, bloqueo_id))


@router.post("/bloqueos/{bloqueo_id}/aprobacion", response_model=Bloqueo)
def aprobar(bloqueo_id: str, request: Request, administrador: sqlite3.Row = Depends(administrador_actual)) -> Bloqueo:
    """Un administrador aprueba desde su teléfono un pedido de bloqueo: la duración corre desde ahora. 403
    desde el tablero web; 409 si el pedido ya no está pendiente (lo aprobó otra persona, se rechazó o venció)."""
    _exigir_prendidos(request)
    if administrador["tipo"] != "telefono":
        raise HTTPException(status_code=403, detail="Un bloqueo se aprueba desde el teléfono de un administrador")
    base = _base(request)
    with base.transaccion() as con:
        poner_al_dia(con)
        fila = _bloqueo(con, bloqueo_id)
        if fila["estado"] != "pedido":
            raise HTTPException(status_code=409, detail=NO_PENDIENTE)
        if _en_la_lista(fila["ip"], nunca_bloquear(base)):
            raise HTTPException(status_code=422, detail=EN_LA_LISTA)
        momento = ahora()
        con.execute(
            "UPDATE bloqueos SET estado = 'aprobado', aprobado_por_id = ?, aprobado_por = ?, aprobado_en = ?, vence_en = ? WHERE id = ?",
            (administrador["id"], nombre_de(administrador), iso(momento), _vence(momento, fila["duracion"]), bloqueo_id),
        )
        registro.anotar(con, "bloqueo", administrador, objeto=fila["ip"], nombre=_nombre_equipo(con, fila["ip"]), despues=texto_duracion(fila["duracion"]), motivo=fila["motivo"])
        return bloqueo_dto(con, _bloqueo(con, bloqueo_id))


@router.post("/bloqueos/{bloqueo_id}/rechazo", response_model=Bloqueo)
def rechazar(bloqueo_id: str, entrada: MotivoEntrada, request: Request, administrador: sqlite3.Row = Depends(administrador_actual)) -> Bloqueo:
    """Un administrador rechaza un pedido de bloqueo, con su motivo. 409 si ya no está pendiente."""
    _exigir_prendidos(request)
    with _base(request).transaccion() as con:
        poner_al_dia(con)
        if _bloqueo(con, bloqueo_id)["estado"] != "pedido":
            raise HTTPException(status_code=409, detail=NO_PENDIENTE)
        _terminar(con, bloqueo_id, "rechazado", administrador, entrada.motivo)
        return bloqueo_dto(con, _bloqueo(con, bloqueo_id))


@router.post("/bloqueos/{bloqueo_id}/desbloqueo", response_model=Bloqueo)
def desbloquear(bloqueo_id: str, entrada: MotivoEntrada, request: Request, administrador: sqlite3.Row = Depends(administrador_actual)) -> Bloqueo:
    """Un administrador desbloquea antes del vencimiento, con su motivo; también desde el tablero web, porque
    devuelve el tráfico. El firewall lo saca en su consulta siguiente. Si ya terminó, no cambia nada. 409 si
    todavía es un pedido: un pedido se rechaza."""
    _exigir_prendidos(request)
    with _base(request).transaccion() as con:
        poner_al_dia(con)
        fila = _bloqueo(con, bloqueo_id)
        if fila["estado"] == "pedido":
            raise HTTPException(status_code=409, detail="Todavía es un pedido: se rechaza, no se desbloquea")
        if fila["estado"] in EN_EL_FIREWALL:
            _terminar(con, bloqueo_id, "desbloqueado", administrador, entrada.motivo)
            registro.anotar(con, "desbloqueo", administrador, objeto=fila["ip"], nombre=_nombre_equipo(con, fila["ip"]), antes=texto_duracion(fila["duracion"]), motivo=entrada.motivo)
        return bloqueo_dto(con, _bloqueo(con, bloqueo_id))


@router.put("/bloqueos/{bloqueo_id}/duracion", response_model=Bloqueo)
def cambiar_duracion(bloqueo_id: str, entrada: DuracionEntrada, request: Request, administrador: sqlite3.Row = Depends(administrador_actual)) -> Bloqueo:
    """Cuánto dura desde ahora un bloqueo vigente, con su motivo; sin duración queda hasta desbloquear. El
    firewall lo vuelve a aplicar con la duración nueva; hasta que lo confirma, queda aprobado. 409 si no está
    vigente."""
    _exigir_prendidos(request)
    with _base(request).transaccion() as con:
        poner_al_dia(con)
        fila = _bloqueo(con, bloqueo_id)
        if fila["estado"] not in EN_EL_FIREWALL:
            raise HTTPException(status_code=409, detail="Ese bloqueo no está vigente")
        con.execute(
            "UPDATE bloqueos SET estado = 'aprobado', aplicado_en = NULL, duracion = ?, vence_en = ? WHERE id = ?",
            (entrada.duracion, _vence(ahora(), entrada.duracion), bloqueo_id),
        )
        registro.anotar(
            con,
            "bloqueo",
            administrador,
            objeto=fila["ip"],
            nombre=_nombre_equipo(con, fila["ip"]),
            antes=texto_duracion(fila["duracion"]),
            despues=texto_duracion(entrada.duracion),
            motivo=entrada.motivo,
        )
        return bloqueo_dto(con, _bloqueo(con, bloqueo_id))


# ---- Lo que hace el agente del firewall, con su propio token ----


def verificar_token_firewall(request: Request, x_token_firewall: Annotated[str | None, Header()] = None) -> None:
    """El agente del firewall se autentica con su propio token (ZONDA_FIREWALL_TOKEN), distinto del de los
    dispositivos y del de la ingesta, en la cabecera X-Token-Firewall. Solo sirve para leer los bloqueos
    aprobados y contar qué hizo. 503 sin token configurado; 401 si no coincide."""
    esperado = _config(request).firewall_token
    if not esperado:
        raise HTTPException(status_code=503, detail=APAGADOS)
    if not x_token_firewall or not secrets.compare_digest(x_token_firewall, esperado):
        raise HTTPException(status_code=401, detail="Token del firewall inválido")


def _segundos(vence_en: str | None, momento: datetime) -> int | None:
    if vence_en is None:
        return None
    return max(1, math.ceil((datetime.fromisoformat(vence_en) - momento).total_seconds()))


@router.get("/firewall/bloqueos", response_model=ListaFirewall)
def para_el_firewall(request: Request, _: None = Depends(verificar_token_firewall)) -> ListaFirewall:
    """Lo que el firewall tiene que tener bloqueado ahora: cada IP aprobada y los segundos que le quedan (null:
    hasta desbloquear). El agente agrega lo que falta y saca lo que ya no está. Anota cuándo preguntó."""
    with _base(request).transaccion() as con:
        poner_al_dia(con)
        momento = ahora()
        Base.escribir_meta(con, "firewall_consulta", iso(momento))
        filas = con.execute("SELECT * FROM bloqueos WHERE estado IN ('aprobado', 'aplicado') ORDER BY aprobado_en, rowid").fetchall()
    return ListaFirewall(
        bloqueos=[BloqueoFirewall(id=f["id"], ip=f["ip"], segundos=_segundos(f["vence_en"], momento)) for f in filas],
        horaServidor=iso(momento),
    )


@router.post("/firewall/informe", response_model=InformeResultado)
def informe_del_firewall(entrada: InformeFirewall, request: Request, _: None = Depends(verificar_token_firewall)) -> InformeResultado:
    """Lo que hizo el firewall en su última vuelta: qué bloqueos aplicó, cuáles rechazó y por qué, y su lista de
    «nunca bloquear», que el servidor usa para no aceptar esos equipos y la app para no ofrecer el botón."""
    aplicados = rechazados = 0
    with _base(request).transaccion() as con:
        poner_al_dia(con)
        momento = iso(ahora())
        Base.escribir_meta(con, "firewall_consulta", momento)
        if entrada.nuncaBloquear is not None:
            Base.escribir_meta(con, "firewall_nunca_bloquear", json.dumps(entrada.nuncaBloquear))
        for bloqueo_id in dict.fromkeys(entrada.aplicados):
            aplicados += con.execute(
                "UPDATE bloqueos SET estado = 'aplicado', aplicado_en = ? WHERE id = ? AND estado = 'aprobado'", (momento, bloqueo_id)
            ).rowcount
        for rechazo in entrada.rechazados:
            rechazados += con.execute(
                "UPDATE bloqueos SET estado = 'rechazado', terminado_por = ?, terminado_en = ?, detalle = ? "
                "WHERE id = ? AND estado IN ('aprobado', 'aplicado')",
                (AUTOR_FIREWALL, momento, rechazo.motivo, rechazo.id),
            ).rowcount
    return InformeResultado(aplicados=aplicados, rechazados=rechazados)
