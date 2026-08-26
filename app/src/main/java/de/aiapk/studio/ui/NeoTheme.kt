package de.aiapk.studio.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

val NeoBgLight = Color(0xFFE6EBF2)
val NeoBgDark = Color(0xFF171A21)
val Accent = Color(0xFF6C63FF)
val Mint = Color(0xFF2ED6A1)

private val Light = lightColorScheme(
    primary = Accent,
    secondary = Mint,
    background = NeoBgLight,
    surface = NeoBgLight,
    surfaceVariant = Color(0xFFDDE3EB),
    onBackground = Color(0xFF252833),
    onSurface = Color(0xFF252833)
)
private val Dark = darkColorScheme(
    primary = Color(0xFF9D97FF),
    secondary = Mint,
    background = NeoBgDark,
    surface = NeoBgDark,
    surfaceVariant = Color(0xFF20242D),
    onBackground = Color(0xFFECEFF5),
    onSurface = Color(0xFFECEFF5)
)

@Composable
fun AIAPKTheme(dark: Boolean, content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (dark) Dark else Light, content = content)
}

@Composable
fun NeoCard(
    modifier: Modifier = Modifier,
    padding: PaddingValues = PaddingValues(16.dp),
    content: @Composable BoxScope.() -> Unit
) {
    val bg = MaterialTheme.colorScheme.surface
    val lightShadow = if (bg == NeoBgDark) Color(0xFF252A35) else Color.White.copy(alpha = .95f)
    val darkShadow = if (bg == NeoBgDark) Color.Black.copy(alpha = .62f) else Color(0xFFB7BEC8).copy(alpha = .72f)
    val shape = RoundedCornerShape(22.dp)
    Box(modifier.padding(7.dp)) {
        Box(Modifier.matchParentSize().offset(6.dp, 6.dp).background(darkShadow, shape))
        Box(Modifier.matchParentSize().offset((-6).dp, (-6).dp).background(lightShadow, shape))
        Box(Modifier.fillMaxWidth().background(bg, shape).padding(padding), content = content)
    }
}

@Composable
fun NeoActionButton(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    primary: Boolean = true,
    icon: (@Composable () -> Unit)? = null,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(20.dp)
    val bg = if (primary) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface
    val fg = if (primary) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    Box(
        modifier
            .alpha(if (enabled) 1f else .45f)
            .shadow(9.dp, shape)
            .clip(shape)
            .background(bg)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            androidx.compose.material3.ProvideTextStyle(MaterialTheme.typography.labelLarge) {
                androidx.compose.runtime.CompositionLocalProvider(androidx.compose.material3.LocalContentColor provides fg) {
                    if (icon != null) { icon(); Spacer(Modifier.width(8.dp)) }
                    androidx.compose.material3.Text(text)
                }
            }
        }
    }
}
