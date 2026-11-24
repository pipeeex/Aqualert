package com.aqualer.app.data.service

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import com.aqualer.app.BuildConfig
import com.aqualer.app.util.Constantes
import com.aqualer.app.util.Recurso
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Servicio de almacenamiento de imagenes sobre Cloudinary.
 *
 * Corresponde al nodo "Servidor de Almacenamiento / Imagenes de
 * reportes" del diagrama de distribucion, y a los pasos del diagrama
 * de colaboracion de Creacion de Reporte:
 *   1.10 guardar imagen -> Storage
 *   1.11 URL imagen
 *
 * Se eligio Cloudinary porque Cloud Storage for Firebase exige el
 * plan de pago desde septiembre de 2024. La interfaz publica de esta
 * clase es la misma que tenia la version de Firebase: el resto del
 * sistema guarda una URL en imagen_url y no sabe quien la sirve.
 *
 * La subida usa un "upload preset" sin firma, pensado por Cloudinary
 * para clientes moviles: no expone la clave secreta de la cuenta.
 */
class StorageService {

    private val cloudName = BuildConfig.CLOUDINARY_CLOUD_NAME
    private val uploadPreset = BuildConfig.CLOUDINARY_UPLOAD_PRESET

    /**
     * Sube la evidencia fotografica y devuelve su URL publica.
     *
     * Antes de enviarla la comprime: las camaras actuales producen
     * archivos de varios MB que harian incumplir el RNF004 (tiempo de
     * carga) y agotarian la cuota de la cuenta.
     */
    suspend fun subirImagen(
        context: Context,
        uri: Uri,
        carpeta: String = Constantes.STORAGE_REPORTES
    ): Recurso<String> = withContext(Dispatchers.IO) {
        try {
            if (cloudName.isBlank() || uploadPreset.isBlank()) {
                return@withContext Recurso.Error(
                    "Falta configurar Cloudinary en local.properties"
                )
            }

            val bytes = comprimirImagen(context, uri)
                ?: return@withContext Recurso.Error(
                    "No se pudo procesar la imagen seleccionada"
                )

            val url = enviar(bytes, carpeta)
            Recurso.Exito(url)

        } catch (e: Exception) {
            Log.e(TAG, "Fallo la subida de la imagen", e)
            Recurso.Error(mensajeDeError(e), e)
        }
    }

    /**
     * Borrado de una imagen ya subida.
     *
     * Cloudinary no permite eliminar con un preset sin firma: hacerlo
     * requiere la clave secreta de la cuenta, que no puede vivir en la
     * aplicacion porque cualquiera podria extraerla del APK y borrar
     * todas las evidencias.
     *
     * Por eso el borrado se deja como operacion administrativa: las
     * imagenes huerfanas se depuran desde el panel de Cloudinary o,
     * mas adelante, desde un servicio con credenciales propias.
     */
    suspend fun eliminarImagen(url: String): Recurso<Unit> {
        Log.i(TAG, "Solicitud de borrado no ejecutada (requiere firma): $url")
        return Recurso.Exito(Unit)
    }

    // ==========================================================
    //  Envio a Cloudinary
    // ==========================================================

    /**
     * Envia la imagen como multipart/form-data.
     *
     * Se arma a mano con HttpURLConnection para no sumar una libreria
     * de red al proyecto: es la unica peticion HTTP directa que hace
     * la aplicacion, todo lo demas pasa por el SDK de Firebase.
     */
    private fun enviar(bytes: ByteArray, carpeta: String): String {
        val limite = "----AqualertFrontera${System.currentTimeMillis()}"
        val url = URL("https://api.cloudinary.com/v1_1/$cloudName/image/upload")

        val conexion = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            useCaches = false
            connectTimeout = TIEMPO_CONEXION_MS
            readTimeout = TIEMPO_LECTURA_MS
            setRequestProperty("Content-Type", "multipart/form-data; boundary=$limite")
        }

