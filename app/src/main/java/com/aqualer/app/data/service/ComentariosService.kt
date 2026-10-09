package com.aqualer.app.data.service

import com.aqualer.app.data.estado.EstadoComentario
import com.aqualer.app.data.model.Comentario
import com.aqualer.app.data.model.Reporte
import com.aqualer.app.data.model.ResultadoValidacion
import com.aqualer.app.util.Constantes
import com.aqualer.app.util.Recurso
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.tasks.await

/**
 * Servicio de Comentarios (CommentSvc del diagrama de componentes).
 *
 * Implementa el Diagrama de Secuencia "Comentarios":
 *   escribirComentario()  -> la interfaz recoge el texto
 *   enviarComentario()    -> ComentariosApp llama al API
 *   registrarComentario() -> este servicio
 *   guardarComentario()   -> Firestore
 *   confirmacion          -> se devuelve el resultado
 *   actualizarVista()     -> la pantalla refresca el listado
 *
 * Cubre los metodos crear() y eliminar() de la clase Comentario y las
 * operaciones de moderacion del Administrador (moderarContenido()).
 */
class ComentariosService(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()
) {

    private val coleccion get() = db.collection(Constantes.COL_COMENTARIOS)
    private val coleccionReportes get() = db.collection(Constantes.COL_REPORTES)

    // ==========================================================
    //  CREATE
    // ==========================================================

    /**
     * crear() de la clase Comentario.
     *
     * Transicion del diagrama de estados:
     *   REDACTANDO --crear()--> PUBLICADO
     *
     * Ademas incrementa el contador total_comentarios del reporte para
     * poder mostrarlo en el listado sin una consulta adicional (RNF004).
     */
    suspend fun publicar(comentario: Comentario): Recurso<String> = try {
        when (val validacion = comentario.validar()) {
            is ResultadoValidacion.Invalido -> Recurso.Error(validacion.primerError)

            else -> {
                // El comentario nace REDACTANDO y pasa a PUBLICADO al enviarse
                val publicado = comentario.cambiarEstado(EstadoComentario.PUBLICADO)

                val referencia = coleccion
                    .add(publicado.aMapa(usarTimestampServidor = true))
                    .await()

                incrementarContador(comentario.idReporte, 1)

                Recurso.Exito(referencia.id)
            }
        }
    } catch (e: Exception) {
        Recurso.Error(mensajeDeError(e), e)
    }

    // ==========================================================
    //  READ
    // ==========================================================

    /**
     * Comentarios visibles de un reporte, del mas antiguo al mas reciente.
     *
     * El ordenamiento se hace en memoria para no exigir un indice
     * compuesto de Firestore sobre (id_reporte, fecha).
     */
    suspend fun listarPorReporte(idReporte: String): Recurso<List<Comentario>> = try {
        val consulta = coleccion
            .whereEqualTo(Comentario.CAMPO_ID_REPORTE, idReporte)
            .get()
            .await()

        val comentarios = consulta.documents
            .map { Comentario.desde(it) }
            .filter { it.estado == EstadoComentario.PUBLICADO }
            .sortedBy { it.fecha }

        Recurso.Exito(comentarios)
    } catch (e: Exception) {
        Recurso.Error(mensajeDeError(e), e)
    }

    /**
     * Listado para el Administrador: incluye los comentarios ocultos,
     * necesarios para el caso de uso "Revisar comentarios".
     */
    suspend fun listarParaModeracion(idReporte: String): Recurso<List<Comentario>> = try {
        val consulta = coleccion
            .whereEqualTo(Comentario.CAMPO_ID_REPORTE, idReporte)
            .get()
            .await()

        val comentarios = consulta.documents
            .map { Comentario.desde(it) }
            .filter { it.estado != EstadoComentario.ELIMINADO }
            .sortedBy { it.fecha }

        Recurso.Exito(comentarios)
    } catch (e: Exception) {
        Recurso.Error(mensajeDeError(e), e)
    }

    /** Comentarios escritos por un usuario concreto. */
    suspend fun listarPorUsuario(uid: String): Recurso<List<Comentario>> = try {
        val consulta = coleccion
            .whereEqualTo(Comentario.CAMPO_UID_USUARIO, uid)
            .get()
            .await()

        val comentarios = consulta.documents
            .map { Comentario.desde(it) }
            .filter { it.estado == EstadoComentario.PUBLICADO }
            .sortedByDescending { it.fecha }

        Recurso.Exito(comentarios)
    } catch (e: Exception) {
        Recurso.Error(mensajeDeError(e), e)
    }

    /**
     * Escucha en tiempo real los comentarios de un reporte.
     *
     * Devuelve el registro del listener para que la pantalla lo cierre
     * en onDestroy y no deje la conexion abierta.
     */
    fun observarPorReporte(
        idReporte: String,
        alCambiar: (List<Comentario>) -> Unit
    ): ListenerRegistration =
        coleccion
            .whereEqualTo(Comentario.CAMPO_ID_REPORTE, idReporte)
            .addSnapshotListener { instantanea, error ->
                if (error != null || instantanea == null) return@addSnapshotListener

                val comentarios = instantanea.documents
                    .map { Comentario.desde(it) }
                    .filter { it.estado == EstadoComentario.PUBLICADO }
                    .sortedBy { it.fecha }

                alCambiar(comentarios)
            }

    // ==========================================================
    //  UPDATE / Moderacion
    // ==========================================================

    /**
     * moderarContenido() del Administrador.
     * Transicion: PUBLICADO --moderarContenido() [Admin]--> OCULTO
     */
    suspend fun ocultar(comentario: Comentario): Recurso<Unit> =
        aplicarTransicion(comentario, EstadoComentario.OCULTO, restarContador = true)

    /**
     * restaurar() del Administrador.
     * Transicion: OCULTO --restaurar() [Admin]--> PUBLICADO
     */
    suspend fun restaurar(comentario: Comentario): Recurso<Unit> =
        aplicarTransicion(comentario, EstadoComentario.PUBLICADO, sumarContador = true)

    /** Permite corregir el texto de un comentario ya publicado. */
    suspend fun editarContenido(
        idComentario: String,
        nuevoContenido: String
    ): Recurso<Unit> = try {
        val texto = nuevoContenido.trim()
        when {
            texto.isBlank() ->
                Recurso.Error("El comentario no puede estar vacio")

            texto.length > Constantes.MAX_LONGITUD_COMENTARIO ->
                Recurso.Error("El comentario no puede superar ${Constantes.MAX_LONGITUD_COMENTARIO} caracteres")

            else -> {
                coleccion.document(idComentario)
                    .update(Comentario.CAMPO_CONTENIDO, texto)
                    .await()
                Recurso.Exito(Unit)
            }
        }
    } catch (e: Exception) {
        Recurso.Error(mensajeDeError(e), e)
    }

    // ==========================================================
    //  DELETE
    // ==========================================================

    /**
     * eliminar() de la clase Comentario.
     *
     * Transicion: PUBLICADO u OCULTO --eliminar() [Admin / Autor]--> ELIMINADO
     *
     * Es un borrado logico: el documento permanece para conservar la
     * trazabilidad de la moderacion.
     */
    suspend fun eliminar(comentario: Comentario): Recurso<Unit> =
        aplicarTransicion(
            comentario,
            EstadoComentario.ELIMINADO,
            restarContador = comentario.estado == EstadoComentario.PUBLICADO
        )

    /**
     * Comprueba si un usuario puede borrar el comentario.
     * El diagrama permite eliminar() al Administrador o al Autor.
     */
    fun puedeEliminar(
        comentario: Comentario,
        uidUsuario: String,
        esAdministrador: Boolean
    ): Boolean = esAdministrador || comentario.uidUsuario == uidUsuario

    // ==========================================================
    //  Apoyo interno
    // ==========================================================

    /**
     * Aplica una transicion validandola contra la maquina de estados
     * del Comentario y ajusta el contador del reporte si corresponde.
     */
    private suspend fun aplicarTransicion(
        comentario: Comentario,
        destino: EstadoComentario,
        sumarContador: Boolean = false,
        restarContador: Boolean = false
    ): Recurso<Unit> = try {
        if (!comentario.estado.puedeTransicionarA(destino)) {
            Recurso.Error(
                "No se puede pasar de ${comentario.estado.valor} a ${destino.valor}"
            )
        } else {
            coleccion.document(comentario.id).update(
                mapOf(
                    Comentario.CAMPO_ESTADO to destino.valor,
                    Comentario.CAMPO_VISIBLE to destino.esVisible
                )
            ).await()

            when {
                sumarContador -> incrementarContador(comentario.idReporte, 1)
                restarContador -> incrementarContador(comentario.idReporte, -1)
            }

            Recurso.Exito(Unit)
        }
    } catch (e: Exception) {
        Recurso.Error(mensajeDeError(e), e)
    }

    /**
     * Ajusta total_comentarios del reporte.
     * Se hace con increment para que sea atomico aunque varios usuarios
     * comenten al mismo tiempo.
     */
    private suspend fun incrementarContador(idReporte: String, cantidad: Long) {
        if (idReporte.isBlank()) return
        runCatching {
            coleccionReportes.document(idReporte)
                .update(Reporte.CAMPO_TOTAL_COMENTARIOS, FieldValue.increment(cantidad))
                .await()
        }
    }

    private fun mensajeDeError(e: Exception): String {
        val texto = e.message.orEmpty()
        return when {
            texto.contains("PERMISSION_DENIED", true) ->
                "No tienes permisos para esta accion sobre el comentario"
            texto.contains("UNAVAILABLE", true) || texto.contains("network", true) ->
                "Sin conexion a internet. Revisa tu red e intenta de nuevo."
            texto.contains("NOT_FOUND", true) ->
                "El comentario ya no existe"
            else -> "Ocurrio un error al procesar el comentario."
        }
    }
}
