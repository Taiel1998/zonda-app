package ar.edu.uade.zonda

import android.app.Application
import androidx.work.Configuration
import ar.edu.uade.zonda.di.AppContainer
import ar.edu.uade.zonda.sync.FabricaDeWorkers
import ar.edu.uade.zonda.sync.SincronizacionWorker

/**
 * Punto de entrada de la aplicación. Vive mientras el proceso vive, por eso acá se crea el contenedor
 * de dependencias: una sola instancia de la base, del cliente HTTP y de los repositorios para toda la app.
 *
 * También configura WorkManager con la fábrica de workers del contenedor (el inicializador automático
 * está desactivado en el manifiesto), crea los canales de notificación y programa la sincronización
 * en segundo plano (RNF08).
 */
class ZondaApp : Application(), Configuration.Provider {

    lateinit var container: AppContainer
        private set

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(FabricaDeWorkers(container)).build()

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.notificador.crearCanales()
        SincronizacionWorker.programar(this)
    }
}
