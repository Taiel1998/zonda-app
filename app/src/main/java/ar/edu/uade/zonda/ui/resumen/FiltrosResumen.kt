package ar.edu.uade.zonda.ui.resumen

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import ar.edu.uade.zonda.R
import ar.edu.uade.zonda.domain.model.FiltroResumen
import ar.edu.uade.zonda.domain.model.Franja
import ar.edu.uade.zonda.domain.model.MetodoDeteccion
import ar.edu.uade.zonda.domain.model.RevisionResumen
import ar.edu.uade.zonda.domain.model.SeveridadPorTipo
import ar.edu.uade.zonda.domain.model.TipoAtaque
import ar.edu.uade.zonda.domain.model.ToqueResumen
import ar.edu.uade.zonda.ui.components.BotonEnlace
import ar.edu.uade.zonda.ui.components.BotonesDeHoja
import ar.edu.uade.zonda.ui.components.ChipOpcion
import ar.edu.uade.zonda.ui.components.GrupoDeChips
import ar.edu.uade.zonda.ui.components.HojaInferior
import ar.edu.uade.zonda.ui.components.MarcaCasilla
import ar.edu.uade.zonda.ui.components.PuntoDeSeveridad
import ar.edu.uade.zonda.ui.components.TituloDeBloque
import ar.edu.uade.zonda.ui.etiqueta
import ar.edu.uade.zonda.ui.nombreEnFiltro
import ar.edu.uade.zonda.ui.theme.opcion
import ar.edu.uade.zonda.ui.theme.tituloHoja

private val FormaChip = RoundedCornerShape(8.dp)

/**
 * Un chip con ícono de la fila del período (Figma 52-2629): "Fechas" y "Filtros". Como los chips de
 * siempre, Medium 13 con 12 por 6 y esquinas de 8; elegido, verde claro con el texto y el ícono verdes y
 * sin borde (28 de alto), y si no, con el borde gris de 1 por dentro (30 de alto). El ícono es de 14 y
 * queda a 6 del texto; [iconoFinal] es el relojito de "Fechas" con horas.
 */
@Composable
fun ChipConIcono(
    texto: String,
    @DrawableRes icono: Int,
    elegido: Boolean,
    alTocar: () -> Unit,
    modifier: Modifier = Modifier,
    @DrawableRes iconoFinal: Int? = null,
) {
    val colores = MaterialTheme.colorScheme
    val color = if (elegido) colores.primary else colores.onSurface
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .clip(FormaChip)
            .then(if (elegido) Modifier.background(colores.secondaryContainer, FormaChip) else Modifier.border(1.dp, colores.outlineVariant, FormaChip))
            .selectable(selected = elegido, onClick = alTocar, role = Role.Button)
            .padding(horizontal = if (elegido) 12.dp else 13.dp, vertical = if (elegido) 6.dp else 7.dp),
    ) {
        Icon(painter = painterResource(icono), contentDescription = null, tint = color, modifier = Modifier.size(14.dp))
        Text(text = texto, style = MaterialTheme.typography.opcion, color = color, maxLines = 1)
        if (iconoFinal != null) {
            Icon(painter = painterResource(iconoFinal), contentDescription = null, tint = color, modifier = Modifier.size(14.dp))
        }
    }
}

/** "Filtros" o "Filtros (4)": cuántos hay puestos, verde si hay alguno (cambio 16; Figma 463-1145). */
@Composable
fun BotonFiltros(cantidad: Int, alTocar: () -> Unit, modifier: Modifier = Modifier) {
    ChipConIcono(
        texto = if (cantidad == 0) stringResource(R.string.resumen_filtros) else stringResource(R.string.resumen_filtros_con_cantidad, cantidad),
        icono = R.drawable.ic_filtros,
        elegido = cantidad > 0,
        alTocar = alTocar,
        modifier = modifier,
    )
}

/**
 * La hoja "Filtrar el resumen" (cambio 16; Figma 463-1317). Muestra tildado lo que se tocó en el
 * Resumen y se edita en un borrador: "Mostrar N alertas" lo aplica, "Ver estas N alertas en la lista"
 * lo aplica y abre la lista, y cerrarla tocando afuera o deslizándola lo descarta, como las hojas de
 * Alertas. Antes de aplicar dice cuántas alertas quedan ([cuantas]).
 */
@Composable
fun HojaFiltrosResumen(
    filtro: FiltroResumen,
    barras: List<Franja>,
    severidades: SeveridadPorTipo,
    cuantas: (FiltroResumen) -> Int,
    alMostrar: (FiltroResumen) -> Unit,
    alVerEnLista: (FiltroResumen) -> Unit,
    alCerrar: () -> Unit,
) {
    HojaInferior(alCerrar = alCerrar) {
        ContenidoFiltrosResumen(filtro, barras, severidades, cuantas, alMostrar, alVerEnLista)
    }
}

