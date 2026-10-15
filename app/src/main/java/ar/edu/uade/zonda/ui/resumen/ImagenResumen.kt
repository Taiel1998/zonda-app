package ar.edu.uade.zonda.ui.resumen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ar.edu.uade.zonda.R
import ar.edu.uade.zonda.domain.model.PeriodoResumen
import ar.edu.uade.zonda.domain.model.ResumenActividad
import ar.edu.uade.zonda.ui.theme.VerdeZonda
import ar.edu.uade.zonda.ui.theme.nota
import java.time.Instant
import java.time.ZoneId

/** El ancho de la imagen que se comparte (Figma 107-4862). */
val AnchoImagen = 380.dp

/** Para encontrar la imagen en las pruebas. */
const val ETIQUETA_IMAGEN = "imagen_resumen"

/**
 * La imagen que se comparte (Figma 107-4862): "Zonda" y "Resumen semanal" o "mensual", la línea de la
 * red, los totales, las dos métricas y el gráfico con su frase, y el pie "Generado con Zonda · no
 * incluye IPs ni nombres de equipos". Solo cantidades: nada de direcciones ni nombres de equipos
 * (RNF02). Fondo blanco, 16 de margen y 12 entre piezas (10 antes de las métricas).
 */
@Composable
fun ImagenResumen(
    resumen: ResumenActividad,
    red: String?,
    datosDe: Instant?,
    sinConexion: Boolean,
    zona: ZoneId,
    modifier: Modifier = Modifier,
) {
    val recursos = LocalContext.current.resources
    // En la imagen no se puede tocar nada: la frase de un grupo no dice "Tocá de nuevo".
    val frase = resumen.grupo?.let { grupo ->
        recursos.getQuantityString(R.plurals.resumen_mostrando_imagen, resumen.total, grupo.etiquetaFrase(recursos), resumen.total)
    } ?: fraseDelGrafico(resumen, recursos)
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
                    if (resumen.periodo == PeriodoResumen.SIETE_DIAS) R.string.resumen_imagen_semanal else R.string.resumen_imagen_mensual,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = lineaDelResumen(red, resumen.tramo, datosDe, sinConexion, recursos, zona),
            style = MaterialTheme.typography.nota,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(12.dp))
        FilaDeTotales(resumen = resumen, alTocarCifra = {})
        Spacer(modifier = Modifier.height(10.dp))
        FilaDeMetricas(resumen = resumen, alTocarCifra = {})
        Spacer(modifier = Modifier.height(12.dp))
        TarjetaGrafico(resumen = resumen, alTocarCifra = {}, frase = frase)
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.resumen_imagen_pie),
            style = estiloRotuloChicoResumen,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
