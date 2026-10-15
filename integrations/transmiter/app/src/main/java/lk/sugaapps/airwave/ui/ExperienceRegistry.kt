package lk.sugaapps.airwave.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import lk.sugaapps.airwave.ui.theme.TextMuted

/**
 * Lets the local/private build plug in the full broadcast experience (the
 * screens that reproduce the existing app) WITHOUT the public repo referencing
 * them at compile time. The private module registers its screen here at startup;
 * the public build keeps the default placeholder and still compiles and runs.
 */
object ExperienceRegistry {
    var home: (@androidx.compose.runtime.Composable () -> Unit) = { PublicHomePlaceholder() }
}

@androidx.compose.runtime.Composable
fun PublicHomePlaceholder() {
    Box(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "Broadcast console is part of the private module and is not included in this build.",
            style = MaterialTheme.typography.bodyLarge,
            color = TextMuted,
            textAlign = TextAlign.Center,
        )
    }
}
