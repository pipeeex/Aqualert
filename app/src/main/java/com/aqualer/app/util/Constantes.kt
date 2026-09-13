package com.aqualer.app.util

/**
 * Constantes globales del sistema Aqualert.
 * Centraliza nombres de colecciones y parametros de negocio para cumplir
 * el RNF008 (mantenibilidad del codigo).
 */
object Constantes {

    // ===== Colecciones de Firestore (Modelo Estructural de Base de Datos) =====
    const val COL_USUARIOS = "usuarios"
    const val COL_REPORTES = "reportes"
    const val SUBCOL_HISTORIAL_ESTADOS = "historial_estados"
    const val COL_COMENTARIOS = "comentarios"
    const val COL_CONSEJOS = "consejos"
    const val COL_ALERTAS = "alertas"
    const val COL_NOTIFICACIONES = "notificaciones"
    const val COL_CHATBOT_SESIONES = "chatbot_sesiones"
    const val SUBCOL_MENSAJES = "mensajes"

    // ===== Carpetas de Firebase Storage =====
    const val STORAGE_REPORTES = "reportes"
    const val STORAGE_PERFILES = "perfiles"

    // ===== Reglas de negocio del ClasificadorIA =====
    /** Umbral de confianza minimo para aceptar una clasificacion automatica. */
    const val UMBRAL_CONFIANZA_IA = 0.70f

    /** Version del modelo de clasificacion en uso. */
    const val VERSION_MODELO_IA = "1.0.0-stub"

    // ===== Validaciones (RNF007: integridad de los datos) =====
    const val MIN_LONGITUD_PASSWORD = 6
    const val MIN_LONGITUD_DESCRIPCION = 15
    const val MAX_LONGITUD_DESCRIPCION = 1000
    const val MIN_LONGITUD_TITULO = 5
    const val MAX_LONGITUD_TITULO = 120
    const val MAX_LONGITUD_COMENTARIO = 500

    // ===== Parametros de consulta =====
    const val PAGINA_REPORTES = 20L

    /** Radio por defecto en kilometros para buscar reportes cercanos. */
    const val RADIO_BUSQUEDA_KM = 5.0

    // ===== Ubicacion por defecto: Bogota (alcance del prototipo) =====
    const val BOGOTA_LAT = 4.7110
    const val BOGOTA_LNG = -74.0721
    const val ZOOM_CIUDAD = 11f
    const val ZOOM_DETALLE = 16f

    // ===== Claves de navegacion entre pantallas =====
    const val EXTRA_REPORTE_ID = "extra_reporte_id"
    const val EXTRA_USUARIO_ID = "extra_usuario_id"
    const val EXTRA_MODO_VISITANTE = "extra_modo_visitante"
}