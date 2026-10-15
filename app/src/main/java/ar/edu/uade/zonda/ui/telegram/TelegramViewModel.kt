package ar.edu.uade.zonda.ui.telegram

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ar.edu.uade.zonda.domain.model.ErrorZonda
import ar.edu.uade.zonda.domain.repository.PreferenciasRepository
import ar.edu.uade.zonda.domain.repository.RedesRepository
import ar.edu.uade.zonda.domain.usecase.VincularTelegram
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ViewModel de las pantallas de Telegram (RF05). Vincular tiene un paso afuera de la app: se abre el
 * chat con el bot, la persona toca Iniciar y el servidor asocia el chat. La app no se entera sola, así
 * que pregunta al volver y, mientras espera, cada [CADA_CUANTO_PREGUNTAR] milisegundos. Cuando el
 * servidor dice que quedó vinculado, la red cambia en la base y las pantallas cambian solas.
 */
class TelegramViewModel(
    private val redes: RedesRepository,
    private val vincular: VincularTelegram,
    preferencias: PreferenciasRepository,
    nombreDelTelefono: String,
    private val reloj: () -> Instant = Instant::now,
) : ViewModel() {

    private data class Controles(
        /** Hasta cuándo vale el código que se mandó a Telegram; null si no se está esperando. */
        val esperandoHasta: Instant? = null,
        val ocupado: Boolean = false,
        val enlace: String? = null,
        val aviso: AvisoTelegram? = null,
    )

    private val controles = MutableStateFlow(Controles())

    val estado: StateFlow<TelegramUiState> = combine(redes.observarRedActiva(), preferencias.observarAvisos(), controles) { red, avisos, c ->
        TelegramUiState(
            red = red,
            avisos = avisos,
            nombreDelTelefono = nombreDelTelefono,
            esperandoInicio = c.esperandoHasta != null && red?.telegramVinculado != true,
            ocupado = c.ocupado,
            enlaceParaAbrir = c.enlace,
            aviso = c.aviso,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TelegramUiState(nombreDelTelefono = nombreDelTelefono))

    init {
        // Al abrir: el usuario del bot y si ya está vinculado (pudo vincularse desde otro teléfono).
        comprobar()
    }

    /** "Abrir Telegram": un código nuevo cada vez (vence a los 10 minutos) y el enlace para abrir el bot. */
    fun abrirTelegram() {
        if (controles.value.ocupado) return
        viewModelScope.launch {
            val red = redes.observarRedActiva().first() ?: return@launch
            controles.update { it.copy(ocupado = true) }
            try {
                val vinculo = vincular.pedirEnlace(red.id)
                controles.update { it.copy(ocupado = false, enlace = vinculo.enlaceBot, esperandoHasta = reloj().plus(VALIDEZ_DEL_CODIGO)) }
            } catch (e: ErrorZonda) {
                controles.update { it.copy(ocupado = false, aviso = AvisoTelegram.Error(e)) }
            }
        }
    }

    fun enlaceAbierto() {
        controles.update { it.copy(enlace = null) }
    }

    fun noSePudoAbrir() {
        controles.update { it.copy(enlace = null, esperandoHasta = null, aviso = AvisoTelegram.NoSeAbrio) }
    }

    /** Pregunta al servidor si el chat quedó vinculado, sin avisar nada: sin conexión se vuelve a preguntar. */
    fun comprobar() {
        viewModelScope.launch { comprobarAhora() }
    }

    private suspend fun comprobarAhora() {
        val red = redes.observarRedActiva().first() ?: return
        try {
            vincular.comprobar(red.id)
        } catch (e: ErrorZonda) {
            // Se vuelve a preguntar.
        }
    }

    /**
     * Mientras se espera que la persona toque Iniciar: pregunta enseguida y después cada
     * [CADA_CUANTO_PREGUNTAR] milisegundos, hasta que queda vinculado o vence el código. La pantalla lo
     * corre solo mientras está al frente, así que también pregunta apenas la persona vuelve de Telegram.
     */
    suspend fun esperarVinculo() {
        while (true) {
            val hasta = controles.value.esperandoHasta ?: return
            if (reloj() > hasta) {
                controles.update { it.copy(esperandoHasta = null) }
                return
            }
            comprobarAhora()
            if (redes.observarRedActiva().first()?.telegramVinculado == true) {
                controles.update { it.copy(esperandoHasta = null) }
                return
            }
            delay(CADA_CUANTO_PREGUNTAR)
        }
    }

    /** "Enviar un aviso de prueba" (Figma 66-3484). */
    fun enviarPrueba() = conLaRed { redId ->
        vincular.enviarPrueba(redId)
        AvisoTelegram.PruebaEnviada
    }

    /** "Desvincular Telegram": la pantalla pasa a Conectar Telegram con el aviso. */
    fun desvincular() = conLaRed { redId ->
        vincular.desvincular(redId)
        AvisoTelegram.Desvinculado
    }

    fun avisoMostrado() {
        controles.update { it.copy(aviso = null) }
    }

    private fun conLaRed(accion: suspend (redId: String) -> AvisoTelegram) {
        if (controles.value.ocupado) return
        viewModelScope.launch {
            val red = redes.observarRedActiva().first() ?: return@launch
            controles.update { it.copy(ocupado = true) }
            val aviso = try {
                accion(red.id)
            } catch (e: ErrorZonda) {
                AvisoTelegram.Error(e)
            }
            controles.update { it.copy(ocupado = false, aviso = aviso) }
        }
    }

    companion object {
        /** El código de vinculación vence a los 10 minutos, como el de pareo (backend, `Config.minutos_codigo`). */
        val VALIDEZ_DEL_CODIGO: Duration = Duration.ofMinutes(10)
        const val CADA_CUANTO_PREGUNTAR = 3_000L
    }
}
