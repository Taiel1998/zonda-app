"""Esquemas de entrada y salida de la API (pydantic). Son el contrato con la app.

Convenciones: campos en camelCase; enums en minúsculas con guion bajo; fechas en ISO 8601 con zona
(UTC). `tipoAtaque` es null cuando la alerta la detectaron solo las reglas o los umbrales; su severidad
sale de `servicio_no_autorizado` (las reglas), de `volumen_inusual` (los umbrales) o, si la vieron los
dos, de la más alta de las dos (cambio 9).
"""

import ipaddress
from datetime import datetime
from typing import Literal

from pydantic import BaseModel, Field, field_validator

TipoAtaque = Literal["fuerza_bruta", "escaneo", "ddos", "probable_falsa_alarma", "sin_tipificar"]
Severidad = Literal["alta", "media", "baja"]
EstadoRevision = Literal["sin_revisar", "ataque_real", "falso_positivo"]
Resultado = Literal["ataque_real", "falso_positivo"]
# Dos perfiles (decisión 39): el analista revisa alertas y elige sus avisos; el administrador, además,
# cambia la configuración de la red y suma o quita gente.
Perfil = Literal["administrador", "analista"]
# Qué se pareó (decisión 51): un teléfono o un navegador con el tablero web. Un bloqueo en el firewall solo
# lo hace efectivo un administrador desde su teléfono; desde el navegador se pide.
TipoDispositivo = Literal["telefono", "navegador"]
# Qué tan importante es un equipo de la red (decisión 41): sube o baja un nivel la severidad de sus alertas.
Importancia = Literal["critico", "normal", "poco_importante"]
# Lo que anota el registro de cambios (decisión 40), con los bloqueos del firewall (decisión 51).
TipoCambio = Literal["severidad", "importancia", "persona_nueva", "perfil", "persona_quitada", "bloqueo", "desbloqueo"]
# Los motivos del Figma (pantallas "revisar: ataque real" y "revisar: falso positivo").
Motivo = Literal[
    "escalado_al_proveedor",
    "bloqueado_manualmente",
    "contrasena_cambiada",
    "investigando",
    "tarea_programada",
    "backup",
    "equipo_nuevo",
    "usuario_autorizado",
    "otro",
]

MOTIVOS_POR_RESULTADO: dict[str, set[str]] = {
    "ataque_real": {"escalado_al_proveedor", "bloqueado_manualmente", "contrasena_cambiada", "investigando"},
    "falso_positivo": {"tarea_programada", "backup", "equipo_nuevo", "usuario_autorizado", "otro"},
}

# Los motivos que tuvo la API antes y su equivalente actual, para las revisiones ya guardadas.
MOTIVOS_ANTERIORES: dict[str, str] = {
    "equipo_aislado": "bloqueado_manualmente",
    "credenciales_cambiadas": "contrasena_cambiada",
    "en_investigacion": "investigando",
    "herramienta_autorizada": "usuario_autorizado",
    "prueba_del_equipo_de_it": "otro",
    "servicio_nuevo": "equipo_nuevo",
}

# Las claves de la tabla de severidades para el tipo vacío (null), una por detector (cambio 9): lo que
# vieron las reglas (un servicio que no está permitido) y lo que vieron los umbrales (demasiadas conexiones).
CLAVE_REGLAS = "servicio_no_autorizado"
CLAVE_UMBRALES = "volumen_inusual"
# La única clave que tuvo el tipo vacío hasta la 1.0.3; al iniciar, una base anterior la reparte en las dos.
CLAVE_SIN_TIPO_ANTERIOR = "solo_reglas_o_umbrales"
TIPOS_SEVERIDAD = ["fuerza_bruta", "escaneo", "ddos", "probable_falsa_alarma", "sin_tipificar", CLAVE_REGLAS, CLAVE_UMBRALES]


class Veredictos(BaseModel):
    ia: bool
    errorReconstruccion: float
    umbralIa: float
    umbrales: bool
    contexto60s: int
    umbralServicio: int
    servicioSinLineaBase: bool
    reglas: bool
    parDestino: str
    parEnCatalogo: bool


