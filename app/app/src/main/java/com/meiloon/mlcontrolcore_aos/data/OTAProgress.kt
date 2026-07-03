package com.meiloon.mlcontrolcore_aos.data

data class OTAProgress(
    //0: 下载资源, 1: 更新韌體
    val type: Int,
    val progress: Float
)
