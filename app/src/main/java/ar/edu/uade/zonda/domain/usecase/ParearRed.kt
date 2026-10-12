package ar.edu.uade.zonda.domain.usecase

import ar.edu.uade.zonda.domain.model.DatosPareo
import ar.edu.uade.zonda.domain.model.ErrorZonda
import ar.edu.uade.zonda.domain.model.Red
import ar.edu.uade.zonda.domain.repository.RedesRepository

/**
 * Parear el teléfono con una red (RF05): valida los datos antes de tocar la red y delega el canje
 * del código en el repositorio, que verifica el servidor y guarda el token.
 */
class ParearRed(
    private val redes: RedesRepository,
) {
    suspend operator fun invoke(datos: DatosPareo, nombreDispositivo: String): Red {
        val limpio = datos.copy(urlBase = datos.urlBase.trim().trimEnd('/'), codigo = datos.codigo.trim().uppercase())
        if (!limpio.urlBase.startsWith("https://") && !limpio.urlBase.startsWith("http://")) {
            throw ErrorZonda.Validacion("urlBase", "La dirección tiene que empezar con https://")
        }
        if (!limpio.codigoValido) {
            throw ErrorZonda.Validacion("codigo", "El código tiene ocho caracteres, por ejemplo K7M2-QX9P")
        }
        if (nombreDispositivo.isBlank()) {
            throw ErrorZonda.Validacion("nombreDispositivo", "Falta el nombre del teléfono")
        }
        return redes.parear(limpio, nombreDispositivo.trim())
    }
}
