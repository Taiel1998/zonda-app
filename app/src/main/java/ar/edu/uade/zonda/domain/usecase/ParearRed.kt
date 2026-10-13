package ar.edu.uade.zonda.domain.usecase

import ar.edu.uade.zonda.domain.model.DatosPareo
import ar.edu.uade.zonda.domain.model.ErrorZonda
import ar.edu.uade.zonda.domain.model.PasoPareo
import ar.edu.uade.zonda.domain.model.RedPareada
import ar.edu.uade.zonda.domain.repository.RedesRepository

/**
 * Parear el teléfono con una red (RF05): normaliza y valida los datos antes de tocar la red, verifica
 * el servidor y recién después canjea el código, que es de un solo uso. [alAvanzar] informa cada paso
 * para la pantalla Conexión. Las alertas no se bajan acá: la primera descarga pasa en Estado.
 */
class ParearRed(
    private val redes: RedesRepository,
) {
    suspend operator fun invoke(
        datos: DatosPareo,
        nombreDispositivo: String,
        alAvanzar: (PasoPareo) -> Unit = {},
    ): RedPareada {
        val limpio = DatosPareo(
            urlBase = DatosPareo.normalizarDireccion(datos.urlBase),
            codigo = DatosPareo.normalizarCodigo(datos.codigo),
            codigoVence = datos.codigoVence,
        )
        if (limpio.urlBase.isEmpty() || limpio.urlBase.contains(' ')) {
            throw ErrorZonda.Validacion("urlBase", "Falta la dirección del servidor")
        }
        if (!limpio.codigoValido) {
            throw ErrorZonda.Validacion("codigo", "El código tiene ocho caracteres, por ejemplo K7M2-QX9P")
        }
        if (nombreDispositivo.isBlank()) {
            throw ErrorZonda.Validacion("nombreDispositivo", "Falta el nombre del teléfono")
        }
        alAvanzar(PasoPareo.BUSCANDO_SERVIDOR)
        redes.verificarServidor(limpio.urlBase)
        alAvanzar(PasoPareo.REGISTRANDO_TELEFONO)
        return redes.parear(limpio, nombreDispositivo.trim())
    }
}
