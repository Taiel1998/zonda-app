package ar.edu.uade.zonda.ui.primerinicio

import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ar.edu.uade.zonda.R
import ar.edu.uade.zonda.domain.model.EstadoEnvio
import ar.edu.uade.zonda.domain.model.EstadoRevision
import ar.edu.uade.zonda.domain.model.MetodoDeteccion
import ar.edu.uade.zonda.domain.model.Severidad
import ar.edu.uade.zonda.ui.Formato
import ar.edu.uade.zonda.ui.components.BannerSinConexion
import ar.edu.uade.zonda.ui.components.BotonEnlace
import ar.edu.uade.zonda.ui.components.BotonPrincipal
import ar.edu.uade.zonda.ui.components.ColumnaDePantalla
import ar.edu.uade.zonda.ui.components.EtiquetaMetodo
import ar.edu.uade.zonda.ui.components.FilaContadores
import ar.edu.uade.zonda.ui.components.FilaDato
import ar.edu.uade.zonda.ui.components.IndicadorEnvio
import ar.edu.uade.zonda.ui.components.IndicadorEstadoRevision
import ar.edu.uade.zonda.ui.components.IndicadorSeveridad
import ar.edu.uade.zonda.ui.components.LogoZonda
import ar.edu.uade.zonda.ui.components.MarcaSemaforo
import ar.edu.uade.zonda.ui.components.Panel
import ar.edu.uade.zonda.ui.components.Semaforo
import ar.edu.uade.zonda.ui.theme.Tonos
import ar.edu.uade.zonda.ui.theme.cuerpo
import ar.edu.uade.zonda.ui.theme.opcion
import ar.edu.uade.zonda.ui.theme.rotulo
import ar.edu.uade.zonda.ui.theme.tituloPaso
import ar.edu.uade.zonda.ui.theme.valor
import kotlinx.coroutines.launch

private class PaginaTutorial(
    @param:StringRes val titulo: Int,
    @param:StringRes val texto: Int,
    val ilustracion: @Composable () -> Unit,
)

/**
 * Tutorial de tres pasos (RF05; Figma 35-263, 35-300 y 35-348): "Paso N de 3" y "Omitir" arriba, el
 * título, el texto y una ilustración armada con los componentes reales (el semáforo y los contadores,
 * el aviso de Telegram y el detalle, el banner sin conexión y una tarjeta pendiente). Abajo, los tres
 * puntos y el botón; el último dice "Configurar mis avisos".
 */
@Composable
fun TutorialScreen(alOmitir: () -> Unit, alTerminar: () -> Unit) {
    val paginas = listOf(
        PaginaTutorial(R.string.tutorial_1_titulo, R.string.tutorial_1_texto) { IlustracionSemaforo() },
        PaginaTutorial(R.string.tutorial_2_titulo, R.string.tutorial_2_texto) { IlustracionAviso() },
        PaginaTutorial(R.string.tutorial_3_titulo, R.string.tutorial_3_texto) { IlustracionSinSenal() },
    )
    val estadoPaginas = rememberPagerState { paginas.size }
    val alcance = rememberCoroutineScope()
    val ultima = estadoPaginas.currentPage == paginas.lastIndex

    ColumnaDePantalla(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.tutorial_paso, estadoPaginas.currentPage + 1, paginas.size),
                style = MaterialTheme.typography.opcion,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                // "Siguiente" cambia de página: TalkBack anuncia el paso nuevo.
                modifier = Modifier
                    .weight(1f)
                    .semantics { liveRegion = LiveRegionMode.Polite },
            )
            // Con letra grande, "Omitir" crece en lugar de cortarse; con la de fábrica mide lo mismo.
            BotonEnlace(
                texto = stringResource(R.string.tutorial_omitir),
                alTocar = alOmitir,
                relleno = PaddingValues(4.dp),
                modifier = Modifier.heightIn(min = 25.dp),
            )
        }
        HorizontalPager(state = estadoPaginas, verticalAlignment = Alignment.Top) { indice ->
            val pagina = paginas[indice]
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(
                    text = stringResource(pagina.titulo),
                    style = MaterialTheme.typography.tituloPaso,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = stringResource(pagina.texto),
                    style = MaterialTheme.typography.cuerpo,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                // Figma: 20 libres entre el texto y la ilustración, además de los 16 de cada lado.
                Spacer(modifier = Modifier.height(20.dp))
                pagina.ilustracion()
            }
        }
        Spacer(modifier = Modifier.weight(1f))
        Puntos(cantidad = paginas.size, actual = estadoPaginas.currentPage)
        BotonPrincipal(
            texto = stringResource(if (ultima) R.string.tutorial_configurar else R.string.tutorial_siguiente),
            alTocar = {
                if (ultima) alTerminar() else alcance.launch { estadoPaginas.animateScrollToPage(estadoPaginas.currentPage + 1) }
            },
        )
    }
}

