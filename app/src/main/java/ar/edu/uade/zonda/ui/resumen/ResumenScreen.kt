package ar.edu.uade.zonda.ui.resumen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ar.edu.uade.zonda.R
import ar.edu.uade.zonda.domain.model.FiltroResumen
import ar.edu.uade.zonda.domain.model.MetodoDeteccion
import ar.edu.uade.zonda.domain.model.PeriodoResumen
import ar.edu.uade.zonda.domain.model.ResumenActividad
import ar.edu.uade.zonda.domain.model.RevisionResumen
import ar.edu.uade.zonda.domain.model.SeveridadPorTipo
import ar.edu.uade.zonda.domain.model.TipoAtaque
import ar.edu.uade.zonda.domain.model.ToqueResumen
import ar.edu.uade.zonda.ui.Formato
import ar.edu.uade.zonda.ui.components.AvisoInferior
import ar.edu.uade.zonda.ui.components.BannerSinConexion
import ar.edu.uade.zonda.ui.components.BarraSuperior
import ar.edu.uade.zonda.ui.components.BotonPrincipal
import ar.edu.uade.zonda.ui.components.ChipOpcion
import ar.edu.uade.zonda.ui.components.EsqueletoLista
import ar.edu.uade.zonda.ui.components.EstadoVacio
import ar.edu.uade.zonda.ui.components.HojaTipos
import ar.edu.uade.zonda.ui.components.TituloDeBloque
import ar.edu.uade.zonda.ui.recorrido.ObjetivoRecorrido
import ar.edu.uade.zonda.ui.recorrido.objetivoDelRecorrido
import ar.edu.uade.zonda.ui.theme.CremaBarra
import ar.edu.uade.zonda.ui.theme.Tonos
import ar.edu.uade.zonda.ui.theme.VerdeZonda
import ar.edu.uade.zonda.ui.theme.VerdeZondaClaro
import ar.edu.uade.zonda.ui.theme.nota
import ar.edu.uade.zonda.ui.viewModelDeZonda
import java.time.ZoneId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * Pantalla Resumen (RF07; Figma 52-2629 a 107-5511): la actividad de 7 o 30 días de la red activa,
 * calculada en el teléfono. Tocar una cifra suma o saca su filtro y todo se recalcula (cambio 16); el
 * botón "Filtros" abre la hoja con todos, que también lleva a la lista de esas alertas
 * ([alVerAlertas]). "Compartir resumen" arma la imagen sin IP ni nombres de equipos y abre la hoja de
 * compartir.
 */
@Composable
fun ResumenScreen(
    alVolver: () -> Unit,
    alVerAlertas: () -> Unit = {},
    viewModel: ResumenViewModel = resumenViewModel(),
) {
    val estado by viewModel.estado.collectAsStateWithLifecycle()
    val contexto = LocalContext.current
    val alcance = rememberCoroutineScope()
    val capa = rememberGraphicsLayer()
    var imagen by remember { mutableStateOf<ImagenCompartida?>(null) }
    // La app elegida para compartir: el aviso "Resumen enviado por …" se da al volver de ella.
    var enviadoA by rememberSaveable { mutableStateOf<DestinoCompartir?>(null) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        enviadoA?.let(viewModel::compartido)
        enviadoA = null
    }
    val zona = remember { ZoneId.systemDefault() }
    val resumen = estado.resumen

    Box(modifier = Modifier.fillMaxSize()) {
        if (resumen != null) {
            // La imagen que se comparte (Figma 107-4862) se dibuja en una capa aparte y no en la pantalla:
            // está en la composición para poder capturarla, pero nunca se pinta ni la lee TalkBack.
            Box(
                modifier = Modifier
                    .wrapContentSize(align = Alignment.TopStart, unbounded = true)
                    .requiredWidth(AnchoImagen)
                    .clearAndSetSemantics {}
                    .drawWithContent { capa.record { this@drawWithContent.drawContent() } },
            ) {
                ImagenResumen(resumen = resumen, red = estado.red, datosDe = estado.datosDe, sinConexion = estado.sinConexion, zona = zona, altas = estado.altas)
            }
        }
        ResumenContenido(
            estado = estado,
            alVolver = alVolver,
            alElegirPeriodo = viewModel::elegirPeriodo,
            alTocar = viewModel::tocar,
            alAplicarFiltro = viewModel::aplicarFiltro,
            alVerAlertas = { filtro ->
                viewModel.aplicarFiltro(filtro)
                alVerAlertas()
            },
            alCompartir = {
                val actual = estado.resumen
                if (actual != null) {
                    alcance.launch {
                        try {
                            imagen = guardarImagen(
                                contexto = contexto,
                                captura = capa.toImageBitmap(),
                                nombre = nombreDeLaImagen(actual.tramo, contexto.resources),
                                texto = textoParaCompartir(actual, contexto.resources),
                            )
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            // Sin espacio en la caché o sin la capa todavía dibujada: se avisa y no se abre la hoja.
                            viewModel.compartirFallo()
                        }
                    }
                }
            },
            alAvisoMostrado = viewModel::avisoMostrado,
            zona = zona,
        )
    }

    imagen?.let { lista ->
        HojaCompartir(
            imagen = lista,
            destinos = remember { destinosDisponibles(contexto) },
            alElegir = { destino ->
                imagen = null
                when {
                    !compartirCon(contexto, destino, lista) -> viewModel.compartirFallo()
                    // Copiar no sale de la pantalla: el aviso va enseguida (Figma 107-5349).
                    destino == DestinoCompartir.COPIAR -> viewModel.compartido(destino)
                    else -> enviadoA = destino
                }
            },
            alCancelar = { imagen = null },
        )
    }
}

