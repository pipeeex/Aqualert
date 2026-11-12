package com.aqualer.app.ui.consejos

import android.os.Bundle
import android.view.View
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.aqualer.app.data.model.CategoriaConsejo
import com.aqualer.app.databinding.ActivityConsejosBinding
import com.aqualer.app.util.Recurso
import com.google.android.material.chip.Chip
import com.google.android.material.snackbar.Snackbar

/**
 * Seccion informativa sobre el cuidado del agua (RF0005).
 *
 * Caso de uso UC9 "Consultar consejos", disponible tanto para el
 * Visitante como para el Ciudadano, con el filtro por categoria que
 * exige el diagrama de casos de uso extendidos.
 */
class ConsejosActivity : AppCompatActivity() {

    private lateinit var binding: ActivityConsejosBinding
    private val viewModel: ConsejosViewModel by viewModels()

    private lateinit var adaptador: ConsejosAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityConsejosBinding.inflate(layoutInflater)
        setContentView(binding.root)

        configurarLista()
        binding.toolbar.setNavigationOnClickListener { finish() }

        observarViewModel()
        viewModel.cargar()
    }

    private fun configurarLista() {
        adaptador = ConsejosAdapter()
        binding.listaConsejos.apply {
            layoutManager = LinearLayoutManager(this@ConsejosActivity)
            adapter = adaptador
        }
    }

    private fun observarViewModel() {
        viewModel.consejos.observe(this) { estado ->
            when (estado) {
                is Recurso.Cargando -> {
                    binding.progreso.visibility = View.VISIBLE
                    binding.txtVacio.visibility = View.GONE
                }

                is Recurso.Exito -> {
                    binding.progreso.visibility = View.GONE
                    adaptador.submitList(estado.datos)
                    binding.txtVacio.visibility =
                        if (estado.datos.isEmpty()) View.VISIBLE else View.GONE

                    // Los chips se arman una sola vez, cuando ya hay datos
                    if (binding.grupoCategorias.childCount == 0) {
                        construirFiltros()
                    }
                }

                is Recurso.Error -> {
                    binding.progreso.visibility = View.GONE
                    Snackbar.make(binding.root, estado.mensaje, Snackbar.LENGTH_LONG).show()
                }
            }
        }
    }

    /**
     * Construye los chips de filtro con las categorias que realmente
     * tienen contenido publicado, mas la opcion "Todos".
     */
    private fun construirFiltros() {
        val disponibles = viewModel.categoriasDisponibles()
        if (disponibles.isEmpty()) return

        binding.grupoCategorias.removeAllViews()
        binding.grupoCategorias.addView(crearChip("Todos", null, marcado = true))

        disponibles.forEach { categoria ->
            binding.grupoCategorias.addView(
                crearChip(categoria.etiqueta, categoria, marcado = false)
            )
        }
    }

    private fun crearChip(
        texto: String,
        categoria: CategoriaConsejo?,
        marcado: Boolean
    ): Chip = Chip(this).apply {
        text = texto
        isCheckable = true
        isChecked = marcado
        setOnClickListener { viewModel.filtrarPor(categoria) }
    }
}