class Revision(BaseModel):
    resultado: Resultado
    motivo: Motivo
    comentario: str
    # El teléfono que la revisó ("Teléfono de Laura").
    autor: str
    # Cuándo llegó al servidor.
    fecha: str
    # Cuándo se revisó en el teléfono; null en las revisiones anteriores a este campo.
    revisadaEn: str | None = None
    # La persona de ese teléfono ("Laura"); null en los teléfonos pareados antes de la 1.0.5.
    persona: str | None = None


class Asignacion(BaseModel):
    """Un cambio de responsable de una alerta: a quién (null si quedó sin asignar), quién y cuándo."""

    responsableId: str | None
    responsable: str | None
    porId: str
    por: str
    fecha: str


class Alerta(BaseModel):
    id: str
    creadaEn: str
    inicio: str
    fin: str
    cantidadFlujos: int
    uidReferencia: str
    origenIp: str
    origenNombre: str | None
    destinoIp: str
    destinoNombre: str | None
    servicio: str
    # Protocolo de transporte del episodio ("tcp" o "udp"): el detalle muestra "SSH · TCP/22".
    protocolo: str
    puerto: int
    puertoReferencia: int
    puertosDistintos: int
    tipoAtaque: TipoAtaque | None
    confianzaTipo: float | None
    severidad: Severidad
    veredictos: Veredictos
    estadoRevision: EstadoRevision
    historial: list[Revision]
    version: int
    actualizadaEn: str
    # Cuándo salió el primer aviso de Telegram de esta alerta; null si no salió ninguno.
    avisoTelegram: str | None = None
    # El responsable de ahora; null si está sin asignar (decisión 42).
    asignacion: Asignacion | None = None
    # Cada cambio de responsable, del primero al último, para el historial del detalle.
    asignaciones: list[Asignacion] = Field(default_factory=list)


class PaginaAlertas(BaseModel):
    alertas: list[Alerta]
    cursorSiguiente: str
    hayMas: bool
    horaServidor: str
    # Solo en la primera página (sin cursor): cuántas alertas trae la sincronización completa.
    total: int | None = None


EstadoPieza = Literal["funciona", "detenido", "sin_datos"]
# El sensor además puede estar "parcial": Zeek anda pero perdió más que el límite de lo que pasó por el
# puerto espejo (pérdida de captura), así que puede haber tráfico que no ve.
EstadoSensor = Literal["funciona", "parcial", "detenido", "sin_datos"]


class EstadoSistema(BaseModel):
    """Cómo está cada pieza de la cadena, para la tarjeta "Estado del sistema" de la app: el servidor,
    que respondió; el análisis en vivo, por la edad de su último latido; y el sensor, por lo que dijo ese
    latido, con su pérdida de captura. Con el latido viejo no se sabe cómo está el sensor (sin_datos)."""

    servidor: Literal["funciona"]
    analisis: EstadoPieza
    sensor: EstadoSensor
    # Cuándo llegó el último latido, con el reloj del servidor; null si nunca llegó ninguno.
    ultimaCorrida: str | None
    segundosDesdeUltimaCorrida: int | None
    # El flujo más nuevo del conn.log según el último latido.
    ultimoFlujo: str | None
    flujosUltimaCorrida: int | None
    limiteSegundos: int
    # La pérdida de captura de Zeek (capture_loss.log, una medición cada 15 minutos) según el último
    # latido: el porcentaje, cuándo se midió y sobre cuántos ACK de TCP; null si no vino.
    perdidaCaptura: float | None = None
    perdidaCapturaEn: str | None = None
    acksCaptura: int | None = None
    # El valor salió del archivo de prueba del laboratorio, no de Zeek.
    perdidaSimulada: bool = False
    # Porcentaje desde el que el sensor ve parcialmente.
    perdidaLimite: float


class Salud(BaseModel):
    version: str
    hora: str
    alertas: int
    red: dict
    tokenVence: str | None
    # Solo con un token válido: sin token, /salud no dice nada del laboratorio.
    sistema: EstadoSistema | None = None


