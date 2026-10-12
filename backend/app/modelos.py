"""Esquemas de entrada y salida de la API (pydantic). Son el contrato con la app.

Convenciones: campos en camelCase; enums en minúsculas con guion bajo; fechas en ISO 8601 con zona
(UTC). `tipoAtaque` es null cuando la alerta la detectaron solo las reglas o los umbrales.
"""

from typing import Literal

from pydantic import BaseModel, Field

TipoAtaque = Literal["fuerza_bruta", "escaneo", "ddos", "probable_falsa_alarma", "sin_tipificar"]
Severidad = Literal["alta", "media", "baja"]
EstadoRevision = Literal["sin_revisar", "ataque_real", "falso_positivo"]
Resultado = Literal["ataque_real", "falso_positivo"]
Motivo = Literal[
    "escalado_al_proveedor",
    "equipo_aislado",
    "credenciales_cambiadas",
    "en_investigacion",
    "tarea_programada",
    "herramienta_autorizada",
    "prueba_del_equipo_de_it",
    "servicio_nuevo",
]

MOTIVOS_POR_RESULTADO: dict[str, set[str]] = {
    "ataque_real": {"escalado_al_proveedor", "equipo_aislado", "credenciales_cambiadas", "en_investigacion"},
    "falso_positivo": {"tarea_programada", "herramienta_autorizada", "prueba_del_equipo_de_it", "servicio_nuevo"},
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
    comentario: str = Field(min_length=1, max_length=1000)
    versionVista: int | None = None
    forzar: bool = False


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
    vence: str
    dispositivoId: str


class VinculoTelegram(BaseModel):
    codigo: str
    enlaceBot: str
    vence: str


class Catalogo(BaseModel):
    equipos: dict[str, str]
    servicios: list[str]
