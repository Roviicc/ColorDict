package io.github.roviicc.colordict.design

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.airbnb.android.showkase.annotation.ShowkaseTypography

@ShowkaseTypography(name = "Brand title", group = "Typography")
val PopupBrandTitle = TextStyle(
    fontFamily = FontFamily.SansSerif,
    fontWeight = FontWeight.SemiBold,
    fontSize = 25.sp,
    lineHeight = 30.sp,
    letterSpacing = (-0.4).sp,
)

@ShowkaseTypography(name = "Section label", group = "Typography")
val PopupSectionLabel = TextStyle(
    fontFamily = FontFamily.SansSerif,
    fontWeight = FontWeight.Bold,
    fontSize = 13.sp,
    lineHeight = 18.sp,
    letterSpacing = 0.5.sp,
)

@ShowkaseTypography(name = "Body", group = "Typography")
val PopupBody = TextStyle(
    fontFamily = FontFamily.SansSerif,
    fontWeight = FontWeight.Normal,
    fontSize = 16.sp,
    lineHeight = 24.sp,
)

val PopupTypography = Typography(
    headlineMedium = PopupBrandTitle,
    titleMedium = PopupSectionLabel,
    bodyLarge = PopupBody,
    labelLarge = PopupBody.copy(fontWeight = FontWeight.Medium),
)
