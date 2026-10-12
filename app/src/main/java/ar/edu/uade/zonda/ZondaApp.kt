package ar.edu.uade.zonda

import android.app.Application
import ar.edu.uade.zonda.di.AppContainer

/**
 * Punto de entrada de la aplicación. Vive mientras el proceso vive, por eso acá se crea el contenedor
 * de dependencias: una sola instancia de la base, del cliente HTTP y de los repositorios para toda la app.
 */
class ZondaApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
