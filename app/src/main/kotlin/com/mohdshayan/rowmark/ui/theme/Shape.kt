package com.mohdshayan.rowmark.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/*
 * The whole radius scale, and the rule for using it:
 *   RadiusSm  chips, small controls
 *   RadiusMd  buttons, fields
 *   RadiusLg  the count pad, sheet tops
 * Pattern pages stay square. Nothing in the app uses a corner radius that is not one of these.
 */
val RadiusSm = 8.dp
val RadiusMd = 14.dp
val RadiusLg = 28.dp

val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(RadiusSm),
    small = RoundedCornerShape(RadiusSm),
    medium = RoundedCornerShape(RadiusMd),
    large = RoundedCornerShape(RadiusLg),
    extraLarge = RoundedCornerShape(RadiusLg),
)
