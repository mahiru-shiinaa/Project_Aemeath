package com.aemeath.app.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

fun Modifier.softCard(
    shape: Shape = RoundedCornerShape(18.dp)
): Modifier = this
    .shadow(
        elevation = 10.dp,
        shape = shape,
        ambientColor = Color.Black.copy(alpha = 0.35f),
        spotColor = Color.Black.copy(alpha = 0.25f)
    )
    .border(
        width = 1.dp,
        color = Color.White.copy(alpha = 0.05f),
        shape = shape
    )
