package com.meiloon.mlcontrolcore_aos.adapter

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.recyclerview.widget.RecyclerView
import com.meiloon.mlcontrolcore_aos.R
import com.meiloon.mlcontrolcore_aos.data.TempFileItem
import com.meiloon.mlcontrolcore_aos.databinding.ItemTempFileBinding

class TempFileAdapter(
    private val onSetNF: (TempFileItem) -> Unit,
    private val onSetFF: (TempFileItem) -> Unit,
    private val onShare: (TempFileItem) -> Unit,
    private val onDelete: (TempFileItem) -> Unit
) : RecyclerView.Adapter<TempFileAdapter.ViewHolder>() {

    private var items = listOf<TempFileItem>()

    fun submitList(newList: List<TempFileItem>) {
        items = newList
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemTempFileBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class ViewHolder(private val binding: ItemTempFileBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: TempFileItem) {
            binding.tvFileName.text = item.name
            binding.tvDate.text = item.date
            
            val context = binding.root.context
            when (item.tag) {
                "NF" -> {
                    binding.tvTag.visibility = View.VISIBLE
                    binding.tvTag.text = "NF"
                    binding.tvTag.setTextColor(ContextCompat.getColor(context, R.color.status_blue_text))
                    ViewCompat.setBackgroundTintList(binding.tvTag, ColorStateList.valueOf(ContextCompat.getColor(context, R.color.status_blue_bg)))
                    
                    binding.btnSetNF.isEnabled = false
                    binding.btnSetNF.alpha = 0.5f
                    binding.btnSetFF.isEnabled = true
                    binding.btnSetFF.alpha = 1.0f
                }
                "FF" -> {
                    binding.tvTag.visibility = View.VISIBLE
                    binding.tvTag.text = "FF"
                    binding.tvTag.setTextColor(ContextCompat.getColor(context, R.color.status_green_text))
                    ViewCompat.setBackgroundTintList(binding.tvTag, ColorStateList.valueOf(ContextCompat.getColor(context, R.color.status_green_bg)))

                    binding.btnSetNF.isEnabled = true
                    binding.btnSetNF.alpha = 1.0f
                    binding.btnSetFF.isEnabled = false
                    binding.btnSetFF.alpha = 0.5f
                }
                else -> {
                    binding.tvTag.visibility = View.GONE
                    binding.btnSetNF.isEnabled = true
                    binding.btnSetNF.alpha = 1.0f
                    binding.btnSetFF.isEnabled = true
                    binding.btnSetFF.alpha = 1.0f
                }
            }

            binding.btnSetNF.setOnClickListener { onSetNF(item) }
            binding.btnSetFF.setOnClickListener { onSetFF(item) }
            binding.btnShare.setOnClickListener { onShare(item) }
            binding.btnDelete.setOnClickListener { onDelete(item) }
        }
    }
}