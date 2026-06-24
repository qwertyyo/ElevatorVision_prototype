package com.example.elevatorvision.yolo

import android.graphics.RectF

data class Detection(
    val box: RectF,        // [0..1] 정규화 좌표
    val confidence: Float, // 0~1
    val classId: Int
)