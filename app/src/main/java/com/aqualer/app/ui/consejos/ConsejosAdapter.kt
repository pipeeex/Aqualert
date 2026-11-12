package com.aqualer.app.ui.consejos

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import androidx.transition.TransitionManager
import com.aqualer.app.R
import com.aqualer.app.data.model.CategoriaConsejo
import com.aqualer.app.data.model.Consejo
import com.aqualer.app.databinding.ItemConsejoBinding

/**
 * Adaptador de la seccion de consejos (RF0005).
 *
 * Cada consejo se muestra plegado con su titulo y categoria, y se
 * despliega al tocarlo. Asi la persona recorre todos los temas de un
 * vistazo y abre solo el que le interesa, en lugar de enfrentarse a
 * un muro de texto.
 */
class ConsejosAdapter :
    ListAdapter<Consejo, ConsejosAdapter.ConsejoViewHolder>(COMPARADOR) {

    /** Identificadores de los consejos desplegados. */
    private val desplegados = mutableSetOf<String>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ConsejoViewHolder {
        val binding = ItemConsejoBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ConsejoViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ConsejoViewHolder, position: Int) {
        holder.enlazar(getItem(position))
    }

    inner class ConsejoViewHolder(
        private val binding: ItemConsejoBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun enlazar(consejo: Consejo) {
            binding.txtTitulo.text = consejo.titulo
            binding.txtContenido.text = consejo.contenido
            binding.txtCategoria.text = consejo.categoria.etiqueta
            binding.imgCategoria.setImageResource(iconoDe(consejo.categoria))

            aplicarDespliegue(desplegados.contains(consejo.id))

            binding.root.setOnClickListener {
                val abierto = desplegados.contains(consejo.id)
                if (abierto) desplegados.remove(consejo.id)
                else desplegados.add(consejo.id)

                TransitionManager.beginDelayedTransition(binding.root)
                aplicarDespliegue(!abierto)
            }
        }

        private fun aplicarDespliegue(abierto: Boolean) {
            binding.txtContenido.visibility = if (abierto) View.VISIBLE else View.GONE
            binding.imgIndicador.rotation = if (abierto) 180f else 0f
        }

        /** Icono que identifica cada categoria de consejo. */
        private fun iconoDe(categoria: CategoriaConsejo): Int = when (categoria) {
            CategoriaConsejo.PREVENCION -> R.drawable.ic_check
            CategoriaConsejo.AHORRO -> R.drawable.ic_consejos
            CategoriaConsejo.COMO_REPORTAR -> R.drawable.ic_camara
            CategoriaConsejo.NORMATIVA -> R.drawable.ic_admin
            CategoriaConsejo.EMERGENCIA -> R.drawable.ic_alerta
        }
    }

    companion object {
        private val COMPARADOR = object : DiffUtil.ItemCallback<Consejo>() {
            override fun areItemsTheSame(anterior: Consejo, nuevo: Consejo): Boolean =
                anterior.id == nuevo.id

            override fun areContentsTheSame(anterior: Consejo, nuevo: Consejo): Boolean =
                anterior == nuevo
        }
    }
}