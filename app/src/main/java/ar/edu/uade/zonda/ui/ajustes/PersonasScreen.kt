package ar.edu.uade.zonda.ui.ajustes

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PersonRemove
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ar.edu.uade.zonda.R
import ar.edu.uade.zonda.domain.model.Perfil
import ar.edu.uade.zonda.domain.model.Persona
import ar.edu.uade.zonda.ui.components.AvisoInferior
import ar.edu.uade.zonda.ui.components.BarraSuperior
import ar.edu.uade.zonda.ui.components.BotonContorno
import ar.edu.uade.zonda.ui.components.BotonPrincipal
import ar.edu.uade.zonda.ui.components.CampoTexto
import ar.edu.uade.zonda.ui.components.ColumnaDePantalla
import ar.edu.uade.zonda.ui.components.FilaPersona
import ar.edu.uade.zonda.ui.components.FilaRadio
import ar.edu.uade.zonda.ui.components.HojaConfirmacion
import ar.edu.uade.zonda.ui.components.HojaInferior
import ar.edu.uade.zonda.ui.components.LeyendaObligatorio
import ar.edu.uade.zonda.ui.components.Opcion
import ar.edu.uade.zonda.ui.components.Panel
import ar.edu.uade.zonda.ui.components.RellenoBotonAngosto
import ar.edu.uade.zonda.ui.components.detalleDePersona
import ar.edu.uade.zonda.ui.descripcion
import ar.edu.uade.zonda.ui.detalle.ConfirmacionConHuella
import ar.edu.uade.zonda.ui.detalle.LocalConfirmacionDelDueno
import ar.edu.uade.zonda.ui.detalle.actividad
import ar.edu.uade.zonda.ui.detalle.confirmarConHuella
import ar.edu.uade.zonda.ui.etiqueta
import ar.edu.uade.zonda.ui.mensaje
import ar.edu.uade.zonda.ui.theme.nota
import ar.edu.uade.zonda.ui.theme.tituloHoja
import ar.edu.uade.zonda.ui.viewModelDeZonda
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.launch

/** El ViewModel de Personas; "Código para Laura" pasa el de Personas como [dueño]. */
@Composable
fun personasViewModel(dueño: androidx.lifecycle.ViewModelStoreOwner? = null): PersonasViewModel =
    viewModelDeZonda(dueño) { contenedor -> PersonasViewModel(contenedor.redes, contenedor.gestionarPersonas) }

/** Lo que se puede hacer en Personas; separado para probar la pantalla sin ViewModel. */
class AccionesPersonas(
    val alVolver: () -> Unit = {},
    val alAgregar: () -> Unit = {},
    val alCambiarNombre: (String) -> Unit = {},
    val alCambiarPerfilNuevo: (Perfil) -> Unit = {},
    val alCancelarAgregar: () -> Unit = {},
    val alGenerarCodigo: () -> Unit = {},
    val alAbrirCodigo: () -> Unit = {},
    val alCambiarPerfil: (Persona, Perfil) -> Unit = { _, _ -> },
    val alCambiarPermisoBloqueo: (Persona, Boolean) -> Unit = { _, _ -> },
    val alQuitar: (Persona) -> Unit = {},
    val alAvisoMostrado: () -> Unit = {},
)

/**
 * Ajustes → Personas (decisión 39; Figma 468-2568): la gente de la red con su perfil, su teléfono y
 * cuándo lo usó. Todos la ven; solo el administrador toca a alguien y tiene "Agregar persona". Sumar a
 * alguien, quitarlo y cambiarle el perfil o el permiso de bloquear se confirman con la huella o el PIN
 * (decisión 51): eso da poder en la red, y lo tiene que hacer el dueño del teléfono.
 */
@Composable
fun PersonasScreen(viewModel: PersonasViewModel, alVolver: () -> Unit, alAbrirCodigo: () -> Unit) {
    val estado by viewModel.estado.collectAsStateWithLifecycle()
    val contexto = LocalContext.current
    val recursos = LocalResources.current
    val confirmacion = LocalConfirmacionDelDueno.current ?: remember(contexto) { contexto.actividad()?.let(::ConfirmacionConHuella) }
    fun conHuella(detalle: String, accion: () -> Unit) = confirmarConHuella(
        pide = true,
        confirmacion = confirmacion,
        titulo = recursos.getString(R.string.huella_titulo),
        detalle = detalle,
        alConfirmar = accion,
        alNoPoder = viewModel::sinBloqueoDePantalla,
    )
    PersonasContenido(
        estado = estado,
        acciones = AccionesPersonas(
            alVolver = alVolver,
            alAgregar = viewModel::empezarAAgregar,
            alCambiarNombre = viewModel::cambiarNombre,
            alCambiarPerfilNuevo = viewModel::cambiarPerfilNuevo,
            alCancelarAgregar = viewModel::cancelarAgregar,
            alGenerarCodigo = {
                // Sin nombre no se pide la huella: la hoja dice primero que falta.
                val formulario = estado.formulario
                if (formulario == null || formulario.nombre.isBlank() || formulario.generando) {
                    viewModel.generarCodigo()
                } else {
                    val perfil = recursos.getString(formulario.perfil.etiqueta()).lowercase()
                    conHuella(recursos.getString(R.string.huella_sumar, formulario.nombre.trim(), perfil), viewModel::generarCodigo)
                }
            },
            alAbrirCodigo = {
                viewModel.codigoAbierto()
                alAbrirCodigo()
            },
            alCambiarPerfil = { persona, perfil ->
                val texto = if (perfil.esAdministrador) R.string.huella_hacer_administrador else R.string.huella_pasar_a_analista
                conHuella(recursos.getString(texto, persona.nombreVisible)) { viewModel.cambiarPerfil(persona, perfil) }
            },
            alCambiarPermisoBloqueo = { persona, puede ->
                val texto = if (puede) R.string.huella_dar_permiso else R.string.huella_sacar_permiso
                conHuella(recursos.getString(texto, persona.nombreVisible)) { viewModel.cambiarPermisoBloqueo(persona, puede) }
            },
            alQuitar = { persona ->
                conHuella(recursos.getString(R.string.huella_quitar, persona.nombreVisible)) { viewModel.quitar(persona) }
            },
            alAvisoMostrado = viewModel::avisoMostrado,
        ),
    )
}

