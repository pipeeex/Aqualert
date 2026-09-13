package com.aqualer.app.data.model

import com.aqualer.app.data.estado.EstadoUsuario
import com.aqualer.app.data.estado.RolUsuario
import java.util.Date

/**
 * Clase Ciudadano del diagrama de clases (extiende Usuario).
 *
 * Actor principal, registrado. Operaciones del diagrama:
 *   + crearReporte()
 *   + comentarReporte()
 *   + actualizarEstadoAlerta()
 *   + usarChatbot()
 *
 * Relaciones del diagrama de clases:
 *   Ciudadano 1 --crea--> 0..* Reporte
 *   Ciudadano 1 --publica--> 0..* Comentario
 *   Ciudadano 1 --inicia--> 0..* ChatbotSesion
 */
class Ciudadano(
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
    rol = RolUsuario.CIUDADANO,
    activo = activo,
    estado = estado,
    fechaRegistro = fechaRegistro,
    tokenFcm = tokenFcm
) {

    /** crearReporte() - UC5. Requiere cuenta habilitada. */
    fun puedeCrearReporte(): Boolean = activo && estado.puedeAcceder

    /** comentarReporte() - UC8. */
    fun puedeComentarReporte(): Boolean = activo && estado.puedeAcceder

    /** actualizarEstadoAlerta() - UC12 sobre sus propios reportes. */
    fun puedeActualizarEstadoAlerta(reporte: Reporte): Boolean =
        activo && estado.puedeAcceder && reporte.uidUsuario == id

    /** usarChatbot() - UC10. */
    fun puedeUsarChatbot(): Boolean = activo && estado.puedeAcceder

    /** Verifica si este ciudadano es el autor del reporte dado. */
    fun esAutorDe(reporte: Reporte): Boolean = reporte.uidUsuario == id

    /** Verifica si este ciudadano es el autor del comentario dado. */
    fun esAutorDe(comentario: Comentario): Boolean = comentario.uidUsuario == id
}