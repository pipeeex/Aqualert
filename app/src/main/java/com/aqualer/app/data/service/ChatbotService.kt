package com.aqualer.app.data.service

import com.aqualer.app.data.estado.EstadoChatbotSesion
import com.aqualer.app.data.model.AutorMensaje
import com.aqualer.app.data.model.ChatbotSesion
import com.aqualer.app.data.model.MensajeChat
import com.aqualer.app.util.Constantes
import com.aqualer.app.util.Recurso
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.delay
import kotlinx.coroutines.tasks.await
import java.text.Normalizer
import java.util.Date

/**
 * Servicio de Chatbot (ChatSvc del diagrama de componentes).
 *
 * Implementa el Diagrama de Secuencia "Chatbot":
 *   enviarPregunta()     -> ChatbotApp
 *   consultarRespuesta() -> API
 *   procesarPregunta()   -> este servicio
 *   respuesta            -> se devuelve al usuario
 *   mostrarRespuesta()   -> la interfaz
 *
 * Cubre el caso de uso UC10 "Usar chatbot" y sus inclusiones:
 *   --include--> Enviar pregunta
 *   --include--> Recibir respuesta automatica
 *   --include--> Consultar preguntas frecuentes
 *
 * La resolucion de respuestas se hace con una base de conocimiento
 * local por coincidencia de palabras clave. No requiere conexion a un
 * modelo de lenguaje externo, lo que mantiene la app funcional sin
 * costos de API y permite sustituir el motor mas adelante.
 */
