package com.aqualer.app.data.service

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import com.aqualer.app.data.estado.EstadoClasificadorIA
import com.aqualer.app.data.estado.TipoContaminacion
import com.aqualer.app.data.model.EtiquetaIA
import com.aqualer.app.data.model.ResultadoClasificacion
import com.aqualer.app.util.Constantes
import com.aqualer.app.util.FotosDemo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/**
 * Implementacion real de ClasificadorIA sobre TensorFlow Lite.
 *
 * Sustituye a ClasificadorIAService (la version simulada) sin que el
 * resto del sistema cambie: respeta la misma interfaz y la misma
 * maquina de estados del diagrama CLASIFICADOR IA. Esa sustituibilidad
 * es lo que el RNF006 pedia al exigir una arquitectura modular capaz
 * de incorporar inteligencia artificial.
 *
 * El modelo se entreno con Teachable Machine sobre un conjunto de
 * imagenes reunido para este proyecto, y distingue cinco clases:
 *
 *   vertimiento       -> contaminacion por descarga de aguas residuales
 *   espumas           -> contaminacion por espumas y detergentes
 *   residuos_solidos  -> basuras y escombros en el cauce
 *   canal_limpio      -> es un canal, pero no se observa contaminacion
 *   no_es_agua        -> la fotografia no corresponde a un cuerpo de agua
 *
 * Las dos ultimas son las que dan valor real al componente: permiten
 * que la plataforma descarte evidencia que no sustenta un reporte, en
 * lugar de aceptar cualquier fotografia.
 */
