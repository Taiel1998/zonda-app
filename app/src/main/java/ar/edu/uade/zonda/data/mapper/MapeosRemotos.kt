package ar.edu.uade.zonda.data.mapper

import ar.edu.uade.zonda.data.remote.AlertaDto
import ar.edu.uade.zonda.data.remote.AvisosDto
import ar.edu.uade.zonda.data.remote.CatalogoDto
import ar.edu.uade.zonda.data.remote.EstadoTelegramDto
import ar.edu.uade.zonda.data.remote.RevisionDto
import ar.edu.uade.zonda.data.remote.RevisionEntradaDto
import ar.edu.uade.zonda.data.remote.VinculoTelegramDto
import ar.edu.uade.zonda.domain.model.AccionPendiente
import ar.edu.uade.zonda.domain.model.Alerta
import ar.edu.uade.zonda.domain.model.Catalogo
import ar.edu.uade.zonda.domain.model.Equipo
import ar.edu.uade.zonda.domain.model.EstadoRevision
import ar.edu.uade.zonda.domain.model.EstadoTelegram
import ar.edu.uade.zonda.domain.model.ModoAviso
import ar.edu.uade.zonda.domain.model.Motivo
import ar.edu.uade.zonda.domain.model.OrigenRevision
import ar.edu.uade.zonda.domain.model.PreferenciasAvisos
import ar.edu.uade.zonda.domain.model.ResultadoRevision
import ar.edu.uade.zonda.domain.model.Revision
import ar.edu.uade.zonda.domain.model.Severidad
import ar.edu.uade.zonda.domain.model.SeveridadPorTipo
import ar.edu.uade.zonda.domain.model.TipoAtaque
import ar.edu.uade.zonda.domain.model.VeredictoIa
import ar.edu.uade.zonda.domain.model.VeredictoReglas
import ar.edu.uade.zonda.domain.model.VeredictoUmbrales
import ar.edu.uade.zonda.domain.model.Veredictos
import ar.edu.uade.zonda.domain.model.VinculoTelegram
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

// Conversores entre los DTO de la API y el dominio. Las enumeraciones viajan en minúsculas con guion
// bajo y acá se pasan al nombre del enum (`fuerza_bruta` ↔ FUERZA_BRUTA). Un valor que el dominio no
// conoce lanza IllegalArgumentException: la sincronización lo informa como error del servidor. Los
// motivos anteriores al Figma se aceptan con su nombre actual (Motivo.desdeNombre).

/** La API manda fechas como `2026-10-11T14:25:10+00:00`; `Instant.parse` de Android 8 no acepta ese sufijo. */
fun String.aInstante(): Instant = OffsetDateTime.parse(this).toInstant()

/** `2026-10-12T05:13:00Z`: ISO 8601 en UTC, como lo espera la API. */
fun Instant.aTextoIso(): String = DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(atOffset(ZoneOffset.UTC))

fun AlertaDto.aDominio(redId: String): Alerta {
    val historial = historial.map { it.aDominio() }
    return Alerta(
        id = id,
        redId = redId,
        creadaEn = creadaEn.aInstante(),
        inicio = inicio.aInstante(),
        fin = fin.aInstante(),
        cantidadFlujos = cantidadFlujos,
        uidReferencia = uidReferencia,
        origen = Equipo(origenIp, origenNombre),
        destino = Equipo(destinoIp, destinoNombre),
        servicio = servicio,
        protocolo = protocolo,
        puerto = puerto,
        puertoReferencia = puertoReferencia,
        puertosDistintos = puertosDistintos,
        tipoAtaque = tipoAtaque?.let { TipoAtaque.valueOf(it.uppercase()) } ?: TipoAtaque.sinLaIa(veredictos.reglas, veredictos.umbrales),
        confianzaTipo = confianzaTipo,
        severidad = Severidad.valueOf(severidad.uppercase()),
        veredictos = Veredictos(
            ia = VeredictoIa(veredictos.ia, veredictos.errorReconstruccion, veredictos.umbralIa),
            umbrales = VeredictoUmbrales(veredictos.umbrales, veredictos.contexto60s, veredictos.umbralServicio, veredictos.servicioSinLineaBase),
            reglas = VeredictoReglas(veredictos.reglas, veredictos.parDestino, veredictos.parEnCatalogo),
        ),
        estadoRevision = EstadoRevision.valueOf(estadoRevision.uppercase()),
        revisionActual = historial.lastOrNull(),
        historial = historial,
        envio = null,
        version = version,
        actualizadaEn = actualizadaEn.aInstante(),
        avisoTelegram = avisoTelegram?.aInstante(),
    )
}