class ChatbotService(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()
) {

    private val coleccion get() = db.collection(Constantes.COL_CHATBOT_SESIONES)

    // ==========================================================
    //  Gestion de la sesion
    // ==========================================================

    /**
     * iniciarSesion() de la clase ChatbotSesion.
     * Transicion: INICIANDO --iniciarSesion()--> ACTIVA
     *
     * El visitante tambien puede conversar, por eso uidUsuario admite
     * cadena vacia (UC10 esta disponible para Visitante y Ciudadano).
     */
    suspend fun iniciarSesion(uidUsuario: String): Recurso<ChatbotSesion> = try {
        val sesion = ChatbotSesion(
            uidUsuario = uidUsuario,
            inicio = Date(),
            estado = EstadoChatbotSesion.INICIANDO
        ).iniciarSesion()

        val referencia = coleccion.add(sesion.aMapa()).await()

        // El saludo inicial queda registrado como primer mensaje del bot
        val saludo = MensajeChat(
            contenido = MENSAJE_SALUDO,
            autor = AutorMensaje.BOT,
            fecha = Date()
        )
        guardarMensaje(referencia.id, saludo)

        Recurso.Exito(
            sesion.copy(id = referencia.id, historial = listOf(saludo))
        )
    } catch (e: Exception) {
        Recurso.Error(mensajeDeError(e), e)
    }

    /**
     * cerrarSesion() de la clase ChatbotSesion.
     * Transicion: ACTIVA o ESPERANDO_RESPUESTA --cerrarSesion()--> CERRADA
     */
    suspend fun cerrarSesion(sesion: ChatbotSesion): Recurso<Unit> = try {
        val cerrada = sesion.cerrarSesion()
        coleccion.document(sesion.id).update(
            mapOf(
                ChatbotSesion.CAMPO_ESTADO to cerrada.estado.valor,
                ChatbotSesion.CAMPO_FIN to com.google.firebase.Timestamp(cerrada.fin ?: Date())
            )
        ).await()
        Recurso.Exito(Unit)
    } catch (e: Exception) {
        Recurso.Error(mensajeDeError(e), e)
    }

    /** Recupera el historial de una sesion (atributo historial del diagrama). */
    suspend fun cargarHistorial(idSesion: String): Recurso<List<MensajeChat>> = try {
        val consulta = coleccion.document(idSesion)
            .collection(Constantes.SUBCOL_MENSAJES)
            .get()
            .await()

        val mensajes = consulta.documents
            .map { MensajeChat.desde(it) }
            .sortedBy { it.fecha }

        Recurso.Exito(mensajes)
    } catch (e: Exception) {
        Recurso.Error(mensajeDeError(e), e)
    }

    // ==========================================================
    //  enviarMensaje() / procesarPregunta()
    // ==========================================================

    /**
     * enviarMensaje() de la clase ChatbotSesion.
     *
     * Transiciones encadenadas del diagrama de estados:
     *   ACTIVA --enviarMensaje()--> ESPERANDO_RESPUESTA
     *   ESPERANDO_RESPUESTA --recibirRespuesta()--> ACTIVA
     *
     * @return la sesion actualizada con la pregunta y la respuesta
     */
    suspend fun enviarPregunta(
        sesion: ChatbotSesion,
        pregunta: String
    ): Recurso<ChatbotSesion> = try {

        val texto = pregunta.trim()

        when {
            texto.isBlank() ->
                Recurso.Error("Escribe una pregunta para continuar")

            !sesion.estado.aceptaMensajes ->
                Recurso.Error("La conversacion no esta activa. Inicia una nueva.")

            else -> {
                // 1. El usuario envia la pregunta
                val mensajeUsuario = MensajeChat(
                    contenido = texto,
                    autor = AutorMensaje.USUARIO,
                    fecha = Date()
                )
                val esperando = sesion.enviarMensaje(mensajeUsuario)
                actualizarEstado(sesion.id, esperando.estado)
                guardarMensaje(sesion.id, mensajeUsuario)

                // 2. El servicio procesa la pregunta
                delay(TIEMPO_RESPUESTA_MS)
                val respuesta = MensajeChat(
                    contenido = procesarPregunta(texto),
                    autor = AutorMensaje.BOT,
                    fecha = Date()
                )

                // 3. Se recibe la respuesta y la sesion vuelve a ACTIVA
                val activa = esperando.recibirRespuesta(respuesta)
                actualizarEstado(sesion.id, activa.estado)
                guardarMensaje(sesion.id, respuesta)

                Recurso.Exito(activa)
            }
        }
    } catch (e: Exception) {
        // error(): cualquier estado --> FALLIDA
        runCatching { actualizarEstado(sesion.id, EstadoChatbotSesion.FALLIDA) }
        Recurso.Error(mensajeDeError(e), e)
    }

    /**
     * procesarPregunta(): resuelve la respuesta automatica.
     *
     * Puntua cada entrada de la base de conocimiento segun cuantas de
     * sus palabras clave aparecen en la pregunta, y devuelve la de mayor
     * puntaje. Si ninguna supera el minimo, responde con una guia de
     * temas disponibles en lugar de inventar una respuesta.
     */
    fun procesarPregunta(pregunta: String): String {
        val normalizada = normalizar(pregunta)

        val mejor = BASE_CONOCIMIENTO
            .map { entrada -> entrada to puntuar(entrada, normalizada) }
            .filter { (_, puntaje) -> puntaje > 0 }
            .maxByOrNull { (_, puntaje) -> puntaje }

        return mejor?.first?.respuesta ?: RESPUESTA_SIN_COINCIDENCIA
    }

    /** Cuenta las palabras clave de la entrada presentes en la pregunta. */
    private fun puntuar(entrada: EntradaConocimiento, preguntaNormalizada: String): Int =
        entrada.palabrasClave.count { clave ->
            preguntaNormalizada.contains(normalizar(clave))
        }

    /** Quita tildes y pasa a minusculas para comparar sin acentos. */
    private fun normalizar(texto: String): String =
        Normalizer.normalize(texto.lowercase(), Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")

    /** "Consultar preguntas frecuentes" del diagrama de casos de uso. */
    fun preguntasFrecuentes(): List<String> =
        BASE_CONOCIMIENTO.filter { it.esFrecuente }.map { it.pregunta }

    // ==========================================================
    //  Persistencia
    // ==========================================================

    private suspend fun guardarMensaje(idSesion: String, mensaje: MensajeChat) {
        runCatching {
            coleccion.document(idSesion)
                .collection(Constantes.SUBCOL_MENSAJES)
                .add(mensaje.aMapa())
                .await()
        }
    }

    private suspend fun actualizarEstado(idSesion: String, estado: EstadoChatbotSesion) {
        runCatching {
            coleccion.document(idSesion)
                .update(ChatbotSesion.CAMPO_ESTADO, estado.valor)
                .await()
        }
    }

    private fun mensajeDeError(e: Exception): String {
        val texto = e.message.orEmpty()
        return when {
            texto.contains("UNAVAILABLE", true) || texto.contains("network", true) ->
                "Sin conexion a internet. El asistente no esta disponible."
            texto.contains("PERMISSION_DENIED", true) ->
                "No tienes permisos para usar el asistente"
            else -> "El asistente no pudo responder. Intenta de nuevo."
        }
    }

    /** Entrada de la base de conocimiento del asistente. */
    private data class EntradaConocimiento(
        val pregunta: String,
        val palabrasClave: List<String>,
        val respuesta: String,
        val esFrecuente: Boolean = false
    )

    companion object {

        /** Tiempo simulado de procesamiento de la respuesta. */
        private const val TIEMPO_RESPUESTA_MS = 700L

        private const val MENSAJE_SALUDO =
            "Hola, soy el asistente de Aqualert. Puedo ayudarte a reportar " +
                    "contaminacion, explicarte como funcionan los estados de un reporte " +
                    "o resolver dudas sobre el cuidado del agua. Que necesitas saber?"

        private const val RESPUESTA_SIN_COINCIDENCIA =
            "No tengo una respuesta para esa pregunta todavia. Puedo ayudarte con: " +
                    "como crear un reporte, que foto adjuntar, los estados de un reporte, " +
                    "que hacer ante una contaminacion grave, a que entidad acudir, " +
                    "como comentar, y consejos de cuidado del agua."

        /**
         * Base de conocimiento del chatbot.
         * Cada entrada reune las formas en que un usuario puede preguntar
         * lo mismo, para que la coincidencia no dependa de una redaccion exacta.
         */
        private val BASE_CONOCIMIENTO = listOf(

            EntradaConocimiento(
                pregunta = "Como creo un reporte?",
                palabrasClave = listOf(
                    "crear reporte", "como reporto", "hacer un reporte",
                    "nuevo reporte", "reportar", "denunciar", "subir reporte"
                ),
                respuesta = "Para crear un reporte entra a la seccion Reportes y pulsa el " +
                        "boton Reportar. Necesitas tres cosas: una foto de evidencia, una " +
                        "descripcion de lo que observaste y la ubicacion del sitio. Tambien " +
                        "eliges el tipo de contaminacion y el nivel de riesgo. " +
                        "Debes tener una cuenta registrada para publicarlo.",
                esFrecuente = true
            ),

            EntradaConocimiento(
                pregunta = "Que foto debo adjuntar?",
                palabrasClave = listOf(
                    "foto", "imagen", "fotografia", "evidencia", "camara", "adjuntar"
                ),
                respuesta = "La foto debe mostrar claramente el problema y permitir reconocer " +
                        "el lugar. Tomala con buena luz y, si hay una descarga visible, incluye " +
                        "el punto de vertimiento. Evita fotografiar personas: el reporte es " +
                        "sobre el dano ambiental. La evidencia fotografica es obligatoria.",
                esFrecuente = true
            ),

            EntradaConocimiento(
                pregunta = "Que significan los estados de un reporte?",
                palabrasClave = listOf(
                    "estado", "estados", "publicado", "verificado", "en atencion",
                    "resuelto", "rechazado", "en revision", "significa"
                ),
                respuesta = "Un reporte recorre estos estados: En revision mientras se analiza " +
                        "la imagen; Publicado cuando queda visible para la comunidad; " +
                        "Verificado cuando un administrador lo valida; En atencion cuando las " +
                        "autoridades ya estan actuando; Resuelto cuando el problema se " +
                        "soluciono; y Archivado como historico. Si la imagen no es valida o el " +
                        "contenido es inapropiado, pasa a Rechazado.",
                esFrecuente = true
            ),

            EntradaConocimiento(
                pregunta = "Puedo editar o borrar mi reporte?",
                palabrasClave = listOf(
                    "editar", "modificar", "borrar", "eliminar", "cambiar mi reporte",
                    "corregir"
                ),
                respuesta = "Si. Abre el reporte desde el listado y podras modificar su " +
                        "informacion o eliminarlo. Solo puedes editar los reportes que tu " +
                        "creaste. Al eliminarlo no desaparece del sistema: queda registrado " +
                        "en el historial para conservar la trazabilidad."
            ),

            EntradaConocimiento(
                pregunta = "Que hago ante una contaminacion grave?",
                palabrasClave = listOf(
                    "grave", "emergencia", "urgente", "peces muertos", "quimico",
                    "olor fuerte", "derrame", "peligro"
                ),
                respuesta = "Si ves un vertimiento de gran volumen, peces muertos o un olor " +
                        "quimico intenso: no te acerques al agua ni la manipules, crea el " +
                        "reporte con nivel de riesgo Critico y marca la casilla de atencion " +
                        "urgente. Luego comunicate con la autoridad ambiental de tu ciudad. " +
                        "En Bogota atiende la Secretaria Distrital de Ambiente.",
                esFrecuente = true
            ),

            EntradaConocimiento(
                pregunta = "A que entidad debo acudir?",
                palabrasClave = listOf(
                    "entidad", "autoridad", "quien atiende", "secretaria", "acueducto",
                    "car", "denuncia oficial", "a quien"
                ),
                respuesta = "En Bogota, la Secretaria Distrital de Ambiente atiende los casos " +
                        "de contaminacion de cuerpos de agua urbanos, y la Empresa de " +
                        "Acueducto y Alcantarillado se encarga de la red de alcantarillado. " +
                        "Fuera del perimetro urbano corresponde a la Corporacion Autonoma " +
                        "Regional. Aqualert centraliza el reporte ciudadano para que esas " +
                        "entidades reciban la informacion de forma organizada."
            ),

            EntradaConocimiento(
                pregunta = "Como comento un reporte?",
                palabrasClave = listOf(
                    "comentar", "comentario", "responder", "opinar", "proponer solucion"
                ),
                respuesta = "Abre el detalle de cualquier reporte y encontraras la seccion de " +
                        "comentarios. Puedes aportar informacion adicional o proponer una " +
                        "solucion. Necesitas estar registrado para comentar. Los comentarios " +
                        "inapropiados pueden ser ocultados por un administrador."
            ),

            EntradaConocimiento(
                pregunta = "Necesito registrarme para usar la app?",
                palabrasClave = listOf(
                    "registro", "registrarme", "cuenta", "visitante", "sin cuenta",
                    "iniciar sesion", "login"
                ),
                respuesta = "No para consultar: como visitante puedes ver los reportes, abrir " +
                        "su detalle, leer los consejos y usar este asistente. Para crear " +
                        "reportes y comentar si necesitas una cuenta, porque cada reporte " +
                        "queda asociado a un autor responsable."
            ),

            EntradaConocimiento(
                pregunta = "Puedo reportar de forma anonima?",
                palabrasClave = listOf(
                    "anonimo", "anonima", "mi nombre", "privacidad", "ocultar nombre"
                ),
                respuesta = "Si. Al crear el reporte marca la casilla Publicar sin mostrar mi " +
                        "nombre. La comunidad vera el reporte como anonimo, pero el sistema " +
                        "conserva internamente quien lo creo para efectos de trazabilidad " +
                        "ante las autoridades."
            ),

            EntradaConocimiento(
                pregunta = "Como se usa mi ubicacion?",
                palabrasClave = listOf(
                    "ubicacion", "gps", "mapa", "localizacion", "coordenadas", "donde"
                ),
                respuesta = "La ubicacion se usa para situar el reporte en el mapa y que las " +
                        "entidades sepan donde esta el problema. Se registra la coordenada del " +
                        "sitio reportado, no un seguimiento de tus movimientos. Si prefieres, " +
                        "puedes marcar el punto manualmente en el mapa."
            ),

            EntradaConocimiento(
                pregunta = "Que es la contaminacion hidrica?",
                palabrasClave = listOf(
                    "contaminacion hidrica", "que es contaminacion", "canal hidrico",
                    "cuerpo de agua", "por que importa"
                ),
                respuesta = "Es la alteracion de la calidad del agua por residuos solidos, " +
                        "vertimientos industriales o aguas residuales sin tratar. En Colombia " +
                        "solo cerca del 52 % de las aguas residuales recibe tratamiento " +
                        "adecuado, lo que convierte al 48 % restante en una fuente permanente " +
                        "de contaminacion para rios y canales."
            ),

            EntradaConocimiento(
                pregunta = "Como puedo cuidar el agua desde casa?",
                palabrasClave = listOf(
                    "cuidar el agua", "ahorrar agua", "consejos", "que puedo hacer",
                    "ayudar", "prevenir"
                ),
                respuesta = "Tres acciones con impacto real: no arrojes residuos ni aceites a " +
                        "los desagues (un litro de aceite contamina hasta mil litros de agua), " +
                        "revisa fugas en tu casa, y participa en jornadas de limpieza de " +
                        "rondas hidricas. En la seccion Consejos encuentras mas recomendaciones.",
                esFrecuente = true
            ),

            EntradaConocimiento(
                pregunta = "Quien revisa los reportes?",
                palabrasClave = listOf(
                    "quien revisa", "administrador", "moderacion", "verifica", "validan"
                ),
                respuesta = "Los reportes pasan primero por una revision automatica de la " +
                        "imagen. Despues, un administrador puede verificarlos, cambiar su " +
                        "estado conforme avanza la atencion y moderar los comentarios. Cada " +
                        "cambio de estado queda registrado con la fecha y quien lo hizo."
            ),

            EntradaConocimiento(
                pregunta = "Que es Aqualert?",
                palabrasClave = listOf(
                    "que es aqualert", "para que sirve", "aplicacion", "de que se trata",
                    "objetivo"
                ),
                respuesta = "Aqualert es una aplicacion de ciencia ciudadana para reportar y " +
                        "hacer seguimiento a la contaminacion de canales y cuerpos hidricos en " +
                        "Bogota. Cualquier persona puede documentar un caso con foto y " +
                        "ubicacion, la comunidad puede comentarlo y proponer soluciones, y las " +
                        "entidades reciben la informacion organizada para actuar mas rapido."
            )
        )
    }
}
