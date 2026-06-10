package com.meiloon.mlcontrolcore_aos.data

import java.io.File

data class TempFileItem(
    val file: File,
    val name: String,
    val date: String,
    var tag: String? = null // "NF", "FF" or null
)