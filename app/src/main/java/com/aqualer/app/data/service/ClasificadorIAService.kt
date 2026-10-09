package com.aqualer.app.data.service

import android.util.Log
import com.aqualer.app.data.estado.EstadoClasificadorIA
import com.aqualer.app.data.estado.TipoContaminacion
import com.aqualer.app.data.model.EtiquetaIA
import com.aqualer.app.data.model.ResultadoClasificacion
import com.aqualer.app.util.Constantes
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.random.Random

/**
 * Contrato de la clase ClasificadorIA del diagrama de clases.
 *
 * Atributos del diagrama:
 *   String modelo, version
 *   Float umbral_confianza
 *
 * Metodos del diagrama:
 *   clasificarImagen(), obtenerEtiquetas(), calcularConfianza()
 *
 * Se define como interfaz para que el modelo real (TensorFlow Lite o
 * un servicio en la nube) pueda sustituir a la implementacion simulada
 * sin tocar el resto del sistema. Esto responde al RNF006, que pide
 * una arquitectura modular capaz de incorporar inteligencia artificial
 * mas adelante.
 */
interface ClasificadorIA {

    /** Nombre del modelo en uso. */
    val modelo: String

    /** Version del modelo. */
    val version: String

    /** Confianza minima para aceptar una clasificacion automatica. */
    val umbralConfianza: Float

    /** Estado actual segun el diagrama de estados CLASIFICADOR IA. */
    val estado: EstadoClasificadorIA

    /**
     * clasificarImagen(): analiza la evidencia y devuelve el resultado.
     *
     * @param referenciaImagen URL de Storage o identificador de la imagen
     * @param tipoDeclarado tipo que el ciudadano eligio en el formulario
     */
    suspend fun clasificarImagen(
        referenciaImagen: String,
        tipoDeclarado: TipoContaminacion
    ): ResultadoClasificacion

    /** obtenerEtiquetas(): etiquetas detectadas ordenadas por confianza. */
    fun obtenerEtiquetas(resultado: ResultadoClasificacion): List<EtiquetaIA>

    /** calcularConfianza(): confianza de la etiqueta principal. */
    fun calcularConfianza(resultado: ResultadoClasificacion): Float
}

/**
 * Servicio de Clasificacion de Imagenes (AISvc del diagrama de componentes).
 *
 * Implementacion simulada del ClasificadorIA. Reproduce fielmente la
 * maquina de estados del diagrama:
 *
 *   INACTIVO   --clasificarImagen()--> PROCESANDO
 *   PROCESANDO --confianza >= umbral--> EXITOSO
 *   PROCESANDO --confianza <  umbral--> DUDOSO
 *   PROCESANDO --fallo del modelo----> ERROR
 *   EXITOSO    --completar()---------> INACTIVO
 *   DUDOSO     --enviarARevision()---> INACTIVO
 *   ERROR      --registrarError()----> INACTIVO
 *
 * La confianza se deriva de forma determinista de la referencia de la
 * imagen, de modo que una misma evidencia siempre produce el mismo
 * resultado y las pruebas son repetibles. Cuando se integre el modelo
 * real solo se reemplaza esta clase por otra implementacion de la
 * interfaz ClasificadorIA.
 */
