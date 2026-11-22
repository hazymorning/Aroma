package app.kochi.data

import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerializationException
import retrofit2.HttpException
import java.io.IOException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

/** Everything that can go wrong when talking to a Mealie server, in terms the UI can explain. */
sealed class KochiError(message: String? = null, cause: Throwable? = null) : Exception(message, cause) {
    class Offline(cause: Throwable? = null) : KochiError(cause = cause)

    class HostNotFound(cause: Throwable? = null) : KochiError(cause = cause)

    class Certificate(cause: Throwable? = null) : KochiError(cause = cause)

    class Unauthorized : KochiError()

    class InvalidCredentials : KochiError()

    class Forbidden : KochiError()

    class NotFound : KochiError()

    class NotMealie(cause: Throwable? = null) : KochiError(cause = cause)

    class UnsupportedVersion(val version: String) : KochiError(version)

    class ImportFailed : KochiError()

    class Server(val code: Int) : KochiError("HTTP $code")

    class Unknown(cause: Throwable? = null) : KochiError(cause = cause)
}

/** True when the server could not be reached at all, as opposed to answering with an error. */
val KochiError.isConnectivity: Boolean get() = this is KochiError.Offline || this is KochiError.HostNotFound

fun Throwable.toKochiError(): KochiError =
    when (this) {
        is CancellationException -> {
            throw this
        }

        is KochiError -> {
            this
        }

        is UnknownHostException -> {
            KochiError.HostNotFound(this)
        }

        is SSLException -> {
            KochiError.Certificate(this)
        }

        is IOException -> {
            KochiError.Offline(this)
        }

        is HttpException -> {
            when (code()) {
                401 -> KochiError.Unauthorized()
                403 -> KochiError.Forbidden()
                404 -> KochiError.NotFound()
                in 500..599 -> KochiError.Server(code())
                else -> KochiError.Server(code())
            }
        }

        is SerializationException, is IllegalArgumentException -> {
            KochiError.NotMealie(this)
        }

        else -> {
            KochiError.Unknown(this)
        }
    }

/** Runs [block] and translates any failure into a [KochiError]. */
suspend inline fun <T> mealieCall(block: suspend () -> T): T =
    try {
        block()
    } catch (error: Throwable) {
        throw error.toKochiError()
    }
