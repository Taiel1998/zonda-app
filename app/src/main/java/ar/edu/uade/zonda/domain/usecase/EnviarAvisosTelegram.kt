package ar.edu.uade.zonda.domain.usecase

import ar.edu.uade.zonda.domain.model.ErrorZonda
import ar.edu.uade.zonda.domain.model.PreferenciasAvisos
import ar.edu.uade.zonda.domain.repository.RedesRepository
import kotlinx.coroutines.flow.first

/**
 * Manda a cada red pareada cómo avisa Telegram cada severidad en este teléfono, para que su bot lo use
 * desde el aviso siguiente (Figma 51-1688). Si una red no responde no pasa nada: la sincronización
 * compara lo que tiene el servidor con lo del teléfono y lo vuelve a mandar.
 */
class EnviarAvisosTelegram(
    private val redes: RedesRepository,
) {
    suspend operator fun invoke(avisos: PreferenciasAvisos) {
        for (red in redes.observarRedes().first()) {
            try {
                redes.enviarAvisosTelegram(red.id, avisos)
            } catch (e: ErrorZonda) {
                // Lo manda la sincronización siguiente.
            }
        }
    }
}
