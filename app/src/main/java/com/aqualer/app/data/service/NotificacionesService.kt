package com.aqualer.app.data.service

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.aqualer.app.AqualertApp
import com.aqualer.app.R
import com.aqualer.app.data.estado.EstadoNotificacion
import com.aqualer.app.data.estado.EstadoReporte
import com.aqualer.app.data.model.Notificacion
import com.aqualer.app.data.model.Reporte
import com.aqualer.app.data.model.TipoNotificacion
import com.aqualer.app.util.Constantes
import com.aqualer.app.util.Recurso
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.tasks.await

/**
 * Servicio de Notificaciones (NotifySvc del diagrama de componentes).
 *
 * Implementa enviar() y marcarLeida() de la clase Notificacion y su
 * maquina de estados:
 *
 *   PENDIENTE --enviar()------> ENVIADA --marcarLeida()--> LEIDA
 *   PENDIENTE --enviar() FAIL-> FALLIDA --reintentar()---> PENDIENTE
 *   FALLIDA   --descartar()---> DESCARTADA
 *
 * En el diagrama de colaboracion de Creacion de Reporte corresponde a
 * los pasos "1.16 generar notificacion" y "1.17 enviar push".
 *
 * La entrega al dispositivo se hace con el canal de notificaciones
 * creado en AqualertApp; el documento en Firestore conserva el estado
 * para que el usuario vea su bandeja aunque cambie de dispositivo.
 */