/**
 * El ViewModel del Resumen. La lista "Ver estas alertas" pasa la entrada del Resumen como [dueño]: así
 * comparten los filtros y la flecha vuelve al Resumen como estaba.
 */
@Composable
fun resumenViewModel(dueño: ViewModelStoreOwner? = null): ResumenViewModel =
    viewModelDeZonda(dueño) { ResumenViewModel(it.observarResumen, it.redes, it.conectividad) }

/** El resumen calculado, si ya hay uno (con alertas o vacío). */
val ResumenUiState.resumen: ResumenActividad?
    get() = when (val contenido = contenido) {
        is ContenidoResumen.Datos -> contenido.resumen
        is ContenidoResumen.Vacio -> contenido.resumen
        ContenidoResumen.Cargando, ContenidoResumen.SinRed -> null
    }

/** La pantalla sin ViewModel: recibe el estado y devuelve las acciones. Así se prueba sin Android real. */
@Composable
fun ResumenContenido(
    estado: ResumenUiState,
    alVolver: () -> Unit,
    alElegirPeriodo: (PeriodoResumen) -> Unit,
    alTocar: (ToqueResumen) -> Unit,
    alCompartir: () -> Unit,
    alAplicarFiltro: (FiltroResumen) -> Unit = {},
    alVerAlertas: (FiltroResumen) -> Unit = {},
    alAvisoMostrado: () -> Unit = {},
    zona: ZoneId = ZoneId.systemDefault(),
) {
    val snackbar = remember { SnackbarHostState() }
    val contexto = LocalContext.current
    val alcance = rememberCoroutineScope()
    LaunchedEffect(estado.aviso) {
        val aviso = estado.aviso ?: return@LaunchedEffect
        // Se consume enseguida y se muestra en otro alcance, para que consumirlo no lo corte.
        alAvisoMostrado()
        val texto = textoDelAviso(aviso, contexto.resources)
        alcance.launch { snackbar.showSnackbar(texto) }
    }
    val hayResumen = estado.resumen != null
    var ayuda by rememberSaveable { mutableStateOf<AyudaResumen?>(null) }
    var filtrando by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            BarraSuperior(
                titulo = stringResource(R.string.resumen_titulo),
                alVolver = alVolver,
                acciones = {
                    IconButton(onClick = alCompartir, enabled = hayResumen) {
                        Icon(
                            imageVector = Icons.Filled.Share,
                            contentDescription = stringResource(R.string.resumen_compartir),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
            )
        },
        bottomBar = {
            if (hayResumen) {
                // Figma 52-2629: el botón queda fijo abajo, sobre blanco, con 16 alrededor.
                Surface(color = MaterialTheme.colorScheme.surface) {
                    BotonPrincipal(
                        texto = stringResource(R.string.resumen_compartir),
                        alTocar = alCompartir,
                        icono = Icons.Filled.Share,
                        modifier = Modifier
                            .navigationBarsPadding()
                            .padding(16.dp),
                    )
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) { AvisoInferior(it) } },
    ) { relleno ->
        Column(
            modifier = Modifier
                .padding(relleno)
                .fillMaxSize(),
        ) {
            if (estado.sinConexion) {
                BannerSinConexion(
                    datosDe = estado.datosDe,
                    zona = zona,
                    texto = estado.datosDe?.let { stringResource(R.string.resumen_sin_conexion, Formato.hora(it, zona)) },
                )
            }
            when (val contenido = estado.contenido) {
                ContenidoResumen.Cargando -> EsqueletoLista(modifier = Modifier.padding(16.dp), cantidad = 4)
                ContenidoResumen.SinRed -> EstadoVacio(
                    titulo = stringResource(R.string.vacio_sin_red_titulo),
                    detalle = stringResource(R.string.vacio_sin_red_detalle),
                )
                is ContenidoResumen.Vacio -> CuerpoVacio(estado, contenido.resumen, alElegirPeriodo, alAbrirFiltros = { filtrando = true }, zona)
                is ContenidoResumen.Datos -> CuerpoDelResumen(
                    estado = estado,
                    resumen = contenido.resumen,
                    alElegirPeriodo = alElegirPeriodo,
                    alTocar = alTocar,
                    alAbrirFiltros = { filtrando = true },
                    alPedirAyuda = { ayuda = it },
                    zona = zona,
                )
            }
        }
    }

    // Las hojas del ⓘ (cambios 14 y 15) explican lo que se ve, con el filtro elegido.
    val resumenActual = estado.resumen
    if (resumenActual != null) {
        when (ayuda) {
            AyudaResumen.PRECISION -> HojaPrecision(resumen = resumenActual, altas = estado.altas, alCerrar = { ayuda = null })
            AyudaResumen.TIPOS -> HojaTipos(severidades = estado.severidades, alCerrar = { ayuda = null })
            null -> Unit
        }
        // La hoja de filtros (cambio 16) cuenta cuántas quedarían con las alertas del período.
        if (filtrando) {
            HojaFiltrosResumen(
                filtro = estado.filtro,
                barras = resumenActual.barras.map { it.franja },
                severidades = estado.severidades,
                cuantas = { filtro -> resumenActual.delPeriodo.count { filtro.incluye(it, zona) } },
                alMostrar = { filtro ->
                    filtrando = false
                    alAplicarFiltro(filtro)
                },
                alVerEnLista = { filtro ->
                    filtrando = false
                    alVerAlertas(filtro)
                },
                alCerrar = { filtrando = false },
            )
        }
    }
}

/** Figma 52-2629: 16 de margen y 16 entre piezas; el contenido se desplaza por encima del botón. */
@Composable
private fun CuerpoDelResumen(
    estado: ResumenUiState,
    resumen: ResumenActividad,
    alElegirPeriodo: (PeriodoResumen) -> Unit,
    alTocar: (ToqueResumen) -> Unit,
    alAbrirFiltros: () -> Unit,
    alPedirAyuda: (AyudaResumen) -> Unit,
    zona: ZoneId,
) {
    val recursos = LocalContext.current.resources
    val altas = estado.altas
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        FilaDePeriodo(periodo = estado.periodo, cantidadDeFiltros = estado.filtro.cantidad, alElegir = alElegirPeriodo, alAbrirFiltros = alAbrirFiltros)
        LineaDelResumen(estado, resumen, zona)
        FilaDeTotales(resumen, alTocar, modifier = Modifier.objetivoDelRecorrido(ObjetivoRecorrido.CIFRAS_RESUMEN))
        FilaDeMetricas(resumen, altas, alTocar)
        TarjetaGrafico(resumen, alTocar, frase = fraseDelGrafico(resumen, recursos, altas))
        TarjetaTipos(resumen, estado.severidades, alTocar, alPedirAyuda = { alPedirAyuda(AyudaResumen.TIPOS) })
        TarjetaRealesYFalsos(resumen, alTocar)
        TarjetaPrecision(resumen, alTocar, alPedirAyuda = { alPedirAyuda(AyudaResumen.PRECISION) })
    }
}

/** Sin alertas en el período (Figma 52-2976): el círculo verde con el tilde. */
@Composable
private fun CuerpoVacio(
    estado: ResumenUiState,
    resumen: ResumenActividad,
    alElegirPeriodo: (PeriodoResumen) -> Unit,
    alAbrirFiltros: () -> Unit,
    zona: ZoneId,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        FilaDePeriodo(periodo = estado.periodo, cantidadDeFiltros = estado.filtro.cantidad, alElegir = alElegirPeriodo, alAbrirFiltros = alAbrirFiltros)
        Spacer(modifier = Modifier.height(16.dp))
        LineaDelResumen(estado, resumen, zona)
        val siete = resumen.periodo == PeriodoResumen.SIETE_DIAS
        EstadoVacio(
            titulo = stringResource(if (siete) R.string.resumen_vacio_titulo_7 else R.string.resumen_vacio_titulo_30),
            detalle = stringResource(if (siete) R.string.resumen_vacio_detalle_7 else R.string.resumen_vacio_detalle_30),
            colorCirculo = Tonos.Baja.fondo,
            tamanioCirculo = 56.dp,
            icono = Icons.Filled.Check,
            colorIcono = Tonos.Baja.texto,
            modifier = Modifier.padding(top = 16.dp),
        )
    }
}

