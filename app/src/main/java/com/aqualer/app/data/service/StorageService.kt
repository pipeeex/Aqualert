package com.aqualer.app.data.service

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.aqualer.app.util.Constantes
import com.aqualer.app.util.Recurso
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

/**
 * Servicio de almacenamiento de imagenes.
 *
 * Corresponde al nodo "Servidor de Almacenamiento / Imagenes de reportes"
 * del diagrama de distribucion y a los pasos del diagrama de colaboracion:
 *   1.10 guardar imagen -> Storage
 *   1.11 URL imagen
 *
 * Antes de subir comprime la foto, porque las camaras actuales producen
 * archivos de varios MB que harian incumplir el RNF004 (tiempo de carga).
 */
class StorageService(
    private val storage: FirebaseStorage = FirebaseStorage.getInstance()
) {

    /**
     * Sube la evidencia fotografica de un reporte y devuelve su URL publica.
     *
     * @param carpeta subcarpeta destino dentro del bucket
     */
    suspend fun subirImagen(
        context: Context,
        uri: Uri,
        carpeta: String = Constantes.STORAGE_REPORTES
    ): Recurso<String> = try {
        val bytes = comprimirImagen(context, uri)
            ?: return Recurso.Error("No se pudo procesar la imagen seleccionada")

        val nombre = "${carpeta}/${System.currentTimeMillis()}.jpg"
        val referencia = storage.reference.child(nombre)

        referencia.putBytes(bytes).await()
        val url = referencia.downloadUrl.await().toString()

        Recurso.Exito(url)
    } catch (e: Exception) {
        Recurso.Error(mensajeDeError(e), e)
    }

    /** Elimina una imagen a partir de su URL de descarga. */
    suspend fun eliminarImagen(url: String): Recurso<Unit> = try {
        if (url.isBlank()) {
            Recurso.Exito(Unit)
        } else {
            storage.getReferenceFromUrl(url).delete().await()
            Recurso.Exito(Unit)
        }
    } catch (e: Exception) {
        Recurso.Error(mensajeDeError(e), e)
    }

    /**
     * Redimensiona y comprime la imagen conservando su orientacion original.
     * Devuelve null si el archivo no se puede leer.
     */
    private suspend fun comprimirImagen(
        context: Context,
        uri: Uri
    ): ByteArray? = withContext(Dispatchers.IO) {
        try {
            // Primera pasada: solo leer dimensiones, sin cargar el bitmap completo
            val opcionesMedida = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, opcionesMedida)
            }

            val escala = calcularEscala(
                opcionesMedida.outWidth,
                opcionesMedida.outHeight
            )

            // Segunda pasada: cargar ya reducido
            val opciones = BitmapFactory.Options().apply { inSampleSize = escala }
            val bitmap = context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, opciones)
            } ?: return@withContext null

            val salida = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, CALIDAD_JPEG, salida)
            bitmap.recycle()

            salida.toByteArray()
        } catch (e: Exception) {
            null
        }
    }

    /** Calcula el factor de reduccion para no superar el lado maximo. */
    private fun calcularEscala(ancho: Int, alto: Int): Int {
        var escala = 1
        var mayor = maxOf(ancho, alto)
        while (mayor / 2 >= LADO_MAXIMO_PX) {
            mayor /= 2
            escala *= 2
        }
        return escala
    }

    private fun mensajeDeError(e: Exception): String {
        val texto = e.message.orEmpty()
        return when {
            texto.contains("network", true) ->
                "Sin conexion a internet. La imagen no se pudo subir."
            texto.contains("PERMISSION_DENIED", true) || texto.contains("not authorized", true) ->
                "No tienes permisos para subir imagenes"
            texto.contains("quota", true) ->
                "Se agoto el espacio de almacenamiento disponible"
            else -> "No se pudo subir la imagen. Intenta nuevamente."
        }
    }

    companion object {
        /** Lado maximo de la imagen subida, en pixeles. */
        private const val LADO_MAXIMO_PX = 1280

        /** Calidad de compresion JPEG. */
        private const val CALIDAD_JPEG = 80
    }
}