class NotificacionesService(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()
) {

    private val coleccion get() = db.collection(Constantes.COL_NOTIFICACIONES)

    // ==========================================================
    //  Generacion y envio
    // ==========================================================

    /**
     * Genera la notificacion en estado PENDIENTE y la entrega.
     *
     * @param contexto necesario para mostrar el aviso en el dispositivo;
     *                 puede ser null si solo se quiere registrar en la bandeja
     */
    suspend fun generarYEnviar(
        notificacion: Notificacion,
        contexto: Context? = null
    ): Recurso<String> = try {
        // Queda registrada como PENDIENTE: en cola de envio
        val pendiente = notificacion.copy(estado = EstadoNotificacion.PENDIENTE)
        val referencia = coleccion.add(pendiente.aMapa()).await()

        val entregada = runCatching {
            contexto?.let { mostrarEnDispositivo(it, pendiente, referencia.id) }
            true
        }.getOrDefault(false)

        // enviar() OK --> ENVIADA ; enviar() FAIL --> FALLIDA
        val destino = if (entregada) EstadoNotificacion.ENVIADA
        else EstadoNotificacion.FALLIDA

        coleccion.document(referencia.id).update(
            mapOf(
                Notificacion.CAMPO_ESTADO to destino.valor,
                Notificacion.CAMPO_INTENTOS to if (entregada) 0 else 1
            )
        ).await()

        Recurso.Exito(referencia.id)
    } catch (e: Exception) {
        Recurso.Error(mensajeDeError(e), e)
    }

    /**
     * Notifica al autor que el estado de su reporte cambio.
     * Corresponde a "Reports --> PushService : cambios de estado"
     * del diagrama de distribucion.
     */
    suspend fun notificarCambioDeEstado(
        reporte: Reporte,
        nuevoEstado: EstadoReporte,
        contexto: Context? = null
    ): Recurso<String> {
        if (reporte.uidUsuario.isBlank()) {
            return Recurso.Error("El reporte no tiene autor al cual notificar")
        }

        val notificacion = Notificacion(
            uidUsuario = reporte.uidUsuario,
            titulo = "Tu reporte cambio de estado",
            mensaje = "\"${reporte.titulo}\" ahora esta en estado ${nuevoEstado.valor}.",
            tipo = if (nuevoEstado == EstadoReporte.VERIFICADO) TipoNotificacion.VERIFICACION
            else TipoNotificacion.CAMBIO_ESTADO,
            idReporte = reporte.id
        )

        return generarYEnviar(notificacion, contexto)
    }

    /** Notifica al autor que alguien comento su reporte. */
    suspend fun notificarNuevoComentario(
        reporte: Reporte,
        nombreAutorComentario: String,
        contexto: Context? = null
    ): Recurso<String> {
        if (reporte.uidUsuario.isBlank()) {
            return Recurso.Error("El reporte no tiene autor al cual notificar")
        }

        val notificacion = Notificacion(
            uidUsuario = reporte.uidUsuario,
            titulo = "Nuevo comentario en tu reporte",
            mensaje = "$nombreAutorComentario comento en \"${reporte.titulo}\".",
            tipo = TipoNotificacion.NUEVO_COMENTARIO,
            idReporte = reporte.id
        )

        return generarYEnviar(notificacion, contexto)
    }

    // ==========================================================
    //  Lectura de la bandeja
    // ==========================================================

    /** Notificaciones de un usuario, de la mas reciente a la mas antigua. */
    suspend fun listarPorUsuario(uid: String): Recurso<List<Notificacion>> = try {
        val consulta = coleccion
            .whereEqualTo(Notificacion.CAMPO_UID_USUARIO, uid)
            .get()
            .await()

        val notificaciones = consulta.documents
            .map { Notificacion.desde(it) }
            .filter { it.estado != EstadoNotificacion.DESCARTADA }
            .sortedByDescending { it.fecha }

        Recurso.Exito(notificaciones)
    } catch (e: Exception) {
        Recurso.Error(mensajeDeError(e), e)
    }

    /** Cantidad de notificaciones sin leer, para el indicador de la barra. */
    suspend fun contarNoLeidas(uid: String): Recurso<Int> = try {
        val consulta = coleccion
            .whereEqualTo(Notificacion.CAMPO_UID_USUARIO, uid)
            .whereEqualTo(Notificacion.CAMPO_ESTADO, EstadoNotificacion.ENVIADA.valor)
            .get()
            .await()

        Recurso.Exito(consulta.size())
    } catch (e: Exception) {
        Recurso.Error(mensajeDeError(e), e)
    }

    /** Escucha la bandeja en tiempo real. */
    fun observarPorUsuario(
        uid: String,
        alCambiar: (List<Notificacion>) -> Unit
    ): ListenerRegistration =
        coleccion
            .whereEqualTo(Notificacion.CAMPO_UID_USUARIO, uid)
            .addSnapshotListener { instantanea, error ->
                if (error != null || instantanea == null) return@addSnapshotListener

                val notificaciones = instantanea.documents
                    .map { Notificacion.desde(it) }
                    .filter { it.estado != EstadoNotificacion.DESCARTADA }
                    .sortedByDescending { it.fecha }

                alCambiar(notificaciones)
            }

    // ==========================================================
    //  Transiciones de estado
    // ==========================================================

    /** marcarLeida(): ENVIADA --> LEIDA. */
    suspend fun marcarLeida(notificacion: Notificacion): Recurso<Unit> =
        aplicarTransicion(notificacion, EstadoNotificacion.LEIDA)

    /** reintentar(): FALLIDA --> PENDIENTE. */
    suspend fun reintentar(
        notificacion: Notificacion,
        contexto: Context? = null
    ): Recurso<Unit> = try {
        if (notificacion.intentos >= Notificacion.MAX_INTENTOS) {
            descartar(notificacion)
        } else {
            when (val resultado = aplicarTransicion(notificacion, EstadoNotificacion.PENDIENTE)) {
                is Recurso.Exito -> {
                    val entregada = runCatching {
                        contexto?.let {
                            mostrarEnDispositivo(it, notificacion, notificacion.id)
                        }
                        true
                    }.getOrDefault(false)

                    coleccion.document(notificacion.id).update(
                        mapOf(
                            Notificacion.CAMPO_ESTADO to
                                    if (entregada) EstadoNotificacion.ENVIADA.valor
                                    else EstadoNotificacion.FALLIDA.valor,
                            Notificacion.CAMPO_INTENTOS to notificacion.intentos + 1
                        )
                    ).await()

                    Recurso.Exito(Unit)
                }
                else -> resultado
            }
        }
    } catch (e: Exception) {
        Recurso.Error(mensajeDeError(e), e)
    }

    /** descartar(): FALLIDA --> DESCARTADA. */
    suspend fun descartar(notificacion: Notificacion): Recurso<Unit> =
        aplicarTransicion(notificacion, EstadoNotificacion.DESCARTADA)

    /** Marca como leidas todas las notificaciones enviadas de un usuario. */
    suspend fun marcarTodasLeidas(uid: String): Recurso<Int> = try {
        val consulta = coleccion
            .whereEqualTo(Notificacion.CAMPO_UID_USUARIO, uid)
            .whereEqualTo(Notificacion.CAMPO_ESTADO, EstadoNotificacion.ENVIADA.valor)
            .get()
            .await()

        val lote = db.batch()
        consulta.documents.forEach { doc ->
            lote.update(
                doc.reference,
                mapOf(
                    Notificacion.CAMPO_ESTADO to EstadoNotificacion.LEIDA.valor,
                    Notificacion.CAMPO_LEIDA to true
                )
            )
        }
        lote.commit().await()

        Recurso.Exito(consulta.size())
    } catch (e: Exception) {
        Recurso.Error(mensajeDeError(e), e)
    }

    private suspend fun aplicarTransicion(
        notificacion: Notificacion,
        destino: EstadoNotificacion
    ): Recurso<Unit> = try {
        if (!notificacion.estado.puedeTransicionarA(destino)) {
            Recurso.Error(
                "No se puede pasar de ${notificacion.estado.valor} a ${destino.valor}"
            )
        } else {
            coleccion.document(notificacion.id).update(
                mapOf(
                    Notificacion.CAMPO_ESTADO to destino.valor,
                    Notificacion.CAMPO_LEIDA to destino.fueLeida
                )
            ).await()
            Recurso.Exito(Unit)
        }
    } catch (e: Exception) {
        Recurso.Error(mensajeDeError(e), e)
    }

    // ==========================================================
    //  Entrega en el dispositivo
    // ==========================================================

    /**
     * Muestra el aviso usando el canal creado en AqualertApp.
     *
     * En Android 13 y superiores se requiere el permiso POST_NOTIFICATIONS;
     * si no esta concedido, la notificacion queda registrada en la bandeja
     * de la aplicacion pero no se muestra como aviso del sistema.
     */
    private fun mostrarEnDispositivo(
        contexto: Context,
        notificacion: Notificacion,
        idDocumento: String
    ) {
        val permitido = ActivityCompat.checkSelfPermission(
            contexto,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED

        if (!permitido) return

        val intent = contexto.packageManager
            .getLaunchIntentForPackage(contexto.packageName)
            ?.apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(Constantes.EXTRA_REPORTE_ID, notificacion.idReporte)
            }

        val pendiente = PendingIntent.getActivity(
            contexto,
            idDocumento.hashCode(),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val aviso = NotificationCompat.Builder(contexto, AqualertApp.CANAL_ALERTAS_ID)
            .setSmallIcon(R.drawable.ic_alerta)
            .setContentTitle(notificacion.titulo)
            .setContentText(notificacion.mensaje)
            .setStyle(NotificationCompat.BigTextStyle().bigText(notificacion.mensaje))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendiente)
            .build()

        NotificationManagerCompat.from(contexto)
            .notify(idDocumento.hashCode(), aviso)
    }

    private fun mensajeDeError(e: Exception): String {
        val texto = e.message.orEmpty()
        return when {
            texto.contains("PERMISSION_DENIED", true) ->
                "No tienes permisos sobre las notificaciones"
            texto.contains("UNAVAILABLE", true) || texto.contains("network", true) ->
                "Sin conexion a internet. Las notificaciones no se pudieron sincronizar."
            else -> "No se pudo procesar la notificacion."
        }
    }
}
