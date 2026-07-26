package com.homecontrol.ios.sony

import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.put

private const val SONY_HTTP_PORT = 80
private val json = Json { ignoreUnknownKeys = true }

internal sealed interface RegistrationOutcome {
    data object Registered : RegistrationOutcome
    data object PinRequired : RegistrationOutcome
    data class Failed(val reason: String) : RegistrationOutcome
}

/**
 * iOS port of `plugin-sonytv`'s `SonyApiClient.kt` — same two wire formats
 * (ScalarWebAPI JSON-RPC-ish over plain HTTP for system/app control, IRCC-IP
 * SOAP for D-pad/volume/power), same endpoints, same request shapes. Uses
 * Ktor's Darwin engine (already pulled in for [com.homecontrol.ios.companion.CompanionConnection]'s
 * WSS transport, so no new dependency) instead of OkHttp, and is suspend
 * throughout rather than blocking -- there's no raw-socket handshake here
 * the way ADB/Samsung's legacy protocol have, so there's nothing forcing a
 * blocking shape the way [com.homecontrol.core.netio.TcpSocket] does for
 * those; a Ktor HTTP client is naturally suspend-based.
 *
 * Plain HTTP (port 80), not HTTPS — unlike the Companion protocol's WSS
 * transport, there's no TLS trust-on-first-use complexity to work around
 * here at all.
 */
@OptIn(ExperimentalEncodingApi::class)
internal class SonyApiClient(private val ipAddress: String, private val psk: String? = null) {

    private val client = HttpClient(Darwin) {
        install(HttpTimeout) {
            requestTimeoutMillis = 5_000
            connectTimeoutMillis = 5_000
        }
    }

    var authCookie: String? = null
        private set

    /** First call of the pairing handshake — the TV responds 401 and shows a PIN on screen if it doesn't already trust [clientId]. */
    suspend fun beginRegistration(clientId: String): RegistrationOutcome = register(clientId, pin = null)

    /** Second call, with the PIN the user read off the TV screen, sent as the password half of HTTP Basic auth (empty username). */
    suspend fun completeRegistration(clientId: String, pin: String): RegistrationOutcome = register(clientId, pin)

    private suspend fun register(clientId: String, pin: String?): RegistrationOutcome {
        val body = buildJsonObject {
            put("method", "actRegister")
            put("id", 1)
            put(
                "params",
                buildJsonArray {
                    add(
                        buildJsonObject {
                            put("clientid", clientId)
                            put("nickname", "Controly")
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

        return runCatching {
            val response = client.post("http://$ipAddress/sony/accessControl") {
                contentType(ContentType.Application.Json)
                setBody(body.toString())
                if (pin != null) {
                    val credential = Base64.encode(":$pin".encodeToByteArray())
                    header("Authorization", "Basic $credential")
                }
            }
            when {
                response.status.isSuccess() -> {
                    authCookie = response.headers.getAll("Set-Cookie")?.firstOrNull()?.substringBefore(";")
                    RegistrationOutcome.Registered
                }
                response.status.value == 401 -> RegistrationOutcome.PinRequired
                else -> RegistrationOutcome.Failed("HTTP ${response.status.value}")
            }
        }.getOrElse { error ->
            RegistrationOutcome.Failed(error.message ?: "Couldn't reach $ipAddress:$SONY_HTTP_PORT")
        }
    }

    suspend fun call(service: String, method: String, params: JsonArray = JsonArray(emptyList()), version: String = "1.0"): JsonElement? {
        val body = buildJsonObject {
            put("method", method)
            put("id", 1)
            put("params", params)
            put("version", version)
        }

        return runCatching {
            val response = client.post("http://$ipAddress/sony/$service") {
                contentType(ContentType.Application.Json)
                setBody(body.toString())
                if (psk != null) {
                    header("X-Auth-PSK", psk)
                } else {
                    authCookie?.let { header("Cookie", it) }
                }
            }
            json.parseToJsonElement(response.bodyAsText())
        }.getOrNull()
    }

    /**
     * The exact IRCC codes *this* TV actually understands, keyed by Sony's
     * own command names — matches the Android plugin's approach of querying
     * this rather than trusting a hardcoded "widely published" table.
     */
    suspend fun getRemoteControllerCodes(): Map<String, String>? {
        val result = call("system", "getRemoteControllerInfo") as? JsonObject ?: return null
        val codes = result["result"]?.jsonArray?.getOrNull(1)?.jsonArray ?: return null
        return codes.mapNotNull { entry ->
            val obj = entry as? JsonObject ?: return@mapNotNull null
            val name = (obj["name"] as? JsonPrimitive)?.content ?: return@mapNotNull null
            val value = (obj["value"] as? JsonPrimitive)?.content ?: return@mapNotNull null
            name to value
        }.toMap()
    }

    /** The TV's actual external inputs (HDMI 1-4, Component, etc.) with their real `uri`s. */
    suspend fun getExternalInputs(): List<Pair<String, String>>? {
        val result = call("avContent", "getCurrentExternalInputsStatus") as? JsonObject ?: return null
        val terminals = result["result"]?.jsonArray?.getOrNull(0)?.jsonArray ?: return null
        return terminals.mapNotNull { entry ->
            val obj = entry as? JsonObject ?: return@mapNotNull null
            val uri = (obj["uri"] as? JsonPrimitive)?.content ?: return@mapNotNull null
            val title = (obj["title"] as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() } ?: uri
            uri to title
        }
    }

    /** Switches to a specific input by its `uri` (from [getExternalInputs]) -- the real, documented way to pick an exact source, unlike the IRCC "Input" button which only cycles one step at a time. */
    suspend fun setActiveInput(uri: String): Boolean {
        val params = buildJsonArray { add(buildJsonObject { put("uri", uri) }) }
        val response = call("avContent", "setPlayContent", params) as? JsonObject ?: return false
        return response["error"] == null
    }

    suspend fun sendIrcc(irccCode: String) {
        val soapBody =
            "<?xml version=\"1.0\"?>" +
                "<s:Envelope xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\" s:encodingStyle=\"http://schemas.xmlsoap.org/soap/encoding/\">" +
                "<s:Body><u:X_SendIRCC xmlns:u=\"urn:schemas-sony-com:service:IRCC:1\">" +
                "<IRCCCode>$irccCode</IRCCCode>" +
                "</u:X_SendIRCC></s:Body></s:Envelope>"

        runCatching {
            client.post("http://$ipAddress/sony/IRCC") {
                contentType(ContentType.Text.Xml)
                header("SOAPACTION", "\"urn:schemas-sony-com:service:IRCC:1#X_SendIRCC\"")
                setBody(soapBody)
                if (psk != null) {
                    header("X-Auth-PSK", psk)
                } else {
                    authCookie?.let { header("Cookie", it) }
                }
            }
        }
    }

    fun close() {
        client.close()
    }
}
