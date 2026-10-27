package com.pallu.tryon.ui.kiosk

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pallu.tryon.ui.theme.MirrorDeep
import com.pallu.tryon.ui.theme.MirrorLine
import com.pallu.tryon.ui.theme.OnGold
import com.pallu.tryon.ui.theme.OnMirror
import com.pallu.tryon.ui.theme.ZariLight

/**
 * "Front / Back" camera choice. On a tablet on a stand the front camera faces the customer;
 * some stores mount it the other way round, or prefer the sharper back camera.
 */
@Composable
fun CameraSwitch(front: Boolean, onChange: (front: Boolean) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .clip(RoundedCornerShape(50))
            .background(MirrorDeep.copy(alpha = 0.85f))
            .border(1.dp, MirrorLine, RoundedCornerShape(50))
            .padding(4.dp)
            .selectableGroup(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Icon(
            Icons.Filled.Cameraswitch, contentDescription = null, tint = ZariLight,
            modifier = Modifier.padding(start = 10.dp, end = 4.dp).size(22.dp),
        )
        Pill("Front", selected = front) { onChange(true) }
        Pill("Back", selected = !front) { onChange(false) }
    }
}

@Composable
private fun Pill(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .heightIn(min = 44.dp)
            .clip(RoundedCornerShape(50))
            .background(if (selected) ZariLight else Color.Transparent)
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
            .padding(horizontal = 18.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "$label camera".takeIf { selected } ?: label,
            style = MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp),
            color = if (selected) OnGold else OnMirror,
        )
    }
}

/** Small round "switch camera" button for the full-screen photo view. */
@Composable
fun CameraFlipButton(front: Boolean, onChange: (front: Boolean) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(MirrorDeep.copy(alpha = 0.85f))
            .border(1.dp, MirrorLine, RoundedCornerShape(16.dp))
            .selectable(selected = false, onClick = { onChange(!front) }, role = Role.Button)
            .heightIn(min = 56.dp)
            .padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Cameraswitch, contentDescription = null, tint = ZariLight, modifier = Modifier.size(26.dp))
        Spacer(Modifier.width(10.dp))
        Text(
            if (front) "Use back camera" else "Use front camera",
            style = MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp),
            color = OnMirror,
        )
    }
}
