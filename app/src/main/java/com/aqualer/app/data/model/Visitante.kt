package com.aqualer.app.data.model

import com.aqualer.app.data.estado.EstadoUsuario
import com.aqualer.app.data.estado.RolUsuario

/**
 *
 * Actor no registrado y externo. Operaciones del diagrama:
 *   + verReportes()
 *   + verDetalleReporte()
 *   + verConsejos()
 *
 * Casos de uso permitidos: UC1 Registrarse, UC2 Iniciar sesion,
 * UC3 Consultar reportes, UC4 Ver detalle, UC9 Consultar consejos,
 * UC10 Usar chatbot. Sin interaccion (no crea ni comenta).
 *
 * Las consultas las ejecuta la capa de servicios; esta clase declara
 * que operaciones tiene autorizadas el actor.
 */
class Visitante : Usuario(
    id = "",
    nombre = "Visitante",
    email = "",
    rol = RolUsuario.VISITANTE,
    activo = true,
    estado = EstadoUsuario.NO_REGISTRADO
) {

    /** verReportes(): consulta el listado publico de reportes. */
    fun puedeVerReportes(): Boolean = true

    /** verDetalleReporte(): abre el detalle de un reporte publicado. */
    fun puedeVerDetalleReporte(): Boolean = true

    /** verConsejos(): accede a la seccion informativa (RF0005). */
    fun puedeVerConsejos(): Boolean = true

    /** El visitante no puede crear reportes ni comentar. */
    fun requiereRegistro(): Boolean = true
}