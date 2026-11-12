package com.aqualer.app.ui.consejos

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aqualer.app.data.model.CategoriaConsejo
import com.aqualer.app.data.model.Consejo
import com.aqualer.app.data.service.ConsejosService
import com.aqualer.app.util.Recurso
import kotlinx.coroutines.launch

/**
 * ViewModel de la seccion informativa sobre el cuidado del agua (RF0005).
 *
 * Corresponde al modulo TipsUI del diagrama de componentes y cubre:
 *   UC9 Consultar consejos
 *     --include--> Ver consejos por categoria
 *
 * La primera vez que se abre la seccion se siembra el contenido base
 * en Firestore, de modo que el Administrador pueda editarlo despues
 * sin publicar una nueva version de la aplicacion.
 */
class ConsejosViewModel(
    private val consejosService: ConsejosService = ConsejosService()
) : ViewModel() {

    /** Consejos que se muestran en pantalla. */
    private val _consejos = MutableLiveData<Recurso<List<Consejo>>>()
    val consejos: LiveData<Recurso<List<Consejo>>> = _consejos

    /** Categoria activa; null significa "todas". */
    private val _categoria = MutableLiveData<CategoriaConsejo?>(null)
    val categoria: LiveData<CategoriaConsejo?> = _categoria

    /** Listado completo, para filtrar sin volver a consultar la red. */
    private var todos: List<Consejo> = emptyList()

    /**
     * Carga la seccion. Siembra el contenido inicial si la coleccion
     * esta vacia y luego publica el listado.
     */
    fun cargar() {
        _consejos.value = Recurso.Cargando
        viewModelScope.launch {
            consejosService.sembrarContenidoInicial()

            when (val resultado = consejosService.listarPublicados()) {
                is Recurso.Exito -> {
                    todos = resultado.datos
                    aplicarFiltro()
                }
                is Recurso.Error -> _consejos.value = resultado
                else -> Unit
            }
        }
    }

    /** "Ver consejos por categoria": filtra sobre lo ya descargado. */
    fun filtrarPor(categoria: CategoriaConsejo?) {
        _categoria.value = categoria
        aplicarFiltro()
    }

    private fun aplicarFiltro() {
        val activa = _categoria.value
        val filtrados = if (activa == null) todos
        else todos.filter { it.categoria == activa }

        _consejos.value = Recurso.Exito(filtrados)
    }

    /** Categorias que realmente tienen consejos publicados. */
    fun categoriasDisponibles(): List<CategoriaConsejo> =
        todos.map { it.categoria }.distinct()
}