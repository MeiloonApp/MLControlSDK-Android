package com.meiloon.mlcontrolcore_aos.data

data class ChipChannel(val chip: Int, val channel: Int) {

    fun toFormattedString(): String {
        return "$chip-$channel"
    }
}