@Composable
fun PersonasContenido(
    estado: PersonasUiState,
    acciones: AccionesPersonas,
    zona: ZoneId = ZoneId.systemDefault(),
    ahora: Instant = Instant.now(),
) {
    val snackbar = remember { SnackbarHostState() }
    val recursos = LocalResources.current
    val alcance = rememberCoroutineScope()
    var elegidaId by rememberSaveable { mutableStateOf<String?>(null) }
    var aQuitarId by rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(estado.abrirCodigo) {
        if (estado.abrirCodigo) acciones.alAbrirCodigo()
    }
    LaunchedEffect(estado.aviso) {
        val aviso = estado.aviso ?: return@LaunchedEffect
        acciones.alAvisoMostrado()
        val texto = when (aviso) {
            is AvisoPersonas.PerfilCambiado -> recursos.getString(
                if (aviso.perfil.esAdministrador) R.string.personas_ahora_administrador else R.string.personas_ahora_analista,
                aviso.nombre,
            )
            is AvisoPersonas.PermisoBloqueoCambiado -> recursos.getString(
                if (aviso.puedeBloquear) R.string.personas_ahora_bloquea else R.string.personas_ahora_pide_aprobacion,
                aviso.nombre,
            )
            is AvisoPersonas.Quitada -> recursos.getString(R.string.personas_quitada, aviso.nombre)
            is AvisoPersonas.Error -> recursos.getString(aviso.error.mensaje())
            AvisoPersonas.SinBloqueoDePantalla -> recursos.getString(R.string.personas_sin_bloqueo_de_pantalla)
        }
        // Consumir el aviso cambia la clave de este efecto: se muestra en otro alcance para no cortarlo.
        alcance.launch { snackbar.showSnackbar(message = texto, actionLabel = recursos.getString(R.string.aviso_ok), duration = SnackbarDuration.Short) }
    }

    Scaffold(
        topBar = { BarraSuperior(titulo = stringResource(R.string.personas_titulo), alVolver = acciones.alVolver) },
        snackbarHost = { SnackbarHost(snackbar) { AvisoInferior(it) } },
    ) { relleno ->
        ColumnaDePantalla(
            modifier = Modifier
                .padding(relleno)
                .fillMaxSize(),
        ) {
            Text(
                text = stringResource(R.string.personas_intro, estado.red?.nombre.orEmpty()),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (estado.personas.isNotEmpty()) {
                Panel(espacio = 4.dp) {
                    estado.personas.forEach { persona ->
                        FilaPersona(
                            persona = persona,
                            ahora = ahora,
                            zona = zona,
                            // Solo el administrador toca a los demás; a sí mismo se cambia desde otro teléfono.
                            alTocar = if (estado.esAdministrador && !persona.vos) ({ elegidaId = persona.id }) else null,
                        )
                    }
                }
            }
            if (estado.esAdministrador) {
                Spacer(modifier = Modifier.weight(1f))
                BotonPrincipal(texto = stringResource(R.string.personas_agregar), alTocar = acciones.alAgregar)
            }
        }
    }

    estado.formulario?.let { formulario ->
        HojaInferior(alCerrar = acciones.alCancelarAgregar) {
            ContenidoHojaAgregarPersona(formulario, acciones)
        }
    }
    estado.personas.firstOrNull { it.id == elegidaId }?.let { persona ->
        HojaInferior(alCerrar = { elegidaId = null }) {
            ContenidoHojaPersona(
                persona = persona,
                ahora = ahora,
                zona = zona,
                alCambiarPerfil = { perfil ->
                    elegidaId = null
                    acciones.alCambiarPerfil(persona, perfil)
                },
                alCambiarPermisoBloqueo = { puede ->
                    elegidaId = null
                    acciones.alCambiarPermisoBloqueo(persona, puede)
                },
                alQuitar = {
                    elegidaId = null
                    aQuitarId = persona.id
                },
            )
        }
    }
    estado.personas.firstOrNull { it.id == aQuitarId }?.let { persona ->
        HojaConfirmacion(
            titulo = stringResource(R.string.personas_quitar_titulo, persona.nombreVisible),
            texto = stringResource(R.string.personas_quitar_texto, estado.red?.nombre.orEmpty()),
            textoConfirmar = stringResource(R.string.personas_quitar_confirmar),
            alConfirmar = {
                aQuitarId = null
                acciones.alQuitar(persona)
            },
            alCerrar = { aQuitarId = null },
        )
    }
}

/**
 * "Agregar persona" (Figma 468-2642): el nombre, el perfil (analista de entrada) y "Generar código". El
 * código lo da el servidor, así que necesita conexión; si no la hay, lo dice la hoja.
 */
@Composable
fun ColumnScope.ContenidoHojaAgregarPersona(formulario: FormularioPersona, acciones: AccionesPersonas) {
    Text(text = stringResource(R.string.personas_agregar), style = MaterialTheme.typography.tituloHoja, modifier = Modifier.semantics { heading() })
    CampoTexto(
        valor = formulario.nombre,
        alCambiar = acciones.alCambiarNombre,
        etiqueta = stringResource(R.string.personas_nombre),
        ejemplo = stringResource(R.string.personas_nombre_ejemplo),
        error = if (formulario.faltaNombre) stringResource(R.string.personas_falta_nombre) else null,
        obligatorio = true,
    )
    Text(text = stringResource(R.string.personas_perfil), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Column(modifier = Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        listOf(Perfil.ANALISTA, Perfil.ADMINISTRADOR).forEach { perfil ->
            FilaRadio(
                opcion = Opcion(perfil, stringResource(perfil.etiqueta()), stringResource(perfil.descripcion())),
                elegida = formulario.perfil == perfil,
                alElegir = { acciones.alCambiarPerfilNuevo(perfil) },
            )
        }
    }
    formulario.error?.let { error ->
        Text(
            text = stringResource(error.mensaje()),
            style = MaterialTheme.typography.nota,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
    }
    LeyendaObligatorio()
    Row(modifier = Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        BotonContorno(texto = stringResource(R.string.accion_cancelar), alTocar = acciones.alCancelarAgregar, modifier = Modifier.weight(1f), relleno = RellenoBotonAngosto)
        BotonPrincipal(
            texto = stringResource(R.string.personas_generar_codigo),
            alTocar = acciones.alGenerarCodigo,
            modifier = Modifier.weight(1f),
            cargando = formulario.generando,
        )
    }
}

/**
 * La hoja de una persona (Figma 468-3010): su nombre, su detalle y lo que el administrador puede hacer:
 * cambiarle el perfil, darle o sacarle a un analista el permiso de bloquear (decisión 51: el de guardia
 * bloquea en el acto; los demás piden aprobación) o quitarla del equipo (con una confirmación, como
 * "Quitar este dispositivo").
 */
@Composable
fun ColumnScope.ContenidoHojaPersona(
    persona: Persona,
    ahora: Instant,
    zona: ZoneId,
    alCambiarPerfil: (Perfil) -> Unit,
    alQuitar: () -> Unit,
    alCambiarPermisoBloqueo: (Boolean) -> Unit = {},
) {
    Text(text = persona.nombreVisible, style = MaterialTheme.typography.tituloHoja, modifier = Modifier.semantics { heading() })
    Text(text = detalleDePersona(persona, ahora, zona), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    if (persona.perfil.esAdministrador) {
        AccionDePersona(Icons.Outlined.Person, stringResource(R.string.personas_pasar_a_analista), MaterialTheme.colorScheme.primary) {
            alCambiarPerfil(Perfil.ANALISTA)
        }
    } else {
        AccionDePersona(Icons.Outlined.Person, stringResource(R.string.personas_hacer_administrador), MaterialTheme.colorScheme.primary) {
            alCambiarPerfil(Perfil.ADMINISTRADOR)
        }
        if (persona.puedeBloquear) {
            AccionDePersona(Icons.Outlined.LockOpen, stringResource(R.string.personas_sacar_permiso_bloquear), MaterialTheme.colorScheme.primary) {
                alCambiarPermisoBloqueo(false)
            }
        } else {
            AccionDePersona(Icons.Outlined.Lock, stringResource(R.string.personas_dar_permiso_bloquear), MaterialTheme.colorScheme.primary) {
                alCambiarPermisoBloqueo(true)
            }
        }
    }
    AccionDePersona(Icons.Outlined.PersonRemove, stringResource(R.string.personas_quitar), MaterialTheme.colorScheme.error, alQuitar)
}

/** Una acción de la hoja: el ícono de 22 y el texto Medium 15, con 10 arriba y abajo y 12 entre los dos. */
@Composable
private fun AccionDePersona(icono: ImageVector, texto: String, color: Color, alTocar: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = alTocar, role = Role.Button)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(imageVector = icono, contentDescription = null, tint = color, modifier = Modifier.size(22.dp))
        Text(text = texto, style = MaterialTheme.typography.titleSmall, color = color)
    }
}
