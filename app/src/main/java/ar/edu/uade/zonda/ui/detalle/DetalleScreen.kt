package ar.edu.uade.zonda.ui.detalle

import android.content.Intent
import android.content.res.Resources
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ar.edu.uade.zonda.R
import ar.edu.uade.zonda.domain.model.AccionPendiente
import ar.edu.uade.zonda.domain.model.Alerta
import ar.edu.uade.zonda.domain.model.Asignacion
import ar.edu.uade.zonda.domain.model.AvisoRecibido
import ar.edu.uade.zonda.domain.model.CambioDeSeveridad
import ar.edu.uade.zonda.domain.model.DuracionBloqueo
import ar.edu.uade.zonda.domain.model.ErrorZonda
import ar.edu.uade.zonda.domain.model.EstadoEnvio
import ar.edu.uade.zonda.domain.model.EstadoRevision
import ar.edu.uade.zonda.domain.model.Motivo
import ar.edu.uade.zonda.domain.model.OrigenRevision
import ar.edu.uade.zonda.domain.model.Persona
import ar.edu.uade.zonda.domain.model.ResultadoRevision
import ar.edu.uade.zonda.domain.model.Revision
import ar.edu.uade.zonda.domain.model.lectura
import ar.edu.uade.zonda.ui.Formato
import ar.edu.uade.zonda.ui.comentarioPropuesto
import ar.edu.uade.zonda.ui.components.AvisoInferior
import ar.edu.uade.zonda.ui.components.BannerSinConexion
import ar.edu.uade.zonda.ui.components.BarraSuperior
import ar.edu.uade.zonda.ui.components.BotonContorno
import ar.edu.uade.zonda.ui.components.BotonEnlace
import ar.edu.uade.zonda.ui.components.BotonPrincipal
import ar.edu.uade.zonda.ui.components.BotonTonal
import ar.edu.uade.zonda.ui.components.EstadoError
import ar.edu.uade.zonda.ui.components.EstadoVacio
import ar.edu.uade.zonda.ui.components.Etiqueta
import ar.edu.uade.zonda.ui.components.FilaDato
import ar.edu.uade.zonda.ui.components.HojaInferior
import ar.edu.uade.zonda.ui.components.IndicadorEnvio
import ar.edu.uade.zonda.ui.components.IndicadorEstadoRevision
import ar.edu.uade.zonda.ui.components.IndicadorSeveridad
import ar.edu.uade.zonda.ui.components.IndicadorVeredicto
import ar.edu.uade.zonda.ui.components.Panel
import ar.edu.uade.zonda.ui.components.RellenoBotonAngosto
import ar.edu.uade.zonda.ui.components.TarjetaTono
import ar.edu.uade.zonda.ui.components.TituloSeccion
import ar.edu.uade.zonda.ui.etiqueta
import ar.edu.uade.zonda.ui.mensaje
import ar.edu.uade.zonda.ui.nombreCorto
import ar.edu.uade.zonda.ui.nombreEnFrase
import ar.edu.uade.zonda.ui.nombreEnHistorial
import ar.edu.uade.zonda.ui.recorrido.ObjetivoRecorrido
import ar.edu.uade.zonda.ui.recorrido.objetivoDelRecorrido
import ar.edu.uade.zonda.ui.texto
import ar.edu.uade.zonda.ui.theme.Tonos
import ar.edu.uade.zonda.ui.theme.encabezado
import ar.edu.uade.zonda.ui.theme.nota
import ar.edu.uade.zonda.ui.theme.opcion
import ar.edu.uade.zonda.ui.theme.rotulo
import ar.edu.uade.zonda.ui.theme.valor
import ar.edu.uade.zonda.ui.viewModelDeZonda
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers

/** Lo que la persona puede hacer en el detalle. Los valores por defecto no hacen nada (cómodo en las pruebas). */
class AccionesDetalle(
    val alVolver: () -> Unit = {},
    val alCompartir: (texto: String) -> Unit = {},
    val alReintentarDescarga: () -> Unit = {},
    val alRevisar: () -> Unit = {},
    val alElegirResultado: (ResultadoRevision) -> Unit = {},
    val alElegirMotivo: (Motivo, String) -> Unit = { _, _ -> },
    val alCambiarComentario: (String) -> Unit = {},
    val alVolverAlResultado: () -> Unit = {},
    val alCancelarRevision: () -> Unit = {},
    val alConfirmarRevision: () -> Unit = {},
    val alAplicarLaMia: () -> Unit = {},
    val alQuedarmeConLaOtra: () -> Unit = {},
    val alReintentarEnvio: () -> Unit = {},
    val alAvisoMostrado: () -> Unit = {},
    /** Asignar a una persona, o dejarla sin asignar con null (decisión 42). */
    val alAsignar: (Persona?) -> Unit = {},
    /** Bloqueo del origen (decisión 51): abrir la hoja con el motivo propuesto, y lo que sigue. */
    val alBloquear: (motivoPropuesto: String) -> Unit = {},
    val alCambiarDuracionBloqueo: () -> Unit = {},
    val alDesbloquear: () -> Unit = {},
    val alRechazarBloqueo: () -> Unit = {},
    val alAprobarBloqueo: () -> Unit = {},
    val alElegirDuracion: (DuracionBloqueo) -> Unit = {},
    val alCambiarMotivoBloqueo: (String) -> Unit = {},
    val alCerrarBloqueo: () -> Unit = {},
    val alConfirmarBloqueo: () -> Unit = {},
    /** "Revisar" con el origen bloqueado: la revisión viene propuesta como "Bloqueado manualmente". */
    val alRevisarBloqueada: (comentarioPropuesto: String) -> Unit = {},
)

