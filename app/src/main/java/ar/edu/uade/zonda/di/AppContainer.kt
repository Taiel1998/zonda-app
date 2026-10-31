package ar.edu.uade.zonda.di

import android.content.Context
import android.os.Build
import ar.edu.uade.zonda.data.local.ZondaDatabase
import ar.edu.uade.zonda.data.preferencias.PreferenciasRepositoryImpl
import ar.edu.uade.zonda.data.preferencias.preferenciasZonda
import ar.edu.uade.zonda.data.remote.ProveedorDeApi
import ar.edu.uade.zonda.data.remote.ProveedorDeApiRetrofit
import ar.edu.uade.zonda.data.repository.AccionesPendientesRepositoryImpl
import ar.edu.uade.zonda.data.repository.AlertasRepositoryImpl
import ar.edu.uade.zonda.data.repository.BloqueosRepositoryImpl
import ar.edu.uade.zonda.data.repository.RedesRepositoryImpl
import ar.edu.uade.zonda.data.seguridad.AlmacenSeguro
import ar.edu.uade.zonda.data.seguridad.AlmacenSeguroKeystore
import ar.edu.uade.zonda.data.seguridad.ClaveDeBase
import ar.edu.uade.zonda.data.sync.Sincronizador
import ar.edu.uade.zonda.data.sync.SincronizadorHttp
import ar.edu.uade.zonda.domain.repository.AccionesPendientesRepository
import ar.edu.uade.zonda.domain.repository.AlertasRepository
import ar.edu.uade.zonda.domain.repository.BloqueosRepository
import ar.edu.uade.zonda.domain.repository.Conectividad
import ar.edu.uade.zonda.domain.repository.GeneradorDeIds
import ar.edu.uade.zonda.domain.repository.PreferenciasRepository
import ar.edu.uade.zonda.domain.repository.RedesRepository
import ar.edu.uade.zonda.domain.usecase.AsignarAlerta
import ar.edu.uade.zonda.domain.usecase.BorrarDatosLocales
import ar.edu.uade.zonda.domain.usecase.CambiarImportancia
import ar.edu.uade.zonda.domain.usecase.CambiarSeveridades
import ar.edu.uade.zonda.domain.usecase.DeshacerRevision
import ar.edu.uade.zonda.domain.usecase.EnviarAvisosTelegram
import ar.edu.uade.zonda.domain.usecase.GestionarBloqueos
import ar.edu.uade.zonda.domain.usecase.GestionarPersonas
import ar.edu.uade.zonda.domain.usecase.ObservarAlertas
import ar.edu.uade.zonda.domain.usecase.ObservarEstadoSistema
import ar.edu.uade.zonda.domain.usecase.ObservarResumen
import ar.edu.uade.zonda.domain.usecase.ObtenerDetalleAlerta
import ar.edu.uade.zonda.domain.usecase.ObtenerEstadoRed
import ar.edu.uade.zonda.domain.usecase.ParearRed
import ar.edu.uade.zonda.domain.usecase.QuitarDispositivo
import ar.edu.uade.zonda.domain.usecase.ResolverEnvioRevision
import ar.edu.uade.zonda.domain.usecase.RevisarAlerta
import ar.edu.uade.zonda.domain.usecase.SincronizarAlertas
import ar.edu.uade.zonda.domain.usecase.VincularTelegram
import ar.edu.uade.zonda.notificaciones.Notificador
import ar.edu.uade.zonda.sync.ConectividadAndroid
import ar.edu.uade.zonda.ui.detalle.VentanaDeshacer
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import okhttp3.OkHttpClient

/**
 * Contenedor manual de dependencias.
 *
 * Acá se crean una sola vez las piezas compartidas (almacenamiento seguro, base de datos,
 * cliente HTTP, preferencias, repositorios y casos de uso) y las pantallas las obtienen a través de
 * sus ViewModels. Se eligió un contenedor manual en lugar de Hilt para poder explicar cada instancia
 * línea por línea en la defensa (docs/decisiones.md, decisión 2).
 *
 * Todo es `lazy`: nada pesado se crea hasta que alguien lo usa, así el arranque de la app no
 * abre la base ni toca el Keystore si todavía no hace falta.
 */
class AppContainer(context: Context) {

    val appContext: Context = context.applicationContext

    // Capa de datos: local
    val almacenSeguro: AlmacenSeguro by lazy { AlmacenSeguroKeystore(appContext) }
    val baseDeDatos: ZondaDatabase by lazy { ZondaDatabase.cifrada(appContext, ClaveDeBase(almacenSeguro).obtener()) }
    val preferencias: PreferenciasRepository by lazy { PreferenciasRepositoryImpl(appContext.preferenciasZonda) }

