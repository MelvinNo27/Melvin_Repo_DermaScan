package com.example.dermascanai

data class ScanReport(
    val dermaId: String = "",
    val userName: String = "",
    val message: String = "",
    val timestamp: Long = System.currentTimeMillis()
)
