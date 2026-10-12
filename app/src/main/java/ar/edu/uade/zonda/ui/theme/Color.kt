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

/** Fondo del aviso inferior (snackbar). */
val GrisAviso = Color(0xFF2C2C2A)

/** Los tres colores de un tono: el del texto, el del fondo y el del punto que lo acompaña. */
@Immutable
data class Tono(val texto: Color, val fondo: Color, val punto: Color)

/**
 * Tonos de severidad y de estado. No son roles de Material 3, por eso van en un objeto propio.
 * El color nunca va solo: siempre con texto (RNF09).
 */
object Tonos {
    val Alta = Tono(texto = Color(0xFFA32D2D), fondo = Color(0xFFFCEBEB), punto = Color(0xFFE24B4A))
    val Media = Tono(texto = Color(0xFF854F0B), fondo = Color(0xFFFAEEDA), punto = Color(0xFFEF9F27))
    val Baja = Tono(texto = Color(0xFF3B6D11), fondo = Color(0xFFEAF3DE), punto = Color(0xFF639922))

    /** Azul informativo: "Sin revisar", lo pendiente y "Revisada desde otro teléfono". */
    val Info = Tono(texto = Color(0xFF185FA5), fondo = Color(0xFFE6F1FB), punto = Color(0xFF185FA5))

    /** Gris: "Falso positivo" y el semáforo sin datos. */
    val Neutro = Tono(texto = TextoSecundario, fondo = GrisCalido, punto = Color(0xFF8A8983))
}
