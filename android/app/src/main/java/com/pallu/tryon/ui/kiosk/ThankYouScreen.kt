package com.pallu.tryon.ui.kiosk

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pallu.tryon.ui.common.BigButton
import com.pallu.tryon.ui.theme.ZariBorder
import kotlinx.coroutines.delay

@Composable
fun ThankYouScreen(vm: KioskViewModel, onDone: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    var seconds by remember { mutableIntStateOf(20) }
    LaunchedEffect(Unit) {
        while (seconds > 0) {
            delay(1000)
            seconds -= 1
        }
        onDone()
    }

    Column(Modifier.fillMaxSize()) {
        ZariBorder(height = 28.dp)
        Column(
            Modifier.weight(1f).fillMaxWidth().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp, Alignment.CenterVertically),
        ) {
            Text("Thank you", style = MaterialTheme.typography.displayLarge)
            Text("Your selections have been saved.", style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
            if (state.shortlist.isNotEmpty()) {
                Text(
                    "Our staff have your shortlist and will bring these out for you:",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.widthIn(max = 600.dp),
                )
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.shortlist.forEach { name ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Favorite, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(10.dp))
                            Text(name, style = MaterialTheme.typography.titleLarge)
                        }
                    }
                }
            } else {
                Text(
                    "Our staff can help you choose, or bring out more sarees to try.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
            Text(
                "Your photo and the looks have been deleted from this mirror.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            BigButton("Start over ($seconds)", onDone, Modifier.widthIn(min = 260.dp))
        }
        ZariBorder(height = 28.dp)
    }
}
