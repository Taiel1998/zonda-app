package ar.edu.uade.zonda.data.remote

// Objetos que viajan en el JSON de la API, con los nombres exactos del contrato (backend/README.md):
// camelCase, enumeraciones en minúsculas con guion bajo y fechas ISO 8601 como texto. Gson los llena
// por reflexión. El dominio nunca los ve: los mappers de `data/mapper/MapeosRemotos.kt` los traducen.

data class SaludDto(
    val version: String,
    val hora: String,
    val alertas: Int,
    val red: RedDto?,
    val tokenVence: String?,
    /** Desde la API 0.7.0 y solo con token: el sensor, el análisis en vivo y el servidor. */
    val sistema: SistemaDto? = null,
)

/**
 * `sistema` de `GET /salud`. Los estados son `funciona`, `detenido` o `sin_datos`, y el sensor además
 * `parcial` (la pérdida de captura pasó de [perdidaLimite]); el análisis en vivo se da por detenido
 * cuando su último latido tiene más de [limiteSegundos].
 */
data class SistemaDto(
    val servidor: String?,
    val analisis: String?,
    val sensor: String?,
    val ultimaCorrida: String?,
    val segundosDesdeUltimaCorrida: Long?,
    val ultimoFlujo: String?,
    val flujosUltimaCorrida: Int?,
    val limiteSegundos: Int?,
    val perdidaCaptura: Double? = null,
    val perdidaCapturaEn: String? = null,
    val acksCaptura: Int? = null,
    val perdidaSimulada: Boolean? = null,
    val perdidaLimite: Double? = null,
)

data class RedDto(
    val id: String,
    val nombre: String,
)

data class PaginaAlertasDto(
    val alertas: List<AlertaDto>,
    val cursorSiguiente: String,
    val hayMas: Boolean,
    val horaServidor: String?,
    /** Solo en la primera página de la primera sincronización: cuántas alertas trae en total ("120 de 213"). */
    val total: Int? = null,
)

data class AlertaDto(
    val id: String,
    val creadaEn: String,
    val inicio: String,
    val fin: String,
    val cantidadFlujos: Int,
    val uidReferencia: String,
    val origenIp: String,
    val origenNombre: String?,
    val destinoIp: String,
    val destinoNombre: String?,
    val servicio: String,
    /** "tcp" o "udp". Un servidor anterior no lo manda. */
    val protocolo: String? = null,
    val puerto: Int,
    val puertoReferencia: Int,
    val puertosDistintos: Int,
    /** `null` cuando la detectaron solo las reglas o los umbrales. */
    val tipoAtaque: String?,
    val confianzaTipo: Double?,
    val severidad: String,
    val veredictos: VeredictosDto,
    val estadoRevision: String,
    val historial: List<RevisionDto>,
    val version: Int,
    val actualizadaEn: String,
    /** Cuándo salió el primer aviso de Telegram; un servidor anterior no lo manda. */
    val avisoTelegram: String? = null,
    /** El responsable de ahora (API 0.6.0); null si está sin asignar o el servidor es anterior. */
    val asignacion: AsignacionDto? = null,
    /** Cada cambio de responsable, del primero al último; un servidor anterior no lo manda. */
    val asignaciones: List<AsignacionDto>? = null,
)

data class AsignacionDto(
    val responsableId: String?,
    val responsable: String?,
    val porId: String,
    val por: String,
    val fecha: String,
)

/** Cuerpo de `PUT /alertas/{id}/asignacion`: a quién (null para dejarla sin asignar). */
data class AsignacionEntradaDto(
    val accionId: String,
    val responsableId: String?,
)

data class VeredictosDto(
    val ia: Boolean,
    val errorReconstruccion: Double,
    val umbralIa: Double,
    val umbrales: Boolean,
    val contexto60s: Int,
    val umbralServicio: Int,
    val servicioSinLineaBase: Boolean,
    val reglas: Boolean,
    val parDestino: String,
    val parEnCatalogo: Boolean,
)

