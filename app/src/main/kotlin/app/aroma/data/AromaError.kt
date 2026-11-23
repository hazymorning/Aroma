package app.aroma.data

import kotlinx.coroutines.CancellationException
import retrofit2.HttpException
import java.io.IOException
import java.net.UnknownHostException
import java.security.cert.CertificateException
import javax.net.ssl.SSLException
import javax.net.ssl.SSLHandshakeException
import javax.net.ssl.SSLPeerUnverifiedException

/** Everything that can go wrong when talking to a Mealie server, in terms the UI can explain. */
sealed class AromaError(message: String? = null, cause: Throwable? = null) : Exception(message, cause) {
    class Offline(cause: Throwable? = null) : AromaError(cause = cause)

    class HostNotFound(cause: Throwable? = null) : AromaError(cause = cause)

    class Certificate(cause: Throwable? = null) : AromaError(cause = cause)

    class Unauthorized : AromaError()

    class InvalidCredentials : AromaError()

    class Forbidden : AromaError()

    class NotFound : AromaError()

    class NotMealie(cause: Throwable? = null) : AromaError(cause = cause)

    class UnsupportedVersion(val version: String) : AromaError(version)

    /**
     * Mealie could not turn the source into a recipe. [detail] is Mealie's own explanation when it
     * sent one in words rather than as an error code.
     */
    class ImportFailed(val reason: Reason = Reason.NoRecipe, val detail: String? = null) : AromaError(detail ?: reason.name) {
        enum class Reason {
            /** Mealie read the page but found no recipe on it. */
            NoRecipe,

            /** Mealie could not load the page. */
            PageUnreachable,

            /** The connection dropped and no recipe turned up while Aroma waited for it. */
            NoAnswer,

            /** Neither the phone nor Mealie could read the post, and no text of it was given. */
            PostUnreadable,
        }
    }

    class Server(val code: Int) : AromaError("HTTP $code")

    class Unknown(cause: Throwable? = null) : AromaError(cause = cause)
}

/** True when the server could not be reached at all, as opposed to answering with an error. */
val AromaError.isConnectivity: Boolean get() = this is AromaError.Offline || this is AromaError.HostNotFound

fun Throwable.toAromaError(): AromaError =
    when (this) {
        is CancellationException, is Error -> {
            throw this
        }

        is AromaError -> {
            this
        }

        is UnknownHostException -> {
            AromaError.HostNotFound(this)
        }

        // Only a failed handshake is about trust; Android also reports a connection reset in the
        // middle of an HTTPS response as an SSLException, which is just a dropped connection.
        is SSLHandshakeException, is SSLPeerUnverifiedException -> {
            AromaError.Certificate(this)
        }

        is SSLException -> {
            if (cause is CertificateException) AromaError.Certificate(this) else AromaError.Offline(this)
        }

        is IOException -> {
            AromaError.Offline(this)
        }

        is HttpException -> {
            when (code()) {
                401 -> AromaError.Unauthorized()
                403 -> AromaError.Forbidden()
                404 -> AromaError.NotFound()
                else -> AromaError.Server(code())
            }
        }

        // SerializationException is one, and so is .jsonObject on JSON that is not an object.
        is IllegalArgumentException -> {
            AromaError.NotMealie(this)
        }

        else -> {
            AromaError.Unknown(this)
        }
    }

/** Runs [block] and translates any failure into a [AromaError]. */
suspend inline fun <T> mealieCall(block: suspend () -> T): T =
    try {
        block()
    } catch (error: Throwable) {
        throw error.toAromaError()
    }

/**
 * Runs [block] for a screen that shows what went wrong: its failure as an [AromaError]. Unlike
 * `runCatching`, it lets cancellation through, so work for a screen that is gone stops there.
 */
suspend inline fun <T> attempt(block: suspend () -> T): Result<T> =
    try {
        Result.success(block())
    } catch (error: Exception) {
        Result.failure(error.toAromaError())
    }