/**
 * 7 días, 30 días y, a la derecha, el botón "Filtros" con cuántos hay puestos (cambio 16; Figma
 * 52-2629 y 463-1145). El recorrido resalta toda la fila (paso 10).
 */
@Composable
private fun FilaDePeriodo(
    periodo: PeriodoResumen,
    cantidadDeFiltros: Int,
    alElegir: (PeriodoResumen) -> Unit,
    alAbrirFiltros: () -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .objetivoDelRecorrido(ObjetivoRecorrido.PERIODO_RESUMEN),
    ) {
        ChipOpcion(
            texto = stringResource(R.string.resumen_7_dias),
            elegido = periodo == PeriodoResumen.SIETE_DIAS,
            alTocar = { alElegir(PeriodoResumen.SIETE_DIAS) },
            rol = Role.RadioButton,
        )
        ChipOpcion(
            texto = stringResource(R.string.resumen_30_dias),
            elegido = periodo == PeriodoResumen.TREINTA_DIAS,
            alTocar = { alElegir(PeriodoResumen.TREINTA_DIAS) },
            rol = Role.RadioButton,
        )
        Spacer(modifier = Modifier.weight(1f))
        BotonFiltros(cantidad = cantidadDeFiltros, alTocar = alAbrirFiltros)
    }
}

@Composable
private fun LineaDelResumen(estado: ResumenUiState, resumen: ResumenActividad, zona: ZoneId) {
    Text(
        text = lineaDelResumen(estado.red, resumen.tramo, estado.datosDe, estado.sinConexion, LocalContext.current.resources, zona),
        style = MaterialTheme.typography.nota,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** Los tres totales; tocar "alertas" saca todos los filtros (Figma 52-2629 y 100-7158; cambio 16). */
@Composable
fun FilaDeTotales(resumen: ResumenActividad, alTocar: (ToqueResumen) -> Unit, modifier: Modifier = Modifier) {
    val revision = resumen.filtro.revision
    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
    ) {
        val porcentaje = Formato.porcentaje(resumen.cobertura) ?: "0 %"
        TarjetaTotal(
            numero = resumen.total.toString(),
            rotulo = pluralStringResource(R.plurals.resumen_total_alertas, resumen.total),
            fondo = CremaBarra,
            colorNumero = MaterialTheme.colorScheme.onSurface,
            elegida = false,
            alTocar = { alTocar(ToqueResumen.Total) },
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
        )
        TarjetaTotal(
            numero = resumen.revisadas.toString(),
            rotulo = pluralStringResource(R.plurals.resumen_total_revisadas, resumen.revisadas, porcentaje),
            fondo = VerdeZondaClaro,
            colorNumero = VerdeZonda,
            elegida = revision == RevisionResumen.REVISADAS,
            alTocar = { alTocar(ToqueResumen.Revision(RevisionResumen.REVISADAS)) },
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
        )
        TarjetaTotal(
            numero = resumen.sinRevisar.toString(),
            rotulo = stringResource(R.string.resumen_total_sin_revisar),
            fondo = Tonos.Media.fondo,
            colorNumero = Tonos.Media.texto,
            elegida = revision == RevisionResumen.SIN_REVISAR,
            alTocar = { alTocar(ToqueResumen.Revision(RevisionResumen.SIN_REVISAR)) },
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
        )
    }
}

/**
 * Las dos métricas de éxito (sección 7 del informe); tocarlas filtra por revisadas o por los tipos que
 * la red tiene en alta ([altas], cambio 16).
 */
@Composable
fun FilaDeMetricas(resumen: ResumenActividad, altas: Set<TipoAtaque>, alTocar: (ToqueResumen) -> Unit, modifier: Modifier = Modifier) {
    val filtro = resumen.filtro
    val sinDato = stringResource(R.string.resumen_sin_dato)
    val mediana = resumen.tiempoMedianoHastaRevision
    val enLaPrimeraHora = resumen.altasRevisadasEnUnaHora
    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
    ) {
        TarjetaMetrica(
            valor = mediana?.let(Formato::duracionCorta) ?: sinDato,
            rotulo = stringResource(R.string.resumen_hasta_primera_revision),
            detalle = stringResource(if (mediana == null) R.string.resumen_sin_revisiones else R.string.resumen_mediana),
            elegida = filtro.revision == RevisionResumen.REVISADAS,
            alTocar = { alTocar(ToqueResumen.Revision(RevisionResumen.REVISADAS)) },
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
        )
        TarjetaMetrica(
            valor = Formato.porcentaje(enLaPrimeraHora) ?: sinDato,
            rotulo = stringResource(R.string.resumen_altas_primera_hora),
            detalle = if (enLaPrimeraHora.total == 0) {
                stringResource(R.string.resumen_sin_altas)
            } else {
                stringResource(R.string.resumen_x_de_y, enLaPrimeraHora.parte, enLaPrimeraHora.total)
            },
            elegida = filtro.tiposSonLasAltas(altas),
            alTocar = { alTocar(ToqueResumen.Altas) },
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
        )
    }
}

