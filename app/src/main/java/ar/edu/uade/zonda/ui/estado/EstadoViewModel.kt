package ar.edu.uade.zonda.ui.estado

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ar.edu.uade.zonda.domain.model.Alerta
import ar.edu.uade.zonda.domain.model.ErrorZonda
import ar.edu.uade.zonda.domain.model.EstadoEnvio
import ar.edu.uade.zonda.domain.model.EstadoRed
import ar.edu.uade.zonda.domain.model.FiltroAlertas
import ar.edu.uade.zonda.domain.model.FiltroEstado
import ar.edu.uade.zonda.domain.model.Periodo
import ar.edu.uade.zonda.domain.model.ProgresoDescarga
import ar.edu.uade.zonda.domain.model.Semaforo
import ar.edu.uade.zonda.domain.model.Severidad
import ar.edu.uade.zonda.domain.repository.AccionesPendientesRepository
import ar.edu.uade.zonda.domain.repository.AlertasRepository
import ar.edu.uade.zonda.domain.repository.Conectividad
import ar.edu.uade.zonda.domain.repository.RedesRepository
import ar.edu.uade.zonda.domain.usecase.ObservarAlertas
import ar.edu.uade.zonda.domain.usecase.ObtenerEstadoRed
import ar.edu.uade.zonda.domain.usecase.SincronizarAlertas
import ar.edu.uade.zonda.ui.ContextoTarjetas
import ar.edu.uade.zonda.ui.observarContextoTarjetas
import java.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ViewModel de la pantalla Estado (RF01). Junta en un solo [StateFlow] el semáforo y los contadores de
 * la red activa ([ObtenerEstadoRed]), la lista de las alertas sin revisar, la primera descarga y la
 * conexión, y recibe lo que hace la persona: tocar un contador, sincronizar y cerrar un aviso.
 *
 * Todo se observa desde la base local: con datos ya sincronizados el estado aparece enseguida, también
 * sin conexión (criterios 1 y 2), y cuando se revisa la última alta el semáforo cambia solo.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class EstadoViewModel(
    obtenerEstadoRed: ObtenerEstadoRed,
    observarAlertas: ObservarAlertas,
    private val sincronizarAlertas: SincronizarAlertas,
    private val redes: RedesRepository,
    alertas: AlertasRepository,
    private val conectividad: Conectividad,
    acciones: AccionesPendientesRepository,
) : ViewModel() {

    /**
     * Qué sincronización hay en curso. La automática (al abrir o al volver la conexión) no se nota: los
     * datos cambian solos. La que pide la persona hace girar el ícono y avisa con la fecha y la hora.
     */
    private enum class Sincronizacion { NINGUNA, AUTOMATICA, PEDIDA }

    private val severidadElegida = MutableStateFlow<Severidad?>(null)
    private val sincronizacion = MutableStateFlow(Sincronizacion.NINGUNA)
    private val aviso = MutableStateFlow<AvisoEstado?>(null)

    /** Cómo va la primera descarga de la red activa (la barra del Figma 50-616); null si no hay ninguna. */
    private val progreso: Flow<ProgresoDescarga?> = redes.observarRedActiva().flatMapLatest { red ->
        if (red == null) flowOf(null) else alertas.observarProgresoDescarga(red.id)
    }

    /**
     * Las alertas de la red activa con una revisión de este teléfono que no se aplicó porque otra persona
     * la revisó antes (cambio 7). Salen de la cola: el aviso se va al elegir "Aplicar la mía" o
     * "Mantener la otra", porque la acción deja de estar en conflicto.
     */
    private val noAplicadas: Flow<List<String>> = redes.observarRedActiva().flatMapLatest { red ->
        if (red == null) {
            flowOf(emptyList())
        } else {
            acciones.observar(red.id).map { cola -> cola.filter { it.estado == EstadoEnvio.CONFLICTO }.mapNotNull { it.alertaId } }
        }
    }

    private data class Datos(
        val red: EstadoRed?,
        val sinRevisar: List<Alerta>,
        val progreso: ProgresoDescarga?,
        val noAplicadas: List<String>,
        val contexto: ContextoTarjetas,
    )

    private data class Controles(
        val conectado: Boolean,
        val elegida: Severidad?,
        val sincronizacion: Sincronizacion,
        val aviso: AvisoEstado?,
    )

    val estado: StateFlow<EstadoUiState> = combine(
        combine(obtenerEstadoRed(), observarAlertas(SIN_REVISAR_GUARDADAS), progreso, noAplicadas, redes.observarContextoTarjetas()) { red, sinRevisar, progreso, noAplicadas, contexto ->
            Datos(red, sinRevisar, progreso, noAplicadas, contexto)
        },
        combine(conectividad.conectado, severidadElegida, sincronizacion, aviso) { conectado, elegida, sincronizacion, aviso ->
            Controles(conectado, elegida, sincronizacion, aviso)
        },
    ) { datos, controles ->
        EstadoUiState(
            contenido = contenido(datos.red, datos.sinRevisar, datos.progreso, controles.elegida),
            severidadElegida = controles.elegida,
            datosDe = datos.red?.datosDe,
            sinConexion = !controles.conectado,
            accesoVencido = datos.red?.red?.accesoVencido == true,
            sincronizando = controles.sincronizacion == Sincronizacion.PEDIDA,
            aviso = controles.aviso,
            revisionesNoAplicadas = datos.noAplicadas,
            asignadasAMi = datos.sinRevisar.filter { datos.contexto.esMia(it) },
            contexto = datos.contexto,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), EstadoUiState())

    init {
        // Estado es lo primero que se ve al abrir la app: con una red pareada y conexión, se traen las
        // novedades en segundo plano (informe, 14.2). Si la primera descarga del pareo sigue en curso, esta
        // espera a que termine, porque el sincronizador hace una por vez.
        viewModelScope.launch {
            if (redes.observarRedActiva().first() != null && conectividad.hayConexion()) sincronizarSola()
        }
        viewModelScope.launch {
            // Cuando vuelve la conexión se envía lo pendiente y se traen las novedades.
            conectividad.conectado.drop(1).filter { it }.collect {
                if (redes.observarRedActiva().first() != null) sincronizarSola()
            }
        }
    }

    /**
     * Tocar un contador, o el recuadro del semáforo de su color (corrección 9, decisión 44), deja en la
     * lista solo las sin revisar de esa severidad; tocarlo de nuevo, todas (criterio 4).
     */
    fun tocarContador(severidad: Severidad) {
        severidadElegida.update { elegida -> if (elegida == severidad) null else severidad }
    }

    /**
     * El ícono de sincronizar o deslizar la lista hacia abajo. Si ya hay una automática en curso no se
     * lanza otra: esa pasa a ser la de la persona, así el ícono gira y al terminar avisa (criterio 5).
     */
    fun sincronizar() {
        when (sincronizacion.value) {
            Sincronizacion.NINGUNA -> lanzar(Sincronizacion.PEDIDA)
            Sincronizacion.AUTOMATICA -> sincronizacion.value = Sincronizacion.PEDIDA
            Sincronizacion.PEDIDA -> Unit
        }
    }

    fun avisoMostrado() {
        aviso.value = null
    }

    private fun sincronizarSola() {
        if (sincronizacion.value == Sincronizacion.NINGUNA) lanzar(Sincronizacion.AUTOMATICA)
    }

    private fun lanzar(tipo: Sincronizacion) {
        sincronizacion.value = tipo
        viewModelScope.launch {
            try {
                val resultado = sincronizarAlertas()
                if (resultado != null && sincronizacion.value == Sincronizacion.PEDIDA) {
                    aviso.value = AvisoEstado.Sincronizado(resultado.hora, resultado.nuevas)
                }
            } catch (e: ErrorZonda) {
                // La automática no avisa la falta de conexión, que ya muestra el banner; lo demás sí.
                if (sincronizacion.value == Sincronizacion.PEDIDA || e !is ErrorZonda.SinConexion) aviso.value = AvisoEstado.Error(e)
            } finally {
                sincronizacion.value = Sincronizacion.NINGUNA
            }
        }
    }

    companion object {
        /**
         * Todas las sin revisar guardadas en el teléfono, sin cortar por período: son las mismas que
         * cuentan los contadores. El contador elegido se aplica sobre esta lista, sin otra consulta.
         */
        val SIN_REVISAR_GUARDADAS = FiltroAlertas(estado = FiltroEstado.SIN_REVISAR, periodo = Periodo.TODO)

        /**
         * Qué muestra la pantalla. Sin datos manda sobre lo demás: mientras la red no se sincronizó nunca,
         * no hay semáforo de colores (criterio 3); si la primera descarga está en curso, se ve cómo va.
         */
        fun contenido(red: EstadoRed?, sinRevisar: List<Alerta>, progreso: ProgresoDescarga?, elegida: Severidad?): ContenidoEstado = when {
            red == null -> ContenidoEstado.SinRed
            red.semaforo == Semaforo.SIN_DATOS && progreso != null -> ContenidoEstado.Descargando(progreso.descargadas, progreso.total)
            red.semaforo == Semaforo.SIN_DATOS -> ContenidoEstado.SinDatos
            else -> ContenidoEstado.Datos(
                semaforo = red.semaforo,
                sinRevisar = red.sinRevisar,
                ultimaDeteccion = ultimaDeteccion(red.semaforo, sinRevisar),
                alertas = if (elegida == null) sinRevisar else sinRevisar.filter { it.severidad == elegida },
            )
        }

        /**
         * "Última alta sin revisar: hace 5 min" (cambio 11): la hora de la sin revisar más reciente de la
         * severidad que pone el color, la misma que muestra su tarjeta. En rojo cuentan las altas y en
         * amarillo las medias: una baja nueva no puede hacer parecer reciente un semáforo rojo. En verde,
         * la baja más reciente si quedan bajas ("sin urgencias"); si no queda ninguna, null.
         */
        fun ultimaDeteccion(semaforo: Semaforo, sinRevisar: List<Alerta>): Instant? {
            val severidad = when (semaforo) {
                Semaforo.ROJO -> Severidad.ALTA
                Semaforo.AMARILLO -> Severidad.MEDIA
                Semaforo.VERDE -> Severidad.BAJA
                Semaforo.SIN_DATOS -> return null
            }
            return sinRevisar.filter { it.severidad == severidad }.maxOfOrNull { it.inicio }
        }
    }
}
