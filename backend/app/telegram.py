"""Bot de Telegram (informe, secciones 6, 13.3 y 13.4): vincula el chat de la persona con su teléfono
y le manda el aviso de cada alerta nueva.

Habla con la API de bots de Telegram por HTTP, con httpx y sin una biblioteca aparte: getUpdates con
long polling para recibir "/start <código>" y sendMessage para los avisos. Corre dentro de la API, en
dos tareas de asyncio, solo si TELEGRAM_BOT_TOKEN está en .env.

Qué viaja por Telegram (RNF02): la severidad, el servicio y la hora de cada alerta, y de qué red
viene. Nunca direcciones IP ni nombres de equipos: el resto se ve en la app, por el canal cifrado.
El enlace lleva el id de la alerta y lo mismo que dice el aviso, para que la app lo muestre aunque
todavía no tenga el detalle (RF03, criterio 3).

El token es una contraseña: no va a los registros ni a los mensajes de error.
"""

import asyncio
import html
import logging
import sqlite3
from dataclasses import dataclass
from datetime import date, datetime, timezone, tzinfo
from urllib.parse import urlencode

import httpx

from .auth import ahora, iso, normalizar_codigo
from .config import Config
from .db import Base
from .semilla import severidad_de

registro = logging.getLogger("zonda.telegram")
# httpx anota cada pedido con su URL, y la URL de la API de Telegram lleva el token.
logging.getLogger("httpx").setLevel(logging.WARNING)
logging.getLogger("httpcore").setLevel(logging.WARNING)

ESPERA_NOVEDADES = 25  # segundos que Telegram retiene getUpdates si no llegan mensajes (long polling)
REVISAR_CADA = 5  # segundos entre una búsqueda de alertas nuevas y la siguiente
TANDA = 50  # avisos por vuelta, como máximo

ETIQUETA_SEVERIDAD = {"alta": "ALTA", "media": "MEDIA", "baja": "BAJA"}
BOTON_DETALLE = "Ver el detalle en Zonda"

# Lo que responde el bot. Los textos de los avisos están en texto_del_aviso.
TEXTO_VINCULADO = (
    "Listo: este chat recibe los avisos de {red}. Cada aviso trae solo la severidad, el servicio y la hora, "
    "nunca direcciones IP ni nombres de equipos."
)
TEXTO_YA_VINCULADO = "Este chat recibe los avisos de {red}. Cómo avisa cada severidad se cambia desde Zonda → Ajustes → Avisos por severidad."
TEXTO_CODIGO_INVALIDO = "Ese código no sirve: venció o ya se usó. Volvé a Zonda y tocá Abrir Telegram de nuevo."
TEXTO_SIN_CODIGO = "Este bot manda los avisos de Zonda. Para recibirlos, conectá Telegram desde la app: Ajustes → Telegram → Conectar Telegram."
TEXTO_SOLO_PRIVADO = "Los avisos de Zonda llegan a un chat privado con el bot, no a un grupo."
TEXTO_PRUEBA = "<b>Aviso de prueba</b>\nLos avisos de {red} llegan a este chat."
TEXTO_DESVINCULADO = "Este chat ya no recibe los avisos de {red}. Para volver a recibirlos, conectá Telegram desde Zonda."


class ErrorTelegram(Exception):
    """Un pedido a Telegram que no salió. El mensaje nunca lleva la URL, que incluye el token."""

    def __init__(self, metodo: str, codigo: int | None, descripcion: str, reintentar_en: int | None = None) -> None:
        super().__init__(f"{metodo}: {codigo or 'sin respuesta'} {descripcion}".strip())
        self.codigo = codigo
        self.reintentar_en = reintentar_en

    @property
    def chat_perdido(self) -> bool:
        """La persona bloqueó el bot o borró el chat: no tiene sentido seguir mandándole avisos."""
        return self.codigo == 403 or (self.codigo == 400 and "chat not found" in str(self).lower())


