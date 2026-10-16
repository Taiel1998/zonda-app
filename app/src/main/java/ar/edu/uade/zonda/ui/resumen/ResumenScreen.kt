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
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ar.edu.uade.zonda.R
import ar.edu.uade.zonda.domain.model.GrupoResumen
import ar.edu.uade.zonda.domain.model.MetodoDeteccion
import ar.edu.uade.zonda.domain.model.PeriodoResumen
import ar.edu.uade.zonda.domain.model.ResumenActividad
import ar.edu.uade.zonda.domain.model.SeveridadPorTipo
import ar.edu.uade.zonda.domain.model.TipoAtaque
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
 * calculada en el teléfono. Tocar una cifra recalcula todo sobre ese grupo y "Compartir resumen" arma
 * la imagen sin IP ni nombres de equipos y abre la hoja de compartir.
 */
@Composable
fun ResumenScreen(
    alVolver: () -> Unit,
    viewModel: ResumenViewModel = viewModelDeZonda { ResumenViewModel(it.observarResumen, it.redes, it.conectividad) },
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
                ImagenResumen(resumen = resumen, red = estado.red, datosDe = estado.datosDe, sinConexion = estado.sinConexion, zona = zona)
            }
        }
        ResumenContenido(
            estado = estado,
            alVolver = alVolver,
            alElegirPeriodo = viewModel::elegirPeriodo,
            alTocarCifra = viewModel::tocarCifra,
            alQuitarFiltro = viewModel::quitarFiltro,
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
    alTocarCifra: (GrupoResumen?) -> Unit,
    alQuitarFiltro: () -> Unit,
    alCompartir: () -> Unit,
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
                is ContenidoResumen.Vacio -> CuerpoVacio(estado, contenido.resumen, alElegirPeriodo, alQuitarFiltro, zona)
                is ContenidoResumen.Datos -> CuerpoDelResumen(
                    estado = estado,
                    resumen = contenido.resumen,
                    alElegirPeriodo = alElegirPeriodo,
                    alTocarCifra = alTocarCifra,
                    alQuitarFiltro = alQuitarFiltro,
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
            AyudaResumen.PRECISION -> HojaPrecision(resumen = resumenActual, alCerrar = { ayuda = null })
            AyudaResumen.TIPOS -> HojaTipos(severidades = estado.severidades, alCerrar = { ayuda = null })
            null -> Unit
        }
    }
}

/** Figma 52-2629: 16 de margen y 16 entre piezas; el contenido se desplaza por encima del botón. */
@Composable
private fun CuerpoDelResumen(
    estado: ResumenUiState,
    resumen: ResumenActividad,
    alElegirPeriodo: (PeriodoResumen) -> Unit,
    alTocarCifra: (GrupoResumen?) -> Unit,
    alQuitarFiltro: () -> Unit,
    alPedirAyuda: (AyudaResumen) -> Unit,
    zona: ZoneId,
) {
    val recursos = LocalContext.current.resources
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        FilaDePeriodo(periodo = estado.periodo, grupo = estado.grupo, alElegir = alElegirPeriodo, alQuitarFiltro = alQuitarFiltro)
        LineaDelResumen(estado, resumen, zona)
        FilaDeTotales(resumen, alTocarCifra, modifier = Modifier.objetivoDelRecorrido(ObjetivoRecorrido.CIFRAS_RESUMEN))
        FilaDeMetricas(resumen, alTocarCifra)
        TarjetaGrafico(resumen, alTocarCifra, frase = fraseDelGrafico(resumen, recursos))
        TarjetaTipos(resumen, estado.severidades, alTocarCifra, alPedirAyuda = { alPedirAyuda(AyudaResumen.TIPOS) })
        TarjetaRealesYFalsos(resumen, alTocarCifra)
        TarjetaPrecision(resumen, alTocarCifra, alPedirAyuda = { alPedirAyuda(AyudaResumen.PRECISION) })
    }
}

