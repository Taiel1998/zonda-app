package ar.edu.uade.zonda.ui.telegram

import android.content.ActivityNotFoundException
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import ar.edu.uade.zonda.R
import ar.edu.uade.zonda.domain.model.Severidad
import ar.edu.uade.zonda.ui.comoAvisa
import ar.edu.uade.zonda.ui.components.AvisoInferior
import ar.edu.uade.zonda.ui.components.BarraSuperior
import ar.edu.uade.zonda.ui.components.BotonEnlace
import ar.edu.uade.zonda.ui.components.BotonPrincipal
import ar.edu.uade.zonda.ui.components.BotonTonal
import ar.edu.uade.zonda.ui.components.BotonTonalPeligro
import ar.edu.uade.zonda.ui.components.FilaDato
import ar.edu.uade.zonda.ui.components.NotaAlPie
import ar.edu.uade.zonda.ui.components.Panel
import ar.edu.uade.zonda.ui.components.TarjetaDatos
import ar.edu.uade.zonda.ui.components.TarjetaTono
import ar.edu.uade.zonda.ui.components.TituloSeccion
import ar.edu.uade.zonda.ui.etiqueta
import ar.edu.uade.zonda.ui.mensaje
import ar.edu.uade.zonda.ui.nombreDelBot
import ar.edu.uade.zonda.ui.resumenDeAvisos
import ar.edu.uade.zonda.ui.theme.Tonos
import ar.edu.uade.zonda.ui.theme.tituloPaso
import ar.edu.uade.zonda.ui.viewModelDeZonda
import kotlinx.coroutines.launch

/** El ViewModel de Telegram, armado con el contenedor. */
@Composable
fun telegramViewModel(): TelegramViewModel = viewModelDeZonda { contenedor ->
    TelegramViewModel(contenedor.redes, contenedor.vincularTelegram, contenedor.preferencias, contenedor.nombreDelTelefono)
}

/** Lo que se puede hacer en las pantallas de Telegram. */
data class AccionesTelegram(
    val alAbrirTelegram: () -> Unit = {},
    val alAhoraNo: () -> Unit = {},
    val alContinuar: () -> Unit = {},
    val alEnviarPrueba: () -> Unit = {},
    val alDesvincular: () -> Unit = {},
    val alAvisoMostrado: () -> Unit = {},
    /** Null en el primer inicio: la pantalla no tiene flecha (Figma 50-942). */
    val alVolver: (() -> Unit)? = null,
)

/**
 * Conectar Telegram en el primer inicio, después de la red conectada (Figma 50-942 y, ya vinculado,
 * 50-1003). Sin flecha y con "Ahora no"; Volver equivale a seguir, porque la red ya quedó conectada.
 */
@Composable
fun TelegramInicialScreen(alTerminar: () -> Unit, viewModel: TelegramViewModel = telegramViewModel()) {
    val estado by viewModel.estado.collectAsStateWithLifecycle()
    BackHandler(onBack = alTerminar)
    EfectosDeTelegram(estado, viewModel)
    TelegramContenido(
        estado = estado,
        desdeAjustes = false,
        acciones = AccionesTelegram(
            alAbrirTelegram = viewModel::abrirTelegram,
            alAhoraNo = alTerminar,
            alContinuar = alTerminar,
            alAvisoMostrado = viewModel::avisoMostrado,
        ),
    )
}

/**
 * Telegram desde Ajustes: sin vincular, Conectar Telegram con flecha (Figma 66-4205); vinculado, el
 * chat, cómo avisa cada severidad, el aviso de prueba y Desvincular (66-3484).
 */
@Composable
fun TelegramScreen(alVolver: () -> Unit, viewModel: TelegramViewModel = telegramViewModel()) {
    val estado by viewModel.estado.collectAsStateWithLifecycle()
    EfectosDeTelegram(estado, viewModel)
    TelegramContenido(
        estado = estado,
        desdeAjustes = true,
        acciones = AccionesTelegram(
            alAbrirTelegram = viewModel::abrirTelegram,
            alAhoraNo = alVolver,
            alEnviarPrueba = viewModel::enviarPrueba,
            alDesvincular = viewModel::desvincular,
            alAvisoMostrado = viewModel::avisoMostrado,
            alVolver = alVolver,
        ),
    )
}

/**
 * Abre el enlace del bot y, mientras se espera que la persona toque Iniciar, pregunta si ya quedó
 * vinculado. Solo con la pantalla al frente: al volver de Telegram la pregunta sale enseguida.
 */
@Composable
private fun EfectosDeTelegram(estado: TelegramUiState, viewModel: TelegramViewModel) {
    val enlaces = LocalUriHandler.current
    LaunchedEffect(estado.enlaceParaAbrir) {
        val enlace = estado.enlaceParaAbrir ?: return@LaunchedEffect
        viewModel.enlaceAbierto()
        try {
            enlaces.openUri(enlace)
        } catch (e: ActivityNotFoundException) {
            viewModel.noSePudoAbrir()
        } catch (e: IllegalArgumentException) {
            viewModel.noSePudoAbrir()
        }
    }
    val cicloDeVida = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(estado.esperandoInicio) {
        if (estado.esperandoInicio) {
            cicloDeVida.repeatOnLifecycle(Lifecycle.State.RESUMED) { viewModel.esperarVinculo() }
        }
    }
}