class Contadores(BaseModel):
    alta: int
    media: int
    baja: int


class Estado(BaseModel):
    sinRevisar: Contadores
    ultimoFlujoProcesado: str | None
    horaServidor: str


class RevisionEntrada(BaseModel):
    accionId: str = Field(min_length=8, max_length=64)
    resultado: Resultado
    motivo: Motivo
    comentario: str = Field(max_length=1000)
    versionVista: int | None = None
    forzar: bool = False
    # Cuándo se revisó en el teléfono (ISO 8601 con zona). Sin conexión puede ser bastante antes del envío.
    revisadaEn: str | None = None

    @field_validator("comentario")
    @classmethod
    def comentario_con_texto(cls, valor: str) -> str:
        # El comentario es obligatorio (RF04, criterio 2): uno con solo espacios tampoco vale.
        if not valor.strip():
            raise ValueError("El comentario es obligatorio")
        return valor

    @field_validator("revisadaEn")
    @classmethod
    def revisada_en_con_zona(cls, valor: str | None) -> str | None:
        if valor is None:
            return None
        try:
            momento = datetime.fromisoformat(valor)
        except ValueError as error:
            raise ValueError("revisadaEn no es una fecha ISO 8601") from error
        if momento.tzinfo is None:
            raise ValueError("revisadaEn tiene que tener la zona horaria")
        return valor


class Conflicto(BaseModel):
    mensaje: str
    revisionAjena: Revision
    alerta: Alerta


class PareoEntrada(BaseModel):
    codigo: str = Field(min_length=8, max_length=9)
    nombreDispositivo: str = Field(min_length=1, max_length=80)
    # telefono o navegador. Sin el campo (la app y el tablero anteriores a la API 0.9.0), se deduce del nombre.
    tipo: TipoDispositivo | None = None


class PareoSalida(BaseModel):
    token: str
    red: dict
    emitido: str
    vence: str
    dispositivoId: str
    # Alertas de los últimos 30 días que la app va a descargar ("Red conectada", Figma 35-692).
    alertas30Dias: int = 0
    # Con qué perfil y nombre entró la persona (los del código que escaneó).
    perfil: Perfil = "administrador"
    persona: str | None = None


def _texto_obligatorio(valor: str, falta: str) -> str:
    if not valor.strip():
        raise ValueError(falta)
    return valor.strip()


class Persona(BaseModel):
    """Una persona del equipo: su teléfono pareado, con su nombre y su perfil (Ajustes → Personas)."""

    id: str
    # null en los teléfonos pareados antes de la 1.0.5: la app muestra el del teléfono.
    nombre: str | None
    telefono: str
    perfil: Perfil
    ultimoUso: str | None
    # Si es el teléfono que pregunta.
    vos: bool


class CodigoPersonaEntrada(BaseModel):
    """Agregar persona: el nombre y el perfil de quien se suma (Figma 468-2642)."""

    nombre: str = Field(min_length=1, max_length=40)
    perfil: Perfil = "analista"

    @field_validator("nombre")
    @classmethod
    def nombre_con_texto(cls, valor: str) -> str:
        return _texto_obligatorio(valor, "Escribí el nombre de la persona")


class CodigoPersona(BaseModel):
    """El código de un uso para esa persona y lo que lleva su QR (Figma 468-2735)."""

    codigo: str
    vence: str
    # Lo mismo que el QR de zonda pair: zonda:<dirección>#<código>;vence=<fecha>.
    contenido: str
    nombre: str
    perfil: Perfil
    # El QR ya dibujado, en SVG, para que el tablero web lo muestre sin librerías (decisión 50). La app
    # lo ignora: arma el suyo con `contenido`. Null si el servidor no tiene la librería qrcode.
    qrSvg: str | None = None


class PerfilEntrada(BaseModel):
    perfil: Perfil


class Equipo(BaseModel):
    """Un equipo de la red con nombre en el catálogo y su importancia (Ajustes → Equipos)."""

    ip: str
    nombre: str
    importancia: Importancia


