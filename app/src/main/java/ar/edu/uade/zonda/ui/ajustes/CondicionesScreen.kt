package ar.edu.uade.zonda.ui.ajustes

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import ar.edu.uade.zonda.R
import ar.edu.uade.zonda.ui.components.BarraSuperior
import ar.edu.uade.zonda.ui.components.TarjetaSeccion

/**
 * Condiciones de uso desde Ajustes (Figma 66-3558): qué guarda Zonda, qué no se envía, los datos
 * personales y los derechos de quien usa la app, una tarjeta cada uno.
 */
@Composable
fun CondicionesScreen(alVolver: () -> Unit) {
    Scaffold(
        topBar = { BarraSuperior(titulo = stringResource(R.string.condiciones_ajustes_titulo), alVolver = alVolver) },
    ) { relleno ->
        Column(
            modifier = Modifier
                .padding(relleno)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(MargenAjustes),
            verticalArrangement = EntreTarjetas,
        ) {
            BloqueDeCondiciones(R.string.condiciones_guarda_titulo, R.string.condiciones_ajustes_guarda)
            BloqueDeCondiciones(R.string.condiciones_no_se_envia_titulo, R.string.condiciones_no_se_envia)
            BloqueDeCondiciones(R.string.condiciones_datos_titulo, R.string.condiciones_ajustes_datos)
            BloqueDeCondiciones(R.string.condiciones_derechos_titulo, R.string.condiciones_derechos)
        }
    }
}

@Composable
private fun BloqueDeCondiciones(@StringRes titulo: Int, @StringRes texto: Int) {
    TarjetaSeccion(titulo = stringResource(titulo)) {
        Text(text = stringResource(texto), style = MaterialTheme.typography.bodyMedium)
    }
}