/** Sin alertas en el período (Figma 52-2976): el círculo verde con el tilde. */
@Composable
private fun CuerpoVacio(
    estado: ResumenUiState,
    resumen: ResumenActividad,
    alElegirPeriodo: (PeriodoResumen) -> Unit,
    alQuitarFiltro: () -> Unit,
    zona: ZoneId,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        FilaDePeriodo(periodo = estado.periodo, grupo = estado.grupo, alElegir = alElegirPeriodo, alQuitarFiltro = alQuitarFiltro)
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
 * 7 días, 30 días y el chip del filtro aplicado con su cruz (Figma 100-8096). El recorrido resalta
 * toda la fila (paso 10).
 */
@Composable
private fun FilaDePeriodo(
    periodo: PeriodoResumen,
    grupo: GrupoResumen?,
    alElegir: (PeriodoResumen) -> Unit,
    alQuitarFiltro: () -> Unit,
) {
    val recursos = LocalContext.current.resources
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Top,
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
        if (grupo != null) {
            val etiqueta = grupo.etiquetaChip(recursos)
            ChipFiltro(
                texto = etiqueta,
                descripcionQuitar = stringResource(R.string.resumen_quitar_filtro, etiqueta),
                alQuitar = alQuitarFiltro,
            )
        }
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

/** Los tres totales; tocar "alertas" vuelve a todas (Figma 52-2629 y 100-7158). */
@Composable
fun FilaDeTotales(resumen: ResumenActividad, alTocarCifra: (GrupoResumen?) -> Unit, modifier: Modifier = Modifier) {
    val grupo = resumen.grupo
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
            alTocar = { alTocarCifra(null) },
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
        )
        TarjetaTotal(
            numero = resumen.revisadas.toString(),
            rotulo = pluralStringResource(R.plurals.resumen_total_revisadas, resumen.revisadas, porcentaje),
            fondo = VerdeZondaClaro,
            colorNumero = VerdeZonda,
            elegida = grupo == GrupoResumen.Revisadas,
            alTocar = { alTocarCifra(GrupoResumen.Revisadas) },
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
        )
        TarjetaTotal(
            numero = resumen.sinRevisar.toString(),
            rotulo = stringResource(R.string.resumen_total_sin_revisar),
            fondo = Tonos.Media.fondo,
            colorNumero = Tonos.Media.texto,
            elegida = grupo == GrupoResumen.SinRevisar,
            alTocar = { alTocarCifra(GrupoResumen.SinRevisar) },
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
        )
    }
}

/** Las dos métricas de éxito (sección 7 del informe); tocarlas filtra por revisadas o por altas. */
@Composable
fun FilaDeMetricas(resumen: ResumenActividad, alTocarCifra: (GrupoResumen?) -> Unit, modifier: Modifier = Modifier) {
    val grupo = resumen.grupo
    val sinDato = stringResource(R.string.resumen_sin_dato)
    val mediana = resumen.tiempoMedianoHastaRevision
    val altas = resumen.altasRevisadasEnUnaHora
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
            elegida = grupo == GrupoResumen.Revisadas,
            alTocar = { alTocarCifra(GrupoResumen.Revisadas) },
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
        )
        TarjetaMetrica(
            valor = Formato.porcentaje(altas) ?: sinDato,
            rotulo = stringResource(R.string.resumen_altas_primera_hora),
            detalle = if (altas.total == 0) stringResource(R.string.resumen_sin_altas) else stringResource(R.string.resumen_x_de_y, altas.parte, altas.total),
            elegida = grupo == GrupoResumen.Altas,
            alTocar = { alTocarCifra(GrupoResumen.Altas) },
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
        )
    }
}

/**
 * Alertas por día o por semana, con la frase de abajo (Figma 52-2629 y 100-7024). En 7 días la barra
 * de hoy va en el verde base.
 */
