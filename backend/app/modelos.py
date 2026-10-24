"""Esquemas de entrada y salida de la API (pydantic). Son el contrato con la app.

Convenciones: campos en camelCase; enums en minúsculas con guion bajo; fechas en ISO 8601 con zona
(UTC). `tipoAtaque` es null cuando la alerta la detectaron solo las reglas o los umbrales; su severidad
sale de `servicio_no_autorizado` (las reglas), de `volumen_inusual` (los umbrales) o, si la vieron los
dos, de la más alta de las dos (cambio 9).
"""

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
# Qué tan importante es un equipo de la red (decisión 41): sube o baja un nivel la severidad de sus alertas.
Importancia = Literal["critico", "normal", "poco_importante"]
# Lo que anota el registro de cambios (decisión 40).
TipoCambio = Literal["severidad", "importancia", "persona_nueva", "perfil", "persona_quitada"]
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
