package io.github.roviicc.colordict.design

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

object PopupCorners {
    val compact = 8.dp
    val control = 16.dp
    val prominent = 28.dp
    val pill = 999.dp
}

val PopupShapes = Shapes(
    extraSmall = RoundedCornerShape(PopupCorners.compact),
    small = RoundedCornerShape(PopupCorners.compact),
    medium = RoundedCornerShape(PopupCorners.control),
    large = RoundedCornerShape(PopupCorners.prominent),
    extraLarge = RoundedCornerShape(PopupCorners.pill),
)
