package com.aqualer.app.data.service

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.aqualer.app.data.model.Ubicacion
import com.aqualer.app.util.Recurso
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.location.FusedLocationProviderClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * Servicio de georreferenciacion.
 *
 * Implementa obtenerUbicacion() del entregable 4 y el caso de uso
 * UC7 "Registrar ubicacion", incluido por UC5 "Crear reporte".
 *
 * En el diagrama de colaboracion de Creacion de Reporte corresponde a:
 *   1.2 obtener ubicacion -> Maps
 *   1.3 coordenadas
 *
 * El proveedor de ubicacion de Google Play Services es gratuito y no
 * requiere cuenta de facturacion; solo el SDK de mapas la exigia, y
 * por eso el mapa se dibuja con OpenStreetMap.
 */
class UbicacionService(private val contexto: Context) {

    private val proveedor = LocationServices.getFusedLocationProviderClient(contexto)

    /** Indica si el usuario ya concedio el permiso de ubicacion. */
    fun tienePermiso(): Boolean {
        val fina = ContextCompat.checkSelfPermission(
            contexto, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        val aproximada = ContextCompat.checkSelfPermission(
            contexto, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        return fina || aproximada
    }

    /**
     * Obtiene la ubicacion actual del dispositivo.
     *
     * Usa getCurrentLocation en lugar de lastLocation: la ultima
     * ubicacion conocida puede ser nula en un dispositivo recien
     * encendido, o corresponder a donde estuvo la persona hace horas.
     * Para situar un reporte de contaminacion eso no sirve.
     */
    suspend fun obtenerUbicacionActual(): Recurso<Ubicacion> {
        if (!tienePermiso()) {
            return Recurso.Error("Concede el permiso de ubicacion para registrar el reporte")
        }

        return try {
            @Suppress("MissingPermission")
            val posicion: Location? = proveedor.getCurrentLocation(
                Priority.PRIORITY_HIGH_ACCURACY,
                CancellationTokenSource().token
            ).await()

            if (posicion == null) {
                Recurso.Error("No se pudo obtener la ubicacion. Revisa que el GPS este activo.")
            } else {
                Recurso.Exito(
                    Ubicacion(
                        lat = posicion.latitude,
                        lng = posicion.longitude,
                        direccion = describir(posicion.latitude, posicion.longitude)
                    )
                )
            }
        } catch (e: SecurityException) {
            Recurso.Error("Falta el permiso de ubicacion", e)
        } catch (e: Exception) {
            Log.e(TAG, "Fallo al obtener la ubicacion", e)
            Recurso.Error("No se pudo obtener la ubicacion. Intenta de nuevo.", e)
        }
    }

    /**
     * Traduce unas coordenadas a una direccion legible.
     *
     * El Geocoder del sistema no siempre esta disponible ni acierta,
     * sobre todo en zonas de ronda hidrica sin nomenclatura. Cuando
     * falla se devuelve cadena vacia y la interfaz muestra entonces
     * las coordenadas, que es informacion igual de valida para
     * localizar el punto.
     */
    suspend fun describir(lat: Double, lng: Double): String = withContext(Dispatchers.IO) {
        try {
            if (!Geocoder.isPresent()) return@withContext ""

            val geocoder = Geocoder(contexto, Locale("es", "CO"))

            @Suppress("DEPRECATION")
            val resultados = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                // En Android 13+ la version sincrona esta obsoleta, pero
                // sigue funcionando y la asincrona exige callbacks que
                // complicarian esta llamada sin aportar nada aqui.
                geocoder.getFromLocation(lat, lng, 1)
            } else {
                geocoder.getFromLocation(lat, lng, 1)
            }

            val direccion = resultados?.firstOrNull() ?: return@withContext ""

            listOfNotNull(
                direccion.thoroughfare,          // via
                direccion.subLocality,           // barrio
                direccion.locality               // ciudad
            ).distinct().joinToString(", ").ifBlank { "" }

        } catch (e: Exception) {
            Log.w(TAG, "Geocoder no disponible", e)
            ""
        }
    }

    companion object {
        private const val TAG = "AqualertUbicacion"
    }
}