class ClienteTelegram:
    """Los tres métodos de la API de bots que usa Zonda: getMe, getUpdates y sendMessage."""

    def __init__(self, token: str, api: str = "https://api.telegram.org", transporte: httpx.AsyncBaseTransport | None = None) -> None:
        self._token = token
        # La lectura espera más que el long polling: si no, cada getUpdates sin mensajes terminaría en error.
        self._http = httpx.AsyncClient(base_url=api, transport=transporte, timeout=httpx.Timeout(10, read=ESPERA_NOVEDADES + 10))

    async def _llamar(self, metodo: str, datos: dict | None = None):
        try:
            respuesta = await self._http.post(f"/bot{self._token}/{metodo}", json=datos or {})
            cuerpo = respuesta.json()
        except (httpx.HTTPError, ValueError):
            # Sin conexión o una respuesta que no es JSON. "from None" descarta la excepción original,
            # que lleva la URL con el token.
            raise ErrorTelegram(metodo, None, "Telegram no respondió") from None
        if not cuerpo.get("ok"):
            parametros = cuerpo.get("parameters") or {}
            raise ErrorTelegram(metodo, cuerpo.get("error_code"), cuerpo.get("description", ""), parametros.get("retry_after"))
        return cuerpo.get("result")

    async def yo(self) -> dict:
        return await self._llamar("getMe")

    async def novedades(self, desde: int) -> list[dict]:
        return await self._llamar("getUpdates", {"offset": desde, "timeout": ESPERA_NOVEDADES, "allowed_updates": ["message"]})

    async def enviar(self, chat_id: str, texto: str, boton: tuple[str, str] | None = None, silencioso: bool = False) -> None:
        datos = {
            "chat_id": chat_id,
            "text": texto,
            "parse_mode": "HTML",
            # Sin la vista previa del enlace: el aviso queda corto y la página no se pide sola.
            "link_preview_options": {"is_disabled": True},
            "disable_notification": silencioso,
        }
        if boton is not None:
            datos["reply_markup"] = {"inline_keyboard": [[{"text": boton[0], "url": boton[1]}]]}
        await self._llamar("sendMessage", datos)

    async def cerrar(self) -> None:
        await self._http.aclose()


def texto_del_aviso(severidad: str, servicio: str, deteccion: datetime, red: str, zona: tzinfo | None = None, hoy: date | None = None) -> str:
    """El aviso de una alerta: severidad, servicio y hora de detección, y de qué red viene.

        Alerta ALTA · SSH · 02:09
        Tocá para ver el detalle en Zonda.
        Red: Oficina central

    La hora es la local del servidor (zona None); si la alerta no es de hoy, también va la fecha.
    """
    local = deteccion.astimezone(zona)
    hora = local.strftime("%H:%M")
    if local.date() != (hoy or datetime.now(zona).date()):
        hora = local.strftime("%d/%m %H:%M")
    titulo = f"Alerta {ETIQUETA_SEVERIDAD[severidad]} · {servicio.upper()} · {hora}"
    return f"<b>{html.escape(titulo)}</b>\nTocá para ver el detalle en Zonda.\nRed: {html.escape(red)}"


def texto_de_asignacion(por: str, severidad: str, servicio: str, deteccion: datetime, zona: tzinfo | None = None, hoy: date | None = None) -> str:
    """El aviso a quien le asignan una alerta (decisión 42), con la misma hora que el aviso de la alerta:

        Taiel te asignó una alerta ALTA · SSH · 09:51
        Tocá para ver el detalle en Zonda.
    """
    local = deteccion.astimezone(zona)
    hora = local.strftime("%H:%M")
    if local.date() != (hoy or datetime.now(zona).date()):
        hora = local.strftime("%d/%m %H:%M")
    titulo = f"{por} te asignó una alerta {ETIQUETA_SEVERIDAD[severidad]} · {servicio.upper()} · {hora}"
    return f"<b>{html.escape(titulo)}</b>\nTocá para ver el detalle en Zonda."


# Lo que dice el aviso de cada momento de un bloqueo (decisión 51). Nunca la IP ni el nombre del equipo.
TITULO_BLOQUEO = {
    "pedido": "{persona} pidió bloquear el origen de una alerta {alerta}",
    "aprobado": "{persona} bloqueó el origen de una alerta {alerta}",
    "desbloqueado": "{persona} desbloqueó el origen de una alerta {alerta}",
    "rechazado": "El firewall no aplicó el bloqueo de una alerta {alerta}",
}
DURACION_BLOQUEO = {3600: "1 h", 86400: "24 h"}


