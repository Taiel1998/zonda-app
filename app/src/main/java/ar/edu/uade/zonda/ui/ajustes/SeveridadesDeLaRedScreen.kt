package ar.edu.uade.zonda.ui.ajustes

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ar.edu.uade.zonda.R
import ar.edu.uade.zonda.domain.model.TipoAtaque
import ar.edu.uade.zonda.ui.components.FilaDeCambioDeSeveridad
import ar.edu.uade.zonda.ui.components.HojaMotivo
import ar.edu.uade.zonda.ui.etiqueta
import ar.edu.uade.zonda.ui.primerinicio.SeveridadesScreen

/**
 * Ajustes → Severidad por tipo de ataque (RF06). El administrador las cambia y, al guardar, la hoja "¿Por
 * qué la cambiás?" pide el motivo de todos los cambios (decisión 40); el analista las ve sin poder
 * cambiarlas (decisión 39). Usa el ViewModel de Ajustes, que guarda el borrador.
 */
@Composable
fun SeveridadesDeLaRedScreen(viewModel: AjustesViewModel, alVolver: () -> Unit) {
    val estado by viewModel.estado.collectAsStateWithLifecycle()
    val borrador = estado.borradorSeveridades ?: estado.severidades
    SeveridadesScreen(
        severidades = borrador,
        alCambiar = viewModel::cambiarSeveridadDelBorrador,
        textoBoton = stringResource(R.string.ajustes_guardar_severidades),
        titulo = stringResource(R.string.ajustes_severidad_por_tipo),
        intro = stringResource(R.string.ajustes_severidades_intro),
        alGuardar = {
            if (!viewModel.pedirMotivo()) {
                viewModel.descartarBorrador()
                alVolver()
            }
        },
        alVolver = {
            viewModel.descartarBorrador()
            alVolver()
        },
        quienesLasCambian = if (estado.perfil.esAdministrador) null else enumerarConO(estado.administradores),
    )

    val motivo = estado.motivo
    if (motivo != null) {
        HojaMotivo(
            motivo = motivo,
            alCambiarMotivo = viewModel::cambiarMotivo,
            conError = estado.faltaMotivo,
            alCancelar = viewModel::cancelarMotivo,
            alGuardar = { if (viewModel.guardarSeveridades()) alVolver() },
        ) {
            TipoAtaque.EN_PANTALLA
                .filter { estado.severidades.severidadDe(it) != borrador.severidadDe(it) }
                .forEach { tipo ->
                    FilaDeCambioDeSeveridad(stringResource(tipo.etiqueta()), estado.severidades.severidadDe(tipo), borrador.severidadDe(tipo))
                }
        }
    }
}

/** "Taiel", "Taiel o Meli", "Taiel, Meli o Laura". */
fun enumerarConO(nombres: List<String>): String = when (nombres.size) {
    0 -> ""
    1 -> nombres.single()
    else -> nombres.dropLast(1).joinToString(", ") + " o " + nombres.last()
}