/**
 * Pantalla Detalle (RF03 y RF04; Figma 3-3 a 3-139 y 51-2379 a 51-2497): los datos del flujo, el
 * veredicto de cada método con palabras, la revisión con su historial y la acción Revisar en dos
 * pasos. Al confirmar, el detalle se cierra solo y vuelve a la pantalla de la que se vino, que muestra
 * "Revisaste ..." con Deshacer (corrección 7, decisión 38).
 */
@Composable
fun DetalleScreen(
    alertaId: String,
    alVolver: () -> Unit,
    /** Lo que trajo el enlace del aviso de Telegram, si se abrió desde ahí. */
    avisoRecibido: AvisoRecibido? = null,
    viewModel: DetalleViewModel = viewModelDeZonda { contenedor ->
        DetalleViewModel(
            obtenerDetalle = contenedor.obtenerDetalleAlerta,
            revisarAlerta = contenedor.revisarAlerta,
            asignarAlerta = contenedor.asignarAlerta,
            ventana = contenedor.ventanaDeshacer,
            resolverEnvio = contenedor.resolverEnvioRevision,
            sincronizarAlertas = contenedor.sincronizarAlertas,
            redes = contenedor.redes,
            acciones = contenedor.accionesPendientes,
            conectividad = contenedor.conectividad,
            alcanceDeEnvio = contenedor.alcanceDeLaApp,
            alertaId = alertaId,
            nombreDelTelefono = contenedor.nombreDelTelefono,
            avisoRecibido = avisoRecibido,
            gestionarBloqueos = contenedor.gestionarBloqueos,
        )
    },
) {
    val estado by viewModel.estado.collectAsStateWithLifecycle()
    // El comentario se escribe en la hoja: el estado se lee sin demora para que el teclado no pierda letras.
    val formulario by viewModel.formulario.collectAsStateWithLifecycle(context = Dispatchers.Main.immediate)
    val hojaBloqueo by viewModel.hojaBloqueo.collectAsStateWithLifecycle(context = Dispatchers.Main.immediate)
    val aviso by viewModel.aviso.collectAsStateWithLifecycle()
    val cerrar by viewModel.cerrar.collectAsStateWithLifecycle()
    val contexto = LocalContext.current
    val recursos = LocalResources.current
    // Lo que corta o devuelve tráfico se confirma con la huella o el PIN del teléfono (decisión 51).
    val confirmacion = LocalConfirmacionDelDueno.current ?: remember(contexto) { contexto.actividad()?.let(::ConfirmacionConHuella) }
    val origen = (estado as? DetalleUiState.Contenido)?.alerta?.origen?.etiqueta.orEmpty()

    // La revisión quedó guardada: vuelve a la lista de la que se vino (o a Estado, si se abrió desde un aviso).
    LaunchedEffect(cerrar) { if (cerrar) alVolver() }

    DetalleContenido(
        estado = estado,
        formulario = formulario,
        hojaBloqueo = hojaBloqueo,
        aviso = aviso,
        acciones = AccionesDetalle(
            alVolver = alVolver,
            alCompartir = { texto ->
                val envio = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, texto)
                contexto.startActivity(Intent.createChooser(envio, null))
            },
            alReintentarDescarga = viewModel::reintentarDescarga,
            alRevisar = viewModel::abrirRevision,
            alElegirResultado = viewModel::elegirResultado,
            alElegirMotivo = viewModel::elegirMotivo,
            alCambiarComentario = viewModel::cambiarComentario,
            alVolverAlResultado = viewModel::volverAlResultado,
            alCancelarRevision = viewModel::cancelarRevision,
            alConfirmarRevision = viewModel::confirmarRevision,
            alAplicarLaMia = viewModel::aplicarLaMiaIgual,
            alQuedarmeConLaOtra = viewModel::quedarmeConLaOtra,
            alReintentarEnvio = viewModel::reintentarEnvio,
            alAvisoMostrado = viewModel::avisoMostrado,
            alAsignar = viewModel::asignar,
            alBloquear = viewModel::abrirBloqueo,
            alCambiarDuracionBloqueo = viewModel::abrirCambioDeDuracion,
            alDesbloquear = viewModel::abrirDesbloqueo,
            alRechazarBloqueo = viewModel::abrirRechazo,
            alAprobarBloqueo = {
                confirmarConHuella(
                    pide = true,
                    confirmacion = confirmacion,
                    titulo = recursos.getString(R.string.huella_titulo),
                    detalle = recursos.getString(R.string.huella_aprobar, origen),
                    alConfirmar = viewModel::aprobarBloqueo,
                    alNoPoder = viewModel::sinBloqueoDePantalla,
                )
            },
            alElegirDuracion = viewModel::elegirDuracion,
            alCambiarMotivoBloqueo = viewModel::cambiarMotivoBloqueo,
            alCerrarBloqueo = viewModel::cerrarBloqueo,
            alConfirmarBloqueo = {
                // Sin motivo no se pide la huella: la hoja dice primero "Escribí el motivo.".
                val hoja = hojaBloqueo
                val puedeBloquear = (estado as? DetalleUiState.Contenido)?.puedeBloquear
                if (hoja == null || puedeBloquear == null || hoja.motivo.isBlank()) {
                    viewModel.confirmarBloqueo()
                } else {
                    confirmarConHuella(
                        pide = pideHuella(hoja.accion, puedeBloquear),
                        confirmacion = confirmacion,
                        titulo = recursos.getString(R.string.huella_titulo),
                        detalle = textoDeLaHuella(recursos, hoja, origen),
                        alConfirmar = viewModel::confirmarBloqueo,
                        alNoPoder = viewModel::sinBloqueoDePantalla,
                    )
                }
            },
            alRevisarBloqueada = viewModel::abrirRevisionBloqueada,
        ),
    )
}

