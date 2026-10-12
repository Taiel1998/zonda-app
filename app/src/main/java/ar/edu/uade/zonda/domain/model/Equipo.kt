package ar.edu.uade.zonda.domain.model

/** Un equipo de la red: su IP y, si el catálogo lo conoce, su nombre. La app muestra el nombre y, si no hay, la IP. */
data class Equipo(
    val ip: String,
    val nombre: String? = null,
) {
    val etiqueta: String
        get() = nombre ?: ip
}
