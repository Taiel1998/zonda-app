package ar.edu.uade.zonda.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ar.edu.uade.zonda.R

/**
 * El logo de Zonda (docs/marca/logo_zonda.png), para la bienvenida, la conexión y la barra de Estado.
 * Va al lado del nombre de la app o de un título, así que es decorativo para TalkBack.
 */
@Composable
fun LogoZonda(modifier: Modifier = Modifier, tamanio: Dp = 88.dp) {
    Image(
        painter = painterResource(R.drawable.logo_zonda),
        contentDescription = null,
        modifier = modifier.size(tamanio),
    )
}
