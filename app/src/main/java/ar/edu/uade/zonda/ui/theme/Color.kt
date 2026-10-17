package ar.edu.uade.zonda.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// Colores del Figma de la etapa 1, confirmados con el cuentagotas en varios cuadros. El verde base
// transmite calma; el ámbar y el rojo quedan para lo que requiere atención, siempre junto a un ícono
// y una palabra (informe, sección 11.3).

/** Verde base: botón principal, enlaces e íconos activos. */
val VerdeZonda = Color(0xFF0F6E56)

/** Verde claro: chip elegido, botón tonal y píldora de la barra inferior. */
val VerdeZondaClaro = Color(0xFFE1F5EE)

/** Acción del aviso inferior (Deshacer, OK), sobre el fondo oscuro. */
val VerdeAccionAviso = Color(0xFF9FE1CB)

val Blanco = Color(0xFFFFFFFF)

/** Fondo crema de la barra inferior y de los recuadros de resumen. */
val CremaBarra = Color(0xFFF7F5EF)

/** Gris cálido: etiquetas de método, esqueleto de carga y recuadros neutros. */
val GrisCalido = Color(0xFFEDEBE4)

/** Bordes de tarjetas, chips y campos. */
val Borde = Color(0xFFD3D1C7)

val Texto = Color(0xFF1C1B1F)

/** Texto secundario y de ayuda: 6,5:1 sobre blanco, pasa AA (RNF09). */
val TextoSecundario = Color(0xFF5F5E5A)

/** Rojo de peligro: errores, el botón "Quitar dispositivo" y el texto de "Borrar datos locales". */
val RojoPeligro = Color(0xFFA32D2D)
val RojoPeligroFondo = Color(0xFFFCEBEB)

/** Fondo del aviso inferior (snackbar) y del globito de ayuda. */
val GrisAviso = Color(0xFF2C2C2A)

/** La letra del globito de ayuda del detalle, sobre [GrisAviso] (Figma 468-723). */
val TextoGlobito = Color(0xFFF4F1EA)

/** Gris oscuro de las notas que no se pueden cambiar y de los motivos citados del registro (Figma 468-3441 y 468-3330). */
val TextoNota = Color(0xFF444441)

/** Fondo del escáner del QR y texto claro sobre él (Figma 35-464 y 49-284). */
val FondoEscaner = Color(0xFF1E1E1C)
val TextoEscaner = Color(0xFFE4E2DA)

/** Los días que el teléfono no guarda en "Elegí las fechas" (cambio 19; Figma 463-2027). */
val GrisDiaSinDatos = Color(0xFFC9C7BF)

// El reloj de "Desde" y "Hasta" (cambio 19; Figma 463-2466): el TimePicker de Material 3 con los colores de Zonda.
val FondoReloj = Color(0xFFF4F1EA)
val RuedaReloj = Color(0xFFE8E5DD)
val HoraElegidaReloj = Color(0xFFC9EBDD)

// Los colores del modo oscuro (corrección 8, decisión 43), cálidos como los del claro: fondo, recuadros,
// bordes y texto con más de 7 a 1 de contraste sobre el fondo.

/** Fondo de las pantallas y de las tarjetas en oscuro. */
val FondoOscuro = Color(0xFF1B1B19)

/** Barra inferior y recuadros (el crema del claro). */
val RecuadroOscuro = Color(0xFF262522)

/** Bordes de tarjetas, chips y campos. */
val BordeOscuro = Color(0xFF3D3C37)

/** Recuadros neutros (el gris cálido del claro): etiquetas de método, el círculo sin asignar. */
val NeutroOscuro = Color(0xFF33322E)

val TextoOscuro = Color(0xFFE8E6DF)
val TextoSecundarioOscuro = Color(0xFFA9A79F)

/** El verde de los botones y su letra. */
val VerdeOscuro = Color(0xFF5DCAA5)
val SobreVerdeOscuro = Color(0xFF04342C)

/** El verde claro de lo elegido (chips, la píldora de la barra, el botón tonal) y su letra. */
val VerdeElegidoOscuro = Color(0xFF085041)
val SobreVerdeElegidoOscuro = Color(0xFF9FE1CB)

/**
 * Los colores de un tono: el del texto, el del fondo, el del punto que lo acompaña y el de lo que va
 * escrito sobre el punto (el número del semáforo, cambio 11).
 */
@Immutable
data class Tono(val texto: Color, val fondo: Color, val punto: Color, val sobrePunto: Color = Color.White)

/** Los tonos de severidad y de estado de un tema. Los puntos son los mismos en claro y en oscuro. */
@Immutable
data class TonosZonda(
    val alta: Tono,
    val media: Tono,
    val baja: Tono,
    val info: Tono,
    val neutro: Tono,
    val critico: Tono,
)

private val PuntoAlta = Color(0xFFE24B4A)
private val PuntoMedia = Color(0xFFEF9F27)
private val PuntoBaja = Color(0xFF639922)
private val SobrePuntoMedia = Color(0xFF3D2402)