@Composable
fun TelegramContenido(estado: TelegramUiState, acciones: AccionesTelegram, desdeAjustes: Boolean) {
    val snackbar = remember { SnackbarHostState() }
    val contexto = LocalContext.current
    val alcance = rememberCoroutineScope()
    val bot = nombreDelBot(estado.red)

    LaunchedEffect(estado.aviso) {
        val aviso = estado.aviso ?: return@LaunchedEffect
        // El aviso se consume enseguida; se muestra en otro alcance para que consumirlo no lo corte.
        acciones.alAvisoMostrado()
        val texto = when (aviso) {
            AvisoTelegram.PruebaEnviada -> contexto.getString(R.string.telegram_prueba_enviada, bot)
            AvisoTelegram.Desvinculado -> contexto.getString(R.string.telegram_desvinculado)
            AvisoTelegram.NoSeAbrio -> contexto.getString(R.string.telegram_no_se_abrio)
            is AvisoTelegram.Error -> contexto.getString(aviso.error.mensaje())
        }
        alcance.launch {
            snackbar.showSnackbar(message = texto, actionLabel = contexto.getString(R.string.aviso_ok), duration = SnackbarDuration.Short)
        }
    }

    val vinculadoDesdeAjustes = estado.vinculado && desdeAjustes
    Scaffold(
        topBar = {
            BarraSuperior(
                titulo = stringResource(if (vinculadoDesdeAjustes) R.string.ajustes_telegram else R.string.telegram_conectar_titulo),
                alVolver = acciones.alVolver,
            )
        },
        snackbarHost = { SnackbarHost(snackbar) { AvisoInferior(it) } },
    ) { relleno ->
        Column(
            modifier = Modifier
                .padding(relleno)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            when {
                !estado.vinculado -> Conectar(estado, acciones, bot)
                desdeAjustes -> Vinculado(estado, acciones, bot)
                else -> RecienConectado(estado, acciones, bot)
            }
        }
    }
}

/** Conectar Telegram (Figma 50-942 y 66-4205): cómo se hace, qué viaja y "Abrir Telegram". */
@Composable
private fun ColumnScope.Conectar(estado: TelegramUiState, acciones: AccionesTelegram, bot: String) {
    Text(text = stringResource(R.string.telegram_encabezado), style = MaterialTheme.typography.tituloPaso)
    Panel {
        TituloSeccion(stringResource(R.string.telegram_como_se_hace))
        PasoDeTelegram(stringResource(R.string.telegram_paso_abrir, bot))
        PasoDeTelegram(stringResource(R.string.telegram_paso_iniciar))
        PasoDeTelegram(stringResource(R.string.telegram_paso_volver))
    }
    // Recuadro crema con el título SemiBold 14 y el texto Regular 13, los dos grises (como el 51-2609).
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(text = stringResource(R.string.telegram_que_viaja), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(text = stringResource(R.string.telegram_que_viaja_texto), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    BotonPrincipal(texto = stringResource(R.string.telegram_abrir), alTocar = acciones.alAbrirTelegram, habilitado = !estado.ocupado)
    BotonEnlace(texto = stringResource(R.string.telegram_ahora_no), alTocar = acciones.alAhoraNo, modifier = Modifier.fillMaxWidth())
    NotaAlPie(stringResource(R.string.telegram_despues))
}

/**
 * Un paso de "Cómo se hace": círculo gris cálido de 24 con un punto de 8, y el texto Regular 14. En el
 * Figma el texto es #8A8983; acá va en el gris de los textos de ayuda, que llega al contraste AA.
 */
@Composable
private fun PasoDeTelegram(texto: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Box(modifier = Modifier.size(8.dp).background(Tonos.Neutro.punto, CircleShape))
        }
        Text(text = texto, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Telegram conectado en el primer inicio (Figma 50-1003): la tarjeta verde con el candado y Continuar. */
@Composable
private fun ColumnScope.RecienConectado(estado: TelegramUiState, acciones: AccionesTelegram, bot: String) {
    Text(text = stringResource(R.string.telegram_encabezado), style = MaterialTheme.typography.tituloPaso)
    TarjetaTono(
        tono = Tonos.Baja,
        titulo = stringResource(R.string.telegram_conectado),
        icono = Icons.Filled.Lock,
        textos = listOf(
            stringResource(R.string.telegram_van_a_llegar, estado.red?.nombre.orEmpty(), bot),
            stringResource(R.string.aviso_resumen_ajustes, resumenDeAvisos(estado.avisos)),
        ),
    )
    BotonPrincipal(texto = stringResource(R.string.conexion_continuar), alTocar = acciones.alContinuar)
}

/** Telegram vinculado, desde Ajustes (Figma 66-3484). */
@Composable
private fun ColumnScope.Vinculado(estado: TelegramUiState, acciones: AccionesTelegram, bot: String) {
    TarjetaTono(
        tono = Tonos.Baja,
        titulo = stringResource(R.string.telegram_conectado),
        textos = listOf(stringResource(R.string.telegram_llegan, estado.red?.nombre.orEmpty(), bot, estado.nombreDelTelefono)),
    )
    TarjetaDatos(titulo = stringResource(R.string.telegram_como_avisa)) {
        Severidad.entries.forEach { severidad ->
            FilaDato(etiqueta = stringResource(severidad.etiqueta()), valor = stringResource(estado.avisos.para(severidad).comoAvisa()))
        }
    }
    BotonTonal(
        texto = stringResource(R.string.telegram_enviar_prueba),
        alTocar = acciones.alEnviarPrueba,
        habilitado = !estado.ocupado,
        modifier = Modifier.fillMaxWidth(),
    )
    BotonTonalPeligro(
        texto = stringResource(R.string.telegram_desvincular),
        alTocar = acciones.alDesvincular,
        habilitado = !estado.ocupado,
        modifier = Modifier.fillMaxWidth(),
    )
}
