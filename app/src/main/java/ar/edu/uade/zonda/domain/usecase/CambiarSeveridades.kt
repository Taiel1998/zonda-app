package ar.edu.uade.zonda.domain.usecase

import ar.edu.uade.zonda.domain.model.AccionPendiente
import ar.edu.uade.zonda.domain.model.ErrorZonda
import ar.edu.uade.zonda.domain.model.EstadoEnvio
import ar.edu.uade.zonda.domain.model.SeveridadPorTipo
import ar.edu.uade.zonda.domain.model.TipoAccion
import ar.edu.uade.zonda.domain.repository.AccionesPendientesRepository
import ar.edu.uade.zonda.domain.repository.GeneradorDeIds
import ar.edu.uade.zonda.domain.repository.RedesRepository
import java.time.Instant

/**
 * Elegir la severidad de cada tipo de ataque para una red (RF05 y RF06). Se guarda en el teléfono y
 * se encola `PUT /severidades`; si falla, queda pendiente y se reintenta (informe, sección 13.3).
 *
 * Desde la 1.0.5 lo hace un administrador y con un motivo, que queda en el registro de cambios con su
 * nombre y la hora (decisiones 39 y 40). Un mismo motivo vale para todos los cambios de ese "Guardar".
 */
class CambiarSeveridades(
    private val redes: RedesRepository,
    private val acciones: AccionesPendientesRepository,
    private val ids: GeneradorDeIds,
    private val reloj: () -> Instant = Instant::now,
) {
    suspend operator fun invoke(redId: String, severidades: SeveridadPorTipo, motivo: String): AccionPendiente {
        val texto = motivo.trim()
        if (texto.isEmpty()) throw ErrorZonda.Validacion("motivo", "Escribí por qué la cambiás")
        redes.guardarSeveridadesLocal(redId, severidades)
        val accion = AccionPendiente(
            id = ids.nuevo(),
            redId = redId,
            tipo = TipoAccion.CAMBIAR_SEVERIDADES,
            alertaId = null,
            revision = null,
            severidades = severidades,
            versionVista = null,
            intentos = 0,
            estado = EstadoEnvio.PENDIENTE,
            creadaEn = reloj(),
            ultimoError = null,
            revisionAjena = null,
            motivo = texto,
        )
        acciones.encolar(accion)
        return accion
    }
}
