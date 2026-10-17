package com.example.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ============================================================================
// NEUMORPHIC COLOR PALETTE (From user's Soft UI screenshot: #0072ff, rgba(163,177,198,.65), etc.)
// ============================================================================
val NeuBackground = Color(0xFF0B1220)        // Pure white background for all screens
val NeuSurface = Color(0xFF142238)           // Subtle soft light gray card background (Slate 100)
val NeuSurfaceRaised = Color(0xFF1D304B)     // Clean light elevated surface / header (Slate 200)
val NeuDarkShadow = Color(0xFF020617)        // Soft light shadow
val NeuLightHighlight = Color(0xFF334862)    // Highlight border
val NeuInsetBg = Color(0xFF0D192A)           // Clean light input background
val NeuInsetBorder = Color(0xFF3B506B)       // Sunken input border

val NeuAccentBlue = Color(0xFF4F8CFF)        // Professional clean blue (Blue 600)
val NeuAccentBlueHover = Color(0xFF78A8FF)   // Deep hover state
val NeuAccentBlueGlow = Color(0x334F8CFF)    // Soft blue glow

val NeuTextPrimary = Color(0xFFF1F5F9)       // Dark slate primary text (Slate 900)
val NeuTextSecondary = Color(0xFFCBD5E1)     // Slate 600 secondary text
val NeuTextMuted = Color(0xFF94A3B8)         // Slate 500 muted text

val NeuSuccess = Color(0xFF34D399)           // Soft emerald
val NeuError = Color(0xFFFB7185)             // Soft crimson alert

// ============================================================================
// NEUMORPHIC REUSABLE COMPOSABLES
// ============================================================================

/**
 * Raised Neumorphic Card with soft dual elevation and top-left light border highlight.
 * Compact and sleek to fit content, icons, and texts crisply without wasted space.
 */
@Composable
fun NeuCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(13.dp),
    backgroundColor: Color = NeuSurface,
    elevation: Dp = 5.dp,
    contentPadding: PaddingValues = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    Box(
        modifier = modifier
            .shadow(
                elevation = elevation,
                shape = shape,
                ambientColor = NeuDarkShadow.copy(alpha = 0.45f),
                spotColor = NeuDarkShadow.copy(alpha = 0.60f)
            )
            .clip(shape)
            .background(backgroundColor)
            .border(
                border = BorderStroke(
                    width = 1.dp,
                    brush = Brush.linearGradient(
                        colors = listOf(
                            NeuLightHighlight.copy(alpha = 0.95f),
                            NeuLightHighlight.copy(alpha = 0.40f),
                            NeuDarkShadow.copy(alpha = 0.25f)
                        )
                    )
                ),
                shape = shape
            )
            .padding(contentPadding)
    ) {
        Column(content = content)
    }
}

/**
 * Sunken / Inset Neumorphic Box for TextFields and debossed controls.
 */
@Composable
fun NeuInsetBox(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(10.dp),
    backgroundColor: Color = NeuInsetBg,
    contentAlignment: Alignment = Alignment.CenterStart,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .clip(shape)
            .background(backgroundColor)
            .border(
                border = BorderStroke(
                    width = 1.dp,
                    brush = Brush.linearGradient(
                        colors = listOf(
                            NeuInsetBorder.copy(alpha = 0.85f),
                            NeuInsetBorder.copy(alpha = 0.40f),
                            NeuLightHighlight.copy(alpha = 0.80f)
                        )
                    )
                ),
                shape = shape
            )
            .padding(horizontal = 10.dp, vertical = 6.dp),
        contentAlignment = contentAlignment,
        content = content
    )
}

/**
 * Primary Neumorphic Button with vibrant blue gradient and soft blue drop glow.
 */
