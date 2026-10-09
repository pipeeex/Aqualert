package com.aqualer.app.data.service

import android.content.Context
import com.aqualer.app.data.estado.EstadoAlerta
import com.aqualer.app.data.model.Alerta
import com.aqualer.app.data.model.NivelRiesgo
import com.aqualer.app.data.model.Notificacion
import com.aqualer.app.data.model.Reporte
import com.aqualer.app.data.model.TipoAlerta
import com.aqualer.app.data.model.TipoNotificacion
import com.aqualer.app.util.Constantes
import com.aqualer.app.util.Recurso
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

/**
 * Servicio de Alertas.
 *
 * Materializa la relacion del diagrama de clases
 *   Reporte 1 --genera--> 0..1 Alerta
 *   Alerta  1 --emite---> 0..* Notificacion
 *
 * e implementa emitir(), escalar() y resolverAlerta() respetando la
 * maquina de estados de la clase Alerta:
 *
 *   GENERADA       --emitir()---------------> EMITIDA
 *   GENERADA       --cancelar()-------------> CANCELADA
 *   EMITIDA        --escalar()--------------> EN_SEGUIMIENTO
 *   EN_SEGUIMIENTO --escalar() [critico]----> ESCALADA
 *   *              --resolverAlerta()-------> RESUELTA
 *   RESUELTA       --archivar()-------------> ARCHIVADA
 */
