package ar.edu.uade.zonda.ui.resumen

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ar.edu.uade.zonda.R
import ar.edu.uade.zonda.domain.model.ResumenActividad
import ar.edu.uade.zonda.domain.model.TipoAtaque
import ar.edu.uade.zonda.ui.components.BloqueDeAyuda
import ar.edu.uade.zonda.ui.components.EntendidoDeHoja
import ar.edu.uade.zonda.ui.components.HojaInferior
import ar.edu.uade.zonda.ui.theme.Tonos
import ar.edu.uade.zonda.ui.theme.tituloHoja

/** Qué hoja de ayuda del Resumen está abierta: "Cómo se lee la precisión" o "Qué es cada tipo". */
enum class AyudaResumen { PRECISION, TIPOS }

/** "Cómo se lee la precisión" (cambio 15; Figma 443-2314 y 443-2505), con las cifras y el filtro de [resumen]. */
@Composable
fun HojaPrecision(resumen: ResumenActividad, alCerrar: () -> Unit, altas: Set<TipoAtaque> = emptySet()) {
    HojaInferior(alCerrar = alCerrar) {
        ContenidoHojaPrecision(resumen, alCerrar, altas)
    }
}

/**
 * El contenido de [HojaPrecision], aparte para dibujarlo en las pruebas sin la ventana de la hoja: qué
 * es la precisión con un ejemplo de las cifras de la tarjeta, la cobertura, los tres modelos, por qué
 * 30 revisiones, el recuadro azul que cambia con los filtros elegidos (cambios 10 y 16) y de dónde sale.
 */
@Composable
fun ColumnScope.ContenidoHojaPrecision(resumen: ResumenActividad, alCerrar: () -> Unit, altas: Set<TipoAtaque> = emptySet()) {
    val recursos = LocalResources.current
    Text(text = stringResource(R.string.precision_ayuda), style = MaterialTheme.typography.tituloHoja)
    val definicion = stringResource(R.string.precision_texto_precision)
    val ejemplo = ejemploDePrecision(resumen, recursos)
    BloqueDeAyuda(stringResource(R.string.precision_titulo_precision), if (ejemplo == null) definicion else "$definicion $ejemplo")
    BloqueDeAyuda(stringResource(R.string.precision_titulo_cobertura), stringResource(R.string.precision_texto_cobertura))
    BloqueDeAyuda(stringResource(R.string.precision_titulo_modelos), stringResource(R.string.precision_texto_modelos))
    BloqueDeAyuda(stringResource(R.string.precision_titulo_30), stringResource(R.string.precision_texto_30))
    RecuadroDelFiltro(recuadroDePrecision(resumen, recursos, altas))
    Text(text = stringResource(R.string.precision_pie), style = estiloNotaResumen, color = MaterialTheme.colorScheme.onSurfaceVariant)
    EntendidoDeHoja(alCerrar)
}

/** El recuadro azul de información (Figma 443-2314): Regular 13, 12 por 10 de margen y esquinas de 8. */
@Composable
private fun RecuadroDelFiltro(texto: String) {
    Surface(color = Tonos.Info.fondo, shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
        Text(
            text = texto,
            style = MaterialTheme.typography.bodySmall,
            color = Tonos.Info.texto,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
        )
    }
}