def texto_de_bloqueo(
    evento: str, persona: str, duracion: int | None, severidad: str, servicio: str, deteccion: datetime, zona: tzinfo | None = None, hoy: date | None = None
) -> str:
    """El aviso a quienes bloquean (los administradores y los analistas con «Bloquea») de cada bloqueo
    (decisión 51), con la misma hora que el aviso de la alerta:

        Taiel bloqueó el origen de una alerta ALTA · SSH · 09:51
        Por 1 h. Tocá para ver el detalle en Zonda.

    Sale cuando se pide, cuando se aprueba, cuando se desbloquea y cuando el firewall no lo aplica: un
    bloqueo hecho con un dispositivo ajeno se ve enseguida.
    """
    local = deteccion.astimezone(zona)
    hora = local.strftime("%H:%M")
    if local.date() != (hoy or datetime.now(zona).date()):
        hora = local.strftime("%d/%m %H:%M")
    titulo = TITULO_BLOQUEO[evento].format(persona=persona, alerta=f"{ETIQUETA_SEVERIDAD[severidad]} · {servicio.upper()} · {hora}")
    if evento == "pedido":
        cuerpo = "Lo aprueba desde Zonda, en su teléfono, alguien que puede bloquear."
    elif evento == "aprobado":
        cuanto = "Hasta que alguien lo desbloquee." if duracion is None else f"Por {DURACION_BLOQUEO.get(duracion, f'{duracion // 60} min')}."
        cuerpo = f"{cuanto} Tocá para ver el detalle en Zonda."
    elif evento == "rechazado":
        cuerpo = "Tocá para ver por qué en Zonda."
    else:
        cuerpo = "Tocá para ver el detalle en Zonda."
    return f"<b>{html.escape(titulo)}</b>\n{html.escape(cuerpo)}"


