package org.example.project.serverData

import kotlinx.serialization.Serializable

@Serializable
data class TotpDevice(val name: String, val secret: String)

/**
 * Server-side storage format. `downloadFolder` is operator-controlled and
 * never exposed to or accepted from the web client.
 */
@Serializable
data class ServerSettings(
    val downloadFolder: String,
    val filelistUsername: String,
    val filelistPassword: String,
    val qbUsername: String,
    val qbPassword: String,
    val totpSecret: String? = null,
    val totpDevices: List<TotpDevice> = emptyList(),
)

/**
 * Wire format exchanged with the web UI. Intentionally omits `downloadFolder`
 * to prevent clients from controlling the FileManager base path.
 *
 * Password fields carry one of:
 *  - empty string — unset
 *  - [MASKED_PASSWORD_SENTINEL] — set on the server but hidden from client
 *  - any other value — new password (will replace existing)
 *
 * `totpDeviceNames` is read-only from the client — 2FA is managed via the totp endpoints.
 */
@Serializable
data class ServerSettingsClient(
    val filelistUsername: String,
    val filelistPassword: String,
    val qbUsername: String,
    val qbPassword: String,
    val totpDeviceNames: List<String> = emptyList(),
)

/** Shared sentinel for masked passwords. Kept in shared so UI code can detect it. */
const val MASKED_PASSWORD_SENTINEL = "__HOMEBOX_MASKED__"