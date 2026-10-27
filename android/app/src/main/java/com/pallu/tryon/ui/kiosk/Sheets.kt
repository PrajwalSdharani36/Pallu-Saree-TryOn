package com.pallu.tryon.ui.kiosk

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pallu.tryon.data.Saree
import com.pallu.tryon.ui.common.RemoteImage
import com.pallu.tryon.ui.common.rupees
import com.pallu.tryon.ui.theme.GlassLine
import com.pallu.tryon.ui.theme.Kumkum
import com.pallu.tryon.ui.theme.Mirror
import com.pallu.tryon.ui.theme.MirrorRaised
import com.pallu.tryon.ui.theme.MirrorRaised2
import com.pallu.tryon.ui.theme.OnMirror
import com.pallu.tryon.ui.theme.OnMirrorMuted
import com.pallu.tryon.ui.theme.Rozha
import com.pallu.tryon.ui.theme.ZariLight

/**
 * A sheet that rises from the bottom of the mirror over a dimmed screen. Tapping the dimmed part
 * closes it. [tall] sheets (a grid of sarees) take most of the height.
 */
@Composable
fun MirrorSheet(
    onClose: () -> Unit,
    tall: Boolean = false,
    maxWidth: Dp = 880.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Mirror.copy(alpha = 0.72f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClickLabel = "Close", onClick = onClose),
        contentAlignment = Alignment.BottomCenter,
    ) {
        BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
            val shape = RoundedCornerShape(topStart = 36.dp, topEnd = 36.dp)
            Column(
                Modifier
                    .widthIn(max = maxWidth)
                    .fillMaxWidth()
                    .then(if (tall) Modifier.fillMaxHeight(0.88f) else Modifier)
                    .clip(shape)
                    .background(MirrorRaised)
                    .border(1.dp, GlassLine, shape)
                    // swallow taps so they don't reach the scrim
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                    .padding(horizontal = if (this@BoxWithConstraints.maxWidth > 900.dp) 44.dp else 34.dp, vertical = 34.dp),
                verticalArrangement = Arrangement.spacedBy(22.dp),
                content = content,
            )
        }
    }
}

@Composable
private fun SheetTitle(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, fontFamily = Rozha, fontSize = 38.sp, lineHeight = 44.sp, color = OnMirror)
        Muted(subtitle, scale = 0.86f)
    }
}

/**
 * "Try more sarees": the store's collection as a grid. She picks a few and they're draped with the
 * photo she already took. Staff can also scan a tag from their phone; it shows up on its own.
 */
@Composable
fun TryMoreSheet(
    catalog: List<Saree>,
    loading: Boolean,
    alreadyTrying: Set<String>,
    canAdd: Int,
    busy: Boolean,
    onAdd: (List<String>) -> Unit,
    onClose: () -> Unit,
) {
    val picked = remember { mutableStateListOf<String>() }
    MirrorSheet(onClose, tall = true, maxWidth = 1100.dp) {
        SheetTitle(
            "Try more sarees",
            "Pick from our collection, or ask our staff to scan the tag of any saree. It will appear here on its own.",
        )
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                loading && catalog.isEmpty() -> CircularProgressIndicator(color = ZariLight, modifier = Modifier.align(Alignment.Center))
                catalog.isEmpty() -> Muted("No sarees in the collection yet.", modifier = Modifier.align(Alignment.Center))
                else -> LazyVerticalGrid(
                    columns = GridCells.Adaptive(180.dp),
                    contentPadding = PaddingValues(vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                    verticalArrangement = Arrangement.spacedBy(26.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(catalog, key = { it.id }) { s ->
                        val trying = s.id in alreadyTrying
                        val on = s.id in picked
                        CatalogCard(
                            s, trying = trying, picked = on,
                            enabled = !trying && !busy && (on || picked.size < canAdd),
                            onClick = { if (on) picked.remove(s.id) else picked.add(s.id) },
                        )
                    }
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            QuietButton("Close", onClose)
            Spacer(Modifier.width(12.dp))
            Muted(
                when {
                    canAdd <= 0 -> "That's the most sarees for one try-on."
                    picked.size >= canAdd -> "You can try ${picked.size} more at a time."
                    else -> ""
                },
                scale = 0.8f, modifier = Modifier.weight(1f),
            )
            GoldButton(
                text = when {
                    busy -> "Adding…"
                    picked.isEmpty() -> "Pick sarees to try"
                    picked.size == 1 -> "Try this saree"
                    else -> "Try these ${picked.size}"
                },
                onClick = { onAdd(picked.toList()) },
                enabled = picked.isNotEmpty() && !busy,
                modifier = Modifier.widthIn(min = 240.dp),
            )
        }
    }
}

@Composable
private fun CatalogCard(s: Saree, trying: Boolean, picked: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Column(
        Modifier
            .clip(RoundedCornerShape(20.dp))
            .clickable(enabled = enabled, role = Role.Checkbox, onClickLabel = if (picked) "Unpick" else "Pick", onClick = onClick)
            .alpha(if (trying) 0.45f else 1f),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(3f / 4f)
                .clip(RoundedCornerShape(20.dp))
                .border(if (picked) 3.dp else 1.dp, if (picked) ZariLight else GlassLine, RoundedCornerShape(20.dp)),
        ) {
            RemoteImage(s.imageUrl, s.name, Modifier.fillMaxSize())
            when {
                picked -> Badge(ZariLight, Icons.Filled.Check, Modifier.align(Alignment.TopEnd).padding(8.dp), size = 30.dp)
                trying -> Text(
                    "Already trying",
                    style = MaterialTheme.typography.labelLarge, color = OnMirror,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(8.dp)
                        .clip(RoundedCornerShape(50)).background(Mirror.copy(alpha = 0.85f)).padding(horizontal = 12.dp, vertical = 5.dp),
                )
            }
        }
        Text(
            s.name, style = MaterialTheme.typography.titleMedium, color = OnMirror,
            maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.heightIn(min = 44.dp),
        )
        if (s.price > 0) Text(rupees(s.price), style = MaterialTheme.typography.bodyMedium, color = OnMirrorMuted)
    }
}

/**
 * Asked before finishing, because finishing deletes her photo: a last chance to try more,
 * and a reminder to heart the ones she loves so the staff bring them out.
 */
@Composable
fun DoneSheet(shortlisted: Int, finishing: Boolean, onTryMore: () -> Unit, onDone: () -> Unit, onClose: () -> Unit) {
    MirrorSheet(onClose, maxWidth = 760.dp) {
        SheetTitle(
            "All done?",
            "When you finish, your photo and these looks are deleted from the mirror.",
        )
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(MirrorRaised2).padding(22.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(44.dp).clip(CircleShape).background(if (shortlisted > 0) Kumkum else GlassLine), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Favorite, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(16.dp))
            Text(
                when (shortlisted) {
                    0 -> "You haven't shortlisted any yet. Tap the heart on the looks you love and our staff will bring them out."
                    1 -> "1 saree shortlisted. Our staff will bring it out for you."
                    else -> "$shortlisted sarees shortlisted. Our staff will bring them out for you."
                },
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 19.sp), color = OnMirror,
                modifier = Modifier.weight(1f),
            )
        }
        // one clear main answer, and the two ways back underneath it
        GoldButton(if (finishing) "Finishing…" else "Yes, I'm done", onDone, enabled = !finishing, modifier = Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            OutlineButton("Keep looking", onClose, modifier = Modifier.weight(1f))
            OutlineButton("Try more sarees", onTryMore, gold = true, modifier = Modifier.weight(1f))
        }
    }
}
