package ar.edu.uade.zonda.data.seguridad

import java.security.SecureRandom

/**
 * Frase de cifrado de la base SQLCipher. Se genera al azar la primera vez (32 bytes) y queda
 * guardada en el [AlmacenSeguro]; las veces siguientes se lee de ahí. Nunca sale del teléfono.
 */
class ClaveDeBase(
    private val almacen: AlmacenSeguro,
    private val aleatorio: SecureRandom = SecureRandom(),
) {
    fun obtener(): ByteArray {
        val guardada = almacen.leer(CLAVE)
        if (guardada != null) return guardada.toByteArray(Charsets.UTF_8)
        val bytes = ByteArray(LARGO_BYTES).also(aleatorio::nextBytes)
        val hexadecimal = bytes.joinToString("") { "%02x".format(it) }
        almacen.guardar(CLAVE, hexadecimal)
        return hexadecimal.toByteArray(Charsets.UTF_8)
    }

    companion object {
        const val CLAVE = "clave_base"
        private const val LARGO_BYTES = 32
    }
}
