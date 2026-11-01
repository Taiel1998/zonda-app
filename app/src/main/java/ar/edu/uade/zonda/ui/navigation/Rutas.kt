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
    const val TUTORIAL = "tutorial"
    const val AVISOS = "avisos"
    const val CONEXION_INICIAL = "conexion_inicial"

    /** Conectar Telegram después de la red conectada, con "Ahora no" (Figma 50-942 y 50-1003). */
    const val TELEGRAM_INICIAL = "telegram_inicial"

    /** Conexión fuera del primer inicio: sin red pareada después de la introducción, agregar otra red o conectar de nuevo. */
    const val CONEXION = "conexion"

    // Pantallas que se abren desde Ajustes (Figma 4-66)
    const val REDES = "redes"
    const val DETALLES_TECNICOS = "detalles_tecnicos"
    const val AVISOS_POR_SEVERIDAD = "avisos_por_severidad"
    const val INTRODUCCION = "introduccion"
    const val CONDICIONES_DE_USO = "condiciones_de_uso"

    /** Telegram de la red activa: conectarlo (Figma 66-4205) o, si ya está, probarlo y desvincularlo (66-3484). */
    const val TELEGRAM = "telegram"

    /** Severidad por tipo de ataque de la red activa, desde Ajustes o desde Avisos por severidad (RF06). */
    const val SEVERIDADES = "severidades"

    /** La gente de la red (decisión 39; Figma 468-2568) y el código para sumar a alguien (468-2735). */
    const val PERSONAS = "personas"
    const val CODIGO_PERSONA = "personas/codigo"

    /** Las computadoras con el tablero web, aparte de las personas, como en el tablero. */
    const val NAVEGADORES = "navegadores"

    /** La importancia de cada equipo (decisión 41; Figma 468-3089). */
    const val EQUIPOS = "equipos"

    /** El registro de cambios de la configuración (decisión 40; Figma 468-3283). */
    const val REGISTRO = "registro"

    /** El Resumen de 7 o 30 días (RF07), desde el ícono de barras de Estado. */
    const val RESUMEN = "resumen"

    /** "Ver estas alertas" del Resumen (cambio 16): la lista encima del Resumen, con sus filtros. */
    const val ALERTAS_DEL_RESUMEN = "resumen/alertas"
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

    /** `envio=conflicto`: solo las revisiones de este teléfono que no se aplicaron (cambio 7). */
    const val ARG_ENVIO = "envio"
    const val ENVIO_CONFLICTO = "conflicto"
    const val ALERTAS = "zonda://alertas"
    const val PATRON_ALERTAS = "zonda://alertas?red={red}&estado={estado}&severidad={severidad}&envio={envio}"
    const val PATRON_DETALLE = "zonda://alerta/{alertaId}?red={red}"

    fun alertas(redId: String, estado: EstadoRevision, severidad: Severidad): String =
        "$ALERTAS?$ARG_RED=$redId&$ARG_ESTADO=${estado.name.lowercase()}&$ARG_SEVERIDAD=${severidad.name.lowercase()}"

    /** Alertas de esa red con las revisiones de este teléfono que no se aplicaron. */
    fun noAplicadas(redId: String): String = "$ALERTAS?$ARG_RED=$redId&$ARG_ENVIO=$ENVIO_CONFLICTO"

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

/**
 * La flecha de volver del detalle, que también se abre desde el aviso de Telegram (cambios 20 y 21).
 * Con pantallas atrás vuelve a la anterior. Telegram abre el enlace sin pedir una tarea nueva, así que
 * Zonda queda dentro de la tarea de Telegram con el detalle como única pantalla: ahí abre Zonda en su
 * propia tarea, en la pantalla principal, y cierra esta copia. Eso es `navigateUp()`. Antes se usaba
 * `popBackStack()`, que sacaba esa única pantalla de la pila: la flecha no hacía nada a la vista y el
 * detalle quedaba congelado, sin mostrar los cambios (por ejemplo, el conflicto ya resuelto).
 *
 * Desde la 1.0.3 el enlace entra por la pantalla puente (PuenteDelAvisoActivity, cambio 23), que
 * siempre abre Zonda en su propia tarea con Estado debajo del detalle, así que ese caso ya no se da.
 * La flecha sigue con `navigateUp()`: con pantallas atrás hace lo mismo que `popBackStack()`.
 */
fun NavHostController.volver() {
    navigateUp()
}