/** Lo que dice el diálogo de la huella debajo del título: "Bloquear pc-rrhh · 1 hora". */
private fun textoDeLaHuella(recursos: Resources, hoja: FormularioBloqueo, origen: String): String = when (hoja.accion) {
    AccionBloqueo.BLOQUEAR -> recursos.getString(R.string.huella_bloquear, origen, recursos.getString(hoja.duracion.titulo()))
    AccionBloqueo.CAMBIAR_DURACION -> recursos.getString(R.string.huella_duracion, origen, recursos.getString(hoja.duracion.titulo()))
    AccionBloqueo.DESBLOQUEAR -> recursos.getString(R.string.huella_desbloquear, origen)
    AccionBloqueo.RECHAZAR -> ""
}

/** Cómo se muestra la revisión según en qué quedó (cada caso es un cuadro del Figma). */
private enum class ModoDetalle {
    /** 3-3: Revisar abajo. */
    SIN_REVISAR,

    /** 51-2379: la revisión y el historial arriba, "Cambiar revisión" y recién ahí los datos. */
    REVISADA,

    /** 3-70: todavía no salió del teléfono. */
    PENDIENTE,

    /** 51-2497: otra persona la revisó antes. */
    CONFLICTO,

    /** No salió después de cinco intentos o el servidor la rechazó: Reintentar. */
    ENVIO_FALLIDO,
}

private fun modoDe(estado: DetalleUiState.Contenido): ModoDetalle {
    val accion = estado.accion
    val enCola = accion?.estado == EstadoEnvio.PENDIENTE || accion?.estado == EstadoEnvio.ENVIANDO
    return when {
        accion?.estado == EstadoEnvio.CONFLICTO && accion.revisionAjena != null -> ModoDetalle.CONFLICTO
        accion?.estado == EstadoEnvio.ERROR -> ModoDetalle.ENVIO_FALLIDO
        enCola -> ModoDetalle.PENDIENTE
        estado.alerta.sinRevisar || estado.alerta.revisionActual == null -> ModoDetalle.SIN_REVISAR
        else -> ModoDetalle.REVISADA
    }
}

