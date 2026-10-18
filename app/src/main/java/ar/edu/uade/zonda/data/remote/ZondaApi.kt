package ar.edu.uade.zonda.data.remote

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * El contrato de la API, una función por ruta (backend/README.md). Retrofit genera la implementación.
 *
 * Las funciones son `suspend`: se llaman desde corrutinas y una respuesta que no es 2xx llega como
 * [retrofit2.HttpException], que [conErroresTraducidos] convierte en
 * [ar.edu.uade.zonda.domain.model.ErrorZonda]. El token lo agrega [InterceptorDeToken]; acá no aparece.
 */
interface ZondaApi {

    /** Sin token sirve para verificar que el servidor responde antes de parear. */
    @GET("salud")
    suspend fun salud(): SaludDto

    /** La misma ruta, con la respuesta cruda: Detalles técnicos lee de ahí la versión de TLS y el certificado. */
    @GET("salud")
    suspend fun saludConRespuesta(): Response<SaludDto>

    /** Alertas nuevas o modificadas desde el cursor, de a 200, en orden de cambio. */
    @GET("alertas")
    suspend fun alertas(@Query("desde") desde: String?): PaginaAlertasDto

    @GET("alertas/{id}")
    suspend fun alerta(@Path("id") id: String): AlertaDto

    @POST("alertas/{id}/revision")
    suspend fun revisar(@Path("id") id: String, @Body revision: RevisionEntradaDto): AlertaDto

    @GET("severidades")
    suspend fun severidades(): Map<String, String>

    /** Solo un administrador y con motivo (API 0.6.0; decisiones 39 y 40). */
    @PUT("severidades")
    suspend fun guardarSeveridades(@Body entrada: SeveridadesEntradaDto): Map<String, String>

    @GET("catalogo")
    suspend fun catalogo(): CatalogoDto

    @POST("pareo")
    suspend fun parear(@Body entrada: PareoEntradaDto): PareoSalidaDto

    @GET("telegram")
    suspend fun telegram(): EstadoTelegramDto

    @POST("telegram/vinculo")
    suspend fun vincularTelegram(@Body entrada: VinculoEntradaDto): VinculoTelegramDto

    /** Responde 204 sin cuerpo. */
    @DELETE("telegram/vinculo")
    suspend fun desvincularTelegram(): Response<Unit>

    /** Responde 204 sin cuerpo. */
    @POST("telegram/prueba")
    suspend fun avisoDePrueba(): Response<Unit>

    @PUT("telegram/avisos")
    suspend fun guardarAvisos(@Body avisos: AvisosDto): AvisosDto

    /** Responde 204 sin cuerpo, por eso devuelve la respuesta cruda. */
    @DELETE("dispositivos/actual")
    suspend fun quitarDispositivo(): Response<Unit>

    // ---- Personas, equipos, registro y asignaciones (API 0.6.0; decisiones 39 a 42) ----

    @GET("personas")
    suspend fun personas(): List<PersonaDto>

    @POST("personas/codigo")
    suspend fun codigoParaPersona(@Body entrada: CodigoPersonaEntradaDto): CodigoPersonaDto

    @PUT("personas/{id}/perfil")
    suspend fun cambiarPerfil(@Path("id") id: String, @Body entrada: PerfilEntradaDto): PersonaDto

    /** Responde 204 sin cuerpo. */
    @DELETE("personas/{id}")
    suspend fun quitarPersona(@Path("id") id: String): Response<Unit>

    @GET("equipos")
    suspend fun equipos(): List<EquipoDto>

    @PUT("equipos/{ip}")
    suspend fun cambiarImportancia(@Path("ip") ip: String, @Body entrada: ImportanciaEntradaDto): EquipoDto

    @GET("registro")
    suspend fun registro(): List<CambioRegistroDto>

    @PUT("alertas/{id}/asignacion")
    suspend fun asignar(@Path("id") id: String, @Body entrada: AsignacionEntradaDto): AlertaDto
}
