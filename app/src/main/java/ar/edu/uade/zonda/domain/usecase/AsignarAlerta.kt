package ar.edu.uade.zonda.domain.usecase

import ar.edu.uade.zonda.domain.model.AccionResponsable
import ar.edu.uade.zonda.domain.model.Asignacion
import ar.edu.uade.zonda.domain.model.ErrorZonda
import ar.edu.uade.zonda.domain.model.Persona
import ar.edu.uade.zonda.domain.repository.AccionesPendientesRepository
import ar.edu.uade.zonda.domain.repository.AlertasRepository
import ar.edu.uade.zonda.domain.repository.GeneradorDeIds
import ar.edu.uade.zonda.domain.repository.RedesRepository
import java.time.Instant
import kotlinx.coroutines.flow.first

/**
 * Asignar una alerta a una persona, o dejarla sin asignar (corrección 1, decisión 42). El administrador
 * asigna a cualquiera; el analista se asigna una sin asignar ("Me la asigno") o suelta la suya
 * ("Soltarla"). Funciona sin conexión, como las revisiones: se guarda en el teléfono al instante y
 * queda en la cola; si dos asignan a la vez, queda la última que llega al servidor. El servidor vuelve
 * a controlar las reglas de cada perfil.
 */
class AsignarAlerta(
    private val redes: RedesRepository,
    private val alertas: AlertasRepository,
    private val acciones: AccionesPendientesRepository,
    private val ids: GeneradorDeIds,
    private val reloj: () -> Instant = Instant::now,
) {
    /** [responsable] null deja la alerta sin asignar. */
    suspend operator fun invoke(redId: String, alertaId: String, responsable: Persona?) {
        val red = redes.observarRedes().first().firstOrNull { it.id == redId } ?: throw ErrorZonda.NoEncontrado()
        val alerta = alertas.observarAlerta(redId, alertaId).first() ?: throw ErrorZonda.NoEncontrado()
        val yo = redes.observarPersonas(redId).first().firstOrNull { it.vos }
        val miId = red.dispositivoId ?: yo?.id
        val actual = alerta.asignacion?.responsableId
        if (responsable?.id == actual) return
        val permitido = when (AccionResponsable.para(red.perfil, actual, miId)) {
            AccionResponsable.ASIGNAR, AccionResponsable.CAMBIAR -> true
            AccionResponsable.ASIGNARME -> responsable != null && responsable.id == miId
            AccionResponsable.SOLTARLA -> responsable == null
            AccionResponsable.NINGUNA -> false
        }
        if (!permitido || miId == null) throw ErrorZonda.SinPermiso()
        val ahora = reloj()
        alertas.aplicarAsignacionLocal(
            redId,
            alertaId,
            Asignacion(
                responsableId = responsable?.id,
                responsable = responsable?.nombreVisible,
                porId = miId,
                por = yo?.nombreVisible.orEmpty(),
                fecha = ahora,
            ),
        )
        acciones.encolarAsignacion(redId, alertaId, ids.nuevo(), responsable?.id, ahora)
    }
}
