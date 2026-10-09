package com.aqualer.app.data.service

import android.content.Context
import android.util.Log
import com.aqualer.app.data.estado.EstadoReporte
import com.aqualer.app.data.model.HistorialEstado
import com.aqualer.app.data.model.Reporte
import com.aqualer.app.data.model.ResultadoClasificacion
import com.aqualer.app.data.model.ResultadoValidacion
import com.aqualer.app.util.Constantes
import com.aqualer.app.util.Recurso
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.tasks.await


/* Cada cambio de estado queda trazado en la subcoleccion historial_estados.
 */
class ReportesService(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val clasificador: ClasificadorIA = ClasificadorIAService(),
    private val alertasService: AlertasService = AlertasService(),
    private val notificacionesService: NotificacionesService = NotificacionesService()
) {

    private val coleccion get() = db.collection(Constantes.COL_REPORTES)


    suspend fun crear(
        reporte: Reporte,
        contexto: Context? = null
    ): Recurso<String> = try {

        when (val validacion = reporte.validar()) {
            is ResultadoValidacion.Invalido -> Recurso.Error(validacion.primerError)

            else -> {
                // 1. El reporte entra EN_REVISION
                val enRevision = reporte.copy(estado = EstadoReporte.EN_REVISION)
                val referencia = coleccion
                    .add(enRevision.aMapa(usarTimestampServidor = true))
                    .await()
                val idReporte = referencia.id

                registrarHistorial(
                    idReporte = idReporte,
                    anterior = EstadoReporte.BORRADOR,
                    nuevo = EstadoReporte.EN_REVISION,
                    uidUsuario = reporte.uidUsuario,
                    nombreUsuario = reporte.nombreUsuario,
                    observacion = "Reporte enviado a clasificacion automatica"
                )

                // 2. clasificarImagen()
                val resultado = clasificador.clasificarImagen(
                    referenciaImagen = reporte.imagenUrl,
                    tipoDeclarado = reporte.tipo
                )

                // 3. La clasificacion decide el estado final
                val estadoFinal = if (resultado.fallo) EstadoReporte.RECHAZADO
                else EstadoReporte.PUBLICADO

                coleccion.document(idReporte).update(
                    mapOf(
                        Reporte.CAMPO_ESTADO to estadoFinal.valor,
                        Reporte.CAMPO_CLASIFICACION_IA to resultado.clasificacion,
                        Reporte.CAMPO_CONFIANZA_IA to resultado.confianza.toDouble()
                    )
                ).await()

                registrarHistorial(
                    idReporte = idReporte,
                    anterior = EstadoReporte.EN_REVISION,
                    nuevo = estadoFinal,
                    uidUsuario = HistorialEstado.UID_SISTEMA,
                    nombreUsuario = "Clasificador IA",
                    observacion = observacionDeClasificacion(resultado)
                )

                // 4. Alerta y notificacion solo si quedo publicado
                if (estadoFinal == EstadoReporte.PUBLICADO) {
                    val publicado = enRevision.copy(
                        id = idReporte,
                        estado = estadoFinal,
                        clasificacionIa = resultado.clasificacion,
                        confianzaIa = resultado.confianza
                    )
                    generarAlertaSiAplica(publicado, contexto)
                    Recurso.Exito(idReporte)
                } else {
                    Recurso.Error(
                        "La imagen no pudo analizarse: ${resultado.error}. " +
                                "El reporte quedo como rechazado, puedes editarlo y reintentar."
                    )
                }
            }
        }
    } catch (e: Exception) {
        Recurso.Error(mensajeDeError(e), e)
    }

    /* Texto de la trazabilidad que deja la clasificacion automatica. */
    private fun observacionDeClasificacion(resultado: ResultadoClasificacion): String = when {
        resultado.fallo ->
            "Clasificacion fallida: ${resultado.error}"

        resultado.requiereRevisionHumana ->
            "Clasificado como ${resultado.clasificacion} con confianza baja " +
                    "(${resultado.confianzaPorcentaje}%). Requiere revision humana."

        else ->
            "Clasificado como ${resultado.clasificacion} " +
                    "(${resultado.confianzaPorcentaje}% de confianza)"
    }

    /**
     * AlertasService decide internamente si el caso merece ser una alerta.
     */
    private suspend fun generarAlertaSiAplica(reporte: Reporte, contexto: Context?) {
        runCatching {
            val generada = alertasService.generarDesdeReporte(reporte)
            val alerta = generada.datosONull()
            if (alerta != null) {
                alertasService.emitir(alerta, reporte, contexto)
            }
        }.onFailure { Log.w(TAG, "No se pudo generar la alerta", it) }
    }

    /**
     * Lista los reportes por fecha de generación.
     * Excluye los reportes eliminados, porque no deben verse en el listado publico.
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

    /** Obtiene un reporte puntual. */
    suspend fun obtener(id: String): Recurso<Reporte> = try {
        val doc = coleccion.document(id).get().await()
        if (doc.exists()) Recurso.Exito(Reporte.desde(doc))
        else Recurso.Error("El reporte ya no existe")
    } catch (e: Exception) {
        Recurso.Error(mensajeDeError(e), e)
    }

    /**
     * actualizar() de la clase Reporte.
     * Solo modifica los campos editables; no toca el autor ni la fecha.
     */
    suspend fun actualizar(reporte: Reporte): Recurso<Unit> = try {
        when (val validacion = reporte.validar()) {
            is ResultadoValidacion.Invalido -> Recurso.Error(validacion.primerError)

            else -> {
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
        }
    } catch (e: Exception) {
        Recurso.Error(mensajeDeError(e), e)
    }

    suspend fun reintentarClasificacion(
        reporte: Reporte,
        contexto: Context? = null
    ): Recurso<Unit> = try {
        if (reporte.estado != EstadoReporte.RECHAZADO) {
            Recurso.Error("Solo los reportes rechazados pueden reintentarse")
        } else {
            registrarHistorial(
                idReporte = reporte.id,
                anterior = EstadoReporte.RECHAZADO,
                nuevo = EstadoReporte.BORRADOR,
                uidUsuario = reporte.uidUsuario,
                nombreUsuario = reporte.nombreUsuario,
                observacion = "El autor corrigio el reporte y lo reenvio"
            )

            coleccion.document(reporte.id)
                .update(Reporte.CAMPO_ESTADO, EstadoReporte.EN_REVISION.valor)
                .await()

            val resultado = clasificador.clasificarImagen(
                referenciaImagen = reporte.imagenUrl,
                tipoDeclarado = reporte.tipo
            )

            val estadoFinal = if (resultado.fallo) EstadoReporte.RECHAZADO
            else EstadoReporte.PUBLICADO

            coleccion.document(reporte.id).update(
                mapOf(
                    Reporte.CAMPO_ESTADO to estadoFinal.valor,
                    Reporte.CAMPO_CLASIFICACION_IA to resultado.clasificacion,
                    Reporte.CAMPO_CONFIANZA_IA to resultado.confianza.toDouble()
                )
            ).await()

            registrarHistorial(
                idReporte = reporte.id,
                anterior = EstadoReporte.EN_REVISION,
                nuevo = estadoFinal,
                uidUsuario = HistorialEstado.UID_SISTEMA,
                nombreUsuario = "Clasificador IA",
                observacion = observacionDeClasificacion(resultado)
            )

            if (estadoFinal == EstadoReporte.PUBLICADO) {
                generarAlertaSiAplica(
                    reporte.copy(
                        estado = estadoFinal,
                        clasificacionIa = resultado.clasificacion,
                        confianzaIa = resultado.confianza
                    ),
                    contexto
                )
                Recurso.Exito(Unit)
            } else {
                Recurso.Error("La imagen sigue sin poder analizarse")
            }
        }
    } catch (e: Exception) {
        Recurso.Error(mensajeDeError(e), e)
    }

    /**
     * Valida la transicion contra la maquina de estados antes de
     * escribir, registra la traza y avisa al autor del cambio.
     */
    suspend fun cambiarEstado(
        reporte: Reporte,
        nuevoEstado: EstadoReporte,
        uidUsuario: String,
        nombreUsuario: String,
        observacion: String = "",
        contexto: Context? = null
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

            // El autor se entera de que su reporte avanzo
            if (reporte.uidUsuario != uidUsuario) {
                runCatching {
                    notificacionesService.notificarCambioDeEstado(
                        reporte, nuevoEstado, contexto
                    )
                }
            }

            // Un reporte resuelto cierra su alerta asociada
            if (nuevoEstado == EstadoReporte.RESUELTO) {
                cerrarAlertaDe(reporte.id)
            }

            Recurso.Exito(Unit)
        }
    } catch (e: Exception) {
        Recurso.Error(mensajeDeError(e), e)
    }

    /** resolverAlerta() cuando el problema del reporte ya fue atendido. */
    private suspend fun cerrarAlertaDe(idReporte: String) {
        runCatching {
            val alerta = alertasService.obtenerPorReporte(idReporte).datosONull()
            if (alerta != null && alerta.estado.estaActiva) {
                alertasService.resolver(alerta)
            }
        }.onFailure { Log.w(TAG, "No se pudo cerrar la alerta", it) }
    }
    /**
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

        // La alerta pierde sentido si el reporte desaparece
        cerrarAlertaDe(reporte.id)

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

    companion object {
        private const val TAG = "AqualertReportes"
    }
}