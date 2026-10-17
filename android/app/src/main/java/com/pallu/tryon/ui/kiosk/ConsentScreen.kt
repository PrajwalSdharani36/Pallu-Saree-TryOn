package com.pallu.tryon.ui.kiosk

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pallu.tryon.data.DRAPE_LABELS
import com.pallu.tryon.ui.common.BigButton
import com.pallu.tryon.ui.common.RemoteImage
import com.pallu.tryon.ui.theme.ZariBorder

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ConsentScreen(vm: KioskViewModel, onBack: () -> Unit, onContinue: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val session = state.session ?: return

    Column(Modifier.fillMaxSize()) {
        ZariBorder()
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(Modifier.widthIn(max = 760.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                val name = session.customerLabel.takeIf { it.isNotBlank() && !it.startsWith("Token", ignoreCase = true) }
                Text(
                    if (name != null) "Namaste, $name." else "Namaste.",
                    style = MaterialTheme.typography.displayMedium,
                )
                Text(
                    "You picked ${session.total} ${if (session.total == 1) "saree" else "sarees"}. We'll show you wearing each one.",
                    style = MaterialTheme.typography.bodyLarge,
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(vertical = 4.dp)) {
                    items(session.items, key = { it.id }) { item ->
                        RemoteImage(
                            item.saree.imageUrl, item.saree.name,
                            Modifier.size(width = 96.dp, height = 128.dp).clip(RoundedCornerShape(10.dp)),
                        )
                    }
                }

                Text("Who is trying them on?", style = MaterialTheme.typography.headlineSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Choice(
                        Icons.Filled.Person, "Me", "Stand in front of the mirror for one photo.",
                        state.mode == TryOnMode.SELF, { vm.setMode(TryOnMode.SELF) }, Modifier.weight(1f),
                    )
                    Choice(
                        Icons.Filled.CardGiftcard, "Someone else", "Choose a photo of them. Good for gifts.",
                        state.mode == TryOnMode.GIFT, { vm.setMode(TryOnMode.GIFT) }, Modifier.weight(1f),
                    )
                }

                Text("How should it be draped?", style = MaterialTheme.typography.headlineSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    DRAPE_LABELS.forEach { (id, label) ->
                        FilterChip(
                            selected = state.drapeStyle == id,
                            onClick = { vm.setDrape(id) },
                            label = { Text(label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(vertical = 10.dp)) },
                        )
                    }
                }

                if (state.mode != null) {
                    val text = if (state.mode == TryOnMode.GIFT)
                        "I have this person's permission to use their photo. It is used only to show these sarees, " +
                            "and is deleted when I finish or after ${session.photoTtlMinutes} minutes."
                    else
                        "I agree to a photo being taken to show me these sarees. My photo and the looks are deleted " +
                            "when I finish or after ${session.photoTtlMinutes} minutes, and are not used for anything else."
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .toggleable(value = state.consent, onValueChange = vm::setConsent, role = Role.Checkbox)
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = state.consent, onCheckedChange = null)
                        Spacer(Modifier.width(12.dp))
                        Text(text, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onBack) { Text("Cancel", style = MaterialTheme.typography.labelLarge) }
            Spacer(Modifier.weight(1f))
            BigButton(
                text = if (state.mode == TryOnMode.GIFT) "Choose their photo" else "Take my photo",
                onClick = onContinue,
                enabled = state.mode != null && state.consent,
                modifier = Modifier.widthIn(min = 260.dp),
            )
        }
        ZariBorder()
    }
}

@Composable
private fun Choice(
    icon: ImageVector,
    title: String,
    body: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        border = BorderStroke(if (selected) 3.dp else 1.dp,
            if (selected) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.outline),
    ) {
        Column(Modifier.padding(20.dp)) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(36.dp), tint = MaterialTheme.colorScheme.tertiary)
            Spacer(Modifier.height(10.dp))
            Text(title, style = MaterialTheme.typography.headlineSmall)
            Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
