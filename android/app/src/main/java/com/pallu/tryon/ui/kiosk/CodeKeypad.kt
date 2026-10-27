package com.pallu.tryon.ui.kiosk

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Backspace
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pallu.tryon.ui.theme.Mirror
import com.pallu.tryon.ui.theme.MirrorLine
import com.pallu.tryon.ui.theme.MirrorRaised
import com.pallu.tryon.ui.theme.MirrorRaised2
import com.pallu.tryon.ui.theme.OnMirror
import com.pallu.tryon.ui.theme.Rozha
import com.pallu.tryon.ui.theme.ZariLight

/**
 * Session codes use A–Z without I, L and O, and the digits 2–9 (the server never makes the
 * look-alikes), so the mirror's own keypad needs exactly these 31 keys plus delete.
 */
private val KEY_ROWS = listOf("23456789", "ABCDEFGH", "JKMNPQRS", "TUVWXYZ")

/**
 * The code as six large boxes. Tapping them opens the mirror's keypad (the Android keyboard is never
 * used on the mirror: it can cover the code, and its settings button is a way out of kiosk mode).
 */
@Composable
fun CodeBoxes(value: String, onClick: () -> Unit, boxSize: Dp = 48.dp) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable(role = Role.Button, onClickLabel = "Type the code", onClick = onClick)
            .semantics { contentDescription = "Type the 6-letter code" },
    ) {
        CodeCells(value, boxSize, active = false)
    }
}

@Composable
private fun CodeCells(value: String, boxSize: Dp, active: Boolean) {
    repeat(6) { i ->
        val ch = value.getOrNull(i)?.toString().orEmpty()
        val current = i == value.length || (active && i == 5 && value.length == 6)
        Box(
            Modifier
                .size(boxSize, boxSize * 1.3f)
                .clip(RoundedCornerShape(10.dp))
                .background(Mirror)
                .border(if (current) 2.dp else 1.dp, if (current) ZariLight else MirrorLine, RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text(ch, fontFamily = Rozha, fontSize = (boxSize.value * 0.56f).sp, color = OnMirror)
        }
    }
}

/**
 * Full-screen keypad sheet: the code in big boxes, then big keys right under it, so customers
 * always see what they type. [onStart] runs when all six characters are in and Start is pressed.
 */
@Composable
fun CodeKeypad(
    code: String,
    onCode: (String) -> Unit,
    onStart: () -> Unit,
    onClose: () -> Unit,
    busy: Boolean,
) {
    val scrim = remember { MutableInteractionSource() }
    Box(
        Modifier
            .fillMaxSize()
            .background(Mirror.copy(alpha = 0.72f))
            .clickable(interactionSource = scrim, indication = null, onClickLabel = "Close the keypad", onClick = onClose),
        contentAlignment = Alignment.BottomCenter,
    ) {
        BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
            val wide = maxWidth > 900.dp
            val keyHeight = if (maxWidth < 600.dp) 56.dp else 72.dp
            val box = if (maxWidth < 600.dp) 44.dp else 64.dp
            Column(
                Modifier
                    .widthIn(max = 880.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                    .background(MirrorRaised)
                    .border(1.dp, ZariLight.copy(alpha = 0.3f), RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                    // swallow taps so they don't reach the scrim
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                    .padding(horizontal = if (wide) 40.dp else 24.dp, vertical = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(
                    "Type the 6-letter code",
                    fontFamily = Rozha, fontSize = 34.sp, lineHeight = 40.sp, color = OnMirror, textAlign = TextAlign.Center,
                )
                Muted("It’s printed under the QR code our staff gave you.", scale = 0.85f, align = TextAlign.Center)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { CodeCells(code, box, active = true) }
                Spacer(Modifier.height(4.dp))
                KEY_ROWS.forEachIndexed { r, keys ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        keys.forEach { c ->
                            Key(c.toString(), keyHeight, Modifier.weight(1f), enabled = code.length < 6) { onCode(code + c) }
                        }
                        if (r == KEY_ROWS.lastIndex) {
                            DeleteKey(keyHeight, Modifier.weight(1f), enabled = code.isNotEmpty()) { onCode(code.dropLast(1)) }
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    QuietButton("Close", onClose)
                    Spacer(Modifier.weight(1f))
                    GoldButton(
                        if (busy) "Finding your sarees…" else "Start",
                        onClick = onStart,
                        enabled = code.length == 6 && !busy,
                        modifier = Modifier.widthIn(min = 220.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun Key(label: String, height: Dp, modifier: Modifier, enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier
            .height(height)
            .clip(RoundedCornerShape(14.dp))
            .background(MirrorRaised2)
            .border(1.dp, MirrorLine, RoundedCornerShape(14.dp))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, fontFamily = Rozha, fontSize = (height.value * 0.42f).sp, color = if (enabled) OnMirror else OnMirror.copy(alpha = 0.4f))
    }
}

@Composable
private fun DeleteKey(height: Dp, modifier: Modifier, enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier
            .height(height)
            .clip(RoundedCornerShape(14.dp))
            .background(Mirror)
            .border(1.dp, MirrorLine, RoundedCornerShape(14.dp))
            .clickable(enabled = enabled, role = Role.Button, onClickLabel = "Delete", onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.AutoMirrored.Outlined.Backspace, contentDescription = "Delete",
            tint = if (enabled) ZariLight else MirrorLine, modifier = Modifier.size(height * 0.4f),
        )
    }
}
