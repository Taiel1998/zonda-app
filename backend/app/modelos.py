"""Esquemas de entrada y salida de la API (pydantic). Son el contrato con la app.

Convenciones: campos en camelCase; enums en minúsculas con guion bajo; fechas en ISO 8601 con zona
(UTC). `tipoAtaque` es null cuando la alerta la detectaron solo las reglas o los umbrales.
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

# Clave que representa el tipo vacío (null) en la tabla de severidades.
CLAVE_SIN_TIPO = "solo_reglas_o_umbrales"
TIPOS_SEVERIDAD = ["fuerza_bruta", "escaneo", "ddos", "probable_falsa_alarma", "sin_tipificar", CLAVE_SIN_TIPO]


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


class Catalogo(BaseModel):
    equipos: dict[str, str]
    servicios: list[str]