@Composable
fun NeuButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = RoundedCornerShape(10.dp),
    isPrimary: Boolean = true,
    content: @Composable RowScope.() -> Unit
) {
    val bgModifier = if (isPrimary) {
        Modifier
            .shadow(
                elevation = 6.dp,
                shape = shape,
                ambientColor = NeuAccentBlueGlow,
                spotColor = NeuAccentBlue.copy(alpha = 0.45f)
            )
            .clip(shape)
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        Color(0xFF268AFF),
                        NeuAccentBlue
                    )
                )
            )
    } else {
        Modifier
            .shadow(
                elevation = 4.dp,
                shape = shape,
                ambientColor = NeuDarkShadow.copy(alpha = 0.45f),
                spotColor = NeuDarkShadow.copy(alpha = 0.55f)
            )
            .clip(shape)
            .background(NeuSurfaceRaised)
            .border(
                border = BorderStroke(
                    width = 1.dp,
                    brush = Brush.linearGradient(
                        colors = listOf(
                            NeuLightHighlight.copy(alpha = 0.95f),
                            NeuDarkShadow.copy(alpha = 0.20f)
                        )
                    )
                ),
                shape = shape
            )
    }

    Box(
        modifier = modifier
            .then(bgModifier)
            .clickable(
                enabled = enabled,
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple()
            ) { onClick() }
            .padding(horizontal = 12.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            content = content
        )
    }
}

/**
 * Circular Neumorphic Action Button.
 */
@Composable
fun NeuCircleButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 36.dp,
    isPrimary: Boolean = false,
    content: @Composable BoxScope.() -> Unit
) {
    val bgModifier = if (isPrimary) {
        Modifier
            .shadow(
                elevation = 6.dp,
                shape = CircleShape,
                ambientColor = NeuAccentBlueGlow,
                spotColor = NeuAccentBlue.copy(alpha = 0.45f)
            )
            .clip(CircleShape)
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        Color(0xFF268AFF),
                        NeuAccentBlue
                    )
                )
            )
    } else {
        Modifier
            .shadow(
                elevation = 4.dp,
                shape = CircleShape,
                ambientColor = NeuDarkShadow.copy(alpha = 0.50f),
                spotColor = NeuDarkShadow.copy(alpha = 0.60f)
            )
            .clip(CircleShape)
            .background(NeuSurfaceRaised)
            .border(
                border = BorderStroke(
                    width = 1.dp,
                    brush = Brush.linearGradient(
                        colors = listOf(
                            NeuLightHighlight.copy(alpha = 0.95f),
                            NeuDarkShadow.copy(alpha = 0.25f)
                        )
                    )
                ),
                shape = CircleShape
            )
    }

    Box(
        modifier = modifier
            .size(size)
            .then(bgModifier)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple()
            ) { onClick() },
        contentAlignment = Alignment.Center,
        content = content
    )
}

/**
 * Neumorphic Badge / Icon Container.
 */
@Composable
fun NeuBadge(
    modifier: Modifier = Modifier,
    size: Dp = 36.dp,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .size(size)
            .shadow(
                elevation = 4.dp,
                shape = CircleShape,
                ambientColor = NeuDarkShadow.copy(alpha = 0.50f),
                spotColor = NeuDarkShadow.copy(alpha = 0.60f)
            )
            .clip(CircleShape)
            .background(NeuSurface)
            .border(
                border = BorderStroke(
                    width = 1.2.dp,
                    brush = Brush.linearGradient(
                        colors = listOf(
                            NeuLightHighlight,
                            NeuDarkShadow.copy(alpha = 0.30f)
                        )
                    )
                ),
                shape = CircleShape
            ),
        contentAlignment = Alignment.Center,
        content = content
    )
}

/**
 * Compact Icon Box to neatly contain symbols/icons inside cards and rows.
 */
@Composable
fun NeuIconBox(
    modifier: Modifier = Modifier,
    size: Dp = 32.dp,
    shape: Shape = RoundedCornerShape(8.dp),
    backgroundColor: Color = NeuSurfaceRaised,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(backgroundColor)
            .border(
                border = BorderStroke(
                    width = 1.dp,
                    brush = Brush.linearGradient(
                        colors = listOf(
                            NeuLightHighlight.copy(alpha = 0.95f),
                            NeuDarkShadow.copy(alpha = 0.25f)
                        )
                    )
                ),
                shape = shape
            ),
        contentAlignment = Alignment.Center,
        content = content
    )
}

/**
 * Sleek compact pill badge for status, quantities, chips, and counters.
 */
@Composable
fun NeuPillBadge(
    text: String,
    modifier: Modifier = Modifier,
    textColor: Color = NeuAccentBlue,
    backgroundColor: Color = NeuInsetBg,
    fontSize: androidx.compose.ui.unit.TextUnit = 10.sp
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(6.dp),
        color = backgroundColor
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            fontSize = fontSize,
            fontWeight = FontWeight.Bold,
            color = textColor
        )
    }
}
