package me.him188.ani.app.ui.settings.tabs.media.torrent.peer

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import me.him188.ani.app.ui.foundation.ProvideCompositionLocalsForPreview
import kotlin.random.Random
import kotlin.random.nextInt

private fun generateRandomIp(): String {
    return sequence<Int> { Random.nextInt(0..255) }
        .take(4)
        .joinToString(".")
}

@Preview
@Composable
fun PreviewBlockListEditPane() {
    ProvideCompositionLocalsForPreview {
        val list = remember {
            sequence<String> { generateRandomIp() }
                .take(Random.nextInt(3..30))
                .toMutableList()
        }
        BlockListEditPane(
            blockedIpList = list, 
            contentPadding = PaddingValues(16.dp), 
            showTitle = true,
            onAdd = { list.add(it) },
            onRemove = { newIp -> list.removeIf { it == newIp } }
        )
    }
}