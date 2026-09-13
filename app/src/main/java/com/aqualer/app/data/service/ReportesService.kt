package com.aqualer.app.data.service

import com.aqualer.app.data.estado.EstadoReporte
import com.aqualer.app.data.model.HistorialEstado
import com.aqualer.app.data.model.Reporte
import com.aqualer.app.util.Constantes
import com.aqualer.app.util.Recurso
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.tasks.await

/**
 * Servicio de Reportes (ReportSvc del diagrama de componentes).
 *
 * Implementa las operaciones crear(), actualizar(), eliminar() y
 * cambiarEstado() de la clase Reporte, y los procesos de los
 * diagramas de colaboracion:
 *
 *   Crear Reporte:    1.12 registrar reporte -> 1.13 guardar reporte
 *   Consultar:        1.3 consultar reportes -> 1.4 obtener datos
 *
 * Cada cambio de estado deja una traza en la subcoleccion
 * historial_estados, tal como exige el Modelo Estructural de BD.
 */
class ReportesService(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()
) {

    private val coleccion get() = db.collection(Constantes.COL_REPORTES)

    // ==========================================================
    //  CREATE
    // ==========================================================

    /**
     * crear() de la clase Reporte.
     *
     * El reporte nace en BORRADOR y, al tener titulo, descripcion y
     * evidencia, transiciona segun el diagrama de estados. En esta
     * version de demostracion pasa directo a PUBLICADO porque el
     * ClasificadorIA aun no esta conectado; cuando lo este, el estado
     * intermedio sera EN_REVISION.
     */
    suspend fun crear(reporte: Reporte): Recurso<String> = try {
        val validacion = reporte.validar()
        if (!validacion.esValido) {
            Recurso.Error(
                (validacion as com.aqualer.app.data.model.ResultadoValidacion.Invalido)
                    .primerError
            )
        } else {
            val publicado = reporte.copy(estado = EstadoReporte.PUBLICADO)
            val referencia = coleccion.add(publicado.aMapa(usarTimestampServidor = true)).await()

            registrarHistorial(
                idReporte = referencia.id,
                anterior = EstadoReporte.BORRADOR,
                nuevo = EstadoReporte.PUBLICADO,
                uidUsuario = reporte.uidUsuario,
                nombreUsuario = reporte.nombreUsuario,
                observacion = "Reporte creado"
            )

            Recurso.Exito(referencia.id)
        }
    } catch (e: Exception) {
        Recurso.Error(mensajeDeError(e), e)
    }

    // ==========================================================
    //  READ
    // ==========================================================

    /**
     * Lista los reportes ordenados del mas reciente al mas antiguo.
     * Excluye los eliminados, que no deben verse en el listado publico.
     */
    suspend fun listar(limite: Long = Constantes.PAGINA_REPORTES): Recurso<List<Reporte>> = try {
        val consulta = coleccion
            .orderBy(Reporte.CAMPO_FECHA, Query.Direction.DESCENDING)
            .limit(limite)
            .get()
            .await()

        val reportes = consulta.documents
            .map { Reporte.desde(it) }
            .filter { it.estado != EstadoReporte.ELIMINADO }

        Recurso.Exito(reportes)
    } catch (e: Exception) {
        Recurso.Error(mensajeDeError(e), e)
    }

    /** Lista unicamente los reportes creados por un usuario. */
    suspend fun listarPorUsuario(uid: String): Recurso<List<Reporte>> = try {
        val consulta = coleccion
            .whereEqualTo(Reporte.CAMPO_UID_USUARIO, uid)
            .get()
            .await()

        val reportes = consulta.documents
            .map { Reporte.desde(it) }
            .filter { it.estado != EstadoReporte.ELIMINADO }
            .sortedByDescending { it.fechaCreacion }

        Recurso.Exito(reportes)
    } catch (e: Exception) {
        Recurso.Error(mensajeDeError(e), e)
    }

    /** Obtiene un reporte puntual (UC4: Ver detalle de reporte). */
    suspend fun obtener(id: String): Recurso<Reporte> = try {
        val doc = coleccion.document(id).get().await()
        if (doc.exists()) Recurso.Exito(Reporte.desde(doc))
        else Recurso.Error("El reporte ya no existe")
    } catch (e: Exception) {
        Recurso.Error(mensajeDeError(e), e)
    }

    // ==========================================================
    //  UPDATE
    // ==========================================================

    /**
     * actualizar() de la clase Reporte.
     * Solo modifica los campos editables; no toca el autor ni la fecha.
     */
    suspend fun actualizar(reporte: Reporte): Recurso<Unit> = try {
        val validacion = reporte.validar()
        if (!validacion.esValido) {
            Recurso.Error(
                (validacion as com.aqualer.app.data.model.ResultadoValidacion.Invalido)
                    .primerError
            )
        } else {
            coleccion.document(reporte.id).update(
                mapOf(
                    Reporte.CAMPO_TITULO to reporte.titulo.trim(),
                    Reporte.CAMPO_DESCRIPCION to reporte.descripcion.trim(),
                    Reporte.CAMPO_TIPO to reporte.tipo.valor,
                    Reporte.CAMPO_NIVEL_RIESGO to reporte.nivelRiesgo.valor,
                    Reporte.CAMPO_ATENCION_URGENTE to reporte.atencionUrgente,
                    Reporte.CAMPO_PUBLICAR_ANONIMO to reporte.publicarAnonimo,
                    Reporte.CAMPO_IMAGEN_URL to reporte.imagenUrl,
                    Reporte.CAMPO_UBICACION to reporte.ubicacion.aMapa()
                )
            ).await()
            Recurso.Exito(Unit)
        }
    } catch (e: Exception) {
        Recurso.Error(mensajeDeError(e), e)
    }

    /**
     * cambiarEstado() de la clase Reporte.
     *
     * Valida la transicion contra la maquina de estados antes de
     * escribir, y registra la traza en historial_estados.
     */
    suspend fun cambiarEstado(
        reporte: Reporte,
        nuevoEstado: EstadoReporte,
        uidUsuario: String,
        nombreUsuario: String,
        observacion: String = ""
    ): Recurso<Unit> = try {
        if (!reporte.estado.puedeTransicionarA(nuevoEstado)) {
            Recurso.Error(
                "No se puede pasar de ${reporte.estado.valor} a ${nuevoEstado.valor}"
            )
        } else {
            coleccion.document(reporte.id)
                .update(Reporte.CAMPO_ESTADO, nuevoEstado.valor)
                .await()

            registrarHistorial(
                idReporte = reporte.id,
                anterior = reporte.estado,
                nuevo = nuevoEstado,
                uidUsuario = uidUsuario,
                nombreUsuario = nombreUsuario,
                observacion = observacion
            )

            Recurso.Exito(Unit)
        }
    } catch (e: Exception) {
        Recurso.Error(mensajeDeError(e), e)
    }

    // ==========================================================
    //  DELETE
    // ==========================================================

    /**
     * eliminar() de la clase Reporte.
     *
     * Borrado logico: el diagrama de estados define ELIMINADO como un
     * estado del reporte, no como su desaparicion. Asi se conserva el
     * historial para auditoria, que es lo que espera el Administrador.
     */
    suspend fun eliminar(
        reporte: Reporte,
        uidUsuario: String,
        nombreUsuario: String
    ): Recurso<Unit> = try {
        coleccion.document(reporte.id)
            .update(Reporte.CAMPO_ESTADO, EstadoReporte.ELIMINADO.valor)
            .await()

        registrarHistorial(
            idReporte = reporte.id,
            anterior = reporte.estado,
            nuevo = EstadoReporte.ELIMINADO,
            uidUsuario = uidUsuario,
            nombreUsuario = nombreUsuario,
            observacion = "Reporte eliminado por el usuario"
        )

        Recurso.Exito(Unit)
    } catch (e: Exception) {
        Recurso.Error(mensajeDeError(e), e)
    }

    /** Borrado fisico definitivo. Solo para limpiar datos de prueba. */
    suspend fun eliminarDefinitivo(id: String): Recurso<Unit> = try {
        coleccion.document(id).delete().await()
        Recurso.Exito(Unit)
    } catch (e: Exception) {
        Recurso.Error(mensajeDeError(e), e)
    }

    // ==========================================================
    //  Historial de estados
    // ==========================================================

    /** Guarda la traza de una transicion en la subcoleccion. */
    private suspend fun registrarHistorial(
        idReporte: String,
        anterior: EstadoReporte,
        nuevo: EstadoReporte,
        uidUsuario: String,
        nombreUsuario: String,
        observacion: String
    ) {
        runCatching {
            val traza = HistorialEstado(
                idReporte = idReporte,
                estadoAnterior = anterior,
                estadoNuevo = nuevo,
                uidUsuario = uidUsuario.ifBlank { HistorialEstado.UID_SISTEMA },
                nombreUsuario = nombreUsuario,
                observacion = observacion
            )
            coleccion.document(idReporte)
                .collection(Constantes.SUBCOL_HISTORIAL_ESTADOS)
                .add(traza.aMapa())
                .await()
        }
    }

    /** Linea de tiempo de cambios de estado de un reporte. */
    suspend fun listarHistorial(idReporte: String): Recurso<List<HistorialEstado>> = try {
        val consulta = coleccion.document(idReporte)
            .collection(Constantes.SUBCOL_HISTORIAL_ESTADOS)
            .get()
            .await()

        val trazas = consulta.documents
            .map { HistorialEstado.desde(it) }
            .sortedBy { it.fecha }

        Recurso.Exito(trazas)
    } catch (e: Exception) {
        Recurso.Error(mensajeDeError(e), e)
    }

    private fun mensajeDeError(e: Exception): String {
        val texto = e.message.orEmpty()
        return when {
            texto.contains("PERMISSION_DENIED", true) ->
                "No tienes permisos para esta accion. Revisa las reglas de Firestore."
            texto.contains("UNAVAILABLE", true) || texto.contains("network", true) ->
                "Sin conexion a internet. Revisa tu red e intenta de nuevo."
            texto.contains("NOT_FOUND", true) ->
                "El reporte ya no existe"
            texto.contains("FAILED_PRECONDITION", true) ->
                "Falta un indice en Firestore. Abre el enlace que aparece en Logcat."
            else -> "Ocurrio un error al procesar el reporte."
        }
    }
}