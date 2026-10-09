package com.ratig.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Material 3 shape scale. Slightly rounder than the M3 baseline so the large
 * touch targets (buttons, cards, dialogs) feel friendly and easy to hit - the
 * app is used by people of all ages on a handheld device.
 */
val RatigShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)
