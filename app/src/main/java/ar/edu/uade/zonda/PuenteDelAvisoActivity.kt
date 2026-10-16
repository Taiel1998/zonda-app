package ar.edu.uade.zonda

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import ar.edu.uade.zonda.ui.detalle.RutaDetalle

/**
 * Pantalla puente de los enlaces del aviso de Telegram (cambio 23, decisión 31). No tiene ventana:
 * recibe el enlace, se lo pasa a [MainActivity] y se cierra.
 *
 * Hace falta porque Android no siempre le entregaba el enlace a Zonda. Si la tarea de Zonda la abrió
 * un aviso y después se tocaba ese mismo aviso, Android veía el mismo enlace que había abierto la
 * tarea y solo la traía al frente, sin avisarle a la app: Zonda quedaba donde estaba (por ejemplo, en
 * Estado). Esta pantalla no tiene afinidad de tarea y no queda en recientes ni en el historial, así
 * que Android la crea de cero en cada toque. Ella abre Zonda con una tarea limpia, que se arma con el
 * enlace nuevo aunque sea el mismo de antes: Estado abajo y el detalle arriba.
 *
 * Solo pasa los enlaces del aviso, sin extras. Los enlaces internos (`zonda://`) los abre únicamente
 * la propia app (ver EnlacesInternos).
 */
class PuenteDelAvisoActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        intent?.data?.takeIf(::esEnlaceDelAviso)?.let { enlace -> startActivity(abrirZonda(this, enlace)) }
        // Sin ventana (Theme.NoDisplay): tiene que cerrarse antes de onResume.
        finish()
    }

    companion object {
        /** Lo mismo que acepta el filtro del manifiesto: https://taiel1998.github.io/a/... */
        fun esEnlaceDelAviso(enlace: Uri): Boolean {
            val dominio = Uri.parse(RutaDetalle.DOMINIO_AVISO)
            return enlace.scheme == dominio.scheme &&
                enlace.host == dominio.host &&
                enlace.path.orEmpty().startsWith(dominio.path.orEmpty())
        }

        /**
         * El intent hacia la app: el enlace solo, a MainActivity. CLEAR_TASK cierra lo que hubiera en la
         * tarea de Zonda y la vuelve a armar con este enlace, aunque ya la hubiera abierto el mismo; con
         * NEW_TASK y CLEAR_TASK, Navigation pone Estado debajo del detalle sin relanzar la Activity.
         * TASK_ON_HOME deja el inicio del teléfono detrás de Zonda, como cuando el enlace llegaba
         * directo: el atrás desde Estado sale al inicio.
         */
        fun abrirZonda(contexto: Context, enlace: Uri): Intent =
            Intent(Intent.ACTION_VIEW, enlace, contexto, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_TASK_ON_HOME)
    }
}
