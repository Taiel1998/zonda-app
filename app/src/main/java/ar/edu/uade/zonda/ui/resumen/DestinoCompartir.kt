package ar.edu.uade.zonda.ui.resumen

import androidx.annotation.StringRes
import ar.edu.uade.zonda.R

/**
 * Las apps de la hoja de compartir del Figma (107-5511), en su orden. Cada una con los paquetes que
 * la abren; la hoja muestra solo las que están instaladas. Copiar (con la imagen) e Imprimir (con el
 * PDF, cambio 18) no abren ninguna app.
 */
enum class DestinoCompartir(@param:StringRes val nombre: Int, val paquetes: List<String>) {
    WHATSAPP(R.string.resumen_destino_whatsapp, listOf("com.whatsapp", "com.whatsapp.w4b")),
    TELEGRAM(R.string.resumen_destino_telegram, listOf("org.telegram.messenger", "org.telegram.messenger.web")),
    GMAIL(R.string.resumen_destino_gmail, listOf("com.google.android.gm")),
    DRIVE(R.string.resumen_destino_drive, listOf("com.google.android.apps.docs")),
    COPIAR(R.string.resumen_destino_copiar, emptyList()),
    IMPRIMIR(R.string.resumen_destino_imprimir, emptyList());

    companion object {
        /** Las que abren una app, en su orden. */
        val APPS = listOf(WHATSAPP, TELEGRAM, GMAIL, DRIVE)
    }
}
