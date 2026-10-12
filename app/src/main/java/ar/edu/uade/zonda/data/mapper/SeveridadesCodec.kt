package ar.edu.uade.zonda.data.mapper

import ar.edu.uade.zonda.domain.model.Severidad
import ar.edu.uade.zonda.domain.model.SeveridadPorTipo
import ar.edu.uade.zonda.domain.model.TipoAtaque

/**
 * Guarda una [SeveridadPorTipo] en una sola columna de texto, con la forma
 * `FUERZA_BRUTA=ALTA;ESCANEO=MEDIA`. Solo lo usa la acción pendiente CAMBIAR_SEVERIDADES;
 * es más simple de explicar que meter una biblioteca de JSON para seis pares.
 */
object SeveridadesCodec {

    fun codificar(severidades: SeveridadPorTipo): String =
        severidades.valores.entries
            .sortedBy { it.key.name }
            .joinToString(";") { "${it.key.name}=${it.value.name}" }

    fun decodificar(texto: String): SeveridadPorTipo =
        SeveridadPorTipo(
            texto.split(";")
                .filter { it.isNotBlank() }
                .associate { par ->
                    val (tipo, severidad) = par.split("=")
                    TipoAtaque.valueOf(tipo) to Severidad.valueOf(severidad)
                },
        )
}
