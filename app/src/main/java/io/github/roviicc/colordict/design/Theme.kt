package io.github.roviicc.colordict.design

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColors = lightColorScheme(
    primary = PopupAzure500,
    onPrimary = PopupPaper,
    primaryContainer = PopupAzure100,
    onPrimaryContainer = PopupNavy950,
    background = PopupPaper,
    onBackground = PopupNavy950,
    surface = PopupPaper,
    onSurface = PopupNavy950,
    onSurfaceVariant = PopupSlate500,
    outline = PopupDivider,
)

private val DarkColors = darkColorScheme(
    primary = PopupAzure500,
    onPrimary = PopupNavy950,
    primaryContainer = PopupNavy900,
    onPrimaryContainer = PopupNightText,
    background = PopupNavy950,
    onBackground = PopupNightText,
    surface = PopupNightPaper,
    onSurface = PopupNightText,
    onSurfaceVariant = PopupAzure100,
    outline = PopupSlate500,
)

@Composable
fun PopupDictionaryTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = PopupTypography,
        shapes = PopupShapes,
        content = content,
    )
}
