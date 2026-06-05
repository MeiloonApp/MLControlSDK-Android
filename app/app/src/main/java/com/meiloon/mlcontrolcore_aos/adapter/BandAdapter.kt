package com.meiloon.mlcontrolcore_aos.adapter

import android.content.Context
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.widget.doAfterTextChanged
import com.meiloon.mlcontrolcore_aos.data.EQDataType
import com.meiloon.mlcontrolcore_aos.databinding.ItemEqBinding
import com.google.android.material.slider.Slider
import com.meiloon.controlcore.main.container.chart.data.EQData
import com.meiloon.controlcore.widget.app.adapter.ViewHolder
import com.meiloon.controlcore.widget.app.android.AppAdapter
import kotlin.math.roundToInt

class BandAdapter: AppAdapter<EQData, ViewHolder<ItemEqBinding>>() {
    var onSendClickListener: ((position: Int, data: EQData) -> Unit)? = null
    var onDropdownClickListener: ((view: android.view.View, position: Int, data: EQData) -> Unit)? = null
    var onSliderStopChangeListener: ((view: android.view.View, position: Int, data: EQData) -> Unit)? = null
    var onDataUpdateListener: ((position: Int, data: EQData) -> Unit)? = null
    private val MIN_GAIN = -12f
    private val MAX_GAIN = 12f

    override fun onBindViewHolder(
        holder: ViewHolder<ItemEqBinding>,
        position: Int,
        item: EQData?,
        selectedPosition: Int
    ) {
        item?.let { data ->
            val binding = holder.binding

            binding.etFreqValue.setText(data.freq.toString())
            binding.etQValue.setText(String.format("%.2f", data.q))
            binding.tvBandTitle.text = "B${data.index}"
            binding.tvDbValue.text = "${data.gain} dB"

            (binding.etQValue.tag as? TextWatcher)?.let { binding.etQValue.removeTextChangedListener(it) }
            val qWatcher = binding.etQValue.doAfterTextChanged { text ->
                if (binding.etQValue.hasFocus()) {
                    val newQ = text.toString().toFloatOrNull()
                    if (newQ != null) {
                        data.q = newQ
                    }
                }
            }
            binding.etQValue.tag = qWatcher

            binding.etQValue.setOnFocusChangeListener { _, hasFocus ->
                if (!hasFocus) {
                    onDataUpdateListener?.invoke(data.index, data)
                }
            }

            (binding.etFreqValue.tag as? TextWatcher)?.let { binding.etFreqValue.removeTextChangedListener(it) }
            val freqWatcher = binding.etFreqValue.doAfterTextChanged { text ->
                if (binding.etFreqValue.hasFocus()) {
                    val newFreq = text.toString().toIntOrNull()
                    if (newFreq != null) {
                        data.freq = newFreq
                    }
                }
            }
            binding.etFreqValue.tag = freqWatcher

            binding.etFreqValue.setOnFocusChangeListener { _, hasFocus ->
                if (!hasFocus) {
                    onDataUpdateListener?.invoke(data.index, data)
                }
            }

            binding.slFrequency.value = data.gain.coerceIn(MIN_GAIN, MAX_GAIN)
            binding.slFrequency.clearOnChangeListeners()
            binding.slFrequency.addOnChangeListener { _, value, fromUser ->
                if (fromUser) {
                    val roundedGain = Math.round(value * 10) / 10f
                    
                    data.gain = roundedGain
                    binding.tvDbValue.text = "$roundedGain dB"
                }
            }

            binding.tvDropdownOff.text = EQDataType.fromId(data.type).typeName

            binding.ivSendBtn.setOnClickListener {
                onSendClickListener?.invoke(position, data)
            }

            binding.tvDropdownOff.setOnClickListener { view ->
                onDropdownClickListener?.invoke(view, position, data)
            }
            
            binding.slFrequency.clearOnSliderTouchListeners()
            binding.slFrequency.addOnSliderTouchListener(object : Slider.OnSliderTouchListener {
                override fun onStartTrackingTouch(p0: Slider) {}

                override fun onStopTrackingTouch(p0: Slider) {
                    val roundedGain = (p0.value * 10).roundToInt() / 10f
                    data.gain = roundedGain
                    // chartStorage的起始資料是從1開始
                    onSliderStopChangeListener?.invoke(p0, data.index, data)
                }
            })
        }
    }

    override fun onRecycled(holder: ViewHolder<ItemEqBinding>) {
        holder.binding.slFrequency.clearOnChangeListeners()
        (holder.binding.etQValue.tag as? TextWatcher)?.let { holder.binding.etQValue.removeTextChangedListener(it) }
        holder.binding.etQValue.tag = null
        (holder.binding.etFreqValue.tag as? TextWatcher)?.let { holder.binding.etFreqValue.removeTextChangedListener(it) }
        holder.binding.etFreqValue.tag = null
    }

    override fun onCreateViewHolder(
        context: Context,
        parent: ViewGroup,
        viewType: Int
    ): ViewHolder<ItemEqBinding>  {
        val binding = ItemEqBinding.inflate(LayoutInflater.from(context), parent, false)
        return ViewHolder<ItemEqBinding>(binding.getRoot(), binding)
    }

}