class ImportanciaEntrada(BaseModel):
    importancia: Importancia
    # Obligatorio, como el de las severidades: queda en el registro de cambios (decisión 40).
    motivo: str = Field(max_length=500)

    @field_validator("motivo")
    @classmethod
    def motivo_con_texto(cls, valor: str) -> str:
        return _texto_obligatorio(valor, "Escribí por qué la cambiás")


class SeveridadesEntrada(BaseModel):
    """Las severidades que cambian y un solo motivo para todos los cambios de ese Guardar."""

    severidades: dict[str, Severidad]
    motivo: str = Field(max_length=500)

    @field_validator("motivo")
    @classmethod
    def motivo_con_texto(cls, valor: str) -> str:
        return _texto_obligatorio(valor, "Escribí por qué la cambiás")


class CambioRegistro(BaseModel):
    """Una línea del registro de cambios. La app arma la frase: "Fuerza bruta pasó de alta a media"."""

    id: int
    fecha: str
    tipo: TipoCambio
    # null si el cambio salió de la terminal del servidor.
    autorId: str | None
    autor: str
    objeto: str
    nombre: str | None
    antes: str | None
    despues: str | None
    motivo: str | None


class AsignacionEntrada(BaseModel):
    accionId: str = Field(min_length=8, max_length=64)
    # A quién se asigna; null para dejarla sin asignar.
    responsableId: str | None = None


class VinculoTelegram(BaseModel):
    codigo: str
    enlaceBot: str
    vence: str


# Cómo avisa Telegram una severidad. "vibracion" es un mensaje sin sonido: el bot no puede pedir
# solo vibración, Telegram manda con sonido o en silencio.
ModoAviso = Literal["sonido", "vibracion", "sin_aviso"]


class AvisosTelegram(BaseModel):
    """Cómo avisa Telegram cada severidad en un teléfono (Figma 51-1688). Por defecto, los del Figma."""

    alta: ModoAviso = "sonido"
    media: ModoAviso = "vibracion"
    baja: ModoAviso = "sin_aviso"


class VinculoEntrada(BaseModel):
    # Los avisos que eligió la persona en la app: el bot los usa desde el primer aviso.
    avisos: AvisosTelegram | None = None


class EstadoTelegram(BaseModel):
    vinculado: bool
    # Usuario del bot, sin la arroba.
    bot: str
    vinculadoEn: str | None
    avisos: AvisosTelegram


class Catalogo(BaseModel):
    equipos: dict[str, str]
    servicios: list[str]


class AlertaIngesta(BaseModel):
    """Un episodio que manda la plataforma de detección (el pipeline en vivo del laboratorio) a
    POST /ingesta/alertas. Mismo formato que la semilla; los campos que la semilla trae de más
    (dia, flujos*, history, etiquetaVerdad) se ignoran."""

    model_config = {"extra": "ignore"}

    id: str = Field(min_length=1, max_length=128)
    inicio: str
    fin: str
    cantidadFlujos: int = Field(ge=1)
    uidReferencia: str
    origenIp: str
    destinoIp: str
    servicio: str
    protocolo: str = "tcp"
    puerto: int = Field(ge=0)
    puertoReferencia: int | None = Field(default=None, ge=0)
    puertosDistintos: int = Field(default=1, ge=1)
    tipoAtaque: TipoAtaque | None = None
    confianzaTipo: float | None = None
    veredictoIa: bool
    errorReconstruccion: float
    umbralIa: float
    veredictoUmbrales: bool
    contexto60s: int = Field(ge=0)
    umbralServicio: int = Field(ge=0)
    # El máximo del día de calibración para ese puerto; null si el puerto no tenía línea base (igual que la semilla).
    umbralServicioLunes: float | None = None
    veredictoReglas: bool
    parDestino: str
    parEnCatalogo: bool

    @field_validator("inicio", "fin")
    @classmethod
    def fecha_con_zona(cls, valor: str) -> str:
        return fecha_iso_con_zona(valor)


