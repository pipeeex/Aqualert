package com.aqualer.app.data.model

import android.os.Parcelable
import com.aqualer.app.util.Constantes
import kotlinx.parcelize.Parcelize
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Objeto de valor que agrupa la georreferenciacion de un reporte.
 *
 * En el Modelo Estructural de Base de Datos corresponde al mapa anidado:
 *   ubicacion { lat (number), lng (number) }
 *
 * Se agrega "direccion" porque el diagrama de clases incluye el
 * atributo String direccion dentro de Reporte.
 */
@Parcelize
data class Ubicacion(
    val lat: Double = Constantes.BOGOTA_LAT,
    val lng: Double = Constantes.BOGOTA_LNG,
    val direccion: String = ""
) : Parcelable {

    /** Indica si las coordenadas son utilizables. */
    val esValida: Boolean
        get() = lat in -90.0..90.0 && lng in -180.0..180.0 &&
                !(lat == 0.0 && lng == 0.0)

    /** Texto corto de coordenadas para mostrar cuando no hay direccion. */
    fun comoTexto(): String =
        if (direccion.isNotBlank()) direccion
        else String.format("%.5f, %.5f", lat, lng)

    /**
     * Distancia en kilometros hasta otra ubicacion (formula de Haversine).
     * Se usa para el filtro de reportes cercanos.
     */
    fun distanciaKmHasta(otra: Ubicacion): Double {
        val radioTierraKm = 6371.0
        val dLat = Math.toRadians(otra.lat - lat)
        val dLng = Math.toRadians(otra.lng - lng)
        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat)) * cos(Math.toRadians(otra.lat)) *
                sin(dLng / 2) * sin(dLng / 2)
        return radioTierraKm * 2 * atan2(sqrt(a), sqrt(1 - a))
    }

    fun aMapa(): Map<String, Any?> = mapOf(
        CAMPO_LAT to lat,
        CAMPO_LNG to lng,
        CAMPO_DIRECCION to direccion
    )

    companion object {
        const val CAMPO_LAT = "lat"
        const val CAMPO_LNG = "lng"
        const val CAMPO_DIRECCION = "direccion"

        /** Reconstruye la ubicacion desde el mapa anidado de Firestore. */
        @Suppress("UNCHECKED_CAST")
        fun desde(mapa: Map<String, Any?>?): Ubicacion {
            if (mapa == null) return Ubicacion()
            return Ubicacion(
                lat = (mapa[CAMPO_LAT] as? Number)?.toDouble() ?: Constantes.BOGOTA_LAT,
                lng = (mapa[CAMPO_LNG] as? Number)?.toDouble() ?: Constantes.BOGOTA_LNG,
                direccion = mapa[CAMPO_DIRECCION] as? String ?: ""
            )
        }
    }
}