    // Capa de datos: red. Un solo OkHttpClient para toda la app; el token de cada red lo lee el interceptor.
    val clienteHttp: OkHttpClient by lazy { ProveedorDeApiRetrofit.clientePorDefecto() }
    val apis: ProveedorDeApi by lazy {
        ProveedorDeApiRetrofit(tokens = { redId -> almacenSeguro.leer(RedesRepositoryImpl.claveToken(redId)) }, clienteBase = clienteHttp)
    }
    val conectividad: Conectividad by lazy { ConectividadAndroid(appContext) }
    val sincronizador: Sincronizador by lazy {
        SincronizadorHttp(baseDeDatos, apis, avisosDelTelefono = { preferencias.observarAvisos().first() })
    }

    // Repositorios
    val redes: RedesRepository by lazy { RedesRepositoryImpl(baseDeDatos.redesDao(), almacenSeguro, apis) }
    val alertas: AlertasRepository by lazy { AlertasRepositoryImpl(baseDeDatos.alertasDao(), sincronizador) }
    val accionesPendientes: AccionesPendientesRepository by lazy { AccionesPendientesRepositoryImpl(baseDeDatos.accionesPendientesDao()) }
    val bloqueos: BloqueosRepository by lazy { BloqueosRepositoryImpl(baseDeDatos.redesDao(), apis) }
    val generadorDeIds: GeneradorDeIds = GeneradorDeIds { UUID.randomUUID().toString() }

    /**
     * Alcance de corrutinas de toda la app, para el trabajo que no tiene que cortarse al cerrar una
     * pantalla: por ejemplo, enviar una revisión cuando termina la ventana de Deshacer (decisión 15).
     */
    val alcanceDeLaApp: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** Notificaciones locales de alertas nuevas; los canales se crean al arrancar la app. */
    val notificador: Notificador by lazy { Notificador(appContext) }

    /** Cómo se llama este teléfono en el servidor ("Google Pixel 7"): es el autor de sus revisiones. */
    val nombreDelTelefono: String = listOf(Build.MANUFACTURER.orEmpty(), Build.MODEL.orEmpty())
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .distinct()
        .joinToString(" ") { parte -> parte.replaceFirstChar { it.uppercase() } }
        .ifBlank { "Teléfono Android" }

    // Casos de uso
    val observarAlertas: ObservarAlertas by lazy { ObservarAlertas(redes, alertas) }
    val obtenerEstadoRed: ObtenerEstadoRed by lazy { ObtenerEstadoRed(redes, alertas) }
    val observarEstadoSistema: ObservarEstadoSistema by lazy { ObservarEstadoSistema(redes) }
    val obtenerDetalleAlerta: ObtenerDetalleAlerta by lazy { ObtenerDetalleAlerta(alertas, conectividad) }
    val revisarAlerta: RevisarAlerta by lazy { RevisarAlerta(alertas, accionesPendientes, generadorDeIds) }
    val deshacerRevision: DeshacerRevision by lazy { DeshacerRevision(alertas, accionesPendientes) }
    val resolverEnvioRevision: ResolverEnvioRevision by lazy { ResolverEnvioRevision(alertas, accionesPendientes) }
    val sincronizarAlertas: SincronizarAlertas by lazy { SincronizarAlertas(redes, alertas) }
    val observarResumen: ObservarResumen by lazy { ObservarResumen(redes, alertas) }
    val cambiarSeveridades: CambiarSeveridades by lazy { CambiarSeveridades(redes, accionesPendientes, generadorDeIds) }
    val parearRed: ParearRed by lazy { ParearRed(redes) }
    val quitarDispositivo: QuitarDispositivo by lazy { QuitarDispositivo(redes, alertas, accionesPendientes) }
    val borrarDatosLocales: BorrarDatosLocales by lazy { BorrarDatosLocales(redes, alertas, accionesPendientes) }
    val vincularTelegram: VincularTelegram by lazy { VincularTelegram(redes, preferencias) }
    val enviarAvisosTelegram: EnviarAvisosTelegram by lazy { EnviarAvisosTelegram(redes) }
    val cambiarImportancia: CambiarImportancia by lazy { CambiarImportancia(redes, accionesPendientes, generadorDeIds) }
    val asignarAlerta: AsignarAlerta by lazy { AsignarAlerta(redes, alertas, accionesPendientes, generadorDeIds) }
    val gestionarPersonas: GestionarPersonas by lazy { GestionarPersonas(redes) }
    val gestionarBloqueos: GestionarBloqueos by lazy { GestionarBloqueos(bloqueos, generadorDeIds) }

    /**
     * La ventana de Deshacer de las revisiones (decisión 38): una para toda la app, porque al confirmar
     * el detalle se cierra y el "Revisaste ..." con Deshacer lo muestra la lista a la que se volvió.
     */
    val ventanaDeshacer: VentanaDeshacer by lazy { VentanaDeshacer(deshacerRevision, sincronizarAlertas, conectividad, alcanceDeLaApp) }
}