def fecha_iso_con_zona(valor: str) -> str:
    """Las fechas que manda la plataforma de detección: ISO 8601 y con zona, para pasarlas bien a UTC."""
    try:
        momento = datetime.fromisoformat(valor)
    except ValueError as error:
        raise ValueError("la fecha tiene que ser ISO 8601") from error
    if momento.tzinfo is None:
        raise ValueError("la fecha tiene que tener la zona horaria")
    return valor


class CatalogoIngesta(BaseModel):
    """Nombres de equipos (por IP) y servicios que trae la ingesta, para que la app los muestre."""

    equipos: dict[str, str] = Field(default_factory=dict)
    servicios: list[str] = Field(default_factory=list)


class IngestaEntrada(BaseModel):
    alertas: list[AlertaIngesta] = Field(min_length=1)
    catalogo: CatalogoIngesta | None = None


class IngestaResultado(BaseModel):
    recibidas: int
    creadas: int
    actualizadas: int


class LatidoEntrada(BaseModel):
    """El latido que manda el análisis en vivo del laboratorio a POST /ingesta/latido al final de cada
    ciclo, aunque no haya episodios: si deja de llegar, el análisis se cayó o se colgó."""

    model_config = {"extra": "ignore"}

    # Cuándo corrió el ciclo, con el reloj del sensor.
    hora: str
    # Los flujos que procesó el ciclo.
    flujos: int = Field(ge=0)
    # El flujo más nuevo del conn.log; null si Zeek todavía no escribió ninguno.
    ultimoFlujo: str | None = None
    # El proceso de Zeek está vivo en el sensor.
    zeekActivo: bool
    episodios: int = Field(default=0, ge=0)
    # Segundos entre un ciclo y el siguiente.
    intervalo: int | None = Field(default=None, ge=1)
    # La última medición de pérdida de captura de Zeek (capture_loss.log): porcentaje de 0 a 100, cuándo
    # se midió y sobre cuántos ACK de TCP (sin tráfico TCP, 0 ACK y 0 %). Null si todavía no hay medición.
    perdidaCaptura: float | None = Field(default=None, ge=0, le=100)
    perdidaCapturaEn: str | None = None
    acksCaptura: int | None = Field(default=None, ge=0)
    # El valor salió del archivo de prueba del laboratorio (vivo/.perdida_simulada), no de Zeek.
    perdidaSimulada: bool = False

    @field_validator("hora", "ultimoFlujo", "perdidaCapturaEn")
    @classmethod
    def fecha_con_zona(cls, valor: str | None) -> str | None:
        return None if valor is None else fecha_iso_con_zona(valor)


class LatidoResultado(BaseModel):
    # Cuándo llegó, con el reloj del servidor: es la hora que mide si el análisis sigue andando.
    recibidoEn: str


# ---- Tráfico del sensor para el tablero web (decisión 49) ----

# El servicio como lo nombra el sensor: el de Zeek ("ssh", "dns") o, si Zeek no lo reconoció, el del
# puerto o "otro". En minúsculas, para que no entre cualquier texto en la base.
PATRON_SERVICIO = r"^[a-z0-9][a-z0-9_.-]{0,31}$"
# Un tiempo razonable: después de 2001 y antes de 2100, en segundos desde 1970.
T_MINIMO = 1_000_000_000
T_MAXIMO = 4_102_444_800


class TramoTrafico(BaseModel):
    """Lo que sumó el sensor en un tramo de 5 segundos para un servicio: las conexiones que empezaron en
    el tramo, cuántas de ellas no llegaron a establecerse, y los bytes y paquetes que pasaron en él."""

    # Comienzo del tramo, en segundos desde 1970 (UTC), con el reloj del sensor.
    t: int = Field(ge=T_MINIMO, le=T_MAXIMO)
    servicio: str = Field(pattern=PATRON_SERVICIO)
    conexiones: int = Field(default=0, ge=0)
    fallidas: int = Field(default=0, ge=0)
    bytes: int = Field(default=0, ge=0)
    paquetes: int = Field(default=0, ge=0)


