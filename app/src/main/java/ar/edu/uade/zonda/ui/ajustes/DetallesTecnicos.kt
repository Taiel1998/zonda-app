package ar.edu.uade.zonda.ui.ajustes

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import ar.edu.uade.zonda.R
import ar.edu.uade.zonda.domain.model.DetallesConexion
import ar.edu.uade.zonda.domain.model.ErrorZonda
import ar.edu.uade.zonda.domain.model.Red
import ar.edu.uade.zonda.domain.repository.RedesRepository
import ar.edu.uade.zonda.ui.Formato
import ar.edu.uade.zonda.ui.components.BarraSuperior
import ar.edu.uade.zonda.ui.components.FilaDato
import ar.edu.uade.zonda.ui.components.NotaAlPie
import ar.edu.uade.zonda.ui.components.TarjetaDatos
import ar.edu.uade.zonda.ui.viewModelDeZonda
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Lo que muestra Detalles técnicos: la red, el teléfono y lo que se leyó de la conexión. */
data class DetallesTecnicosUiState(
    val red: Red? = null,
    val nombreDelTelefono: String = "",
    val conexion: LecturaConexion = LecturaConexion.Leyendo,
)

/** La lectura del protocolo y el certificado, que necesita una llamada al servidor. */
sealed interface LecturaConexion {
    data object Leyendo : LecturaConexion
    data class Lista(val detalles: DetallesConexion) : LecturaConexion
    data class SinRespuesta(val error: ErrorZonda) : LecturaConexion
}

/**
 * ViewModel de Detalles técnicos (Figma 66-3316): lee una vez la conexión con el servidor de la red
 * activa. Sin conexión, muestra lo que se sabe sin preguntar (el servidor y el acceso guardado).
 */
class DetallesTecnicosViewModel(
    private val redes: RedesRepository,
    nombreDelTelefono: String,
) : ViewModel() {

    private val _estado = MutableStateFlow(DetallesTecnicosUiState(nombreDelTelefono = nombreDelTelefono))
    val estado: StateFlow<DetallesTecnicosUiState> = _estado.asStateFlow()

    init {
        viewModelScope.launch {
            val red = redes.observarRedActiva().first()
            _estado.update { it.copy(red = red) }
            if (red == null) return@launch
            val lectura = try {
                LecturaConexion.Lista(redes.detallesDeConexion(red.id))
            } catch (e: ErrorZonda) {
                LecturaConexion.SinRespuesta(e)
            }
            _estado.update { it.copy(conexion = lectura) }
        }
    }
}

@Composable
fun DetallesTecnicosScreen(
    alVolver: () -> Unit,
    viewModel: DetallesTecnicosViewModel = viewModelDeZonda { DetallesTecnicosViewModel(it.redes, it.nombreDelTelefono) },
) {
    val estado by viewModel.estado.collectAsStateWithLifecycle()
    DetallesTecnicosContenido(estado = estado, alVolver = alVolver)
}

@Composable
fun DetallesTecnicosContenido(
    estado: DetallesTecnicosUiState,
    alVolver: () -> Unit,
    zona: ZoneId = ZoneId.systemDefault(),
    ahora: Instant = Instant.now(),
) {
    Scaffold(
        topBar = { BarraSuperior(titulo = stringResource(R.string.ajustes_detalles_tecnicos), alVolver = alVolver) },
    ) { relleno ->
        Column(
            modifier = Modifier
                .padding(relleno)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(MargenAjustes),
            verticalArrangement = EntreTarjetas,
        ) {
            val red = estado.red
            val conexion = estado.conexion
            val detalles = (conexion as? LecturaConexion.Lista)?.detalles
            val sinDato = stringResource(
                when (conexion) {
                    LecturaConexion.Leyendo -> R.string.detalles_leyendo
                    is LecturaConexion.SinRespuesta -> R.string.detalles_sin_respuesta
                    is LecturaConexion.Lista -> R.string.detalles_sin_certificado
                },
            )
            val cifrada = detalles?.versionTls != null || (detalles == null && red?.urlBase?.startsWith("https") == true)
            TarjetaDatos(titulo = stringResource(R.string.detalles_conexion)) {
                FilaDato(
                    etiqueta = stringResource(R.string.detalles_protocolo),
                    valor = when {
                        detalles?.versionTls != null -> stringResource(R.string.detalles_https, versionLegible(detalles.versionTls))
                        cifrada -> stringResource(R.string.detalles_https_sin_version)
                        else -> stringResource(R.string.detalles_http)
                    },
                )
                FilaDato(etiqueta = stringResource(R.string.detalles_certificado), valor = detalles?.emisorCertificado ?: sinDato)
                FilaDato(
                    etiqueta = stringResource(R.string.detalles_valido_hasta),
                    valor = detalles?.certificadoVence?.let { stringResource(R.string.detalles_se_renueva, Formato.fecha(it, zona)) } ?: sinDato,
                )
                FilaDato(etiqueta = stringResource(R.string.detalles_servidor), valor = red?.let(::direccion) ?: sinDato)
            }
            TarjetaDatos(titulo = stringResource(R.string.detalles_acceso)) {
                FilaDato(etiqueta = stringResource(R.string.detalles_nombre), valor = estado.nombreDelTelefono)
                val emitido = red?.accesoEmitido
                val vence = detalles?.accesoVence ?: red?.accesoVence
                FilaDato(etiqueta = stringResource(R.string.detalles_emitido), valor = emitido?.let { Formato.fecha(it, zona) } ?: stringResource(R.string.detalles_sin_registro))
                FilaDato(
                    etiqueta = stringResource(R.string.detalles_vence),
                    valor = when {
                        vence == null -> stringResource(R.string.detalles_sin_registro)
                        emitido != null -> stringResource(R.string.detalles_vence_con_dias, Formato.fecha(vence, zona), Formato.diasEntre(emitido, vence))
                        else -> Formato.fecha(vence, zona)
                    },
                )
                val vencido = red?.accesoVencido == true || (vence != null && vence.isBefore(ahora))
                FilaDato(
                    etiqueta = stringResource(R.string.detalles_estado),
                    valor = stringResource(if (vencido) R.string.detalles_vencido else R.string.detalles_activo),
                )
            }
            NotaAlPie(texto = stringResource(R.string.detalles_nota))
        }
    }
}

/** "TLSv1.3" → "TLS 1.3". */
fun versionLegible(versionTls: String): String = versionTls.replace("TLSv", "TLS ")
