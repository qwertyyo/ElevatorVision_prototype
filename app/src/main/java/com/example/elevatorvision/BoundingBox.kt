package com.example.elevatorvision

data class BoundingBox(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    val confidence: Float = 0f
)