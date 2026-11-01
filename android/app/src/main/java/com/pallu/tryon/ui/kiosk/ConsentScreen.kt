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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.material.icons.filled.Add
import androidx.compose.foundation.clickable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pallu.tryon.data.DRAPES
import com.pallu.tryon.ui.common.RemoteImage
import com.pallu.tryon.ui.theme.Glass
import com.pallu.tryon.ui.theme.GlassLine
import com.pallu.tryon.ui.theme.GlassStrong
import com.pallu.tryon.ui.theme.OnPrimary
import com.pallu.tryon.ui.theme.Primary
import com.pallu.tryon.ui.theme.OnGold
import com.pallu.tryon.ui.theme.OnMirror
import com.pallu.tryon.ui.theme.OnMirrorMuted
import com.pallu.tryon.ui.theme.Rozha
import com.pallu.tryon.ui.theme.ZariLight

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ConsentScreen(
    vm: KioskViewModel,
    onBack: () -> Unit,
    onContinue: () -> Unit,
    scanFront: Boolean = true,
    onScanFront: (Boolean) -> Unit = {},
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val session = state.session ?: return
    val drape = DRAPES.firstOrNull { it.id == state.drapeStyle } ?: DRAPES.first()
    // No sarees scanned (staff handed over a "choose on the mirror" QR), or she wants to add more.
    var choosing by remember { mutableStateOf(false) }
    var tagNote by remember { mutableStateOf<String?>(null) }
    val openCollection = { vm.loadCatalog(); choosing = true }

    KioskPage(ambient = session.items.firstOrNull()?.saree?.imageUrl) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val k = headlineSize(maxWidth)
            val wide = maxWidth > maxHeight
            Column(Modifier.fillMaxSize()) {
                val greeting: @Composable () -> Unit = {
                    Column(Modifier.padding(end = ThemeSwitchRoom - KioskSide), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        val name = session.customerLabel.takeIf { it.isNotBlank() && !it.startsWith("Token", ignoreCase = true) }
                        Text(
                            if (name != null) "Namaste, $name." else "Namaste.",
                            fontFamily = Rozha, fontSize = (64 * k).sp, lineHeight = (70 * k).sp, color = OnMirror,
                        )
                        Muted(
                            if (session.total == 0) "Choose the sarees you'd like to see on you. It only takes a moment."
                            else "You picked ${session.total} ${if (session.total == 1) "saree" else "sarees"}. We'll show you wearing each one.",
                            scale = k,
                        )
                    }
                }
                val picked: @Composable () -> Unit = {
                    if (session.items.isEmpty()) {
                        GlassColumn(Modifier.fillMaxWidth(), spacing = 18.dp) {
                            Text("Choose your sarees", fontFamily = Rozha, fontSize = 32.sp, lineHeight = 38.sp, color = OnMirror)
                            Muted("Pick as many as you like from our collection. You can add more after your photo too.", scale = 0.9f)
                            GoldButton("Choose from our collection", onClick = openCollection, icon = Icons.Filled.Add)
                        }
                    } else {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(18.dp), contentPadding = PaddingValues(vertical = 4.dp)) {
                            items(session.items, key = { it.id }) { item ->
                                Column(Modifier.width(118.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    RemoteImage(
                                        item.saree.imageUrl, item.saree.name,
                                        Modifier.size(118.dp, 150.dp).clip(RoundedCornerShape(20.dp))
                                            .border(1.dp, GlassLine, RoundedCornerShape(20.dp)),
                                    )
                                    Text(
                                        item.saree.name, style = MaterialTheme.typography.bodySmall, color = OnMirrorMuted,
                                        maxLines = 2, overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                            item(key = "add") {
                                Column(
                                    Modifier.size(118.dp, 150.dp).clip(RoundedCornerShape(20.dp)).background(Glass)
                                        .border(1.5.dp, ZariLight.copy(alpha = 0.7f), RoundedCornerShape(20.dp))
                                        .clickable(onClickLabel = "Add more sarees", onClick = openCollection),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
                                ) {
                                    Icon(Icons.Filled.Add, contentDescription = null, tint = ZariLight, modifier = Modifier.size(30.dp))
                                    Text("Add more", style = MaterialTheme.typography.titleSmall, color = ZariLight)
                                }
                            }
                        }
                    }
                }
                val whoSection: @Composable () -> Unit = {
                    GlassColumn(Modifier.fillMaxWidth(), spacing = 22.dp) {
                        StepHeader(1, "Who is trying them on?")
                        Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                            ChoiceCard(
                                Icons.Outlined.Person, "Me", "Stand in front of the mirror for one photo.",
                                state.mode == TryOnMode.SELF, { vm.setMode(TryOnMode.SELF) }, Modifier.weight(1f),
                            )
                            ChoiceCard(
                                Icons.Outlined.CardGiftcard, "Someone else", "Send our staff a photo of them. Good for gifts.",
                                state.mode == TryOnMode.GIFT, { vm.setMode(TryOnMode.GIFT) }, Modifier.weight(1f),
                            )
                        }
                    }
                }
                val drapeSection: @Composable () -> Unit = {
                    GlassColumn(Modifier.fillMaxWidth(), spacing = 20.dp) {
                        StepHeader(2, "How should it be draped?")
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            DRAPES.forEach { d -> DrapePill(d.label, d.id == state.drapeStyle) { vm.setDrape(d.id) } }
                        }
                        Muted(drape.description, scale = 0.9f)
                    }
                }
                val permissionSection: @Composable () -> Unit = {
                    if (state.mode != null) {
                        val text = if (state.mode == TryOnMode.GIFT) {
                            "I have this person's permission to use their photo. It is only used to show these sarees, " +
                                "and is deleted when I finish or after ${session.photoTtlMinutes} minutes."
                        } else {
                            "I agree to a photo being taken to show me these sarees. My photo and the looks are deleted " +
                                "when I finish or after ${session.photoTtlMinutes} minutes, and are not used for anything else."
                        }
                        GlassColumn(Modifier.fillMaxWidth(), spacing = 18.dp) {
                            StepHeader(3, "Your permission")
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(22.dp))
                                    .background(if (state.consent) ZariLight.copy(alpha = 0.14f) else Color.Transparent)
                                    .border(if (state.consent) 2.dp else 1.dp, if (state.consent) ZariLight else GlassLine, RoundedCornerShape(22.dp))
                                    .toggleable(value = state.consent, onValueChange = vm::setConsent, role = Role.Checkbox)
                                    .padding(20.dp),
                                verticalAlignment = Alignment.Top,
                            ) {
                                Checkbox(
                                    checked = state.consent, onCheckedChange = null,
                                    colors = CheckboxDefaults.colors(checkedColor = ZariLight, checkmarkColor = OnGold, uncheckedColor = OnMirrorMuted),
                                    modifier = Modifier.scale(1.3f).padding(top = 4.dp),
                                )
                                Spacer(Modifier.width(18.dp))
                                Text(text, style = MaterialTheme.typography.bodyLarge, color = OnMirror)
                            }
                        }
                    }
                }
                if (wide) {
                    // landscape: two columns, so the drape and permission are on screen without scrolling
                    Row(
                        Modifier.weight(1f).fillMaxWidth().padding(start = KioskSide, end = KioskSide, top = 34.dp),
                        horizontalArrangement = Arrangement.spacedBy(32.dp),
                    ) {
                        Column(
                            Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()).padding(bottom = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(26.dp),
                        ) { greeting(); picked(); whoSection() }
                        Column(
                            Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()).padding(top = 76.dp, bottom = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(26.dp),
                        ) { drapeSection(); permissionSection() }
                    }
                } else {
                    Column(
                        Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
                            .padding(start = KioskSide, end = KioskSide, top = 34.dp, bottom = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(30.dp),
                    ) { greeting(); picked(); whoSection(); drapeSection(); permissionSection() }
                }

                Row(
                    Modifier.fillMaxWidth().padding(horizontal = KioskSide, vertical = if (wide) 18.dp else 28.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlineButton("Cancel", onBack)
                    Spacer(Modifier.weight(1f))
                    if (session.total == 0) {
                        Text("Choose a saree first", style = MaterialTheme.typography.bodyMedium, color = OnMirrorMuted)
                        Spacer(Modifier.width(18.dp))
                    }
                    // keyed by the choice so the button is rebuilt, not relabelled: screen readers (and
                    // the walkthrough) otherwise keep hearing the old "Take my photo"
                    key(state.mode) {
                        GoldButton(
                            text = if (state.mode == TryOnMode.GIFT) "Continue" else "Take my photo",
                            icon = if (state.mode == TryOnMode.GIFT) Icons.Filled.PhotoLibrary else Icons.Filled.PhotoCamera,
                            onClick = onContinue,
                            enabled = state.mode != null && state.consent && session.total > 0,
                            modifier = Modifier.widthIn(min = 300.dp),
                        )
                    }
                }
            }
            if (choosing) {
                TryMoreSheet(
                    catalog = state.catalog,
                    loading = state.catalogLoading,
                    alreadyTrying = session.items.map { it.saree.id }.toSet(),
                    canAdd = 12 - session.items.map { it.saree.id }.distinct().size,
                    busy = state.busy,
                    onAdd = { ids -> vm.addSarees(ids) { choosing = false } },
                    onClose = { choosing = false; tagNote = null },
                    scanFront = scanFront,
                    onScanFront = onScanFront,
                    onScanTag = { code ->
                        vm.addByBarcode(code) { id, note ->
                            tagNote = note
                            if (id != null) choosing = false
                        }
                    },
                    tagNote = tagNote,
                    title = if (session.items.isEmpty()) "Choose your sarees" else "Add more sarees",
                )
            }
        }
    }
}

