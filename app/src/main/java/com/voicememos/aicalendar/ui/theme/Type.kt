package com.voicememos.aicalendar.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val ShengliTypography = Typography(
    displayMedium = Typography().displayMedium.copy(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Bold,
        fontSize = 42.sp,
        letterSpacing = 0.sp,
    ),
    headlineSmall = Typography().headlineSmall.copy(
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.sp,
    ),
    titleMedium = Typography().titleMedium.copy(
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.sp,
    ),
    bodyMedium = Typography().bodyMedium.copy(letterSpacing = 0.sp),
    labelLarge = Typography().labelLarge.copy(
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.sp,
    ),
)