class AlertasService(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val notificacionesService: NotificacionesService = NotificacionesService()
) {

    private val coleccion get() = db.collection(Constantes.COL_ALERTAS)

    // ==========================================================
    //  Generacion
    // ==========================================================

    /**
     * Genera la alerta asociada a un reporte recien publicado.
     *
     * El nivel de riesgo se toma del que declaro el ciudadano, pero si
     * la clasificacion automatica sugiere uno mas alto se conserva el
     * mayor de los dos: ante la duda, el sistema no subestima el riesgo.
     *
     * Devuelve null como exito cuando el reporte no amerita alerta.
     */
    suspend fun generarDesdeReporte(reporte: Reporte): Recurso<Alerta?> = try {
        if (!ameritaAlerta(reporte)) {
            Recurso.Exito(null)
        } else {
            val sugerido = NivelRiesgo.desdeClasificacion(
                clasificacion = reporte.clasificacionIa,
                confianza = reporte.confianzaIa
            )
            val nivel = maxOf(
                reporte.nivelRiesgo,
                sugerido,
                compareBy<NivelRiesgo> { it.peso }
            )

            val alerta = Alerta(
                idReporte = reporte.id,
                tipo = TipoAlerta.CONTAMINACION_DETECTADA,
                nivelRiesgo = nivel,
                estado = EstadoAlerta.GENERADA,
                mensaje = construirMensaje(reporte, nivel)
            )

            val referencia = coleccion.add(alerta.aMapa()).await()
            Recurso.Exito(alerta.copy(id = referencia.id))
        }
    } catch (e: Exception) {
        Recurso.Error(mensajeDeError(e), e)
    }

    /**
     * Regla de negocio: se genera alerta cuando el riesgo es medio o
     * superior, o cuando el ciudadano marco atencion urgente.
     * Un reporte de riesgo bajo no satura de alertas a las autoridades.
     */
    private fun ameritaAlerta(reporte: Reporte): Boolean =
        reporte.atencionUrgente || reporte.nivelRiesgo.peso >= NivelRiesgo.MEDIO.peso

    private fun construirMensaje(reporte: Reporte, nivel: NivelRiesgo): String =
        "Riesgo ${nivel.valor.uppercase()}: ${reporte.titulo}. " +
                "Tipo: ${reporte.tipo.etiqueta}. " +
                "Ubicacion: ${reporte.ubicacion.comoTexto()}."

    // ==========================================================
    //  Transiciones
    // ==========================================================

    /**
     * emitir(): GENERADA --> EMITIDA.
     * Al emitirse se notifica al autor del reporte (relacion
     * Alerta 1 --emite--> 0..* Notificacion).
     */
    suspend fun emitir(
        alerta: Alerta,
        reporte: Reporte,
        contexto: Context? = null
    ): Recurso<Alerta> = try {
        val emitida = alerta.emitir()

        coleccion.document(alerta.id)
            .update(Alerta.CAMPO_ESTADO, emitida.estado.valor)
            .await()

        if (reporte.uidUsuario.isNotBlank()) {
            notificacionesService.generarYEnviar(
                Notificacion(
                    uidUsuario = reporte.uidUsuario,
                    titulo = "Alerta de contaminacion emitida",
                    mensaje = alerta.mensaje,
                    tipo = TipoNotificacion.ALERTA,
                    idReporte = reporte.id,
                    idAlerta = alerta.id
                ),
                contexto
            )
        }

        Recurso.Exito(emitida)
    } catch (e: IllegalStateException) {
        Recurso.Error(e.message ?: "No se puede emitir la alerta en su estado actual")
    } catch (e: Exception) {
        Recurso.Error(mensajeDeError(e), e)
    }

    /**
     * escalar(): EMITIDA --> EN_SEGUIMIENTO, y de ahi a ESCALADA
     * unicamente si el nivel de riesgo es critico.
     */
    suspend fun escalar(alerta: Alerta): Recurso<Alerta> = try {
        val escalada = alerta.escalar()

        coleccion.document(alerta.id)
            .update(Alerta.CAMPO_ESTADO, escalada.estado.valor)
            .await()

        Recurso.Exito(escalada)
    } catch (e: IllegalStateException) {
        Recurso.Error(e.message ?: "No se puede escalar esta alerta")
    } catch (e: Exception) {
        Recurso.Error(mensajeDeError(e), e)
    }

    /** resolverAlerta(): EMITIDA, EN_SEGUIMIENTO o ESCALADA --> RESUELTA. */
    suspend fun resolver(alerta: Alerta): Recurso<Alerta> = try {
        val resuelta = alerta.resolverAlerta()

        coleccion.document(alerta.id)
            .update(Alerta.CAMPO_ESTADO, resuelta.estado.valor)
            .await()

        Recurso.Exito(resuelta)
    } catch (e: IllegalStateException) {
        Recurso.Error(e.message ?: "No se puede resolver esta alerta")
    } catch (e: Exception) {
        Recurso.Error(mensajeDeError(e), e)
    }

    /** archivar(): RESUELTA --> ARCHIVADA. */
    suspend fun archivar(alerta: Alerta): Recurso<Alerta> = try {
        val archivada = alerta.archivar()

        coleccion.document(alerta.id)
            .update(Alerta.CAMPO_ESTADO, archivada.estado.valor)
            .await()

        Recurso.Exito(archivada)
    } catch (e: IllegalStateException) {
        Recurso.Error(e.message ?: "Solo se pueden archivar alertas resueltas")
    } catch (e: Exception) {
        Recurso.Error(mensajeDeError(e), e)
    }

    /** cancelar(): GENERADA --> CANCELADA, antes de haberse emitido. */
    suspend fun cancelar(alerta: Alerta): Recurso<Alerta> = try {
        val cancelada = alerta.cambiarEstado(EstadoAlerta.CANCELADA)

        coleccion.document(alerta.id)
            .update(Alerta.CAMPO_ESTADO, cancelada.estado.valor)
            .await()

        Recurso.Exito(cancelada)
    } catch (e: IllegalStateException) {
        Recurso.Error(e.message ?: "No se puede cancelar una alerta ya emitida")
    } catch (e: Exception) {
        Recurso.Error(mensajeDeError(e), e)
    }

    // ==========================================================
    //  Consultas
    // ==========================================================

    /** Alertas que siguen requiriendo atencion, de mayor a menor riesgo. */
    suspend fun listarActivas(): Recurso<List<Alerta>> = try {
        val consulta = coleccion.get().await()

        val alertas = consulta.documents
            .map { Alerta.desde(it) }
            .filter { it.estado.estaActiva }
            .sortedWith(
                compareByDescending<Alerta> { it.nivelRiesgo.peso }
                    .thenByDescending { it.fechaEmision }
            )

        Recurso.Exito(alertas)
    } catch (e: Exception) {
        Recurso.Error(mensajeDeError(e), e)
    }

    /** Alerta asociada a un reporte, si existe. */
    suspend fun obtenerPorReporte(idReporte: String): Recurso<Alerta?> = try {
        val consulta = coleccion
            .whereEqualTo(Alerta.CAMPO_ID_REPORTE, idReporte)
            .limit(1)
            .get()
            .await()

        val alerta = consulta.documents.firstOrNull()?.let { Alerta.desde(it) }
        Recurso.Exito(alerta)
    } catch (e: Exception) {
        Recurso.Error(mensajeDeError(e), e)
    }

    /** Historico completo para el panel del Administrador. */
    suspend fun listarTodas(): Recurso<List<Alerta>> = try {
        val consulta = coleccion.get().await()

        val alertas = consulta.documents
            .map { Alerta.desde(it) }
            .sortedByDescending { it.fechaEmision }

        Recurso.Exito(alertas)
    } catch (e: Exception) {
        Recurso.Error(mensajeDeError(e), e)
    }

    private fun mensajeDeError(e: Exception): String {
        val texto = e.message.orEmpty()
        return when {
            texto.contains("PERMISSION_DENIED", true) ->
                "No tienes permisos para gestionar alertas"
            texto.contains("UNAVAILABLE", true) || texto.contains("network", true) ->
                "Sin conexion a internet. Revisa tu red e intenta de nuevo."
            texto.contains("NOT_FOUND", true) ->
                "La alerta ya no existe"
            else -> "No se pudo procesar la alerta."
        }
    }
}
