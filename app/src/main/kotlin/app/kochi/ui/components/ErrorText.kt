package app.kochi.ui.components

import androidx.annotation.StringRes
import app.kochi.R
import app.kochi.data.KochiError

@StringRes
fun KochiError.titleRes(): Int =
    when (this) {
        is KochiError.Offline -> R.string.error_offline_title
        is KochiError.HostNotFound -> R.string.error_host_title
        is KochiError.Certificate -> R.string.error_certificate_title
        is KochiError.Unauthorized, is KochiError.InvalidCredentials -> R.string.error_auth_title
        is KochiError.Forbidden -> R.string.error_forbidden_title
        is KochiError.NotFound -> R.string.error_not_found_title
        is KochiError.NotMealie -> R.string.error_not_mealie_title
        is KochiError.UnsupportedVersion -> R.string.error_version_title
        is KochiError.ImportFailed -> R.string.error_import_title
        is KochiError.Server, is KochiError.Unknown -> R.string.error_generic_title
    }

@StringRes
fun KochiError.messageRes(): Int =
    when (this) {
        is KochiError.Offline -> R.string.error_offline_message
        is KochiError.HostNotFound -> R.string.error_host_message
        is KochiError.Certificate -> R.string.error_certificate_message
        is KochiError.Unauthorized -> R.string.error_session_message
        is KochiError.InvalidCredentials -> R.string.error_credentials_message
        is KochiError.Forbidden -> R.string.error_forbidden_message
        is KochiError.NotFound -> R.string.error_not_found_message
        is KochiError.NotMealie -> R.string.error_not_mealie_message
        is KochiError.UnsupportedVersion -> R.string.error_version_message
        is KochiError.ImportFailed -> R.string.error_import_message
        is KochiError.Server, is KochiError.Unknown -> R.string.error_generic_message
    }
