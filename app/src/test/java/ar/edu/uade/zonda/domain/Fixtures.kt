package ar.edu.uade.zonda.domain

import ar.edu.uade.zonda.domain.model.AccionPendiente
import ar.edu.uade.zonda.domain.model.Alerta
import ar.edu.uade.zonda.domain.model.Equipo
import ar.edu.uade.zonda.domain.model.EstadoEnvio
import ar.edu.uade.zonda.domain.model.EstadoRevision
import ar.edu.uade.zonda.domain.model.MetodoDeteccion
import ar.edu.uade.zonda.domain.model.Motivo
import ar.edu.uade.zonda.domain.model.OrigenRevision
import ar.edu.uade.zonda.domain.model.Red
import ar.edu.uade.zonda.domain.model.ResultadoRevision
import ar.edu.uade.zonda.domain.model.Revision
import ar.edu.uade.zonda.domain.model.Severidad
import ar.edu.uade.zonda.domain.model.TipoAccion
import ar.edu.uade.zonda.domain.model.TipoAtaque
import ar.edu.uade.zonda.domain.model.VeredictoIa
import ar.edu.uade.zonda.domain.model.VeredictoReglas
import ar.edu.uade.zonda.domain.model.VeredictoUmbrales
import ar.edu.uade.zonda.domain.model.Veredictos
import java.time.Duration
import java.time.Instant

/** Datos de prueba con valores por defecto razonables; cada test cambia solo lo que le importa. */
object Fixtures {

    val AHORA: Instant = Instant.parse("2026-10-12T12:00:00Z")

    fun red(
        id: String = "red-1",
        activa: Boolean = true,
        ultimaSincronizacion: Instant? = AHORA,
    ) = Red(
        id = id,
        nombre = "Oficina",
        urlBase = "https://zonda-equipo.duckdns.org",
        cursor = null,
        ultimaSincronizacion = ultimaSincronizacion,
        telegramVinculado = false,
        activa = activa,
    )

    fun revision(
        resultado: ResultadoRevision = ResultadoRevision.ATAQUE_REAL,
        motivo: Motivo = Motivo.ESCALADO_AL_PROVEEDOR,
        fecha: Instant = AHORA,
        origen: OrigenRevision = OrigenRevision.ESTE_TELEFONO,
    ) = Revision(
        resultado = resultado,
        motivo = motivo,
        comentario = "Comentario de prueba",
        autor = null,
        fecha = fecha,
        origen = origen,
    )

    fun alerta(
        id: String = "a-1",
        redId: String = "red-1",
        severidad: Severidad = Severidad.ALTA,
        tipoAtaque: TipoAtaque = TipoAtaque.FUERZA_BRUTA,
        creadaEn: Instant = AHORA.minus(Duration.ofHours(1)),
        inicio: Instant = creadaEn.minus(Duration.ofMinutes(10)),
        revision: Revision? = null,
        metodos: Set<MetodoDeteccion> = setOf(MetodoDeteccion.IA),
        servicio: String = "ssh",
        destinoIp: String = "192.168.10.50",
        envio: EstadoEnvio? = null,
    ) = Alerta(
        id = id,
        redId = redId,
        creadaEn = creadaEn,
        inicio = inicio,
        fin = creadaEn.minus(Duration.ofMinutes(1)),
        cantidadFlujos = 65,
        uidReferencia = "CxYz1",
        origen = Equipo("192.168.10.9", "pc-09"),
        destino = Equipo(destinoIp, "srv-web"),
        servicio = servicio,
        puerto = 22,
        puertoReferencia = 22,
        puertosDistintos = 1,
        tipoAtaque = tipoAtaque,
        confianzaTipo = 0.98,
        severidad = severidad,
        veredictos = Veredictos(
            ia = VeredictoIa(MetodoDeteccion.IA in metodos, 0.0412, 0.00306383),
            umbrales = VeredictoUmbrales(MetodoDeteccion.UMBRALES in metodos, 65, 2, false),
            reglas = VeredictoReglas(MetodoDeteccion.REGLAS in metodos, "$destinoIp:22", true),
        ),
        estadoRevision = revision?.let { EstadoRevision.de(it.resultado) } ?: EstadoRevision.SIN_REVISAR,
        revisionActual = revision,
        historial = listOfNotNull(revision),
        envio = envio,
        version = 1,
        actualizadaEn = creadaEn,
    )

    fun accion(
        id: String = "accion-1",
        redId: String = "red-1",
        alertaId: String? = "a-1",
        revision: Revision? = revision(),
        estado: EstadoEnvio = EstadoEnvio.PENDIENTE,
    ) = AccionPendiente(
        id = id,
        redId = redId,
        tipo = if (alertaId == null) TipoAccion.CAMBIAR_SEVERIDADES else TipoAccion.REVISAR_ALERTA,
        alertaId = alertaId,
        revision = revision,
        severidades = null,
        versionVista = 1,
        intentos = 0,
        estado = estado,
        creadaEn = AHORA,
        ultimoError = null,
        revisionAjena = null,
    )
}
