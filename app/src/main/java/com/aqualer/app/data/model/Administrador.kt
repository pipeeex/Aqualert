package com.aqualer.app.data.model

import com.aqualer.app.data.estado.EstadoReporte
import com.aqualer.app.data.estado.EstadoUsuario
import com.aqualer.app.data.estado.RolUsuario
import java.util.Date

/**
 * Clase Administrador del diagrama de clases (extiende Usuario).
 *
 * Actor privilegiado y externo. Operaciones del diagrama:
 *   + consultarTodosReportes()
 *   + filtrarReportes()
 *   + moderarContenido()
 *   + verificarReporte()
 *   + gestionarUsuarios()
 *   + bloquearUsuario()
 *
 * Relacion del diagrama: Administrador 1 --modera--> 0..* Reporte
 *                        Administrador 1 --elimina--> 0..* Comentario
 */
class Administrador(
    id: String = "",
    nombre: String = "",
    email: String = "",
    fotoPerfil: String = "",
    activo: Boolean = true,
    estado: EstadoUsuario = EstadoUsuario.REGISTRADO,
    fechaRegistro: Date = Date(),
    tokenFcm: String = ""
) : Usuario(
    id = id,
    nombre = nombre,
    email = email,
    fotoPerfil = fotoPerfil,
    rol = RolUsuario.ADMINISTRADOR,
    activo = activo,
    estado = estado,
    fechaRegistro = fechaRegistro,
    tokenFcm = tokenFcm
) {

    /** consultarTodosReportes(): ve todos los estados, no solo los publicos. */
    fun puedeConsultarTodosLosReportes(): Boolean = activo

    /** filtrarReportes(): filtra por estado y fecha. */
    fun puedeFiltrarReportes(): Boolean = activo

    /** moderarContenido(): oculta o restaura comentarios. */
    fun puedeModerarContenido(): Boolean = activo

    /**
     * verificarReporte(): valida un reporte publicado.
     * Solo tiene sentido cuando el reporte esta en estado PUBLICADO.
     */
    fun puedeVerificarReporte(reporte: Reporte): Boolean =
        activo && reporte.estado == EstadoReporte.PUBLICADO

    /** gestionarUsuarios(): consulta y administra las cuentas. */
    fun puedeGestionarUsuarios(): Boolean = activo

    /**
     * bloquearUsuario(): un administrador no puede bloquearse a si mismo
     * ni bloquear a otro administrador.
     */
    fun puedeBloquearA(usuario: Usuario): Boolean =
        activo && usuario.id != id && usuario.rol != RolUsuario.ADMINISTRADOR

    /** Valida un cambio de estado de reporte contra el diagrama de estados. */
    fun puedeCambiarEstadoDe(reporte: Reporte, nuevoEstado: EstadoReporte): Boolean =
        activo && reporte.estado.puedeTransicionarA(nuevoEstado)
}