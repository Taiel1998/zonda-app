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
) {
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
