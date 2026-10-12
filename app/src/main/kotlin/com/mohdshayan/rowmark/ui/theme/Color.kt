package com.mohdshayan.rowmark.ui.theme

import androidx.compose.ui.graphics.Color

/*
 * Heather and yarn fuchsia, from BLUEPRINT.md section 7. One accent for the whole app, the same
 * token names in both modes. The two background tokens match the window background in colors.xml.
 */

// Light mode
val LightBackground = Color(0xFFF3EEF2)        // Heather
val LightSurface = Color(0xFFFBF8FA)           // Skein
val LightSurfaceVariant = Color(0xFFEDE6EB)    // a shade of Heather for skeletons and the count pad rim
val LightOutline = Color(0xFF625565)           // Twist
val LightOnBackground = Color(0xFF241A25)      // Ink
val LightOnSurfaceVariant = Color(0xFF625565)  // Twist
val LightAccent = Color(0xFFA32689)            // Fuchsia
val LightOnAccent = Color(0xFFFBF8FA)          // Skein on Fuchsia
val LightError = Color(0xFFA3401F)             // Frog

// Dark mode
val DarkBackground = Color(0xFF1A1519)
val DarkSurface = Color(0xFF251E24)
val DarkSurfaceVariant = Color(0xFF302731)
val DarkOutline = Color(0xFFB3A5B2)
val DarkOnBackground = Color(0xFFEEE6EC)
val DarkOnSurfaceVariant = Color(0xFFB3A5B2)
val DarkAccent = Color(0xFFE486CF)
val DarkOnAccent = Color(0xFF251E24)
val DarkError = Color(0xFFF09A7C)

/** The highlighter is user content, not chrome: one ink in both modes, laid at 40 percent. */
val HighlighterInk = Color(0xFFF2D23C)
const val HighlighterAlpha = 0.4f

/** Dividers are Twist at 24 percent, per the blueprint. */
const val DividerAlpha = 0.24f
