package com.meiloon.mlcontrolcore_aos.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.meiloon.controlcore.main.container.chart.data.EQData
import com.meiloon.mlcontrolcore_aos.R
import com.meiloon.mlcontrolcore_aos.databinding.ItemPeqResultBinding
import java.util.Locale

class PeqResultAdapter : RecyclerView.Adapter<PeqResultAdapter.ViewHolder>() {

    private var items = listOf<EQData>()

    fun submitList(newList: List<EQData>) {
        items = newList
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemPeqResultBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class ViewHolder(private val binding: ItemPeqResultBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: EQData) {
            binding.tvBandIndex.text = String.format(Locale.getDefault(), "Band %d", item.index)
            binding.tvFreq.text = String.format(Locale.getDefault(), "%d Hz", item.freq)
            binding.tvGain.text = String.format(Locale.getDefault(), "%.1f dB", item.gain)
            binding.tvQ.text = String.format(Locale.getDefault(), "Q: %.2f", item.q)

            val context = binding.root.context
            if (item.gain >= 0) {
                binding.tvGain.setTextColor(ContextCompat.getColor(context, R.color.status_orange_text))
            } else {
                binding.tvGain.setTextColor(ContextCompat.getColor(context, R.color.status_blue_text))
            }
        }
    }
}