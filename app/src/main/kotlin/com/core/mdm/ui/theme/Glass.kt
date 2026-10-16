package com.core.mdm.ui.theme

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.core.mdm.R

// Visual language shared with the website: a deep navy ground lit by soft blue/teal/green
// glows, translucent "glass" cards with a hairline border, and a blue→green gradient on
// primary actions and switches.

/** Corner radius of the site's glass cards (22px on the web). */
val GlassShape = RoundedCornerShape(20.dp)

/** The site's primary gradient (135°, #1f7fd6 → #1fb58f). */
@Composable
fun brandGradient(): Brush {
    val c = LocalAppColors.current
    return Brush.linearGradient(listOf(c.gradStart, c.gradEnd))
}

/**
 * Full-screen background matching the website's `.bg` + orbs: static radial glows drawn once
 * into a cached draw (no animation, so it costs nothing while scrolling on low-end devices).
 */
fun Modifier.siteBackground(): Modifier = composed {
    val c = LocalAppColors.current
    drawWithCache {
        val w = size.width
        val h = size.height
        val a = if (c.isDark) 1f else 0.35f
        val top = Brush.radialGradient(
            listOf(Color(0xFF0C2A44).copy(alpha = a), Color.Transparent),
            center = Offset(w * 0.5f, -h * 0.1f), radius = maxOf(w, h) * 0.75f
        )
        val blue = Brush.radialGradient(
            listOf(Color(0xFF1F6FD1).copy(alpha = 0.34f * a), Color.Transparent),
            center = Offset(w * 0.05f, h * 0.02f), radius = w * 0.85f
        )
        val teal = Brush.radialGradient(
            listOf(Color(0xFF14B8A6).copy(alpha = 0.26f * a), Color.Transparent),
            center = Offset(w * 1.05f, h * 0.45f), radius = w * 0.8f
        )
        val green = Brush.radialGradient(
            listOf(Color(0xFF34C77B).copy(alpha = 0.18f * a), Color.Transparent),
            center = Offset(w * 0.35f, h * 1.02f), radius = w * 0.8f
        )
        onDrawBehind {
            drawRect(c.navy)
            drawRect(top)
            drawRect(blue)
            drawRect(teal)
            drawRect(green)
        }
    }
}

/** Primary action button with the site's gradient fill (`button.primary` / `.btn-pri`). */
@Composable
fun GradientButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = RoundedCornerShape(13.dp),
    contentPadding: PaddingValues = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
    content: @Composable RowScope.() -> Unit
) {
    Row(
        modifier = modifier
            .alpha(if (enabled) 1f else 0.4f)
            .clip(shape)
            .background(brandGradient())
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(contentPadding),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        CompositionLocalProvider(LocalContentColor provides Color.White) { content() }
    }
}

/**
 * The website's toggle (42×24 pill, gradient when on, white thumb) — replaces Material's
 * Switch so settings rows look identical to the web console's policy toggles.
 */
@Composable
fun GlassSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val c = LocalAppColors.current
    val thumbOffset by animateDpAsState(if (checked) 18.dp else 0.dp, tween(200), label = "thumb")
    val trackOff = if (c.isDark) Color(0x26FFFFFF) else Color(0x260D1B2A)
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .size(width = 42.dp, height = 24.dp)
            .alpha(if (enabled) 1f else 0.4f)
            .clip(CircleShape)
            .then(
                if (checked) Modifier.background(brandGradient())
                else Modifier.background(trackOff)
            )
            .then(
                if (onCheckedChange != null) Modifier.toggleable(
                    value = checked,
                    enabled = enabled,
                    role = Role.Switch,
                    interactionSource = interaction,
                    indication = null,
                    onValueChange = onCheckedChange
                ) else Modifier
            )
            .padding(3.dp)
    ) {
        Box(
            Modifier
                .offset(x = thumbOffset)
                .size(18.dp)
                .clip(CircleShape)
                .background(Color.White)
        )
    }
}

/** The CORE MDM logo mark (hexagon swirl), used in place of the generic security icon. */
@Composable
fun BrandLogo(size: Dp, modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(R.drawable.logo_mark),
        contentDescription = "CORE MDM",
        modifier = modifier.size(size)
    )
}
