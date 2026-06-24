package com.example.elevatorvision.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import com.example.elevatorvision.yolo.Detection


@Composable
fun BoundingBoxCanvas(
    detections: List<Detection>,
    modifier: Modifier
) {
    Canvas(modifier = modifier) {

        val w = size.width
        val h = size.height

        detections.forEach { det ->
            val box = det.box

            val left = box.left * w
            val top = box.top * h
            val width = (box.right - box.left) * w
            val height = (box.bottom - box.top) * h

            // 🔲 박스
            drawRect(
                color = Color.Red,
                topLeft = Offset(left, top),
                size = Size(width, height),
                style = Stroke(width = 4f)
            )
        }
    }
}