package com.meiloon.mlcontrolcore_aos.extension

/**
 * 將字串中的數字過濾出來並轉換為 Int，若失敗或無數字則回傳 0
 */
fun String.toIntOrZero(): Int = filter { it.isDigit() }.toIntOrNull() ?: 0
