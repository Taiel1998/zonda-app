package ar.edu.uade.zonda.data.mapper

import ar.edu.uade.zonda.data.local.entidades.AccionPendienteEntity
import ar.edu.uade.zonda.data.local.entidades.AlertaConDetalles
import ar.edu.uade.zonda.data.local.entidades.AlertaEntity
import ar.edu.uade.zonda.data.local.entidades.RedEntity
import ar.edu.uade.zonda.data.local.entidades.RevisionEntity
import ar.edu.uade.zonda.data.local.entidades.SeveridadEntity
import ar.edu.uade.zonda.domain.model.AccionPendiente
import ar.edu.uade.zonda.domain.model.Alerta
import ar.edu.uade.zonda.domain.model.Equipo
import ar.edu.uade.zonda.domain.model.EstadoEnvio
import ar.edu.uade.zonda.domain.model.EstadoRevision
import ar.edu.uade.zonda.domain.model.Motivo
import ar.edu.uade.zonda.domain.model.OrigenRevision
import ar.edu.uade.zonda.domain.model.Red
import ar.edu.uade.zonda.domain.model.ResultadoRevision
import ar.edu.uade.zonda.domain.model.Revision
import ar.edu.uade.zonda.domain.model.Severidad
import ar.edu.uade.zonda.domain.model.SeveridadPorTipo
import ar.edu.uade.zonda.domain.model.TipoAccion
import ar.edu.uade.zonda.domain.model.TipoAtaque
import ar.edu.uade.zonda.domain.model.VeredictoIa
import ar.edu.uade.zonda.domain.model.VeredictoReglas
import ar.edu.uade.zonda.domain.model.VeredictoUmbrales
import ar.edu.uade.zonda.domain.model.Veredictos
import java.time.Instant

// Conversores entre las entidades de Room y el modelo del dominio. Las fechas viajan como
// milisegundos desde 1970 y los enums por nombre; la interfaz nunca ve una entidad.

fun Instant.aMilis(): Long = toEpochMilli()

fun Long.aInstant(): Instant = Instant.ofEpochMilli(this)

fun RedEntity.aDominio(): Red = Red(
    id = id,
    nombre = nombre,
    urlBase = urlBase,
    cursor = cursor,
    ultimaSincronizacion = ultimaSincronizacion?.aInstant(),
    telegramVinculado = telegramVinculado,
    activa = activa,
)

fun Red.aEntidad(creadaEn: Instant): RedEntity = RedEntity(
    id = id,
    nombre = nombre,
    urlBase = urlBase,
    cursor = cursor,
    ultimaSincronizacion = ultimaSincronizacion?.aMilis(),
    telegramVinculado = telegramVinculado,
    activa = activa,
    creadaEn = creadaEn.aMilis(),
)

fun RevisionEntity.aDominio(): Revision = Revision(
    resultado = ResultadoRevision.valueOf(resultado),
    motivo = Motivo.valueOf(motivo),
    comentario = comentario,
    autor = autor,
    fecha = fecha.aInstant(),
    origen = OrigenRevision.valueOf(origen),
)

fun Revision.aEntidad(alertaId: String): RevisionEntity = RevisionEntity(
    alertaId = alertaId,
    resultado = resultado.name,
    motivo = motivo.name,
    comentario = comentario,
    autor = autor,
    fecha = fecha.aMilis(),
    origen = origen.name,
)

fun AlertaConDetalles.aDominio(): Alerta {
    val historial = revisiones.sortedBy { it.fecha }.map { it.aDominio() }
    return Alerta(
        id = alerta.id,
        redId = alerta.redId,
        creadaEn = alerta.creadaEn.aInstant(),
        inicio = alerta.inicio.aInstant(),
        fin = alerta.fin.aInstant(),
        cantidadFlujos = alerta.cantidadFlujos,
        uidReferencia = alerta.uidReferencia,
        origen = Equipo(alerta.origenIp, alerta.origenNombre),
        destino = Equipo(alerta.destinoIp, alerta.destinoNombre),
        servicio = alerta.servicio,
        puerto = alerta.puerto,
        puertoReferencia = alerta.puertoReferencia,
        puertosDistintos = alerta.puertosDistintos,
        tipoAtaque = TipoAtaque.valueOf(alerta.tipoAtaque),
        confianzaTipo = alerta.confianzaTipo,
        severidad = Severidad.valueOf(alerta.severidad),
        veredictos = Veredictos(
            ia = VeredictoIa(alerta.veredictoIa, alerta.errorReconstruccion, alerta.umbralIa),
            umbrales = VeredictoUmbrales(alerta.veredictoUmbrales, alerta.contexto60s, alerta.umbralServicio, alerta.servicioSinLineaBase),
            reglas = VeredictoReglas(alerta.veredictoReglas, alerta.parDestino, alerta.parEnCatalogo),
        ),
        estadoRevision = EstadoRevision.valueOf(alerta.estadoRevision),
        revisionActual = historial.lastOrNull(),
        historial = historial,
        envio = accion?.let { EstadoEnvio.valueOf(it.estado) },
        version = alerta.version,
        actualizadaEn = alerta.actualizadaEn.aInstant(),
    )
}

