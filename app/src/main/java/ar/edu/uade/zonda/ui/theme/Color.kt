package ar.edu.uade.zonda.ui.theme

import androidx.compose.ui.graphics.Color

// Color base de Zonda: un verde azulado, que transmite calma (informe, sección 11.3).
val VerdeZonda = Color(0xFF0E6B5B)
val VerdeZondaOscuro = Color(0xFF0A4F43)
val VerdeZondaClaro = Color(0xFFD7F0E8)
val VerdeZondaMuyClaro = Color(0xFFF2FAF7)

// Colores de severidad. Nunca van solos: siempre con ícono y texto (RNF09).
val SeveridadAlta = Color(0xFFB3261E)
val SeveridadAltaFondo = Color(0xFFF9DEDC)
val SeveridadMedia = Color(0xFF9A5B00)
val SeveridadMediaFondo = Color(0xFFFFE8C2)
val SeveridadBaja = Color(0xFF4A5C66)
val SeveridadBajaFondo = Color(0xFFE2E8EB)

// Estado de revisión en la tarjeta: sin revisar (azul), revisada (verde) y falso positivo (gris).
val EstadoSinRevisar = Color(0xFF1B4F8F)
val EstadoSinRevisarFondo = Color(0xFFDCE9F7)
val EstadoRevisada = Color(0xFF2E6B2F)
val EstadoRevisadaFondo = Color(0xFFDFF0D8)
val EstadoFalsoPositivo = SeveridadBaja
val EstadoFalsoPositivoFondo = SeveridadBajaFondo

// Estado de envío de una acción: pendiente (azul), con error (rojo) y en conflicto (naranja).
val EnvioPendiente = Color(0xFF1B5FBF)
val EnvioError = SeveridadAlta
val EnvioConflicto = SeveridadMedia

// Veredicto de cada método en el detalle (RF03).
val VeredictoAnomalo = SeveridadAlta
val VeredictoAnomaloFondo = SeveridadAltaFondo
val VeredictoNormal = EstadoRevisada
val VeredictoNormalFondo = EstadoRevisadaFondo

// Semáforo de la pantalla Estado (RF01).
val SemaforoRojo = Color(0xFFC62828)
val SemaforoAmarillo = Color(0xFFF9A825)
val SemaforoVerde = Color(0xFF2E7D32)
