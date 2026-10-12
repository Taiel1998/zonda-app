package ar.edu.uade.zonda.ui.primerinicio

import androidx.annotation.StringRes
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ar.edu.uade.zonda.R
import ar.edu.uade.zonda.ui.theme.VerdeZondaClaro
import kotlinx.coroutines.launch

private class PaginaTutorial(val icono: ImageVector, @param:StringRes val titulo: Int, @param:StringRes val texto: Int)

/** Tutorial de tres pasos con el orden real de un aviso: Telegram, detalle y revisión (informe, 11.5). Se puede omitir. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TutorialScreen(alTerminar: () -> Unit) {
    val paginas = listOf(
        PaginaTutorial(Icons.Filled.Notifications, R.string.tutorial_1_titulo, R.string.tutorial_1_texto),
        PaginaTutorial(Icons.Filled.Info, R.string.tutorial_2_titulo, R.string.tutorial_2_texto),
        PaginaTutorial(Icons.Filled.CheckCircle, R.string.tutorial_3_titulo, R.string.tutorial_3_texto),
    )
    val estadoPaginas = rememberPagerState { paginas.size }
    val alcance = rememberCoroutineScope()
    val ultima = estadoPaginas.currentPage == paginas.lastIndex

    Scaffold { relleno ->
        Column(
            modifier = Modifier
                .padding(relleno)
                .fillMaxSize()
                .padding(24.dp),
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = alTerminar) { Text(stringResource(R.string.tutorial_omitir)) }
            }
            HorizontalPager(state = estadoPaginas, modifier = Modifier.weight(1f)) { indice ->
                val pagina = paginas[indice]
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        modifier = Modifier
                            .size(96.dp)
                            .background(VerdeZondaClaro, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(imageVector = pagina.icono, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(48.dp))
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                    Text(text = stringResource(pagina.titulo), style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = stringResource(pagina.texto),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }
            Text(
                text = stringResource(R.string.tutorial_paso, estadoPaginas.currentPage + 1, paginas.size),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = {
                    if (ultima) alTerminar() else alcance.launch { estadoPaginas.animateScrollToPage(estadoPaginas.currentPage + 1) }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(if (ultima) R.string.tutorial_listo else R.string.tutorial_siguiente))
            }
        }
    }
}
