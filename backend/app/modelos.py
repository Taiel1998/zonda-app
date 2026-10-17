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
    autor: str
    # Cuándo llegó al servidor.
    fecha: str
    # Cuándo se revisó en el teléfono; null en las revisiones anteriores a este campo.
    revisadaEn: str | None = None


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


class PaginaAlertas(BaseModel):
    alertas: list[Alerta]
    cursorSiguiente: str
    hayMas: bool
    horaServidor: str
    # Solo en la primera página (sin cursor): cuántas alertas trae la sincronización completa.
    total: int | None = None


class Salud(BaseModel):
    version: str
    hora: str
    alertas: int
    red: dict
    tokenVence: str | None


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
