/**
 * Copyright (c) 2025 lcaohoanq. All rights reserved.
 * This software is the confidential and proprietary information of lcaohoanq.
 * You shall not disclose such confidential information and shall use it only in
 * accordance with the terms of the license agreement you entered into with lcaohoanq.
 */
package com.billlocket.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.billlocket.app.domain.model.ThemeMode

private val BillLocketDarkColorScheme = darkColorScheme(
    primary = GrayPrimary,
    onPrimary = GrayOnPrimary,
    primaryContainer = GrayPrimaryDark,
    onPrimaryContainer = GraySecondary,
    secondary = GraySecondary,
    onSecondary = GrayOnSecondary,
    secondaryContainer = Color(0xFF322B1A),
    onSecondaryContainer = Color(0xFFFFE5A3),
    tertiary = GrayTertiary,
    onTertiary = GrayOnPrimary,
    background = GrayBackground,
    onBackground = GrayOnBackground,
    surface = GraySurface,
    onSurface = GrayOnSurface,
    surfaceVariant = GraySurfaceVariant,
    onSurfaceVariant = GrayOnSurfaceVariant,
    outline = GrayOutline,
    outlineVariant = Color(0xFF2E2D28),
    error = GrayError,
    onError = GrayOnError,
)

private val BillLocketLightColorScheme = lightColorScheme(
    primary = Color(0xFF8A6500),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFE4A0),
    onPrimaryContainer = Color(0xFF2B2100),
    secondary = Color(0xFF6C5D33),
    background = Color(0xFFFFFBF2),
    onBackground = Color(0xFF181713),
    surface = Color(0xFFFFFEFA),
    onSurface = Color(0xFF181713),
    surfaceVariant = Color(0xFFF2EDE2),
    onSurfaceVariant = Color(0xFF625E55),
    outline = Color(0xFFD1C9B8),
)

private val BillLocketShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(16.dp),
    medium = RoundedCornerShape(22.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(36.dp),
)

@Composable
fun AppTheme(
    mode: ThemeMode = ThemeMode.DARK,
    content: @Composable () -> Unit,
) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    MaterialTheme(
        colorScheme = if (dark) BillLocketDarkColorScheme else BillLocketLightColorScheme,
        typography = Typography,
        shapes = BillLocketShapes,
        content = content,
    )
}
