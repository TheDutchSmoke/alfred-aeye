package com.giovanni.aeye.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.io.IOException
import java.net.ConnectException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URI
import java.net.UnknownHostException
import javax.net.ssl.SSLException

/**
 * Loose readers for the untyped JSON the Cursor and Anthropic endpoints return, matching what
 * `JSONSerialization` plus `as?` casts accept on iOS.
 */
object AeyeJson {
    /** Numbers and numeric strings; never booleans. */
    fun double(value: JsonElement?): Double? {
        val primitive = value as? JsonPrimitive ?: return null
        if (primitive is JsonNull) return null
        if (primitive.isString) return primitive.content.toDoubleOrNull()
        if (primitive.content == "true" || primitive.content == "false") return null
        return primitive.content.toDoubleOrNull()
    }

    fun long(value: JsonElement?): Long? {
        val number = double(value) ?: return null
        if (!number.isFinite()) return null
        return number.toLong()
    }

    /** JSON booleans, plus the 0/1 numbers `NSNumber` bridges to `Bool`. */
    fun bool(value: JsonElement?): Boolean? {
        val primitive = value as? JsonPrimitive ?: return null
        if (primitive is JsonNull || primitive.isString) return null
        return when (primitive.content) {
            "true" -> true
            "false" -> false
            else -> when (primitive.content.toDoubleOrNull()) {
                1.0 -> true
                0.0 -> false
                else -> null
            }
        }
    }

    fun isBoolean(value: JsonElement?): Boolean {
        val primitive = value as? JsonPrimitive ?: return false
        return !primitive.isString && (primitive.content == "true" || primitive.content == "false")
    }

    /** `as? String` — JSON strings only. */
    fun string(value: JsonElement?): String? {
        val primitive = value as? JsonPrimitive ?: return null
        return if (primitive.isString) primitive.content else null
    }

    fun obj(value: JsonElement?): JsonObject? = value as? JsonObject

    /** `as? [[String: Any]]` — every element must be an object, or the cast fails. */
    fun objectArray(value: JsonElement?): List<JsonObject>? {
        val array = value as? JsonArray ?: return null
        if (array.any { it !is JsonObject }) return null
        return array.map { it as JsonObject }
    }

    /** `JSONSerialization.jsonObject(with:) as? [String: Any]`. */
    fun parseObject(bytes: ByteArray): JsonObject? =
        try {
            Json.parseToJsonElement(bytes.toString(Charsets.UTF_8)) as? JsonObject
        } catch (_: Exception) {
            null
        }
}

class HttpRequest(
    val url: String,
    val method: String = "GET",
    val headers: Map<String, String> = emptyMap(),
    val body: ByteArray? = null,
)

class HttpResponse(val status: Int, val body: ByteArray) {
    val isSuccess: Boolean get() = status in 200..299
    val text: String get() = body.toString(Charsets.UTF_8)
}

/** The one seam between the app and the network, so tests can answer for Cursor and Anthropic. */
fun interface HttpTransport {
    suspend fun send(request: HttpRequest): HttpResponse
}

/**
 * `AeyeHTTP.session` — an ephemeral session (no cache, no cookie jar) with a 20 s request and
 * 30 s overall timeout. `HttpURLConnection` is on both the JVM and Android, so `:core` needs no
 * HTTP library.
 */
class UrlConnectionTransport : HttpTransport {
    override suspend fun send(request: HttpRequest): HttpResponse = try {
        withTimeout(30_000) { perform(request) }
    } catch (_: TimeoutCancellationException) {
        // A timeout is a network failure, not a cancellation of whoever asked.
        throw SocketTimeoutException("The request timed out.")
    }

    private suspend fun perform(request: HttpRequest): HttpResponse =
        withContext(Dispatchers.IO) {
            val connection = URI(request.url).toURL().openConnection() as HttpURLConnection
            try {
                connection.requestMethod = request.method
                connection.useCaches = false
                connection.connectTimeout = 20_000
                connection.readTimeout = 20_000
                connection.instanceFollowRedirects = true
                request.headers.forEach { (name, value) -> connection.setRequestProperty(name, value) }
                if (request.body != null) {
                    connection.doOutput = true
                    connection.outputStream.use { it.write(request.body) }
                }
                val status = connection.responseCode
                val stream = if (status >= 400) connection.errorStream else connection.inputStream
                HttpResponse(status, stream?.use { it.readBytes() } ?: ByteArray(0))
            } finally {
                connection.disconnect()
            }
        }
}

/**
 * `error.localizedDescription`. Aeye's own errors carry their message; network failures get the
 * wording `URLError` uses on iOS, so an error row reads the same on both phones.
 */
fun errorMessage(error: Throwable): String = when (error) {
    is AeyeError -> error.message ?: "Unknown error"
    is UnknownHostException -> "The Internet connection appears to be offline."
    is SocketTimeoutException -> "The request timed out."
    is ConnectException -> "Could not connect to the server."
    is SSLException -> "An SSL error has occurred and a secure connection to the server cannot be made."
    is IOException -> error.message?.takeIf { it.isNotBlank() } ?: "The network connection was lost."
    else -> error.message?.takeIf { it.isNotBlank() } ?: error.toString()
}

/** Base for every error whose message is shown to the user as-is. */
open class AeyeError(message: String) : Exception(message)

/** `String(message.prefix(n))`, without splitting a surrogate pair. */
internal fun String.prefixCodePoints(n: Int): String {
    if (codePointCount(0, length) <= n) return this
    return substring(0, offsetByCodePoints(0, n))
}

internal fun formEncode(value: String): String = buildString {
    // URLComponents leaves the URL-safe characters in a query value alone and encodes the rest.
    for (byte in value.toByteArray(Charsets.UTF_8)) {
        val c = byte.toInt() and 0xFF
        val ch = c.toChar()
        if (ch.isLetterOrDigit() && c < 0x80 || ch in "-._~:/@!$'()*,;?") append(ch)
        else append('%').append("%02X".format(c))
    }
}

internal fun buildUrl(base: String, query: List<Pair<String, String>>): String =
    base + "?" + query.joinToString("&") { (name, value) -> "${formEncode(name)}=${formEncode(value)}" }
