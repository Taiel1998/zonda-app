package ar.edu.uade.zonda.ui.navigation

import androidx.navigation.NavHostController
import ar.edu.uade.zonda.domain.model.EstadoRevision
import ar.edu.uade.zonda.domain.model.Severidad

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

    /** Conexión fuera del primer inicio: sin red pareada después de la introducción, agregar otra red o conectar de nuevo. */
    const val CONEXION = "conexion"

    // Pantallas que se abren desde Ajustes (Figma 4-66)
    const val REDES = "redes"
    const val DETALLES_TECNICOS = "detalles_tecnicos"
    const val AVISOS_POR_SEVERIDAD = "avisos_por_severidad"
    const val INTRODUCCION = "introduccion"
    const val CONDICIONES_DE_USO = "condiciones_de_uso"

    /** Severidad por ataque de la red activa, desde Ajustes o desde Avisos por severidad (RF06). */
    const val SEVERIDADES = "severidades"
}

/**
 * Enlaces internos que usa la notificación local: Alertas de una red con un filtro, o el detalle de
 * una alerta. No hay intent-filter, así que solo los abre la propia app con un intent explícito.
 * El enlace del aviso de Telegram es otro (App Link, ver RutaDetalle).
 */
object EnlacesInternos {
    const val ARG_RED = "red"
    const val ARG_ESTADO = "estado"
    const val ARG_SEVERIDAD = "severidad"
    const val ALERTAS = "zonda://alertas"
    const val PATRON_ALERTAS = "zonda://alertas?red={red}&estado={estado}&severidad={severidad}"
    const val PATRON_DETALLE = "zonda://alerta/{alertaId}?red={red}"

    fun alertas(redId: String, estado: EstadoRevision, severidad: Severidad): String =
        "$ALERTAS?$ARG_RED=$redId&$ARG_ESTADO=${estado.name.lowercase()}&$ARG_SEVERIDAD=${severidad.name.lowercase()}"

    fun detalle(redId: String, alertaId: String): String = "zonda://alerta/$alertaId?$ARG_RED=$redId"
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