fun Alerta.aEntidad(): AlertaEntity = AlertaEntity(
    id = id,
    redId = redId,
    creadaEn = creadaEn.aMilis(),
    inicio = inicio.aMilis(),
    fin = fin.aMilis(),
    cantidadFlujos = cantidadFlujos,
    uidReferencia = uidReferencia,
    origenIp = origen.ip,
    origenNombre = origen.nombre,
    destinoIp = destino.ip,
    destinoNombre = destino.nombre,
    servicio = servicio,
    puerto = puerto,
    puertoReferencia = puertoReferencia,
    puertosDistintos = puertosDistintos,
    tipoAtaque = tipoAtaque.name,
    confianzaTipo = confianzaTipo,
    severidad = severidad.name,
    veredictoIa = veredictos.ia.anomalo,
    errorReconstruccion = veredictos.ia.errorReconstruccion,
    umbralIa = veredictos.ia.umbral,
    veredictoUmbrales = veredictos.umbrales.anomalo,
    contexto60s = veredictos.umbrales.contexto60s,
    umbralServicio = veredictos.umbrales.umbralServicio,
    servicioSinLineaBase = veredictos.umbrales.servicioSinLineaBase,
    veredictoReglas = veredictos.reglas.anomalo,
    parDestino = veredictos.reglas.parDestino,
    parEnCatalogo = veredictos.reglas.enCatalogo,
    estadoRevision = estadoRevision.name,
    version = version,
    actualizadaEn = actualizadaEn.aMilis(),
)

fun AccionPendienteEntity.aDominio(): AccionPendiente = AccionPendiente(
    id = id,
    redId = redId,
    tipo = TipoAccion.valueOf(tipo),
    alertaId = alertaId,
    revision = if (resultado != null && motivo != null && comentario != null && fechaRevision != null) {
        Revision(
            resultado = ResultadoRevision.valueOf(resultado),
            motivo = Motivo.valueOf(motivo),
            comentario = comentario,
            autor = null,
            fecha = fechaRevision.aInstant(),
            origen = OrigenRevision.ESTE_TELEFONO,
        )
    } else {
        null
    },
    severidades = severidades?.let(SeveridadesCodec::decodificar),
    versionVista = versionVista,
    intentos = intentos,
    estado = EstadoEnvio.valueOf(estado),
    creadaEn = creadaEn.aInstant(),
    ultimoError = ultimoError,
    revisionAjena = if (ajenaResultado != null && ajenaMotivo != null && ajenaFecha != null) {
        Revision(
            resultado = ResultadoRevision.valueOf(ajenaResultado),
            motivo = Motivo.valueOf(ajenaMotivo),
            comentario = ajenaComentario.orEmpty(),
            autor = ajenaAutor,
            fecha = ajenaFecha.aInstant(),
            origen = OrigenRevision.SERVIDOR,
        )
    } else {
        null
    },
)

fun AccionPendiente.aEntidad(): AccionPendienteEntity = AccionPendienteEntity(
    id = id,
    redId = redId,
    tipo = tipo.name,
    alertaId = alertaId,
    resultado = revision?.resultado?.name,
    motivo = revision?.motivo?.name,
    comentario = revision?.comentario,
    fechaRevision = revision?.fecha?.aMilis(),
    severidades = severidades?.let(SeveridadesCodec::codificar),
    versionVista = versionVista,
    intentos = intentos,
    estado = estado.name,
    creadaEn = creadaEn.aMilis(),
    ultimoError = ultimoError,
    ajenaResultado = revisionAjena?.resultado?.name,
    ajenaMotivo = revisionAjena?.motivo?.name,
    ajenaComentario = revisionAjena?.comentario,
    ajenaAutor = revisionAjena?.autor,
    ajenaFecha = revisionAjena?.fecha?.aMilis(),
)

/** Sin filas guardadas rige la severidad recomendada. */
fun List<SeveridadEntity>.aDominio(): SeveridadPorTipo =
    if (isEmpty()) {
        SeveridadPorTipo.RECOMENDADA
    } else {
        SeveridadPorTipo(associate { TipoAtaque.valueOf(it.tipoAtaque) to Severidad.valueOf(it.severidad) })
    }

fun SeveridadPorTipo.aEntidades(redId: String): List<SeveridadEntity> =
    valores.map { (tipo, severidad) -> SeveridadEntity(redId, tipo.name, severidad.name) }
