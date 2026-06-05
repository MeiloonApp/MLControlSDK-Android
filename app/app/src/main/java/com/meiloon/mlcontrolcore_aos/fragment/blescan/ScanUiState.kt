package com.meiloon.mlcontrolcore_aos.fragment.blescan

sealed interface ScanUiState {
    object Idle : ScanUiState
    object Scanning : ScanUiState
    data class Connected(val address: String) : ScanUiState
}
