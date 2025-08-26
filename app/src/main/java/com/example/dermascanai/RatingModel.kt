package com.example.dermascanai

data class RatingModel(
    val rating: Float = 0f,
    val message: String = "",
    val timestamp: Long = 0L,
    var userName: String = "",
    val userId: String? = null,
    val reviewerPhoto: String? = null
)
