package com.pallu.tryon.ui.kiosk

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.outlined.CardGiftcard
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pallu.tryon.data.DRAPES
import com.pallu.tryon.ui.common.RemoteImage
import com.pallu.tryon.ui.theme.MirrorLine
import com.pallu.tryon.ui.theme.MirrorRaised
import com.pallu.tryon.ui.theme.OnGold
import com.pallu.tryon.ui.theme.OnMirror
import com.pallu.tryon.ui.theme.OnMirrorMuted
import com.pallu.tryon.ui.theme.Rozha
import com.pallu.tryon.ui.theme.ZariLight

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ConsentScreen(vm: KioskViewModel, onBack: () -> Unit, onContinue: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val session = state.session ?: return
    val drape = DRAPES.firstOrNull { it.id == state.drapeStyle } ?: DRAPES.first()

    KioskPage {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val k = headlineSize(maxWidth)
            val side = 56.dp * k.coerceAtLeast(0.6f)
            Column(Modifier.fillMaxSize()) {
                Column(
                    Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
                        .padding(start = side, end = side, top = 44.dp * k),
                    verticalArrangement = Arrangement.spacedBy(20.dp * k),
                ) {
                    val name = session.customerLabel.takeIf { it.isNotBlank() && !it.startsWith("Token", ignoreCase = true) }
                    Text(
                        if (name != null) "Namaste, $name." else "Namaste.",
                        fontFamily = Rozha, fontSize = (72 * k).sp, lineHeight = (76 * k).sp, color = OnMirror,
                    )
                    Muted(
                        "You picked ${session.total} ${if (session.total == 1) "saree" else "sarees"}. We'll show you wearing each one.",
                        scale = k,
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp), contentPadding = PaddingValues(vertical = 4.dp)) {
                        items(session.items, key = { it.id }) { item ->
                            Column(Modifier.width(124.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                RemoteImage(
                                    item.saree.imageUrl, item.saree.name,
                                    Modifier.size(124.dp, 156.dp).clip(RoundedCornerShape(8.dp))
                                        .border(1.dp, ZariLight.copy(alpha = 0.3f), RoundedCornerShape(8.dp)),
                                )
                                Text(
                                    item.saree.name, style = MaterialTheme.typography.bodyMedium, color = OnMirrorMuted,
                                    maxLines = 2, overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(4.dp))
                    SectionTitle("Who is trying them on?", k)
                    Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                        ChoiceCard(
                            Icons.Outlined.Person, "Me", "Stand in front of the mirror for one photo.",
                            state.mode == TryOnMode.SELF, { vm.setMode(TryOnMode.SELF) }, Modifier.weight(1f),
                        )
                        ChoiceCard(
                            Icons.Outlined.CardGiftcard, "Someone else", "Choose a photo of them. Good for gifts.",
                            state.mode == TryOnMode.GIFT, { vm.setMode(TryOnMode.GIFT) }, Modifier.weight(1f),
                        )
                    }

                    Spacer(Modifier.height(4.dp))
                    SectionTitle("How should it be draped?", k)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        DRAPES.forEach { d -> DrapePill(d.label, d.id == state.drapeStyle) { vm.setDrape(d.id) } }
                    }
                    Muted(drape.description, scale = 0.85f)

                    if (state.mode != null) {
                        val text = if (state.mode == TryOnMode.GIFT) {
                            "I have this person's permission to use their photo. It is only used to show these sarees, " +
                                "and is deleted when I finish or after ${session.photoTtlMinutes} minutes."
                        } else {
                            "I agree to a photo being taken to show me these sarees. My photo and the looks are deleted " +
                                "when I finish or after ${session.photoTtlMinutes} minutes, and are not used for anything else."
                        }
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(MirrorRaised)
                                .border(1.dp, ZariLight.copy(alpha = 0.28f), RoundedCornerShape(16.dp))
                                .toggleable(value = state.consent, onValueChange = vm::setConsent, role = Role.Checkbox)
                                .padding(18.dp),
                            verticalAlignment = Alignment.Top,
                        ) {
                            Checkbox(
                                checked = state.consent, onCheckedChange = null,
                                colors = CheckboxDefaults.colors(checkedColor = ZariLight, checkmarkColor = OnGold, uncheckedColor = OnMirrorMuted),
                            )
                            Spacer(Modifier.width(14.dp))
                            Text(text, style = MaterialTheme.typography.bodyLarge, color = OnMirror)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }

                Row(
                    Modifier.fillMaxWidth().padding(horizontal = side, vertical = 22.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    QuietButton("Cancel", onBack)
                    Spacer(Modifier.weight(1f))
                    GoldButton(
                        text = if (state.mode == TryOnMode.GIFT) "Choose their photo" else "Take my photo",
                        icon = if (state.mode == TryOnMode.GIFT) Icons.Filled.PhotoLibrary else Icons.Filled.PhotoCamera,
                        onClick = onContinue,
                        enabled = state.mode != null && state.consent,
                        modifier = Modifier.widthIn(min = 260.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun ChoiceCard(
    icon: ImageVector,
    title: String,
    body: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    val border by animateColorAsState(if (selected) ZariLight else MirrorLine, label = "choice-border")
    Surface(
        modifier = modifier.selectable(selected = selected, onClick = onClick, role = Role.RadioButton),
        shape = RoundedCornerShape(18.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MirrorRaised,
        border = BorderStroke(if (selected) 3.dp else 1.dp, border),
    ) {
        Box {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(icon, contentDescription = null, tint = ZariLight, modifier = Modifier.size(40.dp))
                Text(title, fontFamily = Rozha, fontSize = 30.sp, color = OnMirror)
                Text(body, style = MaterialTheme.typography.bodyLarge, color = OnMirrorMuted)
            }
            if (selected) {
                Badge(ZariLight, Icons.Filled.Check, Modifier.align(Alignment.TopEnd).padding(16.dp), size = 32.dp)
            }
        }
    }
}

@Composable
private fun DrapePill(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) ZariLight else MirrorRaised.copy(alpha = 0f))
            .border(1.dp, if (selected) ZariLight else MirrorLine, RoundedCornerShape(50))
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
            .padding(horizontal = 24.dp, vertical = 13.dp),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.titleMedium.copy(fontSize = 19.sp),
            color = if (selected) OnGold else OnMirror,
        )
    }
}
