package ar.edu.uade.zonda.ui.telegram

import ar.edu.uade.zonda.domain.model.ErrorZonda
import ar.edu.uade.zonda.domain.model.PreferenciasAvisos
import ar.edu.uade.zonda.domain.model.Red

/** Estado de las pantallas de Telegram (RF05; Figma 50-942, 50-1003, 66-4205 y 66-3484). */
data class TelegramUiState(
    /** La red activa; null mientras se lee. */
    val red: Red? = null,
    /** Cómo avisa cada severidad en este teléfono ("Cómo avisa" y "Alta con sonido · …"). */
    val avisos: PreferenciasAvisos = PreferenciasAvisos(),
    val nombreDelTelefono: String = "",
    /** Se abrió Telegram con un código: la pantalla pregunta si ya quedó vinculado mientras está al frente. */
    val esperandoInicio: Boolean = false,
    /** Pidiendo el código, mandando la prueba o desvinculando: el botón no se vuelve a tocar. */
    val ocupado: Boolean = false,
    /** El enlace del bot que la pantalla tiene que abrir, una sola vez. */
    val enlaceParaAbrir: String? = null,
    val aviso: AvisoTelegram? = null,
) {
    val vinculado: Boolean
        get() = red?.telegramVinculado == true
}

/** Avisos inferiores de un solo uso. */
sealed interface AvisoTelegram {
    data object PruebaEnviada : AvisoTelegram

    data object Desvinculado : AvisoTelegram

    /** El teléfono no pudo abrir el enlace del bot. */
    data object NoSeAbrio : AvisoTelegram

    data class Error(val error: ErrorZonda) : AvisoTelegram
}