class ClasificadorIATensorFlow(
    contexto: Context,
    override val umbralConfianza: Float = Constantes.UMBRAL_CONFIANZA_IA
) : ClasificadorIA {

    /** Solo el contexto de aplicacion: retener una Activity la filtraria. */
    private val contexto: Context = contexto.applicationContext

    override val modelo: String = NOMBRE_MODELO
    override val version: String = Constantes.VERSION_MODELO_IA

    private var estadoActual: EstadoClasificadorIA = EstadoClasificadorIA.INACTIVO
    override val estado: EstadoClasificadorIA get() = estadoActual

    // ==========================================================
    //  Clases del modelo
    // ==========================================================

    /**
     * Correspondencia entre las clases del modelo y el dominio.
     *
     * El orden y los nombres provienen de labels.txt, el archivo que
     * exporta Teachable Machine. Varias categorias del formulario se
     * fusionaron para entrenar: residuos_solidos cubre BASURAS y
     * ESCOMBROS, y vertimiento cubre VERTIMIENTO y AGUAS_RESIDUALES,
     * porque visualmente son indistinguibles y separarlas solo
     * producia confusion en la matriz de resultados.
     *
     * MAL_OLOR y OTRO no son clasificables por imagen y por eso no
     * forman parte del modelo: quedan como declaracion del ciudadano.
     *
     * tipo nulo significa "no es un tipo de contaminacion".
     */
    private enum class ClaseModelo(
        val etiqueta: String,
        val tipo: TipoContaminacion?
    ) {
        CANAL_LIMPIO("canal_limpio", null),
        ESPUMAS("espumas", TipoContaminacion.ESPUMAS),
        NO_ES_AGUA("no_es_agua", null),
        RESIDUOS_SOLIDOS("residuos_solidos", TipoContaminacion.BASURAS),
        VERTIMIENTO("vertimiento", TipoContaminacion.VERTIMIENTO);

        companion object {
            fun desde(etiqueta: String): ClaseModelo? =
                entries.firstOrNull { it.etiqueta.equals(etiqueta, ignoreCase = true) }
        }
    }

    // ==========================================================
    //  clasificarImagen()
    // ==========================================================

    override suspend fun clasificarImagen(
        referenciaImagen: String,
        tipoDeclarado: TipoContaminacion
    ): ResultadoClasificacion {

        // INACTIVO --clasificarImagen()--> PROCESANDO
        if (!transicionar(EstadoClasificadorIA.PROCESANDO)) {
            return ResultadoClasificacion.conError(
                "El clasificador esta ocupado procesando otra imagen"
            )
        }

        return try {
            if (referenciaImagen.isBlank()) {
                return registrarError("No se recibio ninguna imagen para analizar")
            }

            val motor = motor()
                ?: return registrarError("El modelo de clasificacion no esta disponible")

            val bitmap = obtenerBitmap(referenciaImagen)
                ?: return registrarError("No se pudo leer la imagen para analizarla")

            val predicciones = withContext(Dispatchers.Default) {
                motor.clasificar(bitmap)
            }
            bitmap.recycle()

            if (predicciones.isEmpty()) {
                return registrarError("El modelo no devolvio ninguna prediccion")
            }

            val resultado = interpretar(predicciones, tipoDeclarado)

            transicionar(resultado.estado)

            Log.d(
                TAG,
                "Clasificacion: ${resultado.clasificacion} " +
                        "(${resultado.confianzaPorcentaje}%) -> ${resultado.estado.valor}"
            )

            liberar()
            resultado

        } catch (e: Exception) {
            registrarError(e.message ?: "Fallo en el procesamiento de la imagen")
        }
    }

    // ==========================================================
    //  Interpretacion del resultado
    // ==========================================================

    /**
     * Traduce las probabilidades del modelo a una decision del dominio.
     *
     * Las reglas siguen el diagrama de estados CLASIFICADOR IA:
     *
     *   no_es_agua con confianza alta  --> ERROR
     *       La evidencia no sustenta un reporte sobre un canal. El
     *       Reporte transita EN_REVISION --> RECHAZADO.
     *
     *   canal_limpio                   --> DUDOSO
     *       Es un canal, pero el modelo no observa contaminacion. No
     *       se rechaza de plano porque una contaminacion incipiente
     *       puede no ser visible: se publica marcado para revision
     *       humana, que es mas prudente que descartar un reporte
     *       legitimo.
     *
     *   clase de contaminacion         --> EXITOSO o DUDOSO
     *       Segun si supera el umbral de confianza.
     */
    private fun interpretar(
        predicciones: List<Prediccion>,
        tipoDeclarado: TipoContaminacion
    ): ResultadoClasificacion {

        val etiquetas = predicciones.map { prediccion ->
            EtiquetaIA(
                nombre = nombreLegible(prediccion.etiqueta),
                confianza = redondear(prediccion.confianza)
            )
        }

        val principal = predicciones.first()
        val clase = ClaseModelo.desde(principal.etiqueta)
        val confianza = redondear(principal.confianza)

        // Etiqueta desconocida: labels.txt no concuerda con el codigo
        if (clase == null) {
            return registrarError(
                "El modelo devolvio una clase no reconocida: ${principal.etiqueta}"
            )
        }

        // La fotografia no muestra un cuerpo de agua
        if (clase == ClaseModelo.NO_ES_AGUA && confianza >= umbralConfianza) {
            transicionar(EstadoClasificadorIA.ERROR)
            Log.w(TAG, "Evidencia descartada: no es un cuerpo de agua ($confianza)")
            liberar()
            return ResultadoClasificacion.conError(
                "La fotografia no parece corresponder a un canal o cuerpo de agua. " +
                        "Toma la evidencia enfocando el cauce afectado."
            ).copy(etiquetas = etiquetas, confianza = confianza)
        }

        // No se observa contaminacion, o el modelo no esta seguro de que
        // la imagen sea valida: revision humana
        if (clase.tipo == null) {
            return ResultadoClasificacion(
                clasificacion = nombreLegible(principal.etiqueta),
                confianza = confianza,
                etiquetas = etiquetas,
                estado = EstadoClasificadorIA.DUDOSO
            )
        }

        // Discrepancia entre lo declarado y lo detectado: no invalida el
        // reporte, pero queda registrada para el administrador
        if (clase.tipo != tipoDeclarado) {
            Log.i(
                TAG,
                "Discrepancia: el ciudadano declaro ${tipoDeclarado.valor}, " +
                        "el modelo detecto ${clase.tipo.valor}"
            )
        }

        return ResultadoClasificacion.evaluar(
            clasificacion = clase.tipo.valor,
            confianza = confianza,
            etiquetas = etiquetas
        )
    }

    /** Nombre de la clase tal como debe verse en la interfaz. */
    private fun nombreLegible(etiqueta: String): String =
        ClaseModelo.desde(etiqueta)?.let { clase ->
            clase.tipo?.valor ?: clase.etiqueta.replace('_', ' ')
        } ?: etiqueta

    // ==========================================================
    //  Obtencion de la imagen
    // ==========================================================

    /**
     * Resuelve la referencia almacenada en el reporte a un bitmap.
     *
     * El campo imagen_url puede contener una URL de Cloudinary o un
     * identificador de las imagenes de muestra usadas antes de
     * integrar la camara; ambos casos se resuelven aqui para que los
     * reportes antiguos tambien puedan analizarse.
     */
    private suspend fun obtenerBitmap(referencia: String): Bitmap? =
        withContext(Dispatchers.IO) {
            when {
                FotosDemo.esSimulada(referencia) -> BitmapFactory.decodeResource(
                    contexto.resources,
                    FotosDemo.drawableDesde(referencia)
                )

                referencia.startsWith("http") -> descargar(miniatura(referencia))

                else -> null
            }
        }

    /**
     * Pide a Cloudinary la imagen ya recortada al tamano de entrada
     * del modelo.
     *
     * La transformacion se hace del lado del servidor: se descargan
     * unos 12 KB en lugar de los 300 KB del original, y el recorte
     * cuadrado al centro reproduce el preprocesamiento aplicado al
     * preparar el conjunto de entrenamiento.
     */
    private fun miniatura(url: String): String {
        val marca = "/image/upload/"
        if (!url.contains(marca)) return url
        return url.replace(marca, "${marca}w_224,h_224,c_fill,q_auto/")
    }

    private fun descargar(url: String): Bitmap? {
        var conexion: HttpURLConnection? = null

        return try {
            conexion = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = TIEMPO_CONEXION_MS
                readTimeout = TIEMPO_LECTURA_MS
                doInput = true
            }
            conexion.connect()

            if (conexion.responseCode != HttpURLConnection.HTTP_OK) {
                Log.e(TAG, "La descarga de la evidencia fallo: ${conexion.responseCode}")
                null
            } else {
                conexion.inputStream.use { flujo -> BitmapFactory.decodeStream(flujo) }
            }
        } catch (e: Exception) {
            Log.e(TAG, "No se pudo descargar la evidencia", e)
            null
        } finally {
            conexion?.disconnect()
        }
    }

    // ==========================================================
    //  obtenerEtiquetas() y calcularConfianza()
    // ==========================================================

    override fun obtenerEtiquetas(resultado: ResultadoClasificacion): List<EtiquetaIA> =
        resultado.etiquetas.sortedByDescending { it.confianza }

    override fun calcularConfianza(resultado: ResultadoClasificacion): Float =
        resultado.etiquetas.maxOfOrNull { it.confianza } ?: resultado.confianza

    // ==========================================================
    //  Maquina de estados
    // ==========================================================

    private fun transicionar(destino: EstadoClasificadorIA): Boolean {
        if (!estadoActual.puedeTransicionarA(destino)) {
            Log.w(TAG, "Transicion invalida: ${estadoActual.valor} -> ${destino.valor}")
            return false
        }
        estadoActual = destino
        return true
    }

    private fun liberar() {
        transicionar(EstadoClasificadorIA.INACTIVO)
    }

    private fun registrarError(mensaje: String): ResultadoClasificacion {
        transicionar(EstadoClasificadorIA.ERROR)
        Log.e(TAG, "Fallo del clasificador: $mensaje")
        liberar()
        return ResultadoClasificacion.conError(mensaje)
    }

    private fun redondear(valor: Float): Float = Math.round(valor * 100) / 100f

    // ==========================================================
    //  Motor compartido
    // ==========================================================

    /**
     * Devuelve el motor de inferencia, cargandolo la primera vez.
     *
     * El modelo se mantiene cargado para todo el proceso: abrirlo
     * cuesta cientos de milisegundos y no tiene sentido repetirlo en
     * cada reporte.
     */
    private fun motor(): MotorClasificacion? = synchronized(CERROJO) {
        motorCompartido?.takeIf { it.estaListo }?.let { return it }

        val nuevo = MotorClasificacion(contexto)
        return if (nuevo.cargar()) {
            motorCompartido = nuevo
            nuevo
        } else {
            null
        }
    }

    private companion object {
        const val TAG = "AqualertIA"
        const val NOMBRE_MODELO = "aqualert-tm-mobilenet-v1"

        const val TIEMPO_CONEXION_MS = 15_000
        const val TIEMPO_LECTURA_MS = 20_000

        val CERROJO = Any()
        var motorCompartido: MotorClasificacion? = null
    }
}