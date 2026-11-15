package org.example.project

const val DEV_ENV = "dev"
const val PROD_ENV = "prod"
const val APP_ENV = "APP_ENV"
const val SERVER_NAME = "myserver"
const val SESSION_ENCRYPT_KEY_HEX = "SESSION_ENCRYPT_KEY_HEX"
const val SESSION_SIGN_KEY_HEX = "SESSION_SIGN_KEY_HEX"

const val ADMIN_SESSION = "admin-session"

const val SERVER_SETTINGS_FILE = "server_settings.json"

const val DEFAULT_DOWNLOAD_FOLDER = "Download"

/** Operator-controlled env var for the FileManager base path. */
const val HOMEBOX_DOWNLOAD_FOLDER = "HOMEBOX_DOWNLOAD_FOLDER"

/** Hex-encoded 32-byte key used to encrypt server_settings.json (AES-GCM). */
const val SETTINGS_ENC_KEY_HEX = "SETTINGS_ENC_KEY_HEX"

/** Absolute path to the settings file. Defaults to `~/server_settings.json`. */
const val HOMEBOX_SETTINGS_FILE = "HOMEBOX_SETTINGS_FILE"

/** Base URL of the local qBittorrent WebUI. Defaults to `http://127.0.0.1:8080`. */
const val HOMEBOX_QBITTORRENT_URL = "HOMEBOX_QBITTORRENT_URL"

/** TCP port the backend listens on. Defaults to 8085. */
const val HOMEBOX_PORT = "HOMEBOX_PORT"

/** Bind address for the backend. Defaults to `127.0.0.1` in prod, `0.0.0.0` in dev. */
const val HOMEBOX_HOST = "HOMEBOX_HOST"