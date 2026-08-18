package com.example.elevatorvision.yolo

data class DetectionResult(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    val confidence: Float,
    val classId: Int,
    val className: String? = null   // 🌟 추가: 저장 시점의 클래스 이름을 같이 보관 (하위호환 위해 nullable)
)