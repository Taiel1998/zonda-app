package ar.edu.uade.zonda.domain.usecase

import ar.edu.uade.zonda.domain.model.AccionPendiente
import ar.edu.uade.zonda.domain.model.ErrorZonda
import ar.edu.uade.zonda.domain.model.EstadoEnvio
import ar.edu.uade.zonda.domain.model.Importancia
import ar.edu.uade.zonda.domain.model.TipoAccion
import ar.edu.uade.zonda.domain.repository.AccionesPendientesRepository
import ar.edu.uade.zonda.domain.repository.GeneradorDeIds
import ar.edu.uade.zonda.domain.repository.RedesRepository
import java.time.Instant

/**
 * Marcar qué tan importante es un equipo de la red (corrección 4, decisión 41): sube o baja un nivel la
 * severidad de sus alertas. Como las severidades, lo hace un administrador, con un motivo que queda en
 * el registro de cambios; se guarda en el teléfono y se encola `PUT /equipos/{ip}`.
 */
class CambiarImportancia(
    private val redes: RedesRepository,
    private val acciones: AccionesPendientesRepository,
    private val ids: GeneradorDeIds,
    private val reloj: () -> Instant = Instant::now,
) {
    suspend operator fun invoke(redId: String, ip: String, importancia: Importancia, motivo: String): AccionPendiente {
        val texto = motivo.trim()
        if (texto.isEmpty()) throw ErrorZonda.Validacion("motivo", "Escribí por qué la cambiás")
        redes.guardarImportanciaLocal(redId, ip, importancia)
        val accion = AccionPendiente(
            id = ids.nuevo(),
            redId = redId,
            tipo = TipoAccion.CAMBIAR_IMPORTANCIA,
            alertaId = null,
            revision = null,
            severidades = null,
            versionVista = null,
            intentos = 0,
            estado = EstadoEnvio.PENDIENTE,
            creadaEn = reloj(),
            ultimoError = null,
            revisionAjena = null,
            motivo = texto,
            equipoIp = ip,
            importancia = importancia,
        )
        acciones.encolar(accion)
        return accion
    }
}
