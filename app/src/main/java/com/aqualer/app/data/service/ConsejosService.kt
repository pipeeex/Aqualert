package com.aqualer.app.data.service

import com.aqualer.app.data.estado.EstadoConsejo
import com.aqualer.app.data.model.CategoriaConsejo
import com.aqualer.app.data.model.Consejo
import com.aqualer.app.util.Constantes
import com.aqualer.app.util.Recurso
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

/**
 * Servicio de Consejos (TipsSvc del diagrama de componentes).
 *
 * Cubre el RF0005 "Seccion informativa sobre cuidado del agua" y el
 * Diagrama de Secuencia "Consejos":
 *   solicitarConsejos()  -> ConsejosApp
 *   obtenerConsejos()    -> API
 *   consultarContenido() -> este servicio
 *   leerConsejos()       -> Firestore
 *   mostrarConsejos()    -> la interfaz
 *
 * Implementa publicar() y archivar() de la clase Consejo, y el caso de
 * uso "Ver consejos por categoria" mediante el filtro por categoria.
 */
class ConsejosService(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()
) {

    private val coleccion get() = db.collection(Constantes.COL_CONSEJOS)

    // ==========================================================
    //  READ
    // ==========================================================

    /**
     * Consejos visibles para Visitante y Ciudadano.
     * Solo devuelve los que estan en estado PUBLICADO.
     */
    suspend fun listarPublicados(): Recurso<List<Consejo>> = try {
        val consulta = coleccion.get().await()

        val consejos = consulta.documents
            .map { Consejo.desde(it) }
            .filter { it.estado.esVisiblePublicamente }
            .sortedWith(compareBy({ it.orden }, { it.titulo }))

        Recurso.Exito(consejos)
    } catch (e: Exception) {
        Recurso.Error(mensajeDeError(e), e)
    }

    /** "Ver consejos por categoria" del diagrama de casos de uso extendidos. */
    suspend fun listarPorCategoria(categoria: CategoriaConsejo): Recurso<List<Consejo>> = try {
        val consulta = coleccion
            .whereEqualTo(Consejo.CAMPO_TIPO, categoria.valor)
            .get()
            .await()

        val consejos = consulta.documents
            .map { Consejo.desde(it) }
            .filter { it.estado.esVisiblePublicamente }
            .sortedBy { it.orden }

        Recurso.Exito(consejos)
    } catch (e: Exception) {
        Recurso.Error(mensajeDeError(e), e)
    }

    /** Listado completo para el Administrador, incluye borradores. */
    suspend fun listarTodos(): Recurso<List<Consejo>> = try {
        val consulta = coleccion.get().await()

        val consejos = consulta.documents
            .map { Consejo.desde(it) }
            .filter { it.estado != EstadoConsejo.ELIMINADO }
            .sortedWith(compareBy({ it.orden }, { it.titulo }))

        Recurso.Exito(consejos)
    } catch (e: Exception) {
        Recurso.Error(mensajeDeError(e), e)
    }

    /** Obtiene un consejo puntual. */
    suspend fun obtener(id: String): Recurso<Consejo> = try {
        val doc = coleccion.document(id).get().await()
        if (doc.exists()) Recurso.Exito(Consejo.desde(doc))
        else Recurso.Error("El consejo ya no existe")
    } catch (e: Exception) {
        Recurso.Error(mensajeDeError(e), e)
    }

    // ==========================================================
    //  CREATE / UPDATE (Administrador)
    // ==========================================================

    /** Crea un consejo. Nace en BORRADOR segun el diagrama de estados. */
    suspend fun crear(consejo: Consejo): Recurso<String> = try {
        if (consejo.titulo.isBlank() || consejo.contenido.isBlank()) {
            Recurso.Error("El consejo debe tener titulo y contenido")
        } else {
            val borrador = consejo.copy(estado = EstadoConsejo.BORRADOR)
            val referencia = coleccion
                .add(borrador.aMapa(usarTimestampServidor = true))
                .await()
            Recurso.Exito(referencia.id)
        }
    } catch (e: Exception) {
        Recurso.Error(mensajeDeError(e), e)
    }

    /** Edita titulo, contenido, categoria u orden de un consejo. */
    suspend fun actualizar(consejo: Consejo): Recurso<Unit> = try {
        if (consejo.titulo.isBlank() || consejo.contenido.isBlank()) {
            Recurso.Error("El consejo debe tener titulo y contenido")
        } else {
            coleccion.document(consejo.id).update(
                mapOf(
                    Consejo.CAMPO_TITULO to consejo.titulo.trim(),
                    Consejo.CAMPO_CONTENIDO to consejo.contenido.trim(),
                    Consejo.CAMPO_TIPO to consejo.categoria.valor,
                    Consejo.CAMPO_ORDEN to consejo.orden
                )
            ).await()
            Recurso.Exito(Unit)
        }
    } catch (e: Exception) {
        Recurso.Error(mensajeDeError(e), e)
    }

    // ==========================================================
    //  Transiciones de estado (Administrador)
    // ==========================================================

    /** publicar() [Admin]: BORRADOR --> PUBLICADO. */
    suspend fun publicar(consejo: Consejo): Recurso<Unit> =
        aplicarTransicion(consejo, EstadoConsejo.PUBLICADO)

    /** despublicar() [Admin]: PUBLICADO --> BORRADOR. */
    suspend fun despublicar(consejo: Consejo): Recurso<Unit> =
        aplicarTransicion(consejo, EstadoConsejo.BORRADOR)

    /** archivar() [Admin]: PUBLICADO --> ARCHIVADO. */
    suspend fun archivar(consejo: Consejo): Recurso<Unit> =
        aplicarTransicion(consejo, EstadoConsejo.ARCHIVADO)

    /** restaurar() [Admin]: ARCHIVADO --> PUBLICADO. */
    suspend fun restaurar(consejo: Consejo): Recurso<Unit> =
        aplicarTransicion(consejo, EstadoConsejo.PUBLICADO)

    /** eliminar() [Admin]: borrado logico. */
    suspend fun eliminar(consejo: Consejo): Recurso<Unit> =
        aplicarTransicion(consejo, EstadoConsejo.ELIMINADO)

    private suspend fun aplicarTransicion(
        consejo: Consejo,
        destino: EstadoConsejo
    ): Recurso<Unit> = try {
        if (!consejo.estado.puedeTransicionarA(destino)) {
            Recurso.Error(
                "No se puede pasar de ${consejo.estado.valor} a ${destino.valor}"
            )
        } else {
            coleccion.document(consejo.id)
                .update(Consejo.CAMPO_ESTADO, destino.valor)
                .await()
            Recurso.Exito(Unit)
        }
    } catch (e: Exception) {
        Recurso.Error(mensajeDeError(e), e)
    }

    // ==========================================================
    //  Contenido inicial
    // ==========================================================

    /**
     * Siembra la seccion de consejos la primera vez que se abre.
     *
     * El RF0005 indica que el contenido puede ser estatico en esta
     * version; dejarlo en Firestore permite que el Administrador lo
     * edite mas adelante sin publicar una nueva version de la app.
     */
    suspend fun sembrarContenidoInicial(): Recurso<Int> = try {
        val existentes = coleccion.limit(1).get().await()

        if (!existentes.isEmpty) {
            Recurso.Exito(0)
        } else {
            var creados = 0
            contenidoInicial().forEach { consejo ->
                runCatching {
                    coleccion.add(consejo.aMapa(usarTimestampServidor = true)).await()
                    creados++
                }
            }
            Recurso.Exito(creados)
        }
    } catch (e: Exception) {
        Recurso.Error(mensajeDeError(e), e)
    }

    /** Consejos base sobre el cuidado de los canales hidricos. */
    private fun contenidoInicial(): List<Consejo> = listOf(
        Consejo(
            titulo = "No arrojes residuos a los canales",
            contenido = "Las basuras que llegan a un canal obstruyen el flujo del agua y " +
                    "favorecen inundaciones en epoca de lluvias. Separa tus residuos y " +
                    "entregalos en los puntos de recoleccion de tu localidad. Un solo " +
                    "envase plastico puede tardar cientos de anos en degradarse dentro del agua.",
            categoria = CategoriaConsejo.PREVENCION,
            estado = EstadoConsejo.PUBLICADO,
            orden = 1
        ),
        Consejo(
            titulo = "Evita verter aceites por el desague",
            contenido = "Un litro de aceite de cocina puede contaminar hasta mil litros de " +
                    "agua. Guarda el aceite usado en un recipiente cerrado y llevalo a un " +
                    "punto de recoleccion. Nunca lo viertas por el lavaplatos ni por el sifon.",
            categoria = CategoriaConsejo.PREVENCION,
            estado = EstadoConsejo.PUBLICADO,
            orden = 2
        ),
        Consejo(
            titulo = "Como hacer un buen reporte",
            contenido = "Un reporte util necesita tres cosas: una foto donde se vea " +
                    "claramente el problema, una descripcion breve de lo que observaste " +
                    "(que, cuanto y desde cuando) y la ubicacion exacta. Entre mas precisa " +
                    "sea la informacion, mas rapido pueden actuar las entidades competentes.",
            categoria = CategoriaConsejo.COMO_REPORTAR,
            estado = EstadoConsejo.PUBLICADO,
            orden = 3
        ),
        Consejo(
            titulo = "Que fotografiar como evidencia",
            contenido = "Toma la foto con buena luz y desde una distancia que permita " +
                    "reconocer el lugar. Si hay una descarga visible, incluye el punto de " +
                    "vertimiento. Evita fotografiar personas: el reporte es sobre el dano " +
                    "ambiental, no sobre quienes esten en el sitio.",
            categoria = CategoriaConsejo.COMO_REPORTAR,
            estado = EstadoConsejo.PUBLICADO,
            orden = 4
        ),
        Consejo(
            titulo = "Reduce tu consumo de agua en casa",
            contenido = "Cerrar la llave mientras te cepillas ahorra cerca de 12 litros por " +
                    "lavado. Revisar fugas en sanitarios y griferias puede representar hasta " +
                    "un 10 % de ahorro en el recibo. Menos consumo significa menos agua " +
                    "residual llegando a los canales.",
            categoria = CategoriaConsejo.AHORRO,
            estado = EstadoConsejo.PUBLICADO,
            orden = 5
        ),
        Consejo(
            titulo = "Normativa ambiental en Colombia",
            contenido = "El Decreto 1076 de 2015 reune la normativa del sector ambiental y " +
                    "regula los vertimientos a cuerpos de agua. La Resolucion 631 de 2015 " +
                    "fija los limites maximos permisibles. Reportar un vertimiento ilegal " +
                    "es un derecho ciudadano amparado por la Ley 99 de 1993.",
            categoria = CategoriaConsejo.NORMATIVA,
            estado = EstadoConsejo.PUBLICADO,
            orden = 6
        ),
        Consejo(
            titulo = "Que hacer ante una contaminacion grave",
            contenido = "Si observas un vertimiento de gran volumen, peces muertos o un olor " +
                    "quimico intenso: no te acerques al agua ni la manipules, registra el " +
                    "reporte con el nivel de riesgo mas alto y comunicate con la autoridad " +
                    "ambiental de tu ciudad. En Bogota, la Secretaria Distrital de Ambiente " +
                    "atiende este tipo de emergencias.",
            categoria = CategoriaConsejo.EMERGENCIA,
            estado = EstadoConsejo.PUBLICADO,
            orden = 7
        ),
        Consejo(
            titulo = "Participa en jornadas de limpieza",
            contenido = "Las jornadas comunitarias de limpieza de rondas hidricas son una de " +
                    "las formas mas efectivas de recuperar un canal. Consulta con tu junta de " +
                    "accion comunal o con organizaciones ambientales locales. Un reporte " +
                    "publicado en Aqualert puede ser el punto de partida para organizar una.",
            categoria = CategoriaConsejo.PREVENCION,
            estado = EstadoConsejo.PUBLICADO,
            orden = 8
        )
    )

    private fun mensajeDeError(e: Exception): String {
        val texto = e.message.orEmpty()
        return when {
            texto.contains("PERMISSION_DENIED", true) ->
                "No tienes permisos para gestionar los consejos"
            texto.contains("UNAVAILABLE", true) || texto.contains("network", true) ->
                "Sin conexion a internet. Revisa tu red e intenta de nuevo."
            texto.contains("NOT_FOUND", true) ->
                "El consejo ya no existe"
            else -> "No se pudo cargar la seccion de consejos."
        }
    }
}
