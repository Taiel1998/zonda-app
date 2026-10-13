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
    val fecha: String,
)

/** Cuerpo de `POST /alertas/{id}/revision`. */
data class RevisionEntradaDto(
    val accionId: String,
    val resultado: String,
    val motivo: String,
    val comentario: String,
    val versionVista: Int?,
    val forzar: Boolean,
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
