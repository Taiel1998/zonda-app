package ar.edu.uade.zonda.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ar.edu.uade.zonda.R
import ar.edu.uade.zonda.ui.theme.VerdeZonda

/** El ícono de Zonda sobre su fondo verde, para la bienvenida y la conexión. */
@Composable
fun LogoZonda(modifier: Modifier = Modifier, tamanio: Dp = 88.dp) {
    Box(
        modifier = modifier
            .size(tamanio)
            .clip(RoundedCornerShape(tamanio / 4))
            .background(VerdeZonda),
    ) {
        Image(painter = painterResource(R.drawable.ic_launcher_foreground), contentDescription = null, modifier = Modifier.fillMaxSize())
    }
}