data class RevisionDto(
    val resultado: String,
    val motivo: String,
    val comentario: String,
    val autor: String?,
    /** Cuándo llegó al servidor. */
    val fecha: String,
    /** Cuándo se revisó en el teléfono; null en las revisiones anteriores a este dato. */
    val revisadaEn: String? = null,
    /** La persona de ese teléfono ("Laura"); un servidor anterior no la manda. */
    val persona: String? = null,
)

/** Cuerpo de `POST /alertas/{id}/revision`. */
data class RevisionEntradaDto(
    val accionId: String,
    val resultado: String,
    val motivo: String,
    val comentario: String,
    val versionVista: Int?,
    val forzar: Boolean,
    /** Cuándo se revisó en el teléfono (ISO 8601 en UTC): sin conexión sale bastante después. */
    val revisadaEn: String? = null,
)

/** Cuerpo de un 409: la API lo manda dentro de `detail`. */
data class ConflictoDto(
    val mensaje: String?,
    val revisionAjena: RevisionDto?,
    val alerta: AlertaDto?,
)

data class DetalleConflictoDto(
    val detail: ConflictoDto?,
)

data class PareoEntradaDto(
    val codigo: String,
    val nombreDispositivo: String,
)

data class PareoSalidaDto(
    val token: String,
    val red: RedDto,
    val vence: String,
    val dispositivoId: String,
    /** Cuándo se emitió el token. Un servidor anterior puede no mandarlo. */
    val emitido: String? = null,
    /** Alertas de los últimos 30 días que tiene la red para descargar (Figma 35-692). */
    val alertas30Dias: Int = 0,
    /** `administrador` o `analista` (API 0.6.0); un servidor anterior no lo manda y todos son administradores. */
    val perfil: String? = null,
    val persona: String? = null,
)

/** Una persona del equipo (`GET /personas`). */
data class PersonaDto(
    val id: String,
    val nombre: String?,
    val telefono: String,
    val perfil: String,
    val ultimoUso: String?,
    val vos: Boolean,
)

/** Cuerpo de `POST /personas/codigo`. */
data class CodigoPersonaEntradaDto(
    val nombre: String,
    val perfil: String,
)

data class CodigoPersonaDto(
    val codigo: String,
    val vence: String,
    val contenido: String,
    val nombre: String,
    val perfil: String,
)

/** Cuerpo de `PUT /personas/{id}/perfil`. */
data class PerfilEntradaDto(
    val perfil: String,
)

/** Un equipo de la red con su importancia (`GET /equipos`). */
data class EquipoDto(
    val ip: String,
    val nombre: String,
    val importancia: String,
)

/** Cuerpo de `PUT /equipos/{ip}`: la importancia y su motivo obligatorio. */
data class ImportanciaEntradaDto(
    val importancia: String,
    val motivo: String,
)

/** Cuerpo de `PUT /severidades` desde la API 0.6.0: las severidades y un solo motivo para todos los cambios. */
data class SeveridadesEntradaDto(
    val severidades: Map<String, String>,
    val motivo: String,
)

/** Una línea del registro de cambios (`GET /registro`). */
data class CambioRegistroDto(
    val id: Long,
    val fecha: String,
    val tipo: String,
    val autorId: String?,
    val autor: String,
    val objeto: String,
    val nombre: String?,
    val antes: String?,
    val despues: String?,
    val motivo: String?,
)

data class CatalogoDto(
    val equipos: Map<String, String>,
    val servicios: List<String>,
)

data class VinculoTelegramDto(
    val codigo: String,
    val enlaceBot: String,
    val vence: String?,
)

/** Cómo avisa Telegram cada severidad: `sonido`, `vibracion` o `sin_aviso`. */
data class AvisosDto(
    val alta: String,
    val media: String,
    val baja: String,
)

/** Cuerpo de `POST /telegram/vinculo`: los avisos que eligió la persona. */
data class VinculoEntradaDto(
    val avisos: AvisosDto?,
)

/** Respuesta de `GET /telegram`. */
data class EstadoTelegramDto(
    val vinculado: Boolean,
    val bot: String,
    val vinculadoEn: String?,
    val avisos: AvisosDto,
)
