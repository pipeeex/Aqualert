package com.aqualer.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.PersistentCacheSettings

/**
 * Clase Application de Aqualert.
 *
 * Se encarga de la inicializacion global:
 *  - Cache offline de Firestore (soporta el "Gestor de Sincronizacion Offline"
 *    del diagrama de componentes y el RNF004 de rendimiento de carga).
 *  - Canal de notificaciones requerido por la clase Notificacion.
 */
class AqualertApp : Application() {

    override fun onCreate() {
        super.onCreate()
        configurarFirestore()
        crearCanalNotificaciones()
    }

    /**
     * Habilita la persistencia local de Firestore.
     * Permite consultar los reportes ya descargados aunque no haya conexion
     * y acelera la carga inicial de las pantallas principales.
     */
    private fun configurarFirestore() {
        val ajustes = FirebaseFirestoreSettings.Builder()
            .setLocalCacheSettings(
                PersistentCacheSettings.newBuilder()
                    .setSizeBytes(CACHE_SIZE_BYTES)
                    .build()
            )
            .build()
        FirebaseFirestore.getInstance().firestoreSettings = ajustes
    }

    /**
     * Crea el canal de notificaciones usado por el Servicio de Notificaciones
     * para entregar alertas y cambios de estado de reportes.
     */
    private fun crearCanalNotificaciones() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val canal = NotificationChannel(
                CANAL_ALERTAS_ID,
                getString(R.string.notif_canal_nombre),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = getString(R.string.notif_canal_desc)
                enableLights(true)
                enableVibration(true)
            }
            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(canal)
        }
    }

    companion object {
        /** Identificador del canal de notificaciones de alertas. */
        const val CANAL_ALERTAS_ID = "aqualert_alertas"

        /** 100 MB de cache local para el modo offline. */
        private const val CACHE_SIZE_BYTES = 100L * 1024 * 1024
    }
}