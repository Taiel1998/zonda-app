package ar.edu.uade.zonda.data.seguridad

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Guarda secretos chicos (el token de cada red y la frase de cifrado de la base) de forma que no
 * queden en claro en el teléfono (informe, sección 13.4).
 */
interface AlmacenSeguro {
    fun guardar(clave: String, valor: String)
    fun leer(clave: String): String?
    fun borrar(clave: String)
}

/**
 * Implementación sobre el Android Keystore: una clave AES de 256 bits que vive en el hardware
 * seguro del teléfono cifra cada valor (AES/GCM) antes de guardarlo en un archivo de preferencias
 * privado. El Keystore nunca entrega la clave en crudo; por eso la frase de SQLCipher se guarda
 * cifrada con esta clave y no "adentro" del Keystore (docs/decisiones.md, decisión 8).
 */
class AlmacenSeguroKeystore(context: Context) : AlmacenSeguro {

    private val preferencias = context.applicationContext.getSharedPreferences(ARCHIVO, Context.MODE_PRIVATE)
    private val claveMaestra: SecretKey by lazy { obtenerOCrearClave() }

    override fun guardar(clave: String, valor: String) {
        val cifrador = Cipher.getInstance(TRANSFORMACION)
        cifrador.init(Cipher.ENCRYPT_MODE, claveMaestra)
        val cifrado = cifrador.iv + cifrador.doFinal(valor.toByteArray(Charsets.UTF_8))
        preferencias.edit().putString(clave, Base64.encodeToString(cifrado, Base64.NO_WRAP)).apply()
    }

    override fun leer(clave: String): String? {
        val guardado = preferencias.getString(clave, null) ?: return null
        val cifrado = Base64.decode(guardado, Base64.NO_WRAP)
        val iv = cifrado.copyOfRange(0, LARGO_IV)
        val datos = cifrado.copyOfRange(LARGO_IV, cifrado.size)
        val descifrador = Cipher.getInstance(TRANSFORMACION)
        descifrador.init(Cipher.DECRYPT_MODE, claveMaestra, GCMParameterSpec(LARGO_ETIQUETA_BITS, iv))
        return String(descifrador.doFinal(datos), Charsets.UTF_8)
    }

    override fun borrar(clave: String) {
        preferencias.edit().remove(clave).apply()
    }

    private fun obtenerOCrearClave(): SecretKey {
        val keystore = KeyStore.getInstance(PROVEEDOR).apply { load(null) }
        (keystore.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val generador = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, PROVEEDOR)
        generador.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generador.generateKey()
    }

    private companion object {
        const val PROVEEDOR = "AndroidKeyStore"
        const val ALIAS = "zonda_clave_maestra"
        const val ARCHIVO = "zonda_secretos"
        const val TRANSFORMACION = "AES/GCM/NoPadding"
        const val LARGO_IV = 12
        const val LARGO_ETIQUETA_BITS = 128
    }
}
