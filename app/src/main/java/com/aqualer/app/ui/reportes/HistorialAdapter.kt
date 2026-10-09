package com.aqualer.app.ui.reportes

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.aqualer.app.data.model.HistorialEstado
import com.aqualer.app.databinding.ItemHistorialBinding
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Adaptador de la linea de tiempo de estados de un reporte.
 */
class HistorialAdapter :
    ListAdapter<HistorialEstado, HistorialAdapter.HistorialViewHolder>(COMPARADOR) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HistorialViewHolder {
        val binding = ItemHistorialBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return HistorialViewHolder(binding)
    }

    override fun onBindViewHolder(holder: HistorialViewHolder, position: Int) {
        holder.enlazar(
            traza = getItem(position),
            esPrimera = position == 0,
            esUltima = position == itemCount - 1
        )
    }

    inner class HistorialViewHolder(
        private val binding: ItemHistorialBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        private val formatoFecha = SimpleDateFormat("dd MMM, HH:mm", Locale("es", "CO"))

        fun enlazar(traza: HistorialEstado, esPrimera: Boolean, esUltima: Boolean) {
            val contexto = binding.root.context

            binding.txtEstado.text = contexto.getString(traza.estadoNuevo.etiqueta)
            binding.txtFecha.text = formatoFecha.format(traza.fecha)

            // El punto toma el color del estado al que se transiciono
            binding.puntoEstado.backgroundTintList =
                ContextCompat.getColorStateList(contexto, traza.estadoNuevo.color)

            // Quien ejecuto el cambio: una persona o el propio sistema
            binding.txtResponsable.text = if (traza.esAutomatico) {
                traza.nombreUsuario.ifBlank { "Sistema" }
            } else {
                traza.nombreUsuario.ifBlank { "Usuario" }
            }

            binding.txtObservacion.visibility =
                if (traza.observacion.isBlank()) View.GONE else View.VISIBLE
            binding.txtObservacion.text = traza.observacion

            // La linea vertical no se dibuja antes del primero ni despues del ultimo
            binding.lineaSuperior.visibility = if (esPrimera) View.INVISIBLE else View.VISIBLE
            binding.lineaInferior.visibility = if (esUltima) View.INVISIBLE else View.VISIBLE
        }
    }

    companion object {
        private val COMPARADOR = object : DiffUtil.ItemCallback<HistorialEstado>() {
            override fun areItemsTheSame(
                anterior: HistorialEstado,
                nuevo: HistorialEstado
            ): Boolean = anterior.id == nuevo.id

            override fun areContentsTheSame(
                anterior: HistorialEstado,
                nuevo: HistorialEstado
            ): Boolean = anterior == nuevo
        }
    }
}