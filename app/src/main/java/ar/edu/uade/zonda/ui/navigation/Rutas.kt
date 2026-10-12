package ar.edu.uade.zonda.ui.navigation

import androidx.navigation.NavHostController

/** Las rutas de navegación de la app. Kotlin puro: las usan también los ViewModels y sus pruebas. */
object Rutas {
    // Barra inferior
    const val ESTADO = "estado"
    const val ALERTAS = "alertas"
    const val AJUSTES = "ajustes"

    // Primer inicio (RF05): un grafo anidado con su propio ViewModel compartido
    const val PRIMER_INICIO = "primer_inicio"
    const val BIENVENIDA = "bienvenida"
    const val CONDICIONES = "condiciones"
    const val TUTORIAL = "tutorial"
    const val AVISOS = "avisos"
    const val SEVERIDADES_INICIALES = "severidades_iniciales"
    const val CONEXION_INICIAL = "conexion_inicial"

    /** Conexión fuera del primer inicio: sin red pareada después de la introducción. */
    const val CONEXION = "conexion"
}

/** Después del pareo: Estado queda como única pantalla de la pila (Volver sale de la app). */
fun NavHostController.irAPrincipal() {
    navigate(Rutas.ESTADO) {
        popUpTo(graph.id) { inclusive = true }
    }
}

/** Cambia de pestaña sin apilar copias y conservando el estado de cada una. */
fun NavHostController.irAPestania(ruta: String) {
    navigate(ruta) {
        popUpTo(Rutas.ESTADO) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