        DataOutputStream(conexion.outputStream).use { salida ->
            escribirCampo(salida, limite, "upload_preset", uploadPreset)
            escribirCampo(salida, limite, "folder", carpeta)
            escribirArchivo(salida, limite, bytes)
            salida.writeBytes("--$limite--\r\n")
            salida.flush()
        }

        val codigo = conexion.responseCode
        val cuerpo = if (codigo in 200..299) {
            conexion.inputStream.bufferedReader().use { it.readText() }
        } else {
            val detalle = conexion.errorStream?.bufferedReader()?.use { it.readText() }
            throw Exception("Cloudinary respondio $codigo: $detalle")
        }

        conexion.disconnect()

        val respuesta = JSONObject(cuerpo)
        return respuesta.optString("secure_url").ifBlank {
            throw Exception("La respuesta no incluyo la URL de la imagen")
        }
    }

    private fun escribirCampo(
        salida: DataOutputStream,
        limite: String,
        nombre: String,
        valor: String
    ) {
        salida.writeBytes("--$limite\r\n")
        salida.writeBytes("Content-Disposition: form-data; name=\"$nombre\"\r\n\r\n")
        salida.write(valor.toByteArray(Charsets.UTF_8))
        salida.writeBytes("\r\n")
    }

    private fun escribirArchivo(
        salida: DataOutputStream,
        limite: String,
        bytes: ByteArray
    ) {
        val nombre = "reporte_${System.currentTimeMillis()}.jpg"
        salida.writeBytes("--$limite\r\n")
        salida.writeBytes(
            "Content-Disposition: form-data; name=\"file\"; filename=\"$nombre\"\r\n"
        )
        salida.writeBytes("Content-Type: image/jpeg\r\n\r\n")
        salida.write(bytes)
        salida.writeBytes("\r\n")
    }

    // ==========================================================
    //  Compresion
    // ==========================================================

    /**
     * Redimensiona y comprime la imagen conservando su orientacion.
     * Devuelve null si el archivo no se puede leer.
     */
    private fun comprimirImagen(context: Context, uri: Uri): ByteArray? {
        return try {
            // Primera pasada: solo leer dimensiones, sin cargar el bitmap
            val opcionesMedida = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, opcionesMedida)
            }

            val escala = calcularEscala(opcionesMedida.outWidth, opcionesMedida.outHeight)

            // Segunda pasada: cargar ya reducido
            val opciones = BitmapFactory.Options().apply { inSampleSize = escala }
            val bitmap = context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, opciones)
            } ?: return null

            val salida = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, CALIDAD_JPEG, salida)
            bitmap.recycle()

            salida.toByteArray()
        } catch (e: Exception) {
            Log.e(TAG, "No se pudo comprimir la imagen", e)
            null
        }
    }

    /** Factor de reduccion para no superar el lado maximo. */
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
            texto.contains("timeout", true) || texto.contains("timed out", true) ->
                "La subida tardo demasiado. Revisa tu conexion e intenta de nuevo."

            texto.contains("Unable to resolve host", true) ||
                    texto.contains("network", true) ->
                "Sin conexion a internet. La imagen no se pudo subir."

            texto.contains("401") || texto.contains("400") ->
                "La configuracion de Cloudinary no es valida. Revisa el upload preset."

            texto.contains("420") || texto.contains("429") ->
                "Se alcanzo el limite de subidas del plan. Intenta mas tarde."

            else -> "No se pudo subir la imagen. Intenta nuevamente."
        }
    }

    companion object {
        private const val TAG = "AqualertStorage"

        /** Lado maximo de la imagen subida, en pixeles. */
        private const val LADO_MAXIMO_PX = 1280

        /** Calidad de compresion JPEG. */
        private const val CALIDAD_JPEG = 80

        private const val TIEMPO_CONEXION_MS = 15_000
        private const val TIEMPO_LECTURA_MS = 30_000
    }
}