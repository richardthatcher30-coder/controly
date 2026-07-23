package com.homecontrol.plugins.sonytv.networking

import android.util.Base64
import com.homecontrol.core.model.unreachablePortMessage
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

private const val SONY_HTTP_PORT = 80
private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
private val XML_MEDIA_TYPE = "text/xml; charset=utf-8".toMediaType()
private val json = Json { ignoreUnknownKeys = true }

internal sealed interface RegistrationOutcome {
    data object Registered : RegistrationOutcome
    data object PinRequired : RegistrationOutcome
    data class Failed(val reason: String) : RegistrationOutcome
}

/**
 * Sony Bravia's local "ScalarWebAPI" (JSON-RPC-ish over plain HTTP, port 80)
 * for system/app control, plus the older IRCC-IP SOAP endpoint for D-pad/
 * volume/power button simulation — two different wire formats for one
 * device, because Sony layered the newer JSON API on top of an older one
 * rather than replacing it.
 */
internal class SonyApiClient(private val ipAddress: String) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()

    var authCookie: String? = null
        private set

    /** First call of the pairing handshake — the TV responds 401 and shows a PIN on screen if it doesn't already trust [clientId]. */
    fun beginRegistration(clientId: String): RegistrationOutcome = register(clientId, pin = null)

    /** Second call, with the PIN the user read off the TV screen, sent as the password half of HTTP Basic auth (empty username). */
    fun completeRegistration(clientId: String, pin: String): RegistrationOutcome = register(clientId, pin)

    private fun register(clientId: String, pin: String?): RegistrationOutcome {
        val body = buildJsonObject {
            put("method", "actRegister")
            put("id", 1)
            put(
                "params",
                buildJsonArray {
                    add(
                        buildJsonObject {
                            put("clientid", clientId)
                            put("nickname", "HomeControl")
                            put("level", "private")
                        },
                    )
                    add(
                        buildJsonArray {
                            add(
                                buildJsonObject {
                                    put("value", "yes")
                                    put("function", "WOL")
                                },
                            )
                        },
                    )
                },
            )
            put("version", "1.0")
        }

        val requestBuilder = Request.Builder()
            .url("http://$ipAddress/sony/accessControl")
            .post(body.toString().toRequestBody(JSON_MEDIA_TYPE))

        if (pin != null) {
            val credential = Base64.encodeToString(":$pin".toByteArray(), Base64.NO_WRAP)
            requestBuilder.header("Authorization", "Basic $credential")
        }

        return runCatching {
            client.newCall(requestBuilder.build()).execute().use { response ->
                when {
                    response.isSuccessful -> {
                        authCookie = response.headers("Set-Cookie").firstOrNull()?.substringBefore(";")
                        RegistrationOutcome.Registered
                    }
                    response.code == 401 -> RegistrationOutcome.PinRequired
                    else -> RegistrationOutcome.Failed("HTTP ${response.code}")
                }
            }
        }.getOrElse { error ->
            val detail = when (error) {
                is ConnectException, is SocketTimeoutException, is UnknownHostException ->
                    unreachablePortMessage(ipAddress, SONY_HTTP_PORT)
                else -> error.message ?: "Network error"
            }
            RegistrationOutcome.Failed(detail)
        }
    }

    fun call(service: String, method: String, params: JsonArray = JsonArray(emptyList()), version: String = "1.0"): JsonElement? {
        val body = buildJsonObject {
            put("method", method)
            put("id", 1)
            put("params", params)
            put("version", version)
        }

        val requestBuilder = Request.Builder()
            .url("http://$ipAddress/sony/$service")
            .post(body.toString().toRequestBody(JSON_MEDIA_TYPE))
        authCookie?.let { requestBuilder.header("Cookie", it) }

        return runCatching {
            client.newCall(requestBuilder.build()).execute().use { response ->
                response.body?.string()?.let { json.parseToJsonElement(it) }
            }
        }.getOrNull()
    }

    /**
     * The exact IRCC codes *this* TV actually understands, keyed by Sony's
     * own command names ("Up", "Home", "VolumeUp", "ChannelUp", "Input",
     * etc.) — querying this beats hardcoding "widely published" codes that
     * turned out to only partially work on real hardware (confirmed: some
     * standard codes silently did nothing on this exact model, others did).
     */
    fun getRemoteControllerCodes(): Map<String, String>? {
        val result = call("system", "getRemoteControllerInfo") as? JsonObject ?: return null
        val codes = result["result"]?.jsonArray?.getOrNull(1)?.jsonArray ?: return null
        return codes.mapNotNull { entry ->
            val obj = entry as? JsonObject ?: return@mapNotNull null
            val name = (obj["name"] as? JsonPrimitive)?.content ?: return@mapNotNull null
            val value = (obj["value"] as? JsonPrimitive)?.content ?: return@mapNotNull null
            name to value
        }.toMap()
    }

    /**
     * The TV's actual external inputs (HDMI 1-4, Component, etc.) with
     * their real `uri`s — queried rather than assumed, since the exact port
     * numbering and which inputs even exist varies per model.
     */
    fun getExternalInputs(): List<Pair<String, String>>? {
        // Confirmed via this TV's own getMethodTypes listing — the method is
        // named "...Inputs...", not "...Terminals..." as some Sony API docs
        // and reference code elsewhere call it. The wrong name doesn't 404;
        // it comes back as {"error":[12,"..."]} ("Illegal Argument"), which
        // silently looked identical to "TV has no inputs" further up the
        // stack until this was queried directly against the real device.
        val result = call("avContent", "getCurrentExternalInputsStatus") as? JsonObject ?: return null
        val terminals = result["result"]?.jsonArray?.getOrNull(0)?.jsonArray ?: return null
        return terminals.mapNotNull { entry ->
            val obj = entry as? JsonObject ?: return@mapNotNull null
            val uri = (obj["uri"] as? JsonPrimitive)?.content ?: return@mapNotNull null
            val title = (obj["title"] as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() } ?: uri
            uri to title
        }
    }

    /** Switches to a specific input by its `uri` (from [getExternalInputs]) — the real, documented way to pick an exact source, unlike the IRCC "Input" button which only cycles one step at a time. */
    fun setActiveInput(uri: String): Boolean {
        val params = buildJsonArray { add(buildJsonObject { put("uri", uri) }) }
        val response = call("avContent", "setPlayContent", params) as? JsonObject ?: return false
        return response["error"] == null
    }

    fun sendIrcc(irccCode: String) {
        val soapBody =
            "<?xml version=\"1.0\"?>" +
                "<s:Envelope xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\" s:encodingStyle=\"http://schemas.xmlsoap.org/soap/encoding/\">" +
                "<s:Body><u:X_SendIRCC xmlns:u=\"urn:schemas-sony-com:service:IRCC:1\">" +
                "<IRCCCode>$irccCode</IRCCCode>" +
                "</u:X_SendIRCC></s:Body></s:Envelope>"

        val requestBuilder = Request.Builder()
            .url("http://$ipAddress/sony/IRCC")
            .header("SOAPACTION", "\"urn:schemas-sony-com:service:IRCC:1#X_SendIRCC\"")
            .post(soapBody.toRequestBody(XML_MEDIA_TYPE))
        authCookie?.let { requestBuilder.header("Cookie", it) }

        runCatching { client.newCall(requestBuilder.build()).execute().close() }
    }

    fun close() {
        runCatching {
            client.dispatcher.executorService.shutdown()
            client.connectionPool.evictAll()
        }
    }
}
