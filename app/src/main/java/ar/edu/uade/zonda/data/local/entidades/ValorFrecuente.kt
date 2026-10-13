package ar.edu.uade.zonda.data.local.entidades

/** Un valor de una columna con cuántas alertas lo tienen y, si es un equipo destino, su nombre (Más filtros). */
data class ValorFrecuente(
    val valor: String,
    val nombre: String?,
    val cantidad: Int,
)