/** Los tres puntos de abajo: el actual es una píldora verde de 24 por 8; los otros, puntos grises de 8. */
@Composable
private fun ColumnScope.Puntos(cantidad: Int, actual: Int) {
    Row(
        modifier = Modifier
            .align(Alignment.CenterHorizontally)
            .clearAndSetSemantics { },
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        repeat(cantidad) { indice ->
            Box(
                modifier = Modifier
                    .size(width = if (indice == actual) 24.dp else 8.dp, height = 8.dp)
                    .background(
                        if (indice == actual) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                        RoundedCornerShape(4.dp),
                    ),
            )
        }
    }
}

/**
 * El fondo crema de las ilustraciones: esquinas de 20, 16 de margen y 12 entre piezas. Es un dibujo:
 * TalkBack lo saltea, porque el título y el texto de la página ya lo explican.
 */
@Composable
private fun FondoIlustracion(contenido: @Composable ColumnScope.() -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clearAndSetSemantics { },
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = contenido)
    }
}

/** Página 1 (Figma 35-263): el semáforo en rojo y los tres contadores. */
@Composable
private fun IlustracionSemaforo() {
    FondoIlustracion {
        Semaforo(
            tono = Tonos.Alta,
            etiqueta = stringResource(R.string.tutorial_semaforo_etiqueta),
            titulo = stringResource(R.string.tutorial_semaforo_titulo),
            detalle = stringResource(R.string.tutorial_semaforo_detalle),
            marca = MarcaSemaforo.Numero(3),
        )
        FilaContadores(altas = 3, medias = 1, bajas = 0)
    }
}

/** Página 2 (Figma 35-300): el aviso de Telegram, "Lo tocás" y el detalle de esa alerta con "Revisar". */
@Composable
private fun IlustracionAviso() {
    FondoIlustracion {
        TituloDeIlustracion(stringResource(R.string.tutorial_aviso_paso))
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LogoZonda(tamanio = 18.dp)
                    Text(text = stringResource(R.string.tutorial_aviso_origen), style = MaterialTheme.typography.rotulo, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(text = stringResource(R.string.tutorial_aviso_titulo), style = MaterialTheme.typography.labelLarge)
                Text(text = stringResource(R.string.tutorial_aviso_texto), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Text(
            text = stringResource(R.string.tutorial_lo_tocas),
            style = MaterialTheme.typography.valor,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        TituloDeIlustracion(stringResource(R.string.tutorial_detalle_paso))
        Panel {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                IndicadorSeveridad(Severidad.ALTA)
                IndicadorEstadoRevision(EstadoRevision.SIN_REVISAR)
            }
            Text(
                text = stringResource(R.string.tutorial_ruta),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            )
            FilaDato(etiqueta = stringResource(R.string.tutorial_detalle_conexiones), valor = "65")
            FilaDato(
                etiqueta = stringResource(R.string.tutorial_detalle_ia),
                valor = stringResource(R.string.veredicto_anomalo),
                colorValor = Tonos.Alta.texto,
            )
            BotonPrincipal(texto = stringResource(R.string.detalle_revisar), alTocar = {})
        }
    }
}

/** Página 3 (Figma 35-348): el banner sin conexión y una tarjeta revisada que espera para enviarse. */
@Composable
private fun IlustracionSinSenal() {
    FondoIlustracion {
        BannerSinConexion(datosDe = null, texto = stringResource(R.string.sin_conexion_banner, "02:14"))
        // La tarjeta de la lista (Figma 35-363): borde gris, esquinas de 12, 12 de margen más el borde y 8 entre filas.
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IndicadorSeveridad(Severidad.ALTA)
                    Spacer(modifier = Modifier.width(6.dp))
                    IndicadorEstadoRevision(EstadoRevision.ATAQUE_REAL)
                    Spacer(modifier = Modifier.weight(1f))
                    IndicadorEnvio(EstadoEnvio.PENDIENTE, hora = "02:09")
                }
                Text(text = stringResource(R.string.tutorial_ruta), style = MaterialTheme.typography.titleSmall)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = Formato.servicioYPuerto("ssh", 22),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    EtiquetaMetodo(MetodoDeteccion.IA)
                    EtiquetaMetodo(MetodoDeteccion.UMBRALES)
                }
            }
        }
    }
}

/** "1. Llega el aviso por Telegram": SemiBold 13 verde. */
@Composable
private fun TituloDeIlustracion(texto: String) {
    Text(text = texto, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
}