class ParTrafico(BaseModel):
    """Quién habló con quién en un minuto y por qué servicio (el mapa de la red del tablero)."""

    # Comienzo del minuto, en segundos desde 1970 (UTC); si no es redondo, el servidor lo redondea.
    minuto: int = Field(ge=T_MINIMO, le=T_MAXIMO)
    origen: str
    destino: str
    servicio: str = Field(pattern=PATRON_SERVICIO)
    conexiones: int = Field(default=0, ge=0)
    fallidas: int = Field(default=0, ge=0)
    bytes: int = Field(default=0, ge=0)

    @field_validator("origen", "destino")
    @classmethod
    def direccion_ip(cls, valor: str) -> str:
        try:
            return str(ipaddress.ip_address(valor.strip()))
        except ValueError as error:
            raise ValueError("tiene que ser una dirección IP") from error


class TraficoEntrada(BaseModel):
    """Lo que manda el sensor a POST /ingesta/trafico cada 5 segundos. Si el servidor no contestó, el
    sensor junta varios envíos en uno; por eso las listas pueden ser largas."""

    model_config = {"extra": "ignore"}

    tramos: list[TramoTrafico] = Field(default_factory=list, max_length=50_000)
    pares: list[ParTrafico] = Field(default_factory=list, max_length=50_000)


class TraficoResultado(BaseModel):
    tramos: int
    pares: int


class PuntoTrafico(BaseModel):
    """Un punto de la serie: el tramo que empieza en t y dura `paso` segundos."""

    t: int
    conexiones: int
    fallidas: int
    bytes: int
    paquetes: int


class SerieTrafico(BaseModel):
    """GET /trafico: el tráfico de la ventana [desde, hasta), en tramos de `paso` segundos. Solo vienen
    los tramos con tráfico; los que faltan son ceros."""

    desde: int
    hasta: int
    paso: int
    horaServidor: str
    # El tramo más nuevo que llegó del sensor (de cualquier momento); null si nunca llegó ninguno.
    ultimoTramo: int | None
    total: list[PuntoTrafico]
    servicios: dict[str, list[PuntoTrafico]]


class ParDeTrafico(BaseModel):
    origen: str
    origenNombre: str | None
    destino: str
    destinoNombre: str | None
    conexiones: int
    fallidas: int
    bytes: int
    # De más a menos conexiones.
    servicios: list[str]
    ultimoMinuto: int


class ParesTrafico(BaseModel):
    """GET /trafico/pares: quién habló con quién en la ventana, de más a menos conexiones."""

    desde: int
    hasta: int
    pares: list[ParDeTrafico]


# ---- Bloqueos en el firewall (decisión 51) ----

# pedido (falta que lo apruebe un administrador desde su teléfono), aprobado (el firewall todavía no dijo
# que lo aplicó), aplicado, rechazado (por un administrador o por el firewall), desbloqueado o vencido.
EstadoBloqueo = Literal["pedido", "aprobado", "aplicado", "rechazado", "desbloqueado", "vencido"]
# Cuánto puede durar un bloqueo, en segundos: 15 minutos, 1 hora (la que propone la app), 8 y 24 horas. Sin
# duración (null) queda hasta que alguien lo desbloquea, y eso solo con la alerta revisada como ataque real.
DURACIONES_BLOQUEO = (900, 3600, 28800, 86400)


def _duracion_permitida(valor: int | None) -> int | None:
    if valor is not None and valor not in DURACIONES_BLOQUEO:
        raise ValueError("La duración tiene que ser de 15 minutos, 1, 8 o 24 horas, o hasta desbloquear")
    return valor


class Bloqueo(BaseModel):
    """El bloqueo del origen de una alerta en el firewall: quién lo pidió, quién lo aprobó y cómo terminó."""

    id: str
    alertaId: str
    ip: str
    # El nombre del equipo en el catálogo; null si no tiene.
    nombre: str | None
    estado: EstadoBloqueo
    # Segundos; null: hasta desbloquear. Lo que manda es venceEn.
    duracion: int | None
    motivo: str
    pedidoPorId: str
    pedidoPor: str
    pedidoEn: str
    aprobadoPorId: str | None
    aprobadoPor: str | None
    aprobadoEn: str | None
    venceEn: str | None
    # Cuándo dijo el firewall que lo aplicó.
    aplicadoEn: str | None
    # Quién lo rechazó o lo desbloqueó ("Firewall" si lo rechazó el firewall), cuándo y por qué.
    terminadoPorId: str | None
    terminadoPor: str | None
    terminadoEn: str | None
    detalle: str | None