val TonosClaros = TonosZonda(
    alta = Tono(texto = Color(0xFFA32D2D), fondo = Color(0xFFFCEBEB), punto = PuntoAlta),
    // Sobre el naranja, el blanco no se lee: el número va en marrón oscuro.
    media = Tono(texto = Color(0xFF854F0B), fondo = Color(0xFFFAEEDA), punto = PuntoMedia, sobrePunto = SobrePuntoMedia),
    baja = Tono(texto = Color(0xFF3B6D11), fondo = Color(0xFFEAF3DE), punto = PuntoBaja),
    info = Tono(texto = Color(0xFF185FA5), fondo = Color(0xFFE6F1FB), punto = Color(0xFF185FA5)),
    neutro = Tono(texto = TextoSecundario, fondo = GrisCalido, punto = Color(0xFF8A8983)),
    critico = Tono(texto = Color(0xFF0C447C), fondo = Color(0xFFE6F1FB), punto = Color(0xFF185FA5)),
)

/** En oscuro las etiquetas llevan fondo oscuro y letra clara; los puntos y los círculos no cambian (decisión 43). */
val TonosOscuros = TonosZonda(
    alta = Tono(texto = Color(0xFFF7C1C1), fondo = Color(0xFF501313), punto = PuntoAlta),
    media = Tono(texto = Color(0xFFFAC775), fondo = Color(0xFF412402), punto = PuntoMedia, sobrePunto = SobrePuntoMedia),
    baja = Tono(texto = Color(0xFFC0DD97), fondo = Color(0xFF173404), punto = PuntoBaja),
    info = Tono(texto = Color(0xFFB5D4F4), fondo = Color(0xFF042C53), punto = Color(0xFFB5D4F4)),
    neutro = Tono(texto = TextoSecundarioOscuro, fondo = NeutroOscuro, punto = Color(0xFF8A8983)),
    critico = Tono(texto = Color(0xFFB5D4F4), fondo = Color(0xFF042C53), punto = Color(0xFFB5D4F4)),
)

val LocalTonos = staticCompositionLocalOf { TonosClaros }

/**
 * Tonos de severidad y de estado del tema actual. No son roles de Material 3, por eso van en un objeto
 * propio. El color nunca va solo: siempre con texto (RNF09).
 */
object Tonos {
    val Alta: Tono
        @Composable @ReadOnlyComposable get() = LocalTonos.current.alta

    val Media: Tono
        @Composable @ReadOnlyComposable get() = LocalTonos.current.media

    val Baja: Tono
        @Composable @ReadOnlyComposable get() = LocalTonos.current.baja

    /** Azul informativo: "Sin revisar", lo pendiente y "Revisada desde otro teléfono". */
    val Info: Tono
        @Composable @ReadOnlyComposable get() = LocalTonos.current.info

    /** Gris: "Falso positivo" y el semáforo sin datos. */
    val Neutro: Tono
        @Composable @ReadOnlyComposable get() = LocalTonos.current.neutro

    /** Azul oscuro de un equipo crítico (decisión 41; Figma 468-3134). */
    val Critico: Tono
        @Composable @ReadOnlyComposable get() = LocalTonos.current.critico
}

/**
 * Los colores propios de Zonda que no tienen rol en Material 3 y cambian con el tema: las notas, el
 * globito, el calendario y el reloj del Resumen, las barras del gráfico y la hoja de compartir.
 */
@Immutable
data class ColoresZonda(
    val textoNota: Color,
    val textoGlobito: Color,
    val textoLista: Color,
    val diaSinDatos: Color,
    val fondoReloj: Color,
    val ruedaReloj: Color,
    val horaElegidaReloj: Color,
    val barra: Color,
    val barraClara: Color,
    val fondoArchivo: Color,
    val bordeSelector: Color,
)

val ColoresClaros = ColoresZonda(
    textoNota = TextoNota,
    textoGlobito = TextoGlobito,
    textoLista = Color(0xFF40403D),
    diaSinDatos = GrisDiaSinDatos,
    fondoReloj = FondoReloj,
    ruedaReloj = RuedaReloj,
    horaElegidaReloj = HoraElegidaReloj,
    barra = Color(0xFF5AB89A),
    barraClara = Color(0xFF9FE1CB),
    fondoArchivo = Color(0xFFF4F1EA),
    bordeSelector = Color(0xFF9B998F),
)

/** En el Resumen oscuro, la barra de hoy queda más clara que las otras (#5DCAA5 contra #2F8F72), como en el claro. */
val ColoresOscuros = ColoresZonda(
    textoNota = Color(0xFFC9C7BF),
    textoGlobito = FondoOscuro,
    textoLista = Color(0xFFC9C7BF),
    diaSinDatos = Color(0xFF5F5E5A),
    fondoReloj = RecuadroOscuro,
    ruedaReloj = NeutroOscuro,
    horaElegidaReloj = VerdeElegidoOscuro,
    barra = Color(0xFF2F8F72),
    barraClara = Color(0xFF1F6B57),
    fondoArchivo = RecuadroOscuro,
    bordeSelector = Color(0xFF6B6A63),
)

val LocalColoresZonda = staticCompositionLocalOf { ColoresClaros }
