package com.aqualer.app.data.service

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import com.aqualer.app.util.Constantes
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.FileUtil
import org.tensorflow.lite.support.common.ops.NormalizeOp
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.ResizeOp
import org.tensorflow.lite.support.image.ops.ResizeWithCropOrPadOp
import java.io.Closeable
import kotlin.math.min

/**
 * Una prediccion del modelo: la etiqueta y su probabilidad.
 */
data class Prediccion(
    val etiqueta: String,
    val confianza: Float
)

/**
 * Motor de inferencia local sobre TensorFlow Lite (RF005).
 *
 * El modelo fue entrenado con Teachable Machine sobre un conjunto de
 * imagenes reunido para este proyecto, y se ejecuta dentro del
 * dispositivo. Esa decision tiene tres consecuencias deseables:
 *
 *   - No requiere conexion para analizar la evidencia
 *   - No depende de un servicio de pago por peticion
 *   - La fotografia no se envia a un tercero para clasificarse
 *
 * Esta clase solo traduce pixeles a probabilidades. La interpretacion
 * de esas probabilidades en terminos del dominio (tipo de
 * contaminacion, nivel de riesgo, rechazo de la imagen) corresponde a
 * la capa de servicios.
 */
class MotorClasificacion(private val contexto: Context) : Closeable {

    private var interprete: Interpreter? = null
    private var etiquetas: List<String> = emptyList()

    /** Cantidad de clases que el modelo distingue. */
    val cantidadClases: Int get() = etiquetas.size

    val estaListo: Boolean get() = interprete != null

    // ==========================================================
    //  Ciclo de vida
    // ==========================================================

    /**
     * Carga el modelo y sus etiquetas desde los assets.
     *
     * Es una operacion costosa: se hace una sola vez y el interprete
     * queda en memoria mientras la pantalla lo necesite.
     *
     * @return true si el modelo quedo disponible.
     */
    fun cargar(): Boolean {
        if (interprete != null) return true

        return try {
            val modelo = FileUtil.loadMappedFile(contexto, ARCHIVO_MODELO)

            val opciones = Interpreter.Options().apply {
                // Reparte la inferencia entre varios nucleos: en un
                // telefono de gama media baja de ~400 ms a ~150 ms
                numThreads = HILOS
            }

            interprete = Interpreter(modelo, opciones)
            etiquetas = leerEtiquetas()

            Log.d(TAG, "Modelo cargado con ${etiquetas.size} clases: $etiquetas")
            true
        } catch (e: Exception) {
            Log.e(TAG, "No se pudo cargar el modelo", e)
            interprete = null
            false
        }
    }

    override fun close() {
        interprete?.close()
        interprete = null
    }

    /**
     * Teachable Machine exporta labels.txt con el indice delante de
     * cada nombre ("0 canal_limpio"). Aqui se descarta el indice,
     * porque el orden de las lineas ya es el orden de salida del
     * modelo.
     */
    private fun leerEtiquetas(): List<String> =
        FileUtil.loadLabels(contexto, ARCHIVO_ETIQUETAS)
            .map { linea -> linea.trim().substringAfter(' ', linea).trim() }
            .filter { it.isNotBlank() }

    // ==========================================================
    //  Inferencia
    // ==========================================================

    /**
     * Clasifica una imagen y devuelve todas las clases ordenadas de
     * mayor a menor probabilidad.
     *
     * Se devuelve la lista completa, no solo la ganadora, porque la
     * diferencia entre la primera y la segunda es lo que permite
     * saber si el modelo esta seguro o esta dudando entre dos
     * categorias parecidas.
     *
     * Debe llamarse fuera del hilo principal.
     */
    fun clasificar(bitmap: Bitmap): List<Prediccion> {
        val motor = interprete ?: return emptyList()
        if (etiquetas.isEmpty()) return emptyList()

        val entrada = preprocesar(bitmap)
        val salida = Array(1) { FloatArray(etiquetas.size) }

        return try {
            motor.run(entrada.buffer, salida)

            etiquetas.mapIndexed { indice, nombre ->
                Prediccion(nombre, salida[0][indice])
            }.sortedByDescending { it.confianza }
        } catch (e: Exception) {
            Log.e(TAG, "Fallo la inferencia", e)
            emptyList()
        }
    }

    /**
     * Adapta la fotografia al formato que espera el modelo.
     *
     * El preprocesamiento debe reproducir exactamente el que se aplico
     * al preparar el conjunto de entrenamiento; si difiere, el modelo
     * recibe imagenes distintas de las que aprendio y la precision
     * cae sin ninguna senal de error.
     *
     *   1. Recorte cuadrado al centro, no deformacion: un canal
     *      estirado deja de parecerse a un canal
     *   2. Escalado a 224x224, la entrada del modelo
     *   3. Normalizacion al rango [-1, 1], que es la convencion de
     *      los modelos de Teachable Machine
     */
    private fun preprocesar(bitmap: Bitmap): TensorImage {
        val lado = min(bitmap.width, bitmap.height)

        val procesador = ImageProcessor.Builder()
            .add(ResizeWithCropOrPadOp(lado, lado))
            .add(ResizeOp(LADO_ENTRADA, LADO_ENTRADA, ResizeOp.ResizeMethod.BILINEAR))
            .add(NormalizeOp(MEDIA, DESVIACION))
            .build()

        // ARGB_8888 es obligatorio: TensorImage no puede leer un
        // bitmap con asignacion en hardware
        val compatible = if (bitmap.config == Bitmap.Config.ARGB_8888) bitmap
        else bitmap.copy(Bitmap.Config.ARGB_8888, false)

        return procesador.process(TensorImage.fromBitmap(compatible))
    }

    // ==========================================================
    //  Lectura de la imagen
    // ==========================================================

    /**
     * Abre la fotografia que el usuario capturo o eligio.
     *
     * ImageDecoder respeta la orientacion EXIF, de modo que una foto
     * tomada en vertical no llega girada al modelo.
     */
    fun bitmapDesde(uri: Uri): Bitmap? = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val fuente = ImageDecoder.createSource(contexto.contentResolver, uri)
            ImageDecoder.decodeBitmap(fuente) { decodificador, _, _ ->
                decodificador.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                decodificador.isMutableRequired = false
            }
        } else {
            @Suppress("DEPRECATION")
            MediaStore.Images.Media.getBitmap(contexto.contentResolver, uri)
        }
    } catch (e: Exception) {
        Log.e(TAG, "No se pudo leer la imagen $uri", e)
        null
    }

    private companion object {
        const val TAG = "MotorClasificacion"

        const val ARCHIVO_MODELO = "model_unquant.tflite"
        const val ARCHIVO_ETIQUETAS = "labels.txt"

        /** Resolucion de entrada del modelo de Teachable Machine. */
        const val LADO_ENTRADA = 224

        /** Normalizacion a [-1, 1]: (pixel - 127.5) / 127.5 */
        const val MEDIA = 127.5f
        const val DESVIACION = 127.5f

        const val HILOS = 4
    }
}