@Composable
fun TarjetaGrafico(resumen: ResumenActividad, alTocarCifra: (GrupoResumen?) -> Unit, frase: String?, modifier: Modifier = Modifier) {
    val siete = resumen.periodo == PeriodoResumen.SIETE_DIAS
    val elegido = (resumen.grupo as? GrupoResumen.Barra)?.tramo
    TarjetaResumen(
        titulo = stringResource(if (siete) R.string.resumen_alertas_por_dia else R.string.resumen_alertas_por_semana),
        espacioTitulo = 10.dp,
        modifier = modifier,
    ) {
        GraficoDeBarras(
            barras = resumen.barras.mapIndexed { indice, barra ->
                BarraGrafico(
                    etiqueta = if (siete) Formato.diaDeLaSemanaCorto(barra.tramo.desde) else Formato.diaYMesCorto(barra.tramo.desde),
                    cantidad = barra.cantidad,
                    resaltada = siete && indice == resumen.barras.lastIndex,
                    elegida = barra.tramo == elegido,
                    atenuada = elegido != null && barra.tramo != elegido,
                )
            },
            alTocar = { indice -> alTocarCifra(GrupoResumen.Barra(resumen.barras[indice].tramo)) },
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
    alTocarCifra: (GrupoResumen?) -> Unit,
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
                    val grupo = GrupoResumen.Tipo(tipo)
                    FilaTipo(
                        rotulo = stringResource(tipo.enFila()),
                        severidad = severidades.severidadDe(tipo),
                        valor = resumen.porTipo[tipo] ?: 0,
                        maximo = maximo,
                        elegida = resumen.grupo == grupo,
                        alTocar = { alTocarCifra(grupo) },
                    )
                }
            }
        }
    }
}

/** Reales y falsos positivos de las revisadas, con la nota de abajo (Figma 52-2629). */
@Composable
private fun TarjetaRealesYFalsos(resumen: ResumenActividad, alTocarCifra: (GrupoResumen?) -> Unit) {
    val maximo = maxOf(resumen.reales, resumen.falsosPositivos)
    TarjetaResumen(titulo = stringResource(R.string.resumen_reales_y_falsos)) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            FilaConBarra(
                rotulo = stringResource(R.string.resumen_reales),
                valor = resumen.reales,
                maximo = maximo,
                color = VerdeZonda,
                elegida = resumen.grupo == GrupoResumen.Reales,
                alTocar = { alTocarCifra(GrupoResumen.Reales) },
            )
            FilaConBarra(
                rotulo = stringResource(R.string.resumen_falsos_positivos),
                valor = resumen.falsosPositivos,
                maximo = maximo,
                color = NaranjaFalsos,
                elegida = resumen.grupo == GrupoResumen.FalsosPositivos,
                alTocar = { alTocarCifra(GrupoResumen.FalsosPositivos) },
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
private fun TarjetaPrecision(resumen: ResumenActividad, alTocarCifra: (GrupoResumen?) -> Unit, alPedirAyuda: () -> Unit) {
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
            elegida = resumen.grupo == GrupoResumen.Revisadas,
            alTocar = { alTocarCifra(GrupoResumen.Revisadas) },
        )
        Spacer(modifier = Modifier.height(10.dp))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            MetodoDeteccion.entries.forEach { metodo ->
                val precision = resumen.precisionPorMetodo.getValue(metodo)
                val grupo = GrupoResumen.Metodo(metodo)
                FilaPrecision(
                    rotulo = stringResource(metodo.enPrecision()),
                    porcentaje = Formato.porcentaje(precision) ?: sinDato,
                    xDeY = stringResource(R.string.resumen_x_de_y, precision.parte, precision.total),
                    fraccion = precision.valor?.toFloat() ?: 0f,
                    elegida = resumen.grupo == grupo,
                    alTocar = { alTocarCifra(grupo) },
                    clara = precision.barraClara(),
                )
            }
        }
    }
}
