package app.aroma.data.remote

import app.aroma.data.isConnectivity
import app.aroma.data.toAromaError
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Response
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.io.IOException
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

val MealieJson =
    Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        coerceInputValues = true
        isLenient = true
    }

/** Server and token of the signed-in account. */
data class Credentials(val server: HttpUrl, val token: String)

/**
 * Holds the app's HTTP client and creates Mealie APIs on it. Credentials are read on every request,
 * and [answers] learns of every request to the signed-in server whether Mealie answered it.
 */
class MealieClientFactory(
    private val userAgent: String,
    private val answers: (Boolean) -> Unit = {},
    /** How long a request waits for Mealie to answer; a test's Mealie that never does need not hold one a minute. */
    answerTimeout: Duration = ANSWER_TIMEOUT,
    private val credentials: () -> Credentials?,
) {
    /**
     * Shared client, also used by the image loader so both honor user-installed certificates. A
     * server that answers at all opens a connection within seconds, even over a slow mobile network;
     * on a network that cannot reach it, a request gives up after the connect timeout.
     */
    val httpClient: OkHttpClient =
        OkHttpClient
            .Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(answerTimeout.inWholeMilliseconds, TimeUnit.MILLISECONDS)
            .writeTimeout(answerTimeout.inWholeMilliseconds, TimeUnit.MILLISECONDS)
            .addInterceptor(AnswerInterceptor())
            .addInterceptor(HeadersInterceptor())
            .addInterceptor(ImportTimeouts)
            .build()

    fun create(baseUrl: HttpUrl): MealieApi =
        Retrofit
            .Builder()
            .baseUrl(baseUrl)
            .client(httpClient)
            .addConverterFactory(MealieJson.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(MealieApi::class.java)

    /** A request called off here, as its screen went away, tells nothing about Mealie. */
    private inner class AnswerInterceptor : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val request = chain.request()
            val server = credentials()?.server
            if (server == null || !request.url.belongsTo(server)) return chain.proceed(request)
            val response =
                try {
                    chain.proceed(request)
                } catch (error: IOException) {
                    if (!chain.call().isCanceled() && error.toAromaError().isConnectivity) answers(false)
                    throw error
                }
            answers(true)
            return response
        }
    }

    private inner class HeadersInterceptor : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val request = chain.request()
            val builder =
                request
                    .newBuilder()
                    .header("User-Agent", userAgent)
                    .header("Accept-Language", Locale.getDefault().toLanguageTag())
            val current = credentials()
            if (current != null && request.header("Authorization") == null && request.url.belongsTo(current.server)) {
                builder.header("Authorization", "Bearer ${current.token}")
            }
            return chain.proceed(builder.build())
        }
    }

    companion object {
        val ANSWER_TIMEOUT = 60.seconds
    }
}

/** An import can wait minutes for an AI provider; every other request keeps the short timeouts. */
private object ImportTimeouts : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if ("/api/recipes/create/" !in request.url.encodedPath) return chain.proceed(request)
        return chain
            .withReadTimeout(5, TimeUnit.MINUTES)
            .withWriteTimeout(2, TimeUnit.MINUTES)
            .proceed(request)
    }
}

/** The token must never leave the Mealie server it belongs to. */
internal fun HttpUrl.belongsTo(server: HttpUrl): Boolean =
    scheme == server.scheme && host == server.host && port == server.port && encodedPath.startsWith(server.encodedPath)
