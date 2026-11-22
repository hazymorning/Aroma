package app.aroma.data.remote

import kotlinx.serialization.json.Json
import okhttp3.Cache
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Response
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.Locale
import java.util.concurrent.TimeUnit

val MealieJson =
    Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        coerceInputValues = true
        isLenient = true
    }

/** Server and token of the signed-in account. */
data class Credentials(val server: HttpUrl, val token: String)

/** Creates HTTP clients for a Mealie server. Credentials are read on every request. */
class MealieClientFactory(private val userAgent: String, private val cache: Cache?, private val credentials: () -> Credentials?) {
    /** Shared client, also used by the image loader so both honor user-installed certificates. */
    val httpClient: OkHttpClient =
        OkHttpClient
            .Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .addInterceptor(HeadersInterceptor())
            .addInterceptor(ImportTimeouts)
            .apply { if (cache != null) cache(cache) }
            .build()

    fun create(baseUrl: HttpUrl): MealieApi =
        Retrofit
            .Builder()
            .baseUrl(baseUrl)
            .client(httpClient)
            .addConverterFactory(MealieJson.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(MealieApi::class.java)

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
