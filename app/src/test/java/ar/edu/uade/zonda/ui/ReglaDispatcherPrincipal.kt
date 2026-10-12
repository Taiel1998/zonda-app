package ar.edu.uade.zonda.ui

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description

/**
 * Reemplaza `Dispatchers.Main` (el hilo de la interfaz, que no existe en la JVM) por un dispatcher
 * de prueba mientras dura cada test. Lo necesitan los ViewModels, que lanzan corrutinas en `viewModelScope`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ReglaDispatcherPrincipal(
    val dispatcher: TestDispatcher = UnconfinedTestDispatcher(),
) : TestWatcher() {

    override fun starting(description: Description) {
        Dispatchers.setMain(dispatcher)
    }

    override fun finished(description: Description) {
        Dispatchers.resetMain()
    }
}
