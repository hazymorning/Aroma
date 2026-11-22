package app.aroma.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.aroma.R

private data class License(val component: String, val license: String, val asset: String? = null)

private val licenses =
    listOf(
        License("Newsreader", "SIL Open Font License 1.1", "licenses/OFL-Newsreader.txt"),
        License("Figtree", "SIL Open Font License 1.1", "licenses/OFL-Figtree.txt"),
        License("Material Symbols", "Apache License 2.0"),
        License("Android Jetpack (AndroidX, Compose, Room, WorkManager)", "Apache License 2.0"),
        License("Kotlin, kotlinx.coroutines, kotlinx.serialization", "Apache License 2.0"),
        License("OkHttp, Retrofit", "Apache License 2.0"),
        License("Coil", "Apache License 2.0"),
    )

@Composable
fun LicensesScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var open by rememberSaveable { mutableStateOf<String?>(null) }
    Column(
        modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding(),
    ) {
        IconButton(onClick = onBack, modifier = Modifier.padding(8.dp)) {
            Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = stringResource(R.string.action_back))
        }
        Text(
            text = stringResource(R.string.settings_licenses),
            style = MaterialTheme.typography.displaySmall,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 16.dp),
        )
        licenses.forEach { item ->
            val rowModifier = if (item.asset != null) Modifier.clickable(role = Role.Button) { open = item.asset } else Modifier
            Column(
                rowModifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
            ) {
                Text(item.component, style = MaterialTheme.typography.bodyLarge)
                Text(item.license, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    open?.let { asset ->
        val text =
            remember(asset) {
                context.assets
                    .open(asset)
                    .bufferedReader()
                    .use { it.readText() }
            }
        AlertDialog(
            onDismissRequest = { open = null },
            text = { Text(text, style = MaterialTheme.typography.bodySmall, modifier = Modifier.verticalScroll(rememberScrollState())) },
            confirmButton = { TextButton(onClick = { open = null }) { Text(stringResource(R.string.action_close)) } },
        )
    }
}
