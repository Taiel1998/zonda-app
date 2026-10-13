package ar.edu.uade.zonda.ui.ajustes

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ar.edu.uade.zonda.R
import ar.edu.uade.zonda.domain.model.ModoAviso
import ar.edu.uade.zonda.domain.model.PreferenciasAvisos
import ar.edu.uade.zonda.domain.model.Red
import ar.edu.uade.zonda.domain.model.Severidad
import ar.edu.uade.zonda.ui.EstadoPermiso
import ar.edu.uade.zonda.ui.Formato
import ar.edu.uade.zonda.ui.abrirAjustesDeNotificaciones
import ar.edu.uade.zonda.ui.components.AvisoInferior
import ar.edu.uade.zonda.ui.components.BarraSuperior
import ar.edu.uade.zonda.ui.components.BotonTonal
import ar.edu.uade.zonda.ui.components.BotonTonalPeligro
import ar.edu.uade.zonda.ui.components.FilaConInterruptor
import ar.edu.uade.zonda.ui.components.FilaNavegable
import ar.edu.uade.zonda.ui.components.FilaTexto
import ar.edu.uade.zonda.ui.components.HojaConfirmacion
import ar.edu.uade.zonda.ui.components.RellenoBotonAngosto
import ar.edu.uade.zonda.ui.components.TarjetaAdvertencia
import ar.edu.uade.zonda.ui.components.TarjetaSeccion
import ar.edu.uade.zonda.ui.mensaje
import ar.edu.uade.zonda.ui.rememberPermisoNotificaciones
import ar.edu.uade.zonda.ui.viewModelDeZonda
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.launch

/** El margen de las pantallas de Ajustes en el Figma: 16 a los costados y arriba, 24 abajo, 16 entre tarjetas. */
internal val MargenAjustes = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 24.dp)
internal val EntreTarjetas = Arrangement.spacedBy(16.dp)

/**
 * Ajustes (RF05 y RF06; Figma 4-66 y 51-1137): una lista de tarjetas que abren otras pantallas.
 * Red (Redes y Detalles técnicos), Avisos (Telegram, avisos por severidad, severidad por tipo de ataque
 * y el interruptor de notificaciones), Datos locales, Este dispositivo y Ayuda. Las confirmaciones son
 * hojas inferiores con el botón rojo.
 */
@Composable
fun AjustesScreen(
    alAbrirRedes: () -> Unit,
    alAbrirDetallesTecnicos: () -> Unit,
    alAbrirAvisos: () -> Unit,
    alAbrirSeveridades: () -> Unit,
    alVerIntroduccion: () -> Unit,
    alVerCondiciones: () -> Unit,
    alAgregarRed: () -> Unit,
    alQuedarSinRedes: () -> Unit,
    viewModel: AjustesViewModel = ajustesViewModel(),
) {
    val estado by viewModel.estado.collectAsStateWithLifecycle()
    AjustesContenido(
        estado = estado,
        acciones = AccionesAjustes(
            alAbrirRedes = alAbrirRedes,
            alAbrirDetallesTecnicos = alAbrirDetallesTecnicos,
            alAbrirAvisos = {
                viewModel.empezarAEditarAvisos()
                alAbrirAvisos()
            },
            alAbrirSeveridades = {
                viewModel.empezarAEditarSeveridades()
                alAbrirSeveridades()
            },
            alConectarTelegram = viewModel::conectarTelegram,
            alCambiarNotificaciones = viewModel::cambiarNotificaciones,
            alSincronizar = viewModel::sincronizarAhora,
            alBorrarDatos = viewModel::borrarDatosLocales,
            alQuitarDispositivo = { viewModel.quitarDispositivo() },
            alVerIntroduccion = alVerIntroduccion,
            alVerCondiciones = alVerCondiciones,
            alAgregarRed = alAgregarRed,
            alQuedarSinRedes = alQuedarSinRedes,
            alAvisoMostrado = viewModel::avisoMostrado,
        ),
    )
}

/** El ViewModel de Ajustes. Redes, Avisos por severidad y Severidad por ataque pasan el de Ajustes como [dueño]. */
@Composable
fun ajustesViewModel(dueño: ViewModelStoreOwner? = null): AjustesViewModel = viewModelDeZonda(dueño) { contenedor ->
    AjustesViewModel(
        redes = contenedor.redes,
        alertas = contenedor.alertas,
        preferencias = contenedor.preferencias,
        sincronizarAlertas = contenedor.sincronizarAlertas,
        cambiarSeveridades = contenedor.cambiarSeveridades,
        quitarDispositivo = contenedor.quitarDispositivo,
        borrarDatosLocales = contenedor.borrarDatosLocales,
        alcanceDeEnvio = contenedor.alcanceDeLaApp,
        nombreDelTelefono = contenedor.nombreDelTelefono,
    )
}

