package ar.edu.uade.zonda.domain.model

import java.time.Instant

/**
 * Los dos perfiles de la red (corrección 2, decisión 39). El analista revisa alertas y elige sus
 * avisos; el administrador, además, cambia las severidades y la importancia de los equipos, asigna
 * alertas a cualquiera y suma o quita gente. Lo controla el servidor; la app esconde los botones.
 */
enum class Perfil {
    ADMINISTRADOR,
    ANALISTA;

    val esAdministrador: Boolean
        get() = this == ADMINISTRADOR
}

/**
 * Una persona del equipo: su teléfono pareado, con su nombre y su perfil (Ajustes → Personas). Sin
 * cuentas ni correos. [nombre] es null en los teléfonos pareados antes de la 1.0.5: se muestra el del
 * teléfono.
 */
data class Persona(
    val id: String,
    val nombre: String?,
    val telefono: String,
    val perfil: Perfil,
    val ultimoUso: Instant?,
    /** Si es este teléfono. */
    val vos: Boolean,
    /**
     * Es un navegador con el tablero web, no un teléfono (decisión 51). No se le asignan alertas: no
     * recibe el aviso de la asignación. No es una persona: va aparte, en Navegadores, donde se le quita
     * el acceso, como en el tablero.
     */
    val navegador: Boolean = false,
    /** El permiso de bloquear de un analista, que le da un administrador al de guardia (decisión 51). */
    val permisoDeBloquear: Boolean = false,
) {
    /**
     * Si bloquea en el acto (decisión 51): un administrador siempre; un analista, con permiso. Si no, pide el
     * bloqueo y lo aprueba alguien que bloquea.
     */
    val puedeBloquear: Boolean
        get() = perfil.esAdministrador || permisoDeBloquear

    val nombreVisible: String
        get() = nombre ?: telefono

    /** La inicial del círculo (Figma 468-2568): "T" de Taiel. */
    val inicial: String
        get() = nombreVisible.trim().firstOrNull()?.uppercase() ?: "?"

    companion object {
        /**
         * El orden de Personas: vos primero, después los administradores y después los analistas, cada
         * grupo en el orden en que se sumaron (Figma 468-2568).
         */
        fun ordenar(personas: List<Persona>): List<Persona> =
            personas.withIndex()
                .sortedWith(compareBy({ !it.value.vos }, { !it.value.perfil.esAdministrador }, { it.index }))
                .map { it.value }
    }
}

/** Así empiezan los nombres de los navegadores pareados con el tablero web (decisión 50): "Tablero web · Chrome en Windows". */
private const val PREFIJO_TABLERO = "Tablero web"

/**
 * Si [nombre] es el de un navegador del tablero web. El registro de cambios trae el nombre y no si es un
 * navegador: con esto lo nombra como navegador y no como persona, como el tablero.
 */
fun esNombreDeNavegador(nombre: String?): Boolean = nombre?.startsWith(PREFIJO_TABLERO) == true

/**
 * El navegador de un nombre del tablero web, sin el "Tablero web ·" que llevan todos: "Chrome en Windows".
 * Null si no dice más que "Tablero web", como los primeros.
 */
fun navegadorDelNombre(nombre: String): String? =
    nombre.removePrefix(PREFIJO_TABLERO).trim().removePrefix("·").trim().ifEmpty { null }

/**
 * El código de un uso para sumar a una persona (Figma 468-2735): lo que lleva el QR, el código en
 * texto y hasta cuándo vale (10 minutos).
 */
data class CodigoPersona(
    val codigo: String,
    val vence: Instant,
    /** `zonda:<dirección>#<código>;vence=<fecha>`, lo mismo que el QR de `zonda pair`. */
    val contenido: String,
    val nombre: String,
    val perfil: Perfil,
)