@Composable
fun DetalleContenido(
    estado: DetalleUiState,
    acciones: AccionesDetalle,
    formulario: FormularioRevision? = null,
    hojaBloqueo: FormularioBloqueo? = null,
    aviso: AvisoDetalle? = null,
    zona: ZoneId = ZoneId.systemDefault(),
    ahora: Instant = Instant.now(),
) {
    val snackbar = remember { SnackbarHostState() }
    val recursos = LocalResources.current
    var asignando by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(aviso) {
        when (aviso) {
            is AvisoDetalle.Error -> {
                // Si no se pudo bloquear, el servidor dice por qué con sus palabras ("Ese equipo ya tiene un bloqueo...").
                val error = aviso.error
                snackbar.showSnackbar((error as? ErrorZonda.BloqueoNoPosible)?.detalle ?: recursos.getString(error.mensaje()))
                acciones.alAvisoMostrado()
            }
            AvisoDetalle.BloqueoSinConexion -> {
                snackbar.showSnackbar(recursos.getString(R.string.bloqueo_sin_conexion))
                acciones.alAvisoMostrado()
            }
            AvisoDetalle.SinBloqueoDePantalla -> {
                snackbar.showSnackbar(recursos.getString(R.string.bloqueo_sin_bloqueo_de_pantalla))
                acciones.alAvisoMostrado()
            }
            null -> Unit
        }
    }

    val alertaVisible = (estado as? DetalleUiState.Contenido)?.alerta
    val textoParaCompartir = (estado as? DetalleUiState.Contenido)?.let { textoParaCompartir(it.alerta, it.nombreDeLaRed, zona) }

    // Sin barra de pestañas: los márgenes por defecto dejan el aviso y el final del contenido arriba de la
    // barra de navegación de Android, que con los tres botones ocupa 48 dp (decisión 30).
    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            BarraSuperior(
                titulo = stringResource(R.string.detalle_titulo),
                alVolver = acciones.alVolver,
                acciones = {
                    // Figma 33-90: compartir, en gris. Sin la alerta en el teléfono no hay qué compartir (3-139).
                    if (textoParaCompartir != null) {
                        IconButton(onClick = { acciones.alCompartir(textoParaCompartir) }) {
                            Icon(Icons.Filled.Share, contentDescription = stringResource(R.string.accion_compartir), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) { AvisoInferior(it) } },
    ) { relleno ->
        Column(
            modifier = Modifier
                .padding(relleno)
                .fillMaxSize(),
        ) {
            when (estado) {
                DetalleUiState.Cargando -> Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(stringResource(R.string.detalle_cargando), textAlign = TextAlign.Center)
                }
                DetalleUiState.SinRed -> EstadoVacio(
                    titulo = stringResource(R.string.vacio_sin_red_titulo),
                    detalle = stringResource(R.string.vacio_sin_red_detalle),
                )
                is DetalleUiState.NoDisponible -> PendienteDeDescarga(estado, acciones, zona)
                is DetalleUiState.Contenido -> {
                    val modo = modoDe(estado)
                    if (estado.sinConexion) {
                        if (modo == ModoDetalle.PENDIENTE) {
                            BannerSinConexion(datosDe = estado.datosDe, zona = zona, texto = stringResource(R.string.detalle_revision_pendiente_banner))
                        } else {
                            BannerSinConexion(datosDe = estado.datosDe, zona = zona)
                        }
                    }
                    CuerpoDetalle(estado = estado, modo = modo, acciones = acciones, zona = zona, ahora = ahora, alAbrirAsignar = { asignando = true })
                }
            }
        }
    }

    if (formulario != null && alertaVisible != null) HojaRevision(formulario = formulario, alerta = alertaVisible, acciones = acciones)
    val conPersonas = estado as? DetalleUiState.Contenido
    if (hojaBloqueo != null && conPersonas != null) HojaBloqueo(formulario = hojaBloqueo, alerta = conPersonas.alerta, puedeBloquear = conPersonas.puedeBloquear, acciones = acciones)
    if (asignando && conPersonas != null) {
        HojaInferior(alCerrar = { asignando = false }) {
            ContenidoHojaAsignar(conPersonas.personas, conPersonas.alerta.asignacion?.responsableId) { persona ->
                asignando = false
                acciones.alAsignar(persona)
            }
        }
    }
}

/** Figma 3-11: 16 de margen y 16 entre piezas. */
@Composable
private fun CuerpoDetalle(
    estado: DetalleUiState.Contenido,
    modo: ModoDetalle,
    acciones: AccionesDetalle,
    zona: ZoneId,
    ahora: Instant,
    alAbrirAsignar: () -> Unit,
) {
    val alerta = estado.alerta
    val accion = estado.accion
    Column(
        modifier = Modifier
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // En conflicto manda lo que quedó en el servidor, la revisión de la otra persona (Figma 51-2497).
        val estadoVisible = accion?.revisionAjena?.takeIf { modo == ModoDetalle.CONFLICTO }?.let { EstadoRevision.de(it.resultado) } ?: alerta.estadoRevision
        Encabezado(alerta = alerta, estado = estadoVisible, enCola = modo == ModoDetalle.PENDIENTE, envio = accion?.estado, zona = zona)
        // Figma 468-1086: el cálculo arriba, solo si el equipo destino no es normal.
        estado.calculo?.takeIf { it.conImportancia }?.let { TarjetaCalculo(it, alerta) }
        // Figma 468-507: el responsable debajo del encabezado; una alerta revisada sin responsable no lo muestra.
        if (estado.personas.isNotEmpty() && (alerta.asignacion != null || modo == ModoDetalle.SIN_REVISAR)) {
            TarjetaResponsable(
                asignacion = alerta.asignacion,
                perfil = estado.perfil,
                miId = estado.miId,
                zona = zona,
                ahora = ahora,
                alAbrirAsignar = alAbrirAsignar,
                alAsignarme = { estado.personas.firstOrNull { it.id == estado.miId }?.let(acciones.alAsignar) },
                alSoltar = { acciones.alAsignar(null) },
            )
        }
        // Decisión 51: el bloqueo del origen, debajo del responsable; sin firewall conectado no hay tarjeta.
        estado.bloqueo?.let { TarjetaBloqueo(seccion = it, alerta = alerta, puedeBloquear = estado.puedeBloquear, zona = zona, ahora = ahora, acciones = acciones) }
        when (modo) {
            ModoDetalle.SIN_REVISAR -> {
                DatosDelFlujo(alerta, zona)
                Veredictos(alerta)
                // Con el origen bloqueado, la revisión viene propuesta como "Bloqueado manualmente" (decisión 51).
                val bloqueada = estado.bloqueo?.actual?.estado?.enElFirewall == true
                val recursos = LocalResources.current
                BotonPrincipal(
                    texto = stringResource(R.string.detalle_revisar),
                    alTocar = {
                        if (bloqueada) acciones.alRevisarBloqueada(Motivo.BLOQUEADO_MANUALMENTE.comentarioPropuesto(recursos, alerta)) else acciones.alRevisar()
                    },
                    modifier = Modifier.objetivoDelRecorrido(ObjetivoRecorrido.BOTON_REVISAR),
                )
            }
            ModoDetalle.REVISADA -> {
                alerta.revisionActual?.let { TarjetaRevision(it, estado.nombreDelTelefono, zona) }
                TarjetaHistorial(alerta, estado.nombreDelTelefono, estado.cambiosDeSeveridad, zona)
                BotonContorno(
                    texto = stringResource(R.string.detalle_cambiar_revision),
                    alTocar = acciones.alRevisar,
                    modifier = Modifier
                        .fillMaxWidth()
                        .objetivoDelRecorrido(ObjetivoRecorrido.BOTON_REVISAR),
                )
                DatosDelFlujo(alerta, zona)
                Veredictos(alerta)
            }
            ModoDetalle.PENDIENTE -> {
                DatosDelFlujo(alerta, zona)
                Veredictos(alerta)
                alerta.revisionActual?.let { TarjetaRevisionPendiente(it, estado.nombreDelTelefono, zona) }
            }
            ModoDetalle.CONFLICTO -> {
                val ajena = accion?.revisionAjena
                if (ajena != null) TarjetaConflicto(ajena = ajena, propia = accion.revision, zona = zona)
                DatosDelFlujo(alerta, zona)
                Veredictos(alerta)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    BotonContorno(
                        texto = textoMantener(ajena?.autor),
                        alTocar = acciones.alQuedarmeConLaOtra,
                        modifier = Modifier.weight(1f),
                        relleno = RellenoBotonAngosto,
                    )
                    BotonPrincipal(texto = stringResource(R.string.conflicto_aplicar), alTocar = acciones.alAplicarLaMia, modifier = Modifier.weight(1f))
                }
            }
            ModoDetalle.ENVIO_FALLIDO -> {
                if (accion != null) TarjetaEnvioFallido(accion, acciones)
                DatosDelFlujo(alerta, zona)
                Veredictos(alerta)
                alerta.revisionActual?.let { TarjetaRevisionPendiente(it, estado.nombreDelTelefono, zona) }
            }
        }
    }
}

