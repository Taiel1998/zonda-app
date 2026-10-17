package ar.edu.uade.zonda.domain.usecase

import ar.edu.uade.zonda.domain.model.CodigoPersona
import ar.edu.uade.zonda.domain.model.ErrorZonda
import ar.edu.uade.zonda.domain.model.Perfil
import ar.edu.uade.zonda.domain.repository.RedesRepository

/**
 * Sumar, cambiar de perfil o quitar gente de la red desde Ajustes → Personas (corrección 2, decisión 39).
 * Lo hace un administrador y necesita conexión: el código lo genera el servidor, y el servidor es el que
 * controla que siempre quede al menos un administrador. Cada cambio queda en el registro de cambios.
 */
class GestionarPersonas(private val redes: RedesRepository) {

    /** "Generar código": el QR y el código de un uso para que [nombre] se sume con [perfil]. */
    suspend fun crearCodigo(redId: String, nombre: String, perfil: Perfil): CodigoPersona {
        val limpio = nombre.trim()
        if (limpio.isEmpty()) throw ErrorZonda.Validacion("nombre", "Escribí el nombre de la persona")
        return redes.crearCodigoPersona(redId, limpio, perfil)
    }

    /** "Hacer administrador" o "Pasar a analista". */
    suspend fun cambiarPerfil(redId: String, personaId: String, perfil: Perfil) = redes.cambiarPerfil(redId, personaId, perfil)

    /** "Quitar del equipo": el teléfono de esa persona deja de tener acceso a la red. */
    suspend fun quitar(redId: String, personaId: String) = redes.quitarPersona(redId, personaId)

    /** Vuelve a pedir la gente y el registro, por ejemplo al volver de la pantalla del código. */
    suspend fun actualizar(redId: String) = redes.actualizarPersonasYRegistro(redId)
}
