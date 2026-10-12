package ar.edu.uade.zonda.domain.usecase

import ar.edu.uade.zonda.domain.model.AccionPendiente
import ar.edu.uade.zonda.domain.model.Motivo
import ar.edu.uade.zonda.domain.model.ResultadoRevision
import ar.edu.uade.zonda.domain.repository.AccionesPendientesRepository
import ar.edu.uade.zonda.domain.repository.AlertasRepository
import ar.edu.uade.zonda.domain.repository.GeneradorDeIds
import java.time.Instant

/**
 * Registrar la revisión de una alerta (RF04): ataque real o falso positivo, con motivo y comentario.
 * Funciona sin conexión: guarda la revisión en el teléfono y encola la acción para enviarla.
 *
 * LO ESCRIBE TAIEL. Lo que tiene que hacer, según RF04 del informe y la figura 24:
 *
 * 1. Validar. Si algo falla, lanzar [ar.edu.uade.zonda.domain.model.ErrorZonda.Validacion] con el
 *    nombre del campo:
 *    - el comentario no puede estar vacío (usar `trim()`); criterio de aceptación 2;
 *    - el motivo tiene que corresponder al resultado (`motivo.resultado == resultado`);
 *      los motivos válidos de un resultado se obtienen con [Motivo.para].
 * 2. Buscar la alerta con [AlertasRepository.observarAlerta] (tomar el primer valor con `first()`).
 *    Si es null, lanzar [ar.edu.uade.zonda.domain.model.ErrorZonda.NoEncontrado].
 * 3. Armar la [ar.edu.uade.zonda.domain.model.Revision] con `origen = ESTE_TELEFONO`, `autor = null`
 *    (lo completa el servidor) y `fecha = reloj()`.
 * 4. Guardarla con [AlertasRepository.aplicarRevisionLocal]: la alerta se ve revisada al instante
 *    (criterio 1) aunque se cierre la app.
 * 5. Encolar una [AccionPendiente] de tipo REVISAR_ALERTA con `id = ids.nuevo()` (el id único que la
 *    API no aplica dos veces, criterio 5), la revisión, `versionVista = alerta.version` (para que el
 *    servidor detecte si otra persona la revisó antes, criterio 4), `intentos = 0`, estado PENDIENTE.
 *    Si ya había una acción pendiente sobre la misma alerta, [AccionesPendientesRepository.encolar]
 *    la reemplaza: así "Cambiar revisión" antes de que salga la anterior no manda dos.
 * 6. Devolver la acción encolada. El Deshacer de 5 segundos la quita con [DeshacerRevision].
 *
 * El envío real al servidor no va acá: lo hace la sincronización (fase 4). Este caso de uso no
 * necesita saber si hay conexión.
 *
 * Los tests están en `RevisarAlertaTest` (los escribe Rodrigo).
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
        TODO("Lo escribe Taiel en la fase 1: ver el comentario de la clase")
    }
}