/** El contenido de la hoja, aparte para dibujarlo en las pruebas sin la ventana de la hoja. */
@Composable
fun ColumnScope.ContenidoFiltrosResumen(
    filtro: FiltroResumen,
    barras: List<Franja>,
    severidades: SeveridadPorTipo,
    cuantas: (FiltroResumen) -> Int,
    alMostrar: (FiltroResumen) -> Unit,
    alVerEnLista: (FiltroResumen) -> Unit,
) {
    val recursos = LocalContext.current.resources
    var borrador by remember(filtro) { mutableStateOf(filtro) }
    val quedan = cuantas(borrador)

    Text(text = stringResource(R.string.resumen_filtrar_titulo), style = MaterialTheme.typography.tituloHoja)
    GrupoDeChips(titulo = stringResource(R.string.resumen_filtrar_cuando)) {
        barras.forEach { franja ->
            ChipOpcion(
                texto = franja.enHoja(recursos),
                elegido = franja in borrador.barras,
                alTocar = { borrador = borrador.tocar(ToqueResumen.Barra(franja), emptySet()) },
            )
        }
    }
    Column {
        Text(
            text = stringResource(R.string.resumen_filtrar_tipos),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        listOf(
            R.string.tipos_bloque_ia to TipoAtaque.DETECTADOS_POR_LA_IA,
            R.string.tipos_bloque_sin_ia to TipoAtaque.NO_DETECTADOS_POR_LA_IA,
        ).forEach { (titulo, tipos) ->
            TituloDeBloque(stringResource(titulo))
            tipos.forEach { tipo ->
                FilaCasillaTipo(
                    tipo = tipo,
                    severidades = severidades,
                    marcada = tipo in borrador.tipos,
                    alCambiar = { borrador = borrador.tocar(ToqueResumen.Tipo(tipo), emptySet()) },
                )
            }
        }
    }
    GrupoDeChips(titulo = stringResource(R.string.resumen_filtrar_revision)) {
        ChipOpcion(
            texto = stringResource(R.string.resumen_filtrar_todas),
            elegido = borrador.revision == null,
            alTocar = { borrador = borrador.copy(revision = null) },
            rol = Role.RadioButton,
        )
        RevisionResumen.entries.forEach { revision ->
            ChipOpcion(
                texto = stringResource(revision.nombre()),
                elegido = borrador.revision == revision,
                alTocar = { borrador = borrador.copy(revision = revision) },
                rol = Role.RadioButton,
            )
        }
    }
    GrupoDeChips(titulo = stringResource(R.string.resumen_filtrar_modelo)) {
        ChipOpcion(
            texto = stringResource(R.string.resumen_filtrar_todos),
            elegido = borrador.metodo == null,
            alTocar = { borrador = borrador.copy(metodo = null) },
            rol = Role.RadioButton,
        )
        MetodoDeteccion.entries.forEach { metodo ->
            ChipOpcion(
                texto = stringResource(metodo.nombreEnFiltro()),
                elegido = borrador.metodo == metodo,
                alTocar = { borrador = borrador.copy(metodo = metodo) },
                rol = Role.RadioButton,
            )
        }
    }
    BotonEnlace(
        texto = pluralStringResource(R.plurals.resumen_ver_en_la_lista, quedan, quedan),
        alTocar = { alVerEnLista(borrador) },
        modifier = Modifier.fillMaxWidth(),
    )
    BotonesDeHoja(
        alAplicar = { alMostrar(borrador) },
        alLimpiar = { borrador = FiltroResumen() },
        textoAplicar = pluralStringResource(R.plurals.resumen_mostrar_alertas, quedan, quedan),
    )
}

/**
 * Un tipo de ataque en la hoja (Figma 463-1317): la casilla, el punto de la severidad que eligió la
 * red y el nombre Medium 15, con 6 arriba y abajo.
 */
@Composable
private fun FilaCasillaTipo(tipo: TipoAtaque, severidades: SeveridadPorTipo, marcada: Boolean, alCambiar: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = marcada, onValueChange = alCambiar, role = Role.Checkbox)
            .padding(vertical = 6.dp),
    ) {
        MarcaCasilla(marcada)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            PuntoDeSeveridad(severidades.severidadDe(tipo))
            Text(text = stringResource(tipo.etiqueta()), style = MaterialTheme.typography.titleSmall)
        }
    }
}
