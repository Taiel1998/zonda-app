package ar.edu.uade.zonda.domain.usecase

import ar.edu.uade.zonda.domain.model.AccionPendiente
import ar.edu.uade.zonda.domain.model.ErrorZonda
import ar.edu.uade.zonda.domain.model.EstadoEnvio
import ar.edu.uade.zonda.domain.model.Motivo
import ar.edu.uade.zonda.domain.model.OrigenRevision
import ar.edu.uade.zonda.domain.model.ResultadoRevision
import ar.edu.uade.zonda.domain.model.Revision
import ar.edu.uade.zonda.domain.model.TipoAccion
import ar.edu.uade.zonda.domain.repository.AccionesPendientesRepository
import ar.edu.uade.zonda.domain.repository.AlertasRepository
import ar.edu.uade.zonda.domain.repository.GeneradorDeIds
import java.time.Instant
import kotlinx.coroutines.flow.first

/**
 * Registrar la revisión de una alerta (RF04): ataque real o falso positivo, con motivo y comentario.
 *
 * Funciona sin conexión: valida, guarda la revisión en el teléfono (la alerta se ve revisada al
 * instante, criterio 1) y encola una acción con id único para que la sincronización la envíe
 * (criterios 3 y 5). Si ya había una acción pendiente sobre la misma alerta, la nueva la reemplaza:
 * así "Cambiar revisión" antes de que salga la anterior no manda dos. El envío real y el conflicto
 * 409 los resuelve la sincronización (fase 4); el Deshacer de 5 segundos es [DeshacerRevision].
 */
class RevisarAlerta(
    private val alertas: AlertasRepository,
    private val acciones: AccionesPendientesRepository,
    private val ids: GeneradorDeIds,
    private val reloj: () -> Instant = Instant::now,
) {
    suspend operator fun invoke(
        redId: String,
        alertaId: String,
        resultado: ResultadoRevision,
        motivo: Motivo,
        comentario: String,
    ): AccionPendiente {
        val texto = comentario.trim()
        if (texto.isEmpty()) {
            throw ErrorZonda.Validacion("comentario", "Escribí un comentario breve sobre la revisión")
        }
        if (motivo.resultado != resultado) {
            throw ErrorZonda.Validacion("motivo", "El motivo no corresponde al resultado elegido")
        }
        val alerta = alertas.observarAlerta(redId, alertaId).first() ?: throw ErrorZonda.NoEncontrado()

        val ahora = reloj()
        val revision = Revision(
            resultado = resultado,
            motivo = motivo,
            comentario = texto,
            autor = null,
            fecha = ahora,
            origen = OrigenRevision.ESTE_TELEFONO,
        )
        alertas.aplicarRevisionLocal(redId, alertaId, revision)

        val accion = AccionPendiente(
            id = ids.nuevo(),
            redId = redId,
            tipo = TipoAccion.REVISAR_ALERTA,
            alertaId = alertaId,
            revision = revision,
            severidades = null,
            versionVista = alerta.version,
            intentos = 0,
            estado = EstadoEnvio.PENDIENTE,
            creadaEn = ahora,
            ultimoError = null,
            revisionAjena = null,
        )
        acciones.encolar(accion)
        return accion
    }
}
