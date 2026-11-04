package com.c520.lamian.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/** 設計 token：照 DESIGN.md（沿用拉麵的 Linear 深色系），手機版只多一個觸控尺寸。 */
object T {
    val primary = Color(0xFF5E6AD2)
    val onPrimary = Color(0xFFFFFFFF)
    val ink = Color(0xFFF7F8F8)
    val inkMuted = Color(0xFFD0D6E0)
    val inkSubtle = Color(0xFF8A8F98)
    val canvas = Color(0xFF010102)
    val surface1 = Color(0xFF0F1011)
    val surface2 = Color(0xFF141516)
    val surface3 = Color(0xFF18191A)
    val hairline = Color(0xFF23252A)
    val hairlineStrong = Color(0xFF34343A)
    val success = Color(0xFF27A644)
    val danger = Color(0xFFEB5757)
    val successTint = Color(0x2627A644)
    /** 插畫用色：垂降的那條麵條（和角色圖同色系，不是介面色） */
    val illNoodle = Color(0xFFEDBB5F)

    val headline = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.SemiBold, lineHeight = 1.2.em)
    val cardTitle = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Medium, lineHeight = 1.25.em)
    val body = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Normal, lineHeight = 1.5.em)
    val bodySm = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Normal, lineHeight = 1.5.em)
    val caption = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Normal, lineHeight = 1.4.em)
    val eyebrow = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium, lineHeight = 1.3.em)
    val button = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium, lineHeight = 1.2.em)

    val rXs = 4.dp
    val rSm = 6.dp
    val rMd = 8.dp
    val rLg = 12.dp

    val sXxs = 4.dp
    val sXs = 8.dp
    val sSm = 12.dp
    val sMd = 16.dp
    val sLg = 24.dp
    val sXl = 32.dp

    /** 手機版新增：按鈕與可點區域至少 44dp */
    val touch = 44.dp
}

@Composable
fun LamianTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = T.primary, onPrimary = T.onPrimary,
            background = T.canvas, onBackground = T.ink,
            surface = T.surface1, onSurface = T.ink, surfaceVariant = T.surface2, onSurfaceVariant = T.inkMuted,
            surfaceContainerHigh = T.surface2, surfaceContainer = T.surface1,
            outline = T.hairlineStrong, outlineVariant = T.hairline, error = T.danger,
        ),
        typography = Typography(bodyMedium = T.bodySm, bodyLarge = T.body, labelLarge = T.button, titleLarge = T.cardTitle),
        content = content,
    )
}
