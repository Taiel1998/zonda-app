package ar.edu.uade.zonda.domain.usecase

import ar.edu.uade.zonda.domain.model.VinculoTelegram
import ar.edu.uade.zonda.domain.repository.RedesRepository

/**
 * Vincular el chat de Telegram con este teléfono (RF05): pide al servidor un código de un uso y el
 * enlace al bot; al tocar Iniciar en Telegram, el bot recibe el código y el servidor asocia el chat.
 */
class VincularTelegram(
    private val redes: RedesRepository,
) {
    suspend fun pedirCodigo(redId: String): VinculoTelegram = redes.pedirVinculoTelegram(redId)

    suspend fun marcarVinculado(redId: String) = redes.marcarTelegramVinculado(redId, true)
}
