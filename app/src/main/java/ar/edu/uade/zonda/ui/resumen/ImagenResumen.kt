package ar.edu.uade.zonda.ui.resumen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ar.edu.uade.zonda.R
import ar.edu.uade.zonda.domain.model.PeriodoResumen
import ar.edu.uade.zonda.domain.model.ResumenActividad
import ar.edu.uade.zonda.domain.model.SeveridadPorTipo
import ar.edu.uade.zonda.domain.model.TipoAtaque
import ar.edu.uade.zonda.ui.theme.CremaBarra
import ar.edu.uade.zonda.ui.theme.VerdeZonda
import ar.edu.uade.zonda.ui.theme.nota
import java.time.Instant
import java.time.ZoneId

/** El ancho de la imagen que se comparte (Figma 107-4862). */
val AnchoImagen = 380.dp

/** Para encontrar la imagen en las pruebas. */
const val ETIQUETA_IMAGEN = "imagen_resumen"

/**
 * La imagen que se comparte (cambio 18; Figma 107-4862 y 463-3007): "Zonda" y "Resumen semanal" o
 * "mensual", la línea de la red con las fechas, los filtros si hay ("Filtros: viernes 25/09 · fuerza
 * bruta"), los totales, las dos métricas, el gráfico con su frase, "Por tipo de ataque" en los dos
 * bloques y "Reales y falsos positivos", y el pie "Generado con Zonda · no incluye IPs ni nombres de
 * equipos". Sin la precisión de cada modelo ni los ⓘ. Solo cantidades: nada de direcciones ni nombres
 * de equipos (RNF02). Fondo blanco, 16 de margen y 12 entre piezas. El PDF es esta misma imagen.
 */
@Composable
fun ImagenResumen(
    resumen: ResumenActividad,
    red: String?,
    datosDe: Instant?,
    sinConexion: Boolean,
    zona: ZoneId,
    modifier: Modifier = Modifier,
    altas: Set<TipoAtaque> = emptySet(),
    severidades: SeveridadPorTipo = SeveridadPorTipo.RECOMENDADA,
) {
    val recursos = LocalResources.current
    // En la imagen no se puede tocar nada: la frase de un filtro no dice "Tocá de nuevo".
    val frase = fraseDelGrafico(resumen, recursos, altas, enImagen = true)
    Column(
        modifier = modifier
            .width(AnchoImagen)
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp)
            .testTag(ETIQUETA_IMAGEN),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.titleLarge.copy(fontSize = 18.sp, fontWeight = FontWeight.SemiBold),
                color = VerdeZonda,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = stringResource(
                    when (resumen.periodo) {
                        PeriodoResumen.SIETE_DIAS -> R.string.resumen_imagen_semanal
                        PeriodoResumen.TREINTA_DIAS -> R.string.resumen_imagen_mensual
                        null -> R.string.resumen_imagen_fechas
                    },
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = lineaDelResumen(red, resumen, datosDe, sinConexion, recursos, zona),
            style = MaterialTheme.typography.nota,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (!resumen.filtro.vacio) {
            Spacer(modifier = Modifier.height(12.dp))
            // Figma 463-3007: el recuadro crema con los filtros, Regular 12.
            Text(
                text = stringResource(R.string.resumen_imagen_filtros, resumen.filtro.grupos(recursos, altas, conFecha = true).joinToString(" · ")),
                style = MaterialTheme.typography.nota,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .background(CremaBarra, RoundedCornerShape(6.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        FilaDeTotales(resumen = resumen, alTocar = {})
        Spacer(modifier = Modifier.height(12.dp))
        FilaDeMetricas(resumen = resumen, altas = altas, alTocar = {})
        Spacer(modifier = Modifier.height(12.dp))
        TarjetaGrafico(resumen = resumen, alTocar = {}, frase = frase)
        Spacer(modifier = Modifier.height(12.dp))
        TarjetaTipos(resumen = resumen, severidades = severidades, alTocar = {}, alPedirAyuda = null)
        Spacer(modifier = Modifier.height(12.dp))
        TarjetaRealesYFalsos(resumen = resumen, alTocar = {}, conNota = false)
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.resumen_imagen_pie),
            style = estiloRotuloChicoResumen,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
