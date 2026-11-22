package app.aroma.data

import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerializationException
import retrofit2.HttpException
import java.io.IOException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

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

    class ImportFailed : AromaError()

    class Server(val code: Int) : AromaError("HTTP $code")

    class Unknown(cause: Throwable? = null) : AromaError(cause = cause)
}

/** True when the server could not be reached at all, as opposed to answering with an error. */
val AromaError.isConnectivity: Boolean get() = this is AromaError.Offline || this is AromaError.HostNotFound

fun Throwable.toAromaError(): AromaError =
    when (this) {
        is CancellationException -> {
            throw this
        }

        is AromaError -> {
            this
        }

        is UnknownHostException -> {
            AromaError.HostNotFound(this)
        }

        is SSLException -> {
            AromaError.Certificate(this)
        }

        is IOException -> {
            AromaError.Offline(this)
        }

        is HttpException -> {
            when (code()) {
                401 -> AromaError.Unauthorized()
                403 -> AromaError.Forbidden()
                404 -> AromaError.NotFound()
                in 500..599 -> AromaError.Server(code())
                else -> AromaError.Server(code())
            }
        }

        is SerializationException, is IllegalArgumentException -> {
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