def _utc(momento: datetime) -> str:
    return momento.astimezone(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")


def enlace_del_aviso(base: str, alerta_id: str, red_id: str, severidad: str, servicio: str, deteccion: datetime, aviso: datetime) -> str:
    """El enlace que abre la alerta en la app (App Link, decisión 9). Además del id lleva lo mismo que
    dice el aviso: si la alerta todavía no está en el teléfono, el detalle lo muestra como "Lo que
    trajo el aviso" (RF03, criterio 3). Nada que no esté en el aviso."""
    consulta = urlencode(
        {"red": red_id, "severidad": severidad, "servicio": servicio, "deteccion": _utc(deteccion), "aviso": _utc(aviso)},
        safe=":",
    )
    return f"{base}{alerta_id}?{consulta}"


@dataclass
class Pendiente:
    """Un aviso que falta mandar: una alerta nueva para un chat vinculado."""

    alerta_id: str
    chat_id: str
    modo: str
    texto: str
    enlace: str


@dataclass
class AvisoDeAsignacion:
    """Un aviso que falta mandar: la alerta que otra persona le asignó a alguien con Telegram."""

    asignacion_id: int
    chat_id: str
    texto: str
    enlace: str


@dataclass
class AvisoDeBloqueo:
    """Un aviso que falta mandar: un momento de un bloqueo, para alguien que bloquea, con Telegram."""

    bloqueo_id: str
    evento: str
    chat_id: str
    texto: str
    enlace: str


class BotTelegram:
    """El bot de la red: atiende "/start <código>" y manda los avisos. Las rutas lo usan para el
    aviso de prueba y para despedirse al desvincular."""

    def __init__(self, base: Base, cfg: Config, cliente: ClienteTelegram, zona: tzinfo | None = None, pausa: float = 1.0) -> None:
        self.base = base
        self.cfg = cfg
        self.cliente = cliente
        self.zona = zona  # None: la hora local de la computadora del servidor
        self.pausa = pausa  # segundos entre avisos, para no pasar el límite de mensajes de Telegram
        self.usuario = cfg.bot_usuario
        self.presentado = False

    async def presentarse(self) -> None:
        """Pregunta a Telegram el usuario del bot: el enlace de vinculación no depende de ZONDA_TELEGRAM_BOT."""
        yo = await self.cliente.yo()
        self.usuario = yo.get("username") or self.usuario
        self.presentado = True

    def enlace_de_vinculo(self, codigo: str) -> str:
        """Abre el chat con el bot; al tocar Iniciar, Telegram le manda "/start <código>"."""
        return f"https://t.me/{self.usuario}?start={codigo}"

    # ---- Vínculo ----

    async def atender(self, novedad: dict) -> None:
        mensaje = novedad.get("message") or {}
        chat = mensaje.get("chat") or {}
        texto = (mensaje.get("text") or "").strip()
        if not texto or "id" not in chat:
            return
        chat_id = str(chat["id"])
        comando, _, argumento = texto.partition(" ")
        es_start = comando.split("@")[0] == "/start"
        red = html.escape(self.cfg.red_nombre)
        if chat.get("type") != "private":
            # En un grupo los avisos los leería cualquiera del grupo.
            if es_start:
                await self.cliente.enviar(chat_id, TEXTO_SOLO_PRIVADO)
            return
        if not es_start or not argumento.strip():
            vinculado = self.base.conexion().execute(
                "SELECT 1 FROM dispositivos WHERE telegram_chat_id = ? AND revocado_en IS NULL", (chat_id,)
            ).fetchone()
            await self.cliente.enviar(chat_id, TEXTO_YA_VINCULADO.format(red=red) if vinculado else TEXTO_SIN_CODIGO)
            return
        if self.vincular(argumento.strip(), chat_id):
            await self.cliente.enviar(chat_id, TEXTO_VINCULADO.format(red=red))
        else:
            await self.cliente.enviar(chat_id, TEXTO_CODIGO_INVALIDO)

    def vincular(self, codigo: str, chat_id: str) -> bool:
        """Canjea el código de un uso (10 minutos) y asocia el chat al teléfono que lo pidió."""
        momento = ahora()
        with self.base.transaccion() as con:
            fila = con.execute("SELECT * FROM codigos_telegram WHERE codigo = ?", (normalizar_codigo(codigo),)).fetchone()
            if fila is None or fila["usado_en"] is not None or datetime.fromisoformat(fila["vence_en"]) < momento:
                return False
            dispositivo = con.execute("SELECT * FROM dispositivos WHERE id = ? AND revocado_en IS NULL", (fila["dispositivo_id"],)).fetchone()
            if dispositivo is None or datetime.fromisoformat(dispositivo["vence_en"]) < momento:
                return False
            con.execute("UPDATE codigos_telegram SET usado_en = ? WHERE codigo = ?", (iso(momento), fila["codigo"]))
            con.execute(
                "UPDATE dispositivos SET telegram_chat_id = ?, telegram_vinculado_en = ? WHERE id = ?",
                (chat_id, iso(momento), dispositivo["id"]),
            )
        return True

    def desvincular_chat(self, chat_id: str) -> None:
        """La persona bloqueó el bot o borró el chat: los teléfonos de ese chat quedan sin Telegram."""
        with self.base.transaccion() as con:
            con.execute("UPDATE dispositivos SET telegram_chat_id = NULL, telegram_vinculado_en = NULL WHERE telegram_chat_id = ?", (chat_id,))

    async def enviar_prueba(self, chat_id: str) -> None:
        await self.cliente.enviar(chat_id, TEXTO_PRUEBA.format(red=html.escape(self.cfg.red_nombre)))

    async def despedir(self, chat_id: str) -> None:
        await self.cliente.enviar(chat_id, TEXTO_DESVINCULADO.format(red=html.escape(self.cfg.red_nombre)))

    # ---- Avisos ----

    async def avisar_nuevas(self) -> int:
        """Manda el aviso de cada alerta nueva a cada chat vinculado, según cómo avisa esa severidad en
        ese teléfono. Devuelve cuántos mandó. Lo que no sale se reintenta en la vuelta siguiente."""
        enviados = 0
        perdidos: set[str] = set()
        for pendiente in self._pendientes():
            if pendiente.chat_id in perdidos:
                continue
            if pendiente.modo == "sin_aviso":
                self._registrar(pendiente, enviado=False)
                continue
            try:
                await self.cliente.enviar(
                    pendiente.chat_id,
                    pendiente.texto,
                    boton=(BOTON_DETALLE, pendiente.enlace),
                    silencioso=pendiente.modo == "vibracion",
                )
            except ErrorTelegram as error:
                if error.chat_perdido:
                    self.desvincular_chat(pendiente.chat_id)
                    perdidos.add(pendiente.chat_id)
                    continue
                if error.reintentar_en:
                    # Demasiados mensajes seguidos: Telegram dice cuánto esperar.
                    await asyncio.sleep(error.reintentar_en)
                raise
            self._registrar(pendiente, enviado=True)
            enviados += 1
            if self.pausa:
                await asyncio.sleep(self.pausa)
        return enviados

    def _pendientes(self) -> list[Pendiente]:
        """Las alertas creadas después del vínculo de cada chat que todavía no tienen su aviso."""
        con = self.base.conexion()
        filas = con.execute(
            """
            SELECT a.id, a.inicio, a.servicio, a.tipo_ataque, a.veredicto_reglas, a.veredicto_umbrales, a.destino_ip,
                   d.telegram_chat_id AS chat_id, d.aviso_alta, d.aviso_media, d.aviso_baja
            FROM alertas a
            JOIN dispositivos d
              ON d.telegram_chat_id IS NOT NULL AND d.revocado_en IS NULL AND d.vence_en > ?
             AND a.creada_en >= d.telegram_vinculado_en
            WHERE NOT EXISTS (SELECT 1 FROM avisos_telegram t WHERE t.alerta_id = a.id AND t.chat_id = d.telegram_chat_id)
            ORDER BY a.creada_en, d.telegram_vinculado_en
            LIMIT ?
            """,
            (iso(ahora()), TANDA),
        ).fetchall()
        pendientes: list[Pendiente] = []
        vistos: set[tuple[str, str]] = set()
        for fila in filas:
            # Dos teléfonos con el mismo chat: un solo aviso, con los avisos del primero que se vinculó.
            if (fila["id"], fila["chat_id"]) in vistos:
                continue
            vistos.add((fila["id"], fila["chat_id"]))
            severidad = severidad_de(con, fila)
            deteccion = datetime.fromisoformat(fila["inicio"])
            pendientes.append(
                Pendiente(
                    alerta_id=fila["id"],
                    chat_id=fila["chat_id"],
                    modo=fila[f"aviso_{severidad}"],
                    texto=texto_del_aviso(severidad, fila["servicio"], deteccion, self.cfg.red_nombre, self.zona),
                    enlace=enlace_del_aviso(self.cfg.enlace_aviso, fila["id"], self.cfg.red_id, severidad, fila["servicio"], deteccion, ahora()),
                )
            )
        return pendientes

    async def avisar_asignaciones(self) -> int:
        """Avisa a cada persona con Telegram las alertas que otra persona le asignó (decisión 42). Asignarse
        una uno mismo no avisa. Devuelve cuántos mandó; lo que no sale se reintenta en la vuelta siguiente."""
        enviados = 0
        for pendiente in self._asignaciones_pendientes():
            try:
                await self.cliente.enviar(pendiente.chat_id, pendiente.texto, boton=(BOTON_DETALLE, pendiente.enlace))
            except ErrorTelegram as error:
                if error.chat_perdido:
                    self.desvincular_chat(pendiente.chat_id)
                    continue
                if error.reintentar_en:
                    await asyncio.sleep(error.reintentar_en)
                raise
            with self.base.transaccion() as con:
                con.execute("UPDATE asignaciones SET aviso_telegram_en = ? WHERE id = ?", (iso(ahora()), pendiente.asignacion_id))
            enviados += 1
            if self.pausa:
                await asyncio.sleep(self.pausa)
        return enviados

    def _asignaciones_pendientes(self) -> list[AvisoDeAsignacion]:
        """Las asignaciones vigentes que hizo otra persona, después de que el chat se vinculó, sin su aviso."""
        con = self.base.conexion()
        filas = con.execute(
            """
            SELECT s.id AS asignacion_id, s.alerta_id, s.por, d.telegram_chat_id AS chat_id,
                   a.inicio, a.servicio, a.tipo_ataque, a.veredicto_reglas, a.veredicto_umbrales, a.destino_ip
            FROM asignaciones s
            JOIN dispositivos d
              ON d.id = s.responsable_id AND d.telegram_chat_id IS NOT NULL AND d.revocado_en IS NULL AND d.vence_en > ?
             AND s.fecha >= d.telegram_vinculado_en
            JOIN alertas a ON a.id = s.alerta_id
            WHERE s.aviso_telegram_en IS NULL AND s.responsable_id != s.por_id
              AND s.id = (SELECT MAX(x.id) FROM asignaciones x WHERE x.alerta_id = s.alerta_id)
            ORDER BY s.id
            LIMIT ?
            """,
            (iso(ahora()), TANDA),
        ).fetchall()
        pendientes: list[AvisoDeAsignacion] = []
        for fila in filas:
            severidad = severidad_de(con, fila)
            deteccion = datetime.fromisoformat(fila["inicio"])
            pendientes.append(
                AvisoDeAsignacion(
                    asignacion_id=fila["asignacion_id"],
                    chat_id=fila["chat_id"],
                    texto=texto_de_asignacion(fila["por"], severidad, fila["servicio"], deteccion, self.zona),
                    enlace=enlace_del_aviso(self.cfg.enlace_aviso, fila["alerta_id"], self.cfg.red_id, severidad, fila["servicio"], deteccion, ahora()),
                )
            )
        return pendientes

    async def avisar_bloqueos(self) -> int:
        """Avisa a quienes bloquean (los administradores y los analistas con «Bloquea»), si tienen Telegram, cada
        bloqueo que se pide, se aprueba, se desbloquea o el firewall no aplica (decisión 51), también a quien lo
        hizo: si el dispositivo no estaba en sus manos, se entera. Devuelve cuántos mandó; lo que no sale se
        reintenta en la vuelta siguiente."""
        enviados = 0
        for pendiente in self._bloqueos_pendientes():
            try:
                await self.cliente.enviar(pendiente.chat_id, pendiente.texto, boton=(BOTON_DETALLE, pendiente.enlace))
            except ErrorTelegram as error:
                if error.chat_perdido:
                    self.desvincular_chat(pendiente.chat_id)
                    continue
                if error.reintentar_en:
                    await asyncio.sleep(error.reintentar_en)
                raise
            with self.base.transaccion() as con:
                con.execute(
                    "INSERT OR IGNORE INTO avisos_bloqueos (bloqueo_id, evento, chat_id, enviado_en) VALUES (?, ?, ?, ?)",
                    (pendiente.bloqueo_id, pendiente.evento, pendiente.chat_id, iso(ahora())),
                )
            enviados += 1
            if self.pausa:
                await asyncio.sleep(self.pausa)
        return enviados

    def _bloqueos_pendientes(self) -> list[AvisoDeBloqueo]:
        """Cada momento de un bloqueo posterior al vínculo del chat de alguien que bloquea, sin su aviso. Un
        pedido se avisa mientras sigue pendiente; el rechazo, solo el del firewall (el de una persona lo decide ella)."""
        con = self.base.conexion()
        filas = con.execute(
            """
            WITH eventos AS (
                SELECT id, alerta_id, duracion, 'pedido' AS evento, pedido_en AS momento, pedido_por AS persona
                FROM bloqueos WHERE fue_pedido = 1 AND estado = 'pedido'
                UNION ALL
                SELECT id, alerta_id, duracion, 'aprobado', aprobado_en, aprobado_por FROM bloqueos WHERE aprobado_en IS NOT NULL
                UNION ALL
                SELECT id, alerta_id, duracion, 'desbloqueado', terminado_en, terminado_por FROM bloqueos WHERE estado = 'desbloqueado'
                UNION ALL
                SELECT id, alerta_id, duracion, 'rechazado', terminado_en, terminado_por FROM bloqueos
                WHERE estado = 'rechazado' AND aprobado_en IS NOT NULL
            )
            SELECT e.id AS bloqueo_id, e.evento, e.persona, e.duracion, e.alerta_id, d.telegram_chat_id AS chat_id,
                   a.inicio, a.servicio, a.tipo_ataque, a.veredicto_reglas, a.veredicto_umbrales, a.destino_ip
            FROM eventos e
            JOIN dispositivos d
              ON (d.perfil = 'administrador' OR d.puede_bloquear = 1) AND d.telegram_chat_id IS NOT NULL AND d.revocado_en IS NULL AND d.vence_en > ?
             AND e.momento >= d.telegram_vinculado_en
            JOIN alertas a ON a.id = e.alerta_id
            WHERE NOT EXISTS (
                SELECT 1 FROM avisos_bloqueos x WHERE x.bloqueo_id = e.id AND x.evento = e.evento AND x.chat_id = d.telegram_chat_id
            )
            ORDER BY e.momento, e.id
            LIMIT ?
            """,
            (iso(ahora()), TANDA),
        ).fetchall()
        pendientes: list[AvisoDeBloqueo] = []
        vistos: set[tuple[str, str, str]] = set()
        for fila in filas:
            # Dos dispositivos con el mismo chat: un solo aviso.
            clave = (fila["bloqueo_id"], fila["evento"], fila["chat_id"])
            if clave in vistos:
                continue
            vistos.add(clave)
            severidad = severidad_de(con, fila)
            deteccion = datetime.fromisoformat(fila["inicio"])
            pendientes.append(
                AvisoDeBloqueo(
                    bloqueo_id=fila["bloqueo_id"],
                    evento=fila["evento"],
                    chat_id=fila["chat_id"],
                    texto=texto_de_bloqueo(fila["evento"], fila["persona"], fila["duracion"], severidad, fila["servicio"], deteccion, self.zona),
                    enlace=enlace_del_aviso(self.cfg.enlace_aviso, fila["alerta_id"], self.cfg.red_id, severidad, fila["servicio"], deteccion, ahora()),
                )
            )
        return pendientes

    def _registrar(self, pendiente: Pendiente, enviado: bool) -> None:
        momento = iso(ahora())
        with self.base.transaccion() as con:
            con.execute(
                "INSERT OR IGNORE INTO avisos_telegram (alerta_id, chat_id, enviado_en) VALUES (?, ?, ?)",
                (pendiente.alerta_id, pendiente.chat_id, momento if enviado else None),
            )
            ya_avisada = con.execute("SELECT aviso_telegram_en FROM alertas WHERE id = ?", (pendiente.alerta_id,)).fetchone()
            if enviado and ya_avisada is not None and ya_avisada["aviso_telegram_en"] is None:
                # El primer aviso queda en el historial del detalle (Figma 51-2379): la alerta cambia y
                # los teléfonos la vuelven a bajar en la sincronización siguiente.
                con.execute(
                    "UPDATE alertas SET aviso_telegram_en = ?, actualizada_en = ?, secuencia = ? WHERE id = ?",
                    (momento, momento, Base.siguiente_secuencia(con), pendiente.alerta_id),
                )

    # ---- Las dos tareas ----

    async def recibir(self) -> None:
        """Una vuelta de long polling: Telegram retiene el pedido hasta que llega un mensaje o pasan 25 s."""
        desde = int(self.base.leer_meta("telegram_offset", "0"))
        for novedad in await self.cliente.novedades(desde):
            try:
                await self.atender(novedad)
            except ErrorTelegram as error:
                registro.warning("No se pudo responder un mensaje del bot: %s", error)
            except (sqlite3.Error, KeyError, TypeError, ValueError):
                # Un mensaje que no se entiende no puede trabar a los demás.
                registro.exception("Mensaje del bot sin atender")
            with self.base.transaccion() as con:
                Base.escribir_meta(con, "telegram_offset", str(novedad["update_id"] + 1))

    async def escuchar(self) -> None:
        espera = 1
        while True:
            try:
                if not self.presentado:
                    await self.presentarse()
                await self.recibir()
                espera = 1
            except ErrorTelegram as error:
                if error.codigo == 409:
                    registro.warning("Otro proceso lee los mensajes de este bot: ¿dos servidores con el mismo token?")
                else:
                    registro.warning("No se pudieron leer los mensajes del bot: %s", error)
                await asyncio.sleep(error.reintentar_en or espera)
                espera = min(espera * 2, 60)

    async def vigilar(self) -> None:
        while True:
            try:
                await self.avisar_nuevas()
                await self.avisar_asignaciones()
                await self.avisar_bloqueos()
            except ErrorTelegram as error:
                registro.warning("Hay avisos sin mandar, se reintenta: %s", error)
            except sqlite3.Error:
                registro.exception("No se pudieron leer las alertas para avisar")
            await asyncio.sleep(REVISAR_CADA)

    def arrancar(self) -> list[asyncio.Task]:
        return [asyncio.create_task(self.escuchar()), asyncio.create_task(self.vigilar())]
