package com.meiloon.mlcontrolcore_aos.data

enum class EQDataType(val id: Int, val typeName: String) {
    OFF(0, "Off"),
    PEAK(1, "Peak");
    //暫時沒用
//    HIGH_PASS(2, "High Pass"),
//    LOW_PASS(3, "Low Pass"),
//    BAND_PASS(4, "Band Pass"),
//    NOTCH(5, "Notch"),
//    LOW_SHELF(6, "Low Shelf"),
//    HIGH_SHELF(7, "High Shelf");

    companion object {
        fun fromId(id: Int): EQDataType {
            val id = id

            if (id >= 240) {
               id -240
            }

            return entries.find { it.id == id } ?: OFF
        }

        fun getNameById(id: Int): String {
            val id = id

            if (id >= 240) {
                id -240
            }

            return fromId(id).typeName
        }
        
        fun getAllNames(): List<String> {
            return entries.map { it.typeName }
        }
    }
}