/** Lo que se puede hacer desde Ajustes; separado para probar la pantalla sin ViewModel. */
data class AccionesAjustes(
    val alAbrirRedes: () -> Unit = {},
    val alAbrirDetallesTecnicos: () -> Unit = {},
    val alAbrirAvisos: () -> Unit = {},
    val alAbrirSeveridades: () -> Unit = {},
    val alConectarTelegram: () -> Unit = {},
    val alCambiarNotificaciones: (Boolean) -> Unit = {},
    val alSincronizar: () -> Unit = {},
    val alBorrarDatos: () -> Unit = {},
    val alQuitarDispositivo: () -> Unit = {},
    val alVerIntroduccion: () -> Unit = {},
    val alVerCondiciones: () -> Unit = {},
    val alAgregarRed: () -> Unit = {},
    val alQuedarSinRedes: () -> Unit = {},
    val alAvisoMostrado: () -> Unit = {},
)

private enum class HojaAjustes { BORRAR_DATOS, QUITAR_DISPOSITIVO }

@Composable
fun AjustesContenido(
    estado: AjustesUiState,
    acciones: AccionesAjustes,
    zona: ZoneId = ZoneId.systemDefault(),
    ahora: Instant = Instant.now(),
) {
    val snackbar = remember { SnackbarHostState() }
    val contexto = LocalContext.current
    var hoja by rememberSaveable { mutableStateOf<HojaAjustes?>(null) }
    val permiso = rememberPermisoNotificaciones { concedido -> if (concedido) acciones.alCambiarNotificaciones(true) }
    val alcance = rememberCoroutineScope()

    LaunchedEffect(estado.aviso) {
        val aviso = estado.aviso ?: return@LaunchedEffect
        // El aviso se consume enseguida; se muestra en otro alcance para que consumirlo no lo corte.
        acciones.alAvisoMostrado()
        if (aviso is AvisoAjustes.DispositivoQuitado && !aviso.quedanRedes) {
            // Sin redes no queda nada que mostrar acá: se vuelve a Conexión.
            acciones.alQuedarSinRedes()
            return@LaunchedEffect
        }
        val (texto, accion) = textoDelAviso(contexto, aviso)
        alcance.launch {
            val resultado = snackbar.showSnackbar(message = texto, actionLabel = accion, duration = SnackbarDuration.Short)
            if (aviso is AvisoAjustes.Error && resultado == SnackbarResult.ActionPerformed) acciones.alSincronizar()
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = { BarraSuperior(titulo = stringResource(R.string.destino_ajustes)) },
        snackbarHost = { SnackbarHost(snackbar) { AvisoInferior(it) } },
    ) { relleno ->
        Column(
            modifier = Modifier
                .padding(relleno)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(MargenAjustes),
            verticalArrangement = EntreTarjetas,
        ) {
            val red = estado.redActiva
            TarjetaSeccion(titulo = stringResource(R.string.ajustes_seccion_red)) {
                if (red == null) {
                    FilaNavegable(
                        titulo = stringResource(R.string.ajustes_agregar_red),
                        detalle = stringResource(R.string.ajustes_sin_redes),
                        alTocar = acciones.alAgregarRed,
                    )
                } else {
                    FilaNavegable(
                        titulo = red.nombre,
                        detalle = pluralStringResource(R.plurals.ajustes_red_detalle, estado.redes.size, direccion(red), estado.redes.size),
                        alTocar = acciones.alAbrirRedes,
                    )
                    FilaNavegable(
                        titulo = stringResource(R.string.ajustes_detalles_tecnicos),
                        detalle = stringResource(R.string.ajustes_detalles_tecnicos_detalle),
                        alTocar = acciones.alAbrirDetallesTecnicos,
                    )
                }
            }

            TarjetaSeccion(titulo = stringResource(R.string.ajustes_seccion_avisos)) {
                if (red?.telegramVinculado == true) {
                    FilaNavegable(
                        titulo = stringResource(R.string.ajustes_telegram),
                        detalle = stringResource(R.string.ajustes_telegram_conectado, stringResource(R.string.telegram_bot), estado.nombreDelTelefono),
                        alTocar = acciones.alConectarTelegram,
                    )
                } else {
                    TarjetaAdvertencia(
                        titulo = stringResource(R.string.ajustes_telegram_sin_conectar),
                        texto = stringResource(R.string.ajustes_telegram_sin_conectar_texto),
                        accion = stringResource(R.string.ajustes_conectar_telegram),
                        alAccionar = acciones.alConectarTelegram,
                    )
                }
                FilaNavegable(
                    titulo = stringResource(R.string.ajustes_avisos_por_severidad),
                    detalle = resumenDeAvisos(estado.avisos),
                    alTocar = acciones.alAbrirAvisos,
                )
                FilaNavegable(
                    titulo = stringResource(R.string.ajustes_severidad_por_tipo),
                    detalle = stringResource(R.string.ajustes_severidad_por_tipo_detalle),
                    alTocar = acciones.alAbrirSeveridades,
                )
                if (permiso.estado == EstadoPermiso.RECHAZADO) {
                    TarjetaAdvertencia(
                        titulo = stringResource(R.string.ajustes_notificaciones_bloqueadas_titulo),
                        texto = stringResource(R.string.ajustes_notificaciones_bloqueadas_texto),
                        accion = stringResource(R.string.abrir_ajustes),
                        alAccionar = { abrirAjustesDeNotificaciones(contexto) },
                        nota = stringResource(R.string.ajustes_notificaciones_bloqueadas_nota),
                    )
                } else {
                    val activas = estado.notificacionesActivas && permiso.estado == EstadoPermiso.CONCEDIDO
                    FilaConInterruptor(
                        titulo = stringResource(R.string.ajustes_notificaciones_sistema),
                        detalle = stringResource(if (activas) R.string.ajustes_notificaciones_permitidas else R.string.ajustes_notificaciones_desactivadas),
                        activo = activas,
                        alCambiar = { prender ->
                            if (!prender) acciones.alCambiarNotificaciones(false)
                            else if (permiso.estado == EstadoPermiso.CONCEDIDO) acciones.alCambiarNotificaciones(true)
                            else permiso.pedir()
                        },
                    )
                }
            }

            TarjetaSeccion(titulo = stringResource(R.string.ajustes_datos_locales)) {
                FilaTexto(
                    titulo = stringResource(R.string.ajustes_ultima_sincronizacion),
                    detalle = ultimaSincronizacion(red, estado.alertasDeLaRedActiva, ahora, zona),
                )
                Row(modifier = Modifier.padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    BotonTonal(
                        texto = stringResource(R.string.accion_sincronizar),
                        alTocar = acciones.alSincronizar,
                        habilitado = red != null && !estado.sincronizando,
                        cargando = estado.sincronizando,
                        relleno = RellenoBotonAngosto,
                        modifier = Modifier.weight(1f),
                    )
                    BotonTonalPeligro(
                        texto = stringResource(R.string.ajustes_borrar_datos),
                        alTocar = { hoja = HojaAjustes.BORRAR_DATOS },
                        relleno = RellenoBotonAngosto,
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            if (red != null) {
                TarjetaSeccion(titulo = stringResource(R.string.ajustes_seccion_dispositivo)) {
                    FilaNavegable(
                        titulo = stringResource(R.string.ajustes_quitar_dispositivo),
                        detalle = stringResource(R.string.ajustes_quitar_dispositivo_detalle),
                        colorTitulo = MaterialTheme.colorScheme.error,
                        alTocar = { hoja = HojaAjustes.QUITAR_DISPOSITIVO },
                    )
                }
            }

            TarjetaSeccion(titulo = stringResource(R.string.ajustes_seccion_ayuda)) {
                FilaNavegable(titulo = stringResource(R.string.ajustes_ver_introduccion), alTocar = acciones.alVerIntroduccion)
                FilaNavegable(titulo = stringResource(R.string.ajustes_condiciones), alTocar = acciones.alVerCondiciones)
            }
        }
    }

    when (hoja) {
        HojaAjustes.BORRAR_DATOS -> HojaConfirmacion(
            titulo = stringResource(R.string.ajustes_borrar_titulo),
            texto = pluralStringResource(R.plurals.ajustes_borrar_texto, estado.alertasGuardadas, estado.alertasGuardadas),
            textoConfirmar = stringResource(R.string.ajustes_borrar_confirmar),
            alConfirmar = {
                hoja = null
                acciones.alBorrarDatos()
            },
            alCerrar = { hoja = null },
        )
        HojaAjustes.QUITAR_DISPOSITIVO -> estado.redActiva?.let { red ->
            HojaQuitarDispositivo(
                red = red,
                nombreDelTelefono = estado.nombreDelTelefono,
                alertasGuardadas = estado.alertasDeLaRedActiva,
                alConfirmar = {
                    hoja = null
                    acciones.alQuitarDispositivo()
                },
                alCerrar = { hoja = null },
            )
        }
        null -> Unit
    }
}

/** "¿Quitar este dispositivo?" (Figma 51-1451), desde Ajustes o desde el menú de cada red. */
@Composable
fun HojaQuitarDispositivo(
    red: Red,
    nombreDelTelefono: String,
    alertasGuardadas: Int,
    alConfirmar: () -> Unit,
    alCerrar: () -> Unit,
) {
    HojaConfirmacion(
        titulo = stringResource(R.string.ajustes_quitar_titulo),
        texto = stringResource(R.string.ajustes_quitar_texto, red.nombre),
        textoConfirmar = stringResource(R.string.ajustes_quitar_confirmar),
        resumen = pluralStringResource(R.plurals.ajustes_quitar_resumen, alertasGuardadas, nombreDelTelefono, red.nombre, alertasGuardadas),
        alConfirmar = alConfirmar,
        alCerrar = alCerrar,
    )
}

/** La dirección de la red sin el esquema, como en el Figma: "zonda-empresa.duckdns.org". */
fun direccion(red: Red): String = red.urlBase.substringAfter("://").trimEnd('/')

/** "Alta con sonido · Media con vibración · Baja sin aviso". */
@Composable
private fun resumenDeAvisos(avisos: PreferenciasAvisos): String =
    Severidad.entries.map { severidad ->
        val nombre = stringResource(
            when (severidad) {
                Severidad.ALTA -> R.string.severidad_alta
                Severidad.MEDIA -> R.string.severidad_media
                Severidad.BAJA -> R.string.severidad_baja
            },
        )
        val modo = stringResource(
            when (avisos.para(severidad)) {
                ModoAviso.SONIDO -> R.string.aviso_frase_sonido
                ModoAviso.VIBRACION -> R.string.aviso_frase_vibracion
                ModoAviso.SIN_AVISO -> R.string.aviso_frase_sin_aviso
            },
        )
        "$nombre $modo"
    }.joinToString(" · ")

/** "Hoy 02:14 · 213 alertas en el teléfono", o "Sin datos guardados · 0 alertas en el teléfono". */
@Composable
private fun ultimaSincronizacion(red: Red?, alertas: Int, ahora: Instant, zona: ZoneId): String {
    val ultima = red?.ultimaSincronizacion ?: return stringResource(R.string.ajustes_sin_datos_guardados)
    val cuando = when {
        Formato.mismoDia(ultima, ahora, zona) -> stringResource(R.string.ajustes_hoy, Formato.hora(ultima, zona))
        Formato.ayer(ultima, ahora, zona) -> stringResource(R.string.ajustes_ayer, Formato.hora(ultima, zona))
        else -> Formato.fechaYHora(ultima, zona)
    }
    return pluralStringResource(R.plurals.ajustes_alertas_en_telefono, alertas, cuando, alertas)
}

/** El texto del aviso inferior y su acción ("OK", o "Reintentar" en los errores). */
fun textoDelAviso(contexto: Context, aviso: AvisoAjustes): Pair<String, String?> {
    val ok = contexto.getString(R.string.aviso_ok)
    return when (aviso) {
        is AvisoAjustes.Sincronizado -> if (aviso.nuevas == 0) {
            contexto.getString(R.string.aviso_sincronizado_nada) to ok
        } else {
            contexto.resources.getQuantityString(R.plurals.aviso_sincronizado_llegaron, aviso.nuevas, aviso.nuevas) to ok
        }
        AvisoAjustes.SeveridadesGuardadas -> contexto.getString(R.string.ajustes_severidades_guardadas) to ok
        AvisoAjustes.DatosBorrados -> contexto.getString(R.string.ajustes_datos_borrados) to ok
        is AvisoAjustes.DispositivoQuitado -> contexto.getString(R.string.ajustes_dispositivo_quitado) to ok
        is AvisoAjustes.RedActivada -> contexto.getString(R.string.aviso_red_activa, aviso.nombre) to ok
        AvisoAjustes.TelegramPronto -> contexto.getString(R.string.ajustes_telegram_pronto) to ok
        is AvisoAjustes.Error -> contexto.getString(aviso.error.mensaje()) to contexto.getString(R.string.error_reintentar)
    }
}
