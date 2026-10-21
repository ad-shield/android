package io.adshield.android

import java.io.IOException
import java.util.concurrent.TimeUnit
import okhttp3.Request

/** Dedicated original transport: no recovery interceptor, URLConnection or custom DNS/TLS policy. */
internal class OkHttpClient(timeoutSeconds: Long) {
    data class Result(val code: Int, val body: String = "")
    private class NonRetryableStatus(val status: Int) : IOException()

    private val client = baseClient.newBuilder()
        .connectTimeout(timeoutSeconds, TimeUnit.SECONDS)
        .readTimeout(timeoutSeconds, TimeUnit.SECONDS)
        .writeTimeout(timeoutSeconds, TimeUnit.SECONDS)
        .build()

    private companion object {
        // Derived clients share the connection pool and dispatcher while callers own their timeouts.
        val baseClient = okhttp3.OkHttpClient.Builder()
            .retryOnConnectionFailure(false)
            .followRedirects(false)
            .followSslRedirects(false)
            .addNetworkInterceptor { chain ->
                val response = chain.proceed(chain.request())
                // OkHttp can follow up a 503 + Retry-After: 0 even with connection retries disabled.
                // These statuses are failures in every existing SDK operation; close before retry logic.
                if (response.code() == 408 || response.code() == 503) {
                    val status = response.code()
                    response.close()
                    throw NonRetryableStatus(status)
                }
                response
            }.build()
    }

    fun execute(initial: Request, readBody: Boolean = false): Result {
        var request = initial
        repeat(21) { redirects ->
            val call = client.newCall(request)
            try {
                call.execute().use { response ->
                    val code = response.code()
                    val location = response.header("Location")
                    val target = location?.let { request.url().resolve(it) }
                    val redirect = code in listOf(300, 301, 302, 303, 307, 308)
                    val canFollow = redirect && target != null && target.scheme() == request.url().scheme() &&
                        (code !in listOf(307, 308) || request.method() in listOf("GET", "HEAD"))
                    if (!canFollow) return Result(code, if (readBody && code < 400) response.body()?.string().orEmpty() else "")
                    if (redirects == 20) throw IOException("Too many redirects")
                    val builder = request.newBuilder().url(requireNotNull(target))
                    if (request.method() != "GET" && request.method() != "HEAD") {
                        builder.method("GET", null).removeHeader("Content-Type")
                    }
                    request = builder.build()
                }
            } catch (failure: NonRetryableStatus) {
                return Result(failure.status)
            }
        }
        throw IOException("Too many redirects")
    }
}
