package com.ratig.app.ui.theme

import androidx.compose.ui.graphics.Color

// Brand
val GreenPrimary = Color(0xFF1B5E20)
val GreenPrimaryDark = Color(0xFFA5D6A7) // dark-mode primary (on dark surfaces)
val TealSecondary = Color(0xFF00695C)
val TealSecondaryDark = Color(0xFF80CBC4)
val AmberTertiary = Color(0xFFF57F17)
val AmberTertiaryDark = Color(0xFFFFD54F)

// Risk severity scale - used WITH labels/icons, never color alone.
val SeverityOk = Color(0xFF2E7D32)      // band severity 0-1
val SeverityInfo = Color(0xFF1565C0)    // band severity 2
val SeverityWarn = Color(0xFFEF6C00)    // band severity 3
val SeverityHigh = Color(0xFFC62828)    // band severity 4+
val SeverityUnknown = Color(0xFF616161) // unvalidated / insufficient data

// Neutral surfaces (light)
val SurfaceLight = Color(0xFFF6F8F5)
val SurfaceDark = Color(0xFF101410)