class ClasificadorIAService(
    override val umbralConfianza: Float = Constantes.UMBRAL_CONFIANZA_IA
) : ClasificadorIA {

    override val modelo: String = NOMBRE_MODELO
    override val version: String = Constantes.VERSION_MODELO_IA

    /** Estado interno de la maquina de estados. */
    private var estadoActual: EstadoClasificadorIA = EstadoClasificadorIA.INACTIVO

    override val estado: EstadoClasificadorIA get() = estadoActual

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
            // Sin evidencia no hay nada que analizar: fallo del modelo
            if (referenciaImagen.isBlank()) {
                return registrarError("No se recibio ninguna imagen para analizar")
            }

            // Simula el tiempo de inferencia del modelo
            delay(TIEMPO_INFERENCIA_MS)

            val etiquetas = generarEtiquetas(referenciaImagen, tipoDeclarado)
            val principal = etiquetas.first()

            val resultado = ResultadoClasificacion.evaluar(
                clasificacion = principal.nombre,
                confianza = principal.confianza,
                etiquetas = etiquetas
            )

            // PROCESANDO --> EXITOSO o DUDOSO segun el umbral
            transicionar(resultado.estado)

            Log.d(
                TAG,
                "Clasificacion: ${resultado.clasificacion} " +
                        "(${resultado.confianzaPorcentaje}%) -> ${resultado.estado.valor}"
            )

            // El clasificador vuelve a quedar disponible
            liberar()

            resultado

        } catch (e: Exception) {
            registrarError(e.message ?: "Fallo en el procesamiento de la imagen")
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
    //  Generacion simulada de etiquetas
    // ==========================================================

    /**
     * Produce las etiquetas que devolveria el modelo.
     *
     * La etiqueta principal coincide con el tipo declarado por el
     * ciudadano (es lo que haria un modelo entrenado con estas clases),
     * y las secundarias son las categorias que suelen confundirse con
     * ella. La confianza es determinista: depende del contenido de la
     * referencia de la imagen.
     */
    private fun generarEtiquetas(
        referenciaImagen: String,
        tipoDeclarado: TipoContaminacion
    ): List<EtiquetaIA> {

        val semilla = abs(referenciaImagen.hashCode())
        val aleatorio = Random(semilla)

        // Confianza principal entre 0.45 y 0.97
        val confianzaPrincipal = (0.45f + aleatorio.nextFloat() * 0.52f)
            .coerceIn(0.0f, 1.0f)

        val principal = EtiquetaIA(
            nombre = tipoDeclarado.valor,
            confianza = redondear(confianzaPrincipal)
        )

        // Etiquetas secundarias con confianza decreciente
        val secundarias = etiquetasRelacionadas(tipoDeclarado)
            .mapIndexed { indice, tipo ->
                val factor = 0.55f - (indice * 0.18f)
                EtiquetaIA(
                    nombre = tipo.valor,
                    confianza = redondear(
                        (confianzaPrincipal * factor).coerceIn(0.01f, 1.0f)
                    )
                )
            }

        return (listOf(principal) + secundarias).sortedByDescending { it.confianza }
    }

    /** Categorias que un modelo suele confundir con la declarada. */
    private fun etiquetasRelacionadas(tipo: TipoContaminacion): List<TipoContaminacion> =
        when (tipo) {
            TipoContaminacion.BASURAS ->
                listOf(TipoContaminacion.ESCOMBROS, TipoContaminacion.OTRO)

            TipoContaminacion.VERTIMIENTO ->
                listOf(TipoContaminacion.AGUAS_RESIDUALES, TipoContaminacion.ESPUMAS)

            TipoContaminacion.AGUAS_RESIDUALES ->
                listOf(TipoContaminacion.VERTIMIENTO, TipoContaminacion.MAL_OLOR)

            TipoContaminacion.ESPUMAS ->
                listOf(TipoContaminacion.VERTIMIENTO, TipoContaminacion.AGUAS_RESIDUALES)

            TipoContaminacion.ESCOMBROS ->
                listOf(TipoContaminacion.BASURAS, TipoContaminacion.OTRO)

            TipoContaminacion.MAL_OLOR ->
                listOf(TipoContaminacion.AGUAS_RESIDUALES, TipoContaminacion.VERTIMIENTO)

            TipoContaminacion.OTRO ->
                listOf(TipoContaminacion.BASURAS, TipoContaminacion.VERTIMIENTO)
        }

    private fun redondear(valor: Float): Float =
        (Math.round(valor * 100) / 100f)

    // ==========================================================
    //  Maquina de estados
    // ==========================================================

    /** Aplica una transicion solo si el diagrama de estados la permite. */
    private fun transicionar(destino: EstadoClasificadorIA): Boolean {
        if (!estadoActual.puedeTransicionarA(destino)) {
            Log.w(TAG, "Transicion invalida: ${estadoActual.valor} -> ${destino.valor}")
            return false
        }
        estadoActual = destino
        return true
    }

    /**
     * completar() / enviarARevision(): devuelve el clasificador a INACTIVO
     * para que pueda atender la siguiente solicitud.
     */
    private fun liberar() {
        transicionar(EstadoClasificadorIA.INACTIVO)
    }

    /**
     * registrarError(): PROCESANDO --fallo del modelo--> ERROR --> INACTIVO
     */
    private fun registrarError(mensaje: String): ResultadoClasificacion {
        transicionar(EstadoClasificadorIA.ERROR)
        Log.e(TAG, "Fallo del clasificador: $mensaje")
        liberar()
        return ResultadoClasificacion.conError(mensaje)
    }

    companion object {
        private const val TAG = "AqualertIA"

        /** Nombre del modelo simulado. */
        private const val NOMBRE_MODELO = "aqualert-contaminacion-hidrica"

        /** Tiempo que tarda una inferencia simulada. */
        private const val TIEMPO_INFERENCIA_MS = 900L
    }
}