/**
 * Alertas por día o por semana, con la frase de abajo (Figma 52-2629 y 100-7024). En 7 días la barra
 * de hoy va en el verde base. Las barras elegidas llevan el borde verde y las otras quedan atenuadas.
 */
@Composable
fun TarjetaGrafico(resumen: ResumenActividad, alTocar: (ToqueResumen) -> Unit, frase: String?, modifier: Modifier = Modifier) {
    val siete = resumen.periodo == PeriodoResumen.SIETE_DIAS
    val elegidas = resumen.filtro.barras
    TarjetaResumen(
        titulo = stringResource(if (siete) R.string.resumen_alertas_por_dia else R.string.resumen_alertas_por_semana),
        espacioTitulo = 10.dp,
        modifier = modifier,
    ) {
        GraficoDeBarras(
            barras = resumen.barras.mapIndexed { indice, barra ->
                BarraGrafico(
                    etiqueta = barra.franja.etiquetaGrafico(),
                    cantidad = barra.cantidad,
                    resaltada = siete && indice == resumen.barras.lastIndex,
                    elegida = barra.franja in elegidas,
                    atenuada = elegidas.isNotEmpty() && barra.franja !in elegidas,
                )
            },
            alTocar = { indice -> alTocar(ToqueResumen.Barra(resumen.barras[indice].franja)) },
        )
        if (frase != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = frase, style = estiloNotaResumen, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/**
 * Por tipo de ataque (cambio 14; Figma 52-2629 y 100-8498): los dos bloques de la pantalla de
 * severidades, con sus nombres y su orden fijo, y cada tipo con el punto de la severidad que eligió la
 * red. Un tipo en cero queda en gris; un bloque entero en cero se oculta con su título, salvo que los
 * dos lo estén (con un filtro sin alertas). El ⓘ abre "Qué es cada tipo".
 */
@Composable
private fun TarjetaTipos(
    resumen: ResumenActividad,
    severidades: SeveridadPorTipo,
    alTocar: (ToqueResumen) -> Unit,
    alPedirAyuda: () -> Unit,
) {
    val maximo = resumen.porTipo.values.maxOrNull() ?: 0
    val bloques = listOf(
        R.string.tipos_bloque_ia to TipoAtaque.DETECTADOS_POR_LA_IA,
        R.string.tipos_bloque_sin_ia to TipoAtaque.NO_DETECTADOS_POR_LA_IA,
    ).filter { (_, tipos) -> maximo == 0 || tipos.any { (resumen.porTipo[it] ?: 0) > 0 } }
    TarjetaResumen(
        titulo = stringResource(R.string.resumen_por_tipo),
        espacioTitulo = 10.dp,
        ayuda = AyudaDeTarjeta(stringResource(R.string.tipos_titulo), alPedirAyuda),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            bloques.forEachIndexed { indice, (titulo, tipos) ->
                if (indice > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                TituloDeBloque(stringResource(titulo))
                tipos.forEach { tipo ->
                    FilaTipo(
                        rotulo = stringResource(tipo.enFila()),
                        severidad = severidades.severidadDe(tipo),
                        valor = resumen.porTipo[tipo] ?: 0,
                        maximo = maximo,
                        elegida = tipo in resumen.filtro.tipos,
                        alTocar = { alTocar(ToqueResumen.Tipo(tipo)) },
                    )
                }
            }
        }
    }
}

/** Reales y falsos positivos de las revisadas, con la nota de abajo (Figma 52-2629). */
@Composable
private fun TarjetaRealesYFalsos(resumen: ResumenActividad, alTocar: (ToqueResumen) -> Unit) {
    val maximo = maxOf(resumen.reales, resumen.falsosPositivos)
    TarjetaResumen(titulo = stringResource(R.string.resumen_reales_y_falsos)) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            FilaConBarra(
                rotulo = stringResource(R.string.resumen_reales),
                valor = resumen.reales,
                maximo = maximo,
                color = VerdeZonda,
                elegida = resumen.filtro.revision == RevisionResumen.REALES,
                alTocar = { alTocar(ToqueResumen.Revision(RevisionResumen.REALES)) },
            )
            FilaConBarra(
                rotulo = stringResource(R.string.resumen_falsos_positivos),
                valor = resumen.falsosPositivos,
                maximo = maximo,
                color = NaranjaFalsos,
                elegida = resumen.filtro.revision == RevisionResumen.FALSOS_POSITIVOS,
                alTocar = { alTocar(ToqueResumen.Revision(RevisionResumen.FALSOS_POSITIVOS)) },
            )
        }
        Spacer(modifier = Modifier.height(10.dp))
        Text(text = stringResource(R.string.resumen_nota_falsos), style = estiloNotaResumen, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * La cobertura de revisión y la precisión de cada modelo (Figma 52-2629 y 100-9302), sin notas abajo
 * (cambios 4, 10 y 15): no nombra un ganador, porque la precisión dice cuánto acierta un modelo cuando
 * avisa y no cuál es el mejor. Cómo se lee lo explica la hoja del ⓘ.
 */
@Composable
private fun TarjetaPrecision(resumen: ResumenActividad, alTocar: (ToqueResumen) -> Unit, alPedirAyuda: () -> Unit) {
    val sinDato = stringResource(R.string.resumen_sin_dato)
    TarjetaResumen(
        titulo = stringResource(R.string.resumen_precision),
        ayuda = AyudaDeTarjeta(stringResource(R.string.precision_ayuda), alPedirAyuda),
    ) {
        CajaCobertura(
            rotulo = stringResource(R.string.resumen_cobertura),
            valor = stringResource(
                R.string.resumen_cobertura_valor,
                Formato.porcentaje(resumen.cobertura) ?: "0 %",
                resumen.cobertura.parte,
                resumen.cobertura.total,
            ),
            elegida = resumen.filtro.revision == RevisionResumen.REVISADAS,
            alTocar = { alTocar(ToqueResumen.Revision(RevisionResumen.REVISADAS)) },
        )
        Spacer(modifier = Modifier.height(10.dp))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            MetodoDeteccion.entries.forEach { metodo ->
                val precision = resumen.precisionPorMetodo.getValue(metodo)
                FilaPrecision(
                    rotulo = stringResource(metodo.enPrecision()),
                    porcentaje = Formato.porcentaje(precision) ?: sinDato,
                    xDeY = stringResource(R.string.resumen_x_de_y, precision.parte, precision.total),
                    fraccion = precision.valor?.toFloat() ?: 0f,
                    elegida = resumen.filtro.metodo == metodo,
                    alTocar = { alTocar(ToqueResumen.Metodo(metodo)) },
                    clara = precision.barraClara(),
                )
            }
        }
    }
}