/** Toda revisión que llega de la API es del servidor, aunque la haya hecho este mismo teléfono. */
fun RevisionDto.aDominio(): Revision = Revision(
    resultado = ResultadoRevision.valueOf(resultado.uppercase()),
    motivo = Motivo.desdeNombre(motivo.uppercase()),
    comentario = comentario,
    autor = autor,
    fecha = fecha.aInstante(),
    origen = OrigenRevision.SERVIDOR,
    revisadaEn = revisadaEn?.aInstante(),
)

/** La acción pendiente de revisar, como la espera `POST /alertas/{id}/revision`. Null si no tiene revisión. */
fun AccionPendiente.aRevisionEntrada(forzar: Boolean = false): RevisionEntradaDto? {
    val revision = revision ?: return null
    return RevisionEntradaDto(
        accionId = id,
        resultado = revision.resultado.name.lowercase(),
        motivo = revision.motivo.name.lowercase(),
        comentario = revision.comentario,
        versionVista = versionVista,
        forzar = forzar,
        revisadaEn = revision.fecha.aTextoIso(),
    )
}

/**
 * `{ "fuerza_bruta": "alta", ... }`. El tipo vacío va en dos claves, `servicio_no_autorizado` (las
 * reglas) y `volumen_inusual` (los umbrales), desde la 1.0.3 (cambio 9).
 */
fun SeveridadPorTipo.aDto(): Map<String, String> =
    valores.entries.associate { (tipo, severidad) -> tipo.name.lowercase() to severidad.name.lowercase() }

/** La clave única que tenía el tipo vacío en un servidor anterior a la 1.0.3. */
private const val CLAVE_SIN_TIPO_ANTERIOR = "solo_reglas_o_umbrales"

/**
 * Lo que manda `GET /severidades`. Un servidor anterior a la 1.0.3 todavía manda
 * `solo_reglas_o_umbrales`, que vale para los dos tipos que la reemplazan (si también vienen esos, ganan
 * ellos). Una clave que la app no conoce se ignora, así un servidor más nuevo no la rompe.
 */
fun Map<String, String>.aSeveridadPorTipo(): SeveridadPorTipo =
    SeveridadPorTipo(
        entries
            .sortedBy { it.key != CLAVE_SIN_TIPO_ANTERIOR }
            .flatMap { (clave, severidad) ->
                val tipos = if (clave == CLAVE_SIN_TIPO_ANTERIOR) {
                    TipoAtaque.NO_DETECTADOS_POR_LA_IA
                } else {
                    listOfNotNull(TipoAtaque.entries.find { it.name == clave.uppercase() })
                }
                tipos.map { it to Severidad.valueOf(severidad.uppercase()) }
            }
            .toMap(),
    )

fun CatalogoDto.aDominio(): Catalogo = Catalogo(equipos = equipos, servicios = servicios)

fun VinculoTelegramDto.aDominio(): VinculoTelegram = VinculoTelegram(codigo = codigo, enlaceBot = enlaceBot)

/**
 * `sonido`, `vibracion` y `sin_aviso` ↔ SONIDO, SILENCIO y SIN_AVISO. La API sigue llamando
 * `vibracion` al mensaje sin sonido; la app lo muestra como "En silencio" (cambio 6).
 */
fun PreferenciasAvisos.aDto(): AvisosDto = AvisosDto(
    alta = alta.enLaApi(),
    media = media.enLaApi(),
    baja = baja.enLaApi(),
)

fun AvisosDto.aDominio(): PreferenciasAvisos = PreferenciasAvisos(
    alta = modoDeLaApi(alta),
    media = modoDeLaApi(media),
    baja = modoDeLaApi(baja),
)

private const val SILENCIO_EN_LA_API = "vibracion"

private fun ModoAviso.enLaApi(): String = if (this == ModoAviso.SILENCIO) SILENCIO_EN_LA_API else name.lowercase()

private fun modoDeLaApi(valor: String): ModoAviso =
    if (valor.equals(SILENCIO_EN_LA_API, ignoreCase = true)) ModoAviso.SILENCIO else ModoAviso.valueOf(valor.uppercase())

fun EstadoTelegramDto.aDominio(): EstadoTelegram = EstadoTelegram(vinculado = vinculado, bot = bot, avisos = avisos.aDominio())