class EstadoFirewall(BaseModel):
    # Los bloqueos se prenden al conectar un firewall (ZONDA_FIREWALL_TOKEN); si no, la red queda en modo de observación.
    habilitado: bool
    # El firewall preguntó hace menos de ZONDA_FIREWALL_LIMITE segundos.
    conectado: bool
    ultimaConsulta: str | None
    # Lo que el firewall nunca bloquea (redes CIDR), según su último informe: la app no ofrece el botón ahí.
    nuncaBloquear: list[str]


class ListaBloqueos(BaseModel):
    bloqueos: list[Bloqueo]
    firewall: EstadoFirewall


class BloqueoEntrada(BaseModel):
    accionId: str = Field(min_length=8, max_length=64)
    alertaId: str = Field(min_length=1, max_length=128)
    duracion: int | None = 3600
    # Obligatorio: queda en el registro de cambios (decisión 40).
    motivo: str = Field(max_length=500)

    @field_validator("duracion")
    @classmethod
    def duracion_permitida(cls, valor: int | None) -> int | None:
        return _duracion_permitida(valor)

    @field_validator("motivo")
    @classmethod
    def motivo_con_texto(cls, valor: str) -> str:
        return _texto_obligatorio(valor, "Escribí por qué lo bloqueás")


class DuracionEntrada(BaseModel):
    """Cuánto dura desde ahora un bloqueo vigente; null lo deja hasta desbloquear."""

    duracion: int | None
    motivo: str = Field(max_length=500)

    @field_validator("duracion")
    @classmethod
    def duracion_permitida(cls, valor: int | None) -> int | None:
        return _duracion_permitida(valor)

    @field_validator("motivo")
    @classmethod
    def motivo_con_texto(cls, valor: str) -> str:
        return _texto_obligatorio(valor, "Escribí por qué lo cambiás")


class MotivoEntrada(BaseModel):
    """El motivo de un desbloqueo o del rechazo de un pedido."""

    motivo: str = Field(max_length=500)

    @field_validator("motivo")
    @classmethod
    def motivo_con_texto(cls, valor: str) -> str:
        return _texto_obligatorio(valor, "Escribí el motivo")


class BloqueoFirewall(BaseModel):
    """Lo que el firewall tiene que tener bloqueado ahora: la IP y los segundos que le quedan (null: sin vencimiento)."""

    id: str
    ip: str
    segundos: int | None


class ListaFirewall(BaseModel):
    bloqueos: list[BloqueoFirewall]
    horaServidor: str


class RechazoFirewall(BaseModel):
    id: str = Field(min_length=1, max_length=64)
    # nunca_bloquear (está en su lista), tope (pasa sus topes) o invalido (la IP no le sirve).
    motivo: Literal["nunca_bloquear", "tope", "invalido"]


class InformeFirewall(BaseModel):
    """Lo que manda el agente del firewall a POST /firewall/informe después de cada vuelta."""

    model_config = {"extra": "ignore"}

    aplicados: list[str] = Field(default_factory=list, max_length=1000)
    rechazados: list[RechazoFirewall] = Field(default_factory=list, max_length=1000)
    # Su lista de «nunca bloquear»; null si no la manda en esta vuelta (queda la anterior).
    nuncaBloquear: list[str] | None = Field(default=None, max_length=200)

    @field_validator("nuncaBloquear")
    @classmethod
    def redes(cls, valor: list[str] | None) -> list[str] | None:
        if valor is None:
            return None
        try:
            return [str(ipaddress.ip_network(red.strip(), strict=False)) for red in valor]
        except ValueError as error:
            raise ValueError("nuncaBloquear lleva redes o direcciones IP") from error


class InformeResultado(BaseModel):
    aplicados: int
    rechazados: int
