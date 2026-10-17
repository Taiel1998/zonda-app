package ar.edu.uade.zonda.ui.theme

import androidx.compose.runtime.Immutable
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

/** Fondo del escáner del QR y texto claro sobre él (Figma 35-464 y 49-284). */
val FondoEscaner = Color(0xFF1E1E1C)
val TextoEscaner = Color(0xFFE4E2DA)

/** Los días que el teléfono no guarda en "Elegí las fechas" (cambio 19; Figma 463-2027). */
val GrisDiaSinDatos = Color(0xFFC9C7BF)

// El reloj de "Desde" y "Hasta" (cambio 19; Figma 463-2466): el TimePicker de Material 3 con los colores de Zonda.
val FondoReloj = Color(0xFFF4F1EA)
val RuedaReloj = Color(0xFFE8E5DD)
val HoraElegidaReloj = Color(0xFFC9EBDD)

/**
 * Los colores de un tono: el del texto, el del fondo, el del punto que lo acompaña y el de lo que va
 * escrito sobre el punto (el número del semáforo, cambio 11).
 */
@Immutable
data class Tono(val texto: Color, val fondo: Color, val punto: Color, val sobrePunto: Color = Color.White)

/**
 * Tonos de severidad y de estado. No son roles de Material 3, por eso van en un objeto propio.
 * El color nunca va solo: siempre con texto (RNF09).
 */
object Tonos {
    val Alta = Tono(texto = Color(0xFFA32D2D), fondo = Color(0xFFFCEBEB), punto = Color(0xFFE24B4A))
    // Sobre el naranja, el blanco no se lee: el número va en marrón oscuro.
    val Media = Tono(texto = Color(0xFF854F0B), fondo = Color(0xFFFAEEDA), punto = Color(0xFFEF9F27), sobrePunto = Color(0xFF3D2402))
    val Baja = Tono(texto = Color(0xFF3B6D11), fondo = Color(0xFFEAF3DE), punto = Color(0xFF639922))

    /** Azul informativo: "Sin revisar", lo pendiente y "Revisada desde otro teléfono". */
    val Info = Tono(texto = Color(0xFF185FA5), fondo = Color(0xFFE6F1FB), punto = Color(0xFF185FA5))

    /** Gris: "Falso positivo" y el semáforo sin datos. */
    val Neutro = Tono(texto = TextoSecundario, fondo = GrisCalido, punto = Color(0xFF8A8983))
}
