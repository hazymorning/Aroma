package app.aroma.ui.components

import androidx.annotation.StringRes
import app.aroma.R
import app.aroma.data.AromaError

@StringRes
fun AromaError.titleRes(): Int =
    when (this) {
        is AromaError.Offline -> R.string.error_offline_title
        is AromaError.HostNotFound -> R.string.error_host_title
        is AromaError.Certificate -> R.string.error_certificate_title
        is AromaError.Unauthorized, is AromaError.InvalidCredentials -> R.string.error_auth_title
        is AromaError.Forbidden -> R.string.error_forbidden_title
        is AromaError.NotFound -> R.string.error_not_found_title
        is AromaError.NotMealie -> R.string.error_not_mealie_title
        is AromaError.UnsupportedVersion -> R.string.error_version_title
        is AromaError.ImportFailed -> R.string.error_import_title
        is AromaError.Server, is AromaError.Unknown -> R.string.error_generic_title
    }

@StringRes
fun AromaError.messageRes(): Int =
    when (this) {
        is AromaError.Offline -> R.string.error_offline_message
        is AromaError.HostNotFound -> R.string.error_host_message
        is AromaError.Certificate -> R.string.error_certificate_message
        is AromaError.Unauthorized -> R.string.error_session_message
        is AromaError.InvalidCredentials -> R.string.error_credentials_message
        is AromaError.Forbidden -> R.string.error_forbidden_message
        is AromaError.NotFound -> R.string.error_not_found_message
        is AromaError.NotMealie -> R.string.error_not_mealie_message
        is AromaError.UnsupportedVersion -> R.string.error_version_message
        is AromaError.ImportFailed -> R.string.error_import_message
        is AromaError.Server, is AromaError.Unknown -> R.string.error_generic_message
    }