/** "1  Who is trying them on?" */
@Composable
private fun StepHeader(n: Int, title: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(40.dp).clip(CircleShape).background(ZariLight), contentAlignment = Alignment.Center) {
            Text("$n", fontFamily = Rozha, fontSize = 20.sp, color = OnGold)
        }
        Spacer(Modifier.width(16.dp))
        Text(title, fontFamily = Rozha, fontSize = 32.sp, lineHeight = 38.sp, color = OnMirror)
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
    val border by animateColorAsState(if (selected) ZariLight else GlassLine, label = "choice-border")
    Surface(
        modifier = modifier.selectable(selected = selected, onClick = onClick, role = Role.RadioButton),
        shape = RoundedCornerShape(26.dp),
        color = if (selected) ZariLight.copy(alpha = 0.14f) else GlassStrong.copy(alpha = 0.5f),
        border = BorderStroke(if (selected) 2.5.dp else 1.dp, border),
    ) {
        Box {
            Column(Modifier.padding(26.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(icon, contentDescription = null, tint = ZariLight, modifier = Modifier.size(44.dp))
                Text(title, fontFamily = Rozha, fontSize = 32.sp, color = OnMirror)
                Text(body, style = MaterialTheme.typography.bodyMedium, color = OnMirrorMuted)
            }
            if (selected) {
                Badge(ZariLight, Icons.Filled.Check, Modifier.align(Alignment.TopEnd).padding(18.dp), size = 34.dp)
            }
        }
    }
}

@Composable
private fun DrapePill(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .heightIn(min = 60.dp)
            .clip(RoundedCornerShape(50))
            .background(if (selected) Primary else Glass)
            .border(1.dp, if (selected) Primary else GlassLine, RoundedCornerShape(50))
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
            .padding(horizontal = 28.dp, vertical = 15.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.titleMedium.copy(fontSize = 20.sp),
            color = if (selected) OnPrimary else OnMirror,
        )
    }
}
