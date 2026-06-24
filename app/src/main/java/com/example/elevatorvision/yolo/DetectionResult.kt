package com.example.elevatorvision.yolo

data class DetectionResult(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    val confidence: Float,
    val classId: Int
)