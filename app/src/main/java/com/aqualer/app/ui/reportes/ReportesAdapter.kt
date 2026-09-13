package com.aqualer.app.ui.reportes

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.aqualer.app.databinding.ItemReporteBinding
import com.aqualer.app.data.model.Reporte
import com.aqualer.app.util.FotosDemo
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Adaptador del listado de reportes (RF0002: Visualizacion de reportes).
 *
 * Cada elemento muestra la evidencia fotografica, el titulo, el tipo
 * de contaminacion, el estado actual y el nivel de riesgo, de modo que
 * el usuario identifique el reporte sin abrir el detalle.
 */
class ReportesAdapter(
    private val alTocar: (Reporte) -> Unit,
    private val alPedirEliminar: (Reporte) -> Unit
) : ListAdapter<Reporte, ReportesAdapter.ReporteViewHolder>(COMPARADOR) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ReporteViewHolder {
        val binding = ItemReporteBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ReporteViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ReporteViewHolder, position: Int) {
        holder.enlazar(getItem(position))
    }

    inner class ReporteViewHolder(
        private val binding: ItemReporteBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        private val formatoFecha = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale("es", "CO"))

        fun enlazar(reporte: Reporte) {
            val contexto = binding.root.context

            // ImageView: evidencia fotografica simulada
            binding.imgEvidencia.setImageResource(
                FotosDemo.drawableDesde(reporte.imagenUrl)
            )

            binding.txtTitulo.text = reporte.titulo
            binding.txtDescripcion.text = reporte.descripcion
            binding.txtTipo.text = reporte.tipo.etiqueta
            binding.txtAutor.text = reporte.autorVisible
            binding.txtFecha.text = formatoFecha.format(reporte.fechaCreacion)

            // Etiqueta de estado con el color de la maquina de estados
            binding.txtEstado.text = contexto.getString(reporte.estado.etiqueta)
            binding.txtEstado.backgroundTintList = ContextCompat.getColorStateList(
                contexto, reporte.estado.color
            )

            // Nivel de riesgo elegido con los botones de radio
            binding.txtRiesgo.text = reporte.nivelRiesgo.valor.uppercase()
            binding.txtRiesgo.setTextColor(
                ContextCompat.getColor(contexto, reporte.nivelRiesgo.color)
            )

            // Marca de la casilla "atencion urgente"
            binding.txtUrgente.visibility =
                if (reporte.atencionUrgente) View.VISIBLE else View.GONE

            binding.root.setOnClickListener { alTocar(reporte) }
            binding.btnEliminar.setOnClickListener { alPedirEliminar(reporte) }
        }
    }

    companion object {
        /** Evita redibujar toda la lista cuando solo cambia un elemento. */
        private val COMPARADOR = object : DiffUtil.ItemCallback<Reporte>() {
            override fun areItemsTheSame(anterior: Reporte, nuevo: Reporte): Boolean =
                anterior.id == nuevo.id

            override fun areContentsTheSame(anterior: Reporte, nuevo: Reporte): Boolean =
                anterior == nuevo
        }
    }
}