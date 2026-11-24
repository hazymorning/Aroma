package app.aroma.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import app.aroma.R
import app.aroma.ui.components.AromaAlertDialog
import app.aroma.ui.components.DetailPage
import app.aroma.ui.components.Separator
import app.aroma.ui.components.Text
import app.aroma.ui.components.TonalBox
import app.aroma.ui.theme.AromaTheme
import app.aroma.ui.theme.BoxPadding
import app.aroma.ui.theme.Glyph
import app.aroma.ui.theme.PageMargin
import app.aroma.ui.theme.RowHeight
import app.aroma.ui.theme.Space

private data class License(val component: String, val license: String, val asset: String? = null)

private val licenses =
    listOf(
        License("Newsreader", "SIL Open Font License 1.1", "licenses/OFL-Newsreader.txt"),
        License("Instrument Sans", "SIL Open Font License 1.1", "licenses/OFL-InstrumentSans.txt"),
        License("Android Jetpack (AndroidX, Compose, Room, WorkManager)", "Apache License 2.0"),
        License("Kotlin, kotlinx.coroutines, kotlinx.serialization", "Apache License 2.0"),
        License("OkHttp, Retrofit", "Apache License 2.0"),
        License("Coil", "Apache License 2.0"),
    )

@Composable
fun LicensesScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    var openAsset by rememberSaveable { mutableStateOf<String?>(null) }
    DetailPage(title = stringResource(R.string.settings_licenses), onBack = onBack, modifier = modifier) {
        TonalBox(Modifier.padding(horizontal = PageMargin), contentPadding = PaddingValues()) {
            licenses.forEachIndexed { index, item ->
                if (index > 0) Separator()
                LicenseRow(item, onOpen = item.asset?.let { asset -> { openAsset = asset } })
            }
        }
    }
    openAsset?.let { asset ->
        LicenseDialog(title = licenses.first { it.asset == asset }.component, asset = asset, onDismiss = { openAsset = null })
    }
}

/** One component and its licence; rows whose full text ships with the app open it. */
@Composable
private fun LicenseRow(item: License, onOpen: (() -> Unit)?) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = RowHeight.twoLines)
            .then(if (onOpen != null) Modifier.clickable(role = Role.Button, onClick = onOpen) else Modifier)
            .padding(horizontal = BoxPadding, vertical = Space.s12),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(item.component, style = AromaTheme.type.body)
            Text(item.license, style = AromaTheme.type.bodySmall, color = AromaTheme.colors.inkSecondary)
        }
        if (onOpen != null) {
            Spacer(Modifier.width(Space.s16))
            Icon(
                painterResource(R.drawable.ic_chevron_right),
                contentDescription = null,
                tint = AromaTheme.colors.inkSecondary,
                modifier = Modifier.size(Glyph.meta),
            )
        }
    }
}

/** The full licence on paper, with a single way out. */
@Composable
private fun LicenseDialog(title: String, asset: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val text =
        remember(asset) {
            context.assets
                .open(asset)
                .bufferedReader()
                .use { reflow(it.readText()) }
        }
    AromaAlertDialog(
        onDismissRequest = onDismiss,
        title = title,
        confirmLabel = stringResource(R.string.action_close),
        onConfirm = onDismiss,
        content = {
            Text(
                text = text,
                style = AromaTheme.type.bodySmall,
                color = AromaTheme.colors.inkSecondary,
                modifier = Modifier.verticalScroll(rememberScrollState()),
            )
        },
    )
}

/**
 * The licence files are wrapped at 80 columns. Joining the lines of each paragraph lets the text
 * wrap once, to the dialog; a short heading in capitals keeps its own line and rules of dashes go.
 */
private fun reflow(text: String): String =
    text
        .split(Regex("""\n\s*\n"""))
        .map { paragraph -> paragraph.lines().map(String::trim).filter { line -> line.any { it != '-' } } }
        .filter { it.isNotEmpty() }
        .joinToString("\n\n") { lines ->
            val heading = lines.first().takeIf { lines.size > 1 && it.length <= 24 && it.none(Char::isLowerCase) }
            if (heading != null) heading + "\n" + lines.drop(1).joinToString(" ") else lines.joinToString(" ")
        }