/**
 * Figma 3-12: la severidad y el estado (o "Pendiente de sincronizar", 3-70), "pc-rrhh → srv-sueldos"
 * en SemiBold 18 y "SSH · TCP/22 · 02:09:12 · detectada por IA y umbrales" en Regular 13 gris.
 */
@Composable
private fun Encabezado(alerta: Alerta, estado: EstadoRevision, enCola: Boolean, envio: EstadoEnvio?, zona: ZoneId) {
    val metodos = alerta.metodos.sorted().map { stringResource(it.nombreEnFrase()) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            IndicadorSeveridad(alerta.severidad)
            if (enCola) {
                Etiqueta(texto = stringResource(R.string.detalle_pendiente_de_sincronizar), tono = Tonos.Info)
            } else {
                IndicadorEstadoRevision(estado)
                if (envio == EstadoEnvio.ERROR) IndicadorEnvio(envio)
            }
        }
        Text(text = "${alerta.origen.etiqueta} → ${alerta.destino.etiqueta}", style = MaterialTheme.typography.encabezado)
        Text(
            text = listOf(
                Formato.servicioProtocoloYPuerto(alerta.servicio, alerta.protocolo, alerta.puerto),
                Formato.horaConSegundos(alerta.inicio, zona),
                stringResource(R.string.detalle_detectada_por, enumerar(metodos)),
            ).joinToString(" · "),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Figma 3-21: la tarjeta con borde y las filas de etiqueta y valor. Un solo "?", en el título (decisión
 * 48), explica las dos filas que no se entienden solas: las conexiones en 60 s y en total (el episodio).
 */
@Composable
private fun DatosDelFlujo(alerta: Alerta, zona: ZoneId) {
    Panel {
        TituloSeccion(stringResource(R.string.detalle_datos_del_flujo), ayuda = stringResource(R.string.ayuda_datos_del_flujo))
        FilaDato(stringResource(R.string.detalle_origen), Formato.ipYNombre(alerta.origen))
        FilaDato(stringResource(R.string.detalle_destino), Formato.ipYNombre(alerta.destino))
        FilaDato(stringResource(R.string.detalle_servicio), Formato.servicioProtocoloYPuerto(alerta.servicio, alerta.protocolo, alerta.puerto))
        FilaDato(stringResource(R.string.detalle_inicio), Formato.horaConSegundos(alerta.inicio, zona))
        FilaDato(stringResource(R.string.detalle_duracion), Formato.duracion(alerta.duracion))
        FilaDato(stringResource(R.string.detalle_conexiones_60s), alerta.veredictos.umbrales.contexto60s.toString())
        FilaDato(stringResource(R.string.detalle_conexiones_total), alerta.cantidadFlujos.toString())
        if (alerta.puertosDistintos > 1) {
            FilaDato(stringResource(R.string.detalle_puertos_distintos), alerta.puertosDistintos.toString())
        }
    }
}

/**
 * "Predicción por método" (Figma 468-686): cada método dice con palabras cuánto se pasó de lo normal, en
 * la misma escala para la IA y los umbrales, sin el umbral ni los números con ceros (decisión 37). Un
 * solo "?", en el título, explica los tres métodos (decisión 48).
 */
@Composable
private fun Veredictos(alerta: Alerta) {
    Panel {
        TituloSeccion(stringResource(R.string.detalle_veredictos), ayuda = stringResource(R.string.ayuda_metodos))
        val veredictos = alerta.veredictos
        FilaVeredicto(
            titulo = stringResource(R.string.detalle_modelo_ia),
            detalle = veredictos.ia.lectura.texto(),
            anomalo = veredictos.ia.anomalo,
        )
        FilaVeredicto(
            titulo = stringResource(R.string.detalle_umbrales),
            detalle = veredictos.umbrales.lectura.texto(),
            anomalo = veredictos.umbrales.anomalo,
        )
        FilaVeredicto(
            titulo = stringResource(R.string.detalle_reglas),
            detalle = veredictos.reglas.lectura.texto(),
            anomalo = veredictos.reglas.anomalo,
        )
    }
}

/** Figma 468-691: nombre en Medium 14 y lo que dice en Regular 12 gris, con la etiqueta a la derecha. */
@Composable
private fun FilaVeredicto(titulo: String, detalle: String, anomalo: Boolean) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(text = titulo, style = MaterialTheme.typography.valor)
            Text(text = detalle, style = MaterialTheme.typography.nota, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        IndicadorVeredicto(anomalo = anomalo)
    }
}

/** Figma 51-2379: resultado con la hora en que llegó al servidor, motivo, comentario y quién. */
@Composable
private fun TarjetaRevision(revision: Revision, nombreDelTelefono: String, zona: ZoneId) {
    val resultado = stringResource(revision.resultado.etiqueta())
    Panel {
        TituloSeccion(stringResource(R.string.detalle_revision))
        FilaDato(
            etiqueta = stringResource(R.string.detalle_resultado),
            valor = if (revision.origen == OrigenRevision.SERVIDOR) {
                stringResource(R.string.detalle_resultado_sincronizada, resultado, Formato.hora(revision.fecha, zona))
            } else {
                resultado
            },
        )
        FilaDato(stringResource(R.string.detalle_motivo), stringResource(revision.motivo.etiqueta()))
        FilaDato(stringResource(R.string.detalle_comentario), revision.comentario)
        FilaDato(stringResource(R.string.detalle_quien), autorDe(revision, nombreDelTelefono))
    }
}

/** Figma 3-70: la revisión que todavía no salió, con "Revisada 02:13 · Celular Martín · pendiente de envío". */
@Composable
private fun TarjetaRevisionPendiente(revision: Revision, nombreDelTelefono: String, zona: ZoneId) {
    Panel {
        TituloSeccion(stringResource(R.string.detalle_revision))
        FilaDato(stringResource(R.string.detalle_resultado), stringResource(revision.resultado.etiqueta()))
        FilaDato(stringResource(R.string.detalle_motivo), stringResource(revision.motivo.etiqueta()))
        FilaDato(stringResource(R.string.detalle_comentario), revision.comentario)
        FilaDato(
            etiqueta = stringResource(R.string.detalle_revisada),
            valor = listOf(
                Formato.hora(revision.hechaEn, zona),
                autorDe(revision, nombreDelTelefono),
                stringResource(R.string.detalle_pendiente_de_envio),
            ).joinToString(" · "),
        )
    }
}

/** Un momento de la línea de tiempo del historial. */
private data class EventoHistorial(val momento: Instant, val titulo: String, val detalle: String?)

/**
 * Figma 51-2475: la línea de tiempo, de lo último a lo primero. Cada revisión dice cuándo se hizo y,
 * si salió más tarde (se hizo sin conexión), también cuándo llegó al servidor (decisión 21). El aviso
 * de Telegram aparece con la hora en que salió, que informa el servidor.
 */
@Composable
private fun TarjetaHistorial(alerta: Alerta, nombreDelTelefono: String, cambios: List<CambioDeSeveridad>, zona: ZoneId) {
    val eventos = mutableListOf<EventoHistorial>()
    // Un cambio de configuración que cambió su color (Figma 468-1315): "Pasó de alta a media".
    cambios.forEach { cambio ->
        val registro = cambio.cambio
        val objeto = registro.tipoDeAtaque?.let { stringResource(it.nombreCorto()).lowercase() } ?: (registro.nombre ?: registro.objeto)
        val motivo = registro.motivo?.takeIf { it.isNotBlank() }
        eventos += EventoHistorial(
            momento = cambio.fecha,
            titulo = stringResource(
                R.string.historial_paso_de,
                stringResource(cambio.antes.etiqueta()).lowercase(),
                stringResource(cambio.despues.etiqueta()).lowercase(),
            ),
            detalle = if (motivo != null) {
                stringResource(R.string.historial_cambio_con_motivo, registro.autor, objeto, motivo)
            } else {
                stringResource(R.string.historial_cambio, registro.autor, objeto)
            },
        )
    }
    // Las asignaciones (decisión 42): "Asignada a Laura · Taiel", "Laura se la asignó", "Laura la soltó".
    var anterior: Asignacion? = null
    alerta.asignaciones.sortedBy { it.fecha }.forEach { asignacion ->
        val responsable = asignacion.responsable.orEmpty()
        eventos += when {
            asignacion.responsableId != null && asignacion.propia ->
                EventoHistorial(asignacion.fecha, stringResource(R.string.historial_se_la_asigno, responsable), null)
            asignacion.responsableId != null ->
                EventoHistorial(asignacion.fecha, stringResource(R.string.historial_asignada_a, responsable), asignacion.por)
            anterior?.responsableId == asignacion.porId ->
                EventoHistorial(asignacion.fecha, stringResource(R.string.historial_la_solto, asignacion.por), stringResource(R.string.historial_quedo_sin_asignar))
            else ->
                EventoHistorial(asignacion.fecha, stringResource(R.string.historial_sin_asignar), stringResource(R.string.historial_saco_la_asignacion, asignacion.por))
        }
        anterior = asignacion
    }
    alerta.historial.forEach { revision ->
        val autor = autorDe(revision, nombreDelTelefono)
        val titulo = stringResource(
            R.string.historial_revisada,
            stringResource(revision.resultado.etiqueta()).lowercase(),
            stringResource(revision.motivo.etiqueta()),
        )
        val revisadaEn = revision.revisadaEn
        when {
            revision.origen == OrigenRevision.ESTE_TELEFONO ->
                eventos += EventoHistorial(revision.fecha, titulo, "$autor · ${stringResource(R.string.detalle_pendiente_de_envio)}")
            revisadaEn != null && Duration.between(revisadaEn, revision.fecha) >= SALIO_DESPUES -> {
                eventos += EventoHistorial(revision.fecha, stringResource(R.string.historial_enviada), autor)
                eventos += EventoHistorial(revisadaEn, titulo, "$autor · ${stringResource(R.string.historial_quedo_pendiente)}")
            }
            else -> eventos += EventoHistorial(revision.hechaEn, titulo, autor)
        }
    }
    val metodos = alerta.metodos.sorted().map { stringResource(it.nombreEnHistorial()) }
    eventos += EventoHistorial(alerta.inicio, stringResource(R.string.historial_detectada, enumerar(metodos)), null)
    alerta.avisoTelegram?.let { eventos += EventoHistorial(it, stringResource(R.string.historial_aviso_telegram), null) }

    Panel {
        TituloSeccion(stringResource(R.string.detalle_historial))
        eventos.sortedByDescending { it.momento }.forEach { evento ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                // Números de ancho fijo y 32 de mínimo (Figma 51-2478): los textos de cada evento quedan alineados.
                Text(
                    text = if (Formato.mismoDia(evento.momento, alerta.inicio, zona)) Formato.hora(evento.momento, zona) else Formato.fechaYHora(evento.momento, zona),
                    style = MaterialTheme.typography.rotulo.copy(fontFeatureSettings = "tnum"),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.widthIn(min = 32.dp),
                )
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(text = evento.titulo, style = MaterialTheme.typography.opcion)
                    if (evento.detalle != null) {
                        Text(text = evento.detalle, style = MaterialTheme.typography.nota, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

/** Una revisión que llegó al servidor más de un minuto después de hacerse quedó pendiente en el teléfono. */
private val SALIO_DESPUES: Duration = Duration.ofMinutes(1)

/**
 * Figma 51-2579: tarjeta azul arriba con quién la revisó, cuándo y con qué motivo, y que la propia
 * todavía no se aplicó (RF04, criterio 4).
 */
@Composable
private fun TarjetaConflicto(ajena: Revision, propia: Revision?, zona: ZoneId) {
    val textos = buildList {
        add(
            stringResource(
                R.string.conflicto_texto,
                ajena.autor ?: stringResource(R.string.conflicto_otra_persona),
                stringResource(ajena.resultado.etiqueta()).lowercase(),
                Formato.hora(ajena.hechaEn, zona),
                stringResource(ajena.motivo.etiqueta()),
            ),
        )
        if (propia != null) {
            add(stringResource(R.string.conflicto_tu_revision, stringResource(propia.motivo.etiqueta()), Formato.hora(propia.fecha, zona)))
        }
    }
    TarjetaTono(tono = Tonos.Info, titulo = stringResource(R.string.conflicto_titulo), textos = textos)
}

/** "Mantener la de Lucía" (Figma 51-2586); con un nombre más largo no entra en medio ancho y queda "Mantener la otra". */
@Composable
private fun textoMantener(autor: String?): String =
    if (autor != null && autor.length <= LARGO_NOMBRE_EN_BOTON) {
        stringResource(R.string.conflicto_mantener, autor)
    } else {
        stringResource(R.string.conflicto_mantener_otra)
    }

private const val LARGO_NOMBRE_EN_BOTON = 8

/** La revisión no salió después de cinco intentos, o el servidor la rechazó: Reintentar a mano (informe, 14.3). */
@Composable
private fun TarjetaEnvioFallido(accion: AccionPendiente, acciones: AccionesDetalle) {
    Surface(color = Tonos.Alta.fondo, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(text = stringResource(R.string.envio_error_titulo), style = MaterialTheme.typography.labelLarge, color = Tonos.Alta.texto)
            Text(
                text = stringResource(R.string.envio_error_detalle, accion.intentos, accion.ultimoError.orEmpty()),
                style = MaterialTheme.typography.bodySmall,
                color = Tonos.Alta.texto,
            )
            BotonTonal(texto = stringResource(R.string.error_reintentar), alTocar = acciones.alReintentarEnvio)
        }
    }
}

/**
 * Figma 3-139: la alerta no está en el teléfono (llegó el enlace del aviso sin red). El detalle se pide
 * a la API cuando haya conexión; mientras tanto se muestra lo que trajo el aviso (RF03, criterio 3).
 * Si el servidor dice que ya no existe, se muestra el error.
 */
@Composable
private fun PendienteDeDescarga(estado: DetalleUiState.NoDisponible, acciones: AccionesDetalle, zona: ZoneId) {
    val error = estado.error
    if (error != null && error !is ErrorZonda.SinConexion) {
        EstadoError(
            titulo = stringResource(R.string.detalle_no_disponible_titulo),
            detalle = stringResource(error.mensaje()),
            alReintentar = acciones.alReintentarDescarga,
            modifier = Modifier.padding(16.dp),
        )
        return
    }
    BannerSinConexion(datosDe = null, texto = stringResource(R.string.detalle_sin_descargar_banner))
    Column(
        modifier = Modifier
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        val aviso = estado.aviso
        if (aviso == null) {
            Row { Etiqueta(texto = stringResource(R.string.detalle_pendiente_de_descarga), tono = Tonos.Info) }
        } else {
            LoQueTrajoElAviso(aviso, zona)
        }
        // Figma 51-2609: recuadro crema con el título SemiBold 14 y el texto Regular 13, los dos grises.
        Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(text = stringResource(R.string.detalle_descarga_titulo), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(text = stringResource(R.string.detalle_descarga_texto), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        BotonPrincipal(texto = stringResource(R.string.detalle_descarga_reintentar), alTocar = acciones.alReintentarDescarga)
        BotonEnlace(texto = stringResource(R.string.detalle_descarga_volver), alTocar = acciones.alVolver, modifier = Modifier.fillMaxWidth())
    }
}

/**
 * Figma 3-139: lo que trajo el aviso mientras el detalle no está en el teléfono. Arriba, como el
 * encabezado del detalle: la severidad, "Pendiente de descarga", el servicio y la hora del aviso.
 * Abajo, la tarjeta con la severidad, el servicio y la hora de detección. Nada más: el aviso no trae
 * direcciones IP ni nombres de equipos.
 */
@Composable
private fun LoQueTrajoElAviso(aviso: AvisoRecibido, zona: ZoneId) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            IndicadorSeveridad(aviso.severidad)
            Etiqueta(texto = stringResource(R.string.detalle_pendiente_de_descarga), tono = Tonos.Info)
        }
        Text(text = Formato.servicio(aviso.servicio), style = MaterialTheme.typography.encabezado)
        aviso.enviadoEn?.let { enviado ->
            Text(
                text = stringResource(R.string.detalle_aviso_de_las, Formato.hora(enviado, zona)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    Panel {
        TituloSeccion(stringResource(R.string.detalle_lo_que_trajo))
        FilaDato(stringResource(R.string.detalle_trajo_severidad), stringResource(aviso.severidad.etiqueta()))
        FilaDato(stringResource(R.string.detalle_trajo_servicio), Formato.servicio(aviso.servicio))
        aviso.deteccion?.let { FilaDato(stringResource(R.string.detalle_trajo_hora), Formato.hora(it, zona)) }
    }
}

/**
 * Lo que comparte el detalle: lo mismo que el aviso de Telegram, sin direcciones IP ni nombres de
 * equipos ("Alerta ALTA · SSH · 02:09", de qué red viene) y el enlace que abre la alerta en Zonda.
 */
@Composable
private fun textoParaCompartir(alerta: Alerta, red: String, zona: ZoneId): String = stringResource(
    R.string.detalle_compartir_texto,
    stringResource(alerta.severidad.etiqueta()).uppercase(),
    Formato.servicio(alerta.servicio),
    Formato.hora(alerta.inicio, zona),
    red,
    RutaDetalle.enlaceDelAviso(alerta),
)

/**
 * El autor de la revisión: la persona y su teléfono ("Laura · Teléfono de Laura", decisión 39), o solo el
 * teléfono si se pareó antes de la 1.0.5. Las de este teléfono que todavía no salieron llevan su nombre.
 */
private fun autorDe(revision: Revision, nombreDelTelefono: String): String {
    val telefono = revision.autor ?: nombreDelTelefono
    return revision.persona?.takeIf { it != telefono }?.let { "$it · $telefono" } ?: telefono
}

/** "IA", "IA y umbrales", "IA, umbrales y reglas". */
private fun enumerar(partes: List<String>): String = when (partes.size) {
    0 -> ""
    1 -> partes.single()
    else -> partes.dropLast(1).joinToString(", ") + " y " + partes.last()
}
