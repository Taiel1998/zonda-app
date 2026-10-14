package ar.edu.uade.zonda.domain.usecase

import ar.edu.uade.zonda.domain.model.EstadoTelegram
import ar.edu.uade.zonda.domain.model.VinculoTelegram
import ar.edu.uade.zonda.domain.repository.PreferenciasRepository
import ar.edu.uade.zonda.domain.repository.RedesRepository
import kotlinx.coroutines.flow.first

/**
 * Vincular el chat de Telegram con este teléfono (RF05; Figma 50-942 y 66-3484): el servidor da un
 * código de un uso y el enlace al bot; al tocar Iniciar en Telegram, el bot recibe el código y el
 * servidor asocia el chat. La app no se entera sola: pregunta con [comprobar] al volver de Telegram.
 * También el aviso de prueba y desvincular.
 */
class VincularTelegram(
    private val redes: RedesRepository,
    private val preferencias: PreferenciasRepository,
) {
    /** El enlace que abre el chat con el bot, con un código nuevo. Manda los avisos que eligió la persona. */
    suspend fun pedirEnlace(redId: String): VinculoTelegram = redes.pedirVinculoTelegram(redId, preferencias.observarAvisos().first())

    /** Si el chat ya quedó vinculado; la red queda al día y las pantallas cambian solas. */
    suspend fun comprobar(redId: String): EstadoTelegram = redes.consultarTelegram(redId)

    suspend fun enviarPrueba(redId: String) = redes.enviarAvisoDePrueba(redId)

    suspend fun desvincular(redId: String) = redes.desvincularTelegram(redId)
}
