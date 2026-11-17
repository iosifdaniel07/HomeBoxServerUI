# HomeBoxServerUi

Kotlin Multiplatform application exposing a Ktor backend + Compose Multiplatform
web UI (WASM/JS) for managing torrent downloads on a home server. Fronted by
Caddy for HTTPS, talks to a local qBittorrent instance over loopback.

```
   Internet ──HTTPS──▶ Caddy ──┬─▶ /api/*  →  Ktor (127.0.0.1:8085)
   (80/443)                    │                     │
                               │                     └─▶ qBittorrent (127.0.0.1:8080)
                               └─▶ /*      →  Static UI (127.0.0.1:8086)
```

---

## 1. Quick deploy (recommended)

The installer does everything: Java, qBittorrent, Caddy, UFW, systemd units,
secrets, admin password. It is idempotent — safe to re-run for updates.

### Step 1 — Build on your dev machine

```bash
# Requires JDK 17 (not 21/25 — Gradle 8.14 limitation)

# Backend JAR
JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ./gradlew :server:shadowJar

# Web UI (WASM/JS static files)
JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ./gradlew :composeApp:wasmJsBrowserDistribution
```

Produces:
- `server/build/libs/server-all.jar` (~21 MB, all dependencies bundled)
- `composeApp/build/dist/wasmJs/productionExecutable/` (static web UI files)

### Step 2 — Copy to the server

```bash
# Adjust user/IP/port to match your server
SERVER=homebox@192.168.1.129
SSH_PORT=2222

scp -P $SSH_PORT server/build/libs/server-all.jar $SERVER:/tmp/
scp -P $SSH_PORT server/install.sh $SERVER:/tmp/
ssh -p $SSH_PORT $SERVER "mkdir -p /tmp/web-ui"
scp -P $SSH_PORT -r composeApp/build/dist/wasmJs/productionExecutable/. $SERVER:/tmp/web-ui/
```

### Step 3 — Move web UI into place and run the installer (first time only)

```bash
ssh -t -p $SSH_PORT $SERVER "sudo mkdir -p /opt/homebox/web && sudo cp -r /tmp/web-ui/. /opt/homebox/web/"
```

```bash
ssh -p $SSH_PORT $SERVER
sudo bash /tmp/install.sh install
```

### Step 4 — Update (without full reinstall)

**On your dev machine** — build:

```bash
JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ./gradlew :server:shadowJar
JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ./gradlew :composeApp:wasmJsBrowserDistribution
```

**On your dev machine** — copy to server (run each line separately):

```bash
SERVER=homebox@192.168.1.129
SSH_PORT=2222

scp -P $SSH_PORT server/build/libs/server-all.jar $SERVER:/tmp/
ssh -p $SSH_PORT $SERVER "mkdir -p /tmp/web-ui"
scp -P $SSH_PORT -r composeApp/build/dist/wasmJs/productionExecutable/. $SERVER:/tmp/web-ui/
```

**On the server** — deploy and restart (run each line separately):

```bash
sudo systemctl stop homebox
sudo cp /tmp/server-all.jar /opt/homebox/server.jar
sudo cp -r /tmp/web-ui/. /opt/homebox/web/
sudo systemctl start homebox
sudo systemctl status homebox --no-pager
```

The wizard asks you, in order:

1. Install qBittorrent-nox (Y/n). If yes, generates a random WebUI password.
2. Install Caddy (Y/n). If yes, asks for your domain + Let's Encrypt email.
3. Reset firewall (Y/n). If yes, asks which port your SSH runs on.
4. Admin password (one-shot interactive prompt).

At the end the installer prints a credentials summary and saves it to
`/root/homebox-credentials.txt` (chmod 600). Copy them into the HomeBox UI
under *Settings*, then shred the file:

```bash
sudo shred -u /root/homebox-credentials.txt
```

### Updating

Re-run the installer with a new tarball — secrets, admin password and
qBittorrent config are preserved. Only the JAR is replaced and the service
restarted.

---

## 2. Installer sub-commands

```bash
sudo /opt/server-shadow-*/bin/install.sh status       # systemd + ports + UFW
sudo /opt/server-shadow-*/bin/install.sh logs         # audit + journalctl
sudo /opt/server-shadow-*/bin/install.sh credentials  # print saved creds
sudo /opt/server-shadow-*/bin/install.sh regen-keys   # DESTRUCTIVE: new keys
```

`regen-keys` invalidates every session cookie and makes every stored password
unreadable. Use only after a confirmed compromise.

---

## 3. What the installer puts on disk

| Path | Purpose | Permissions |
|------|---------|-------------|
| `/opt/homebox/server.jar` | application fat JAR | `0640 root:homebox` |
| `/etc/homebox/env` | operator secrets (session + settings keys) | `0640 root:homebox` |
| `/var/lib/homebox/` | app home + systemd WorkingDirectory | `0750 homebox:homebox` |
| `/var/lib/homebox/.config/myserver/env` | admin Argon2 hash (JAR-written) | `0640 homebox:homebox` |
| `/var/lib/homebox/server_settings.json` | encrypted qBit/FileList credentials | `0600 homebox:homebox` |
| `/srv/homebox/downloads/` | FileManager base dir (downloads live here) | `0750 homebox:homebox` |
| `/var/log/homebox/audit.log` | JSON-lines audit log (login, delete, settings change) | `0600 homebox:homebox` |
| `/etc/systemd/system/homebox.service` | hardened systemd unit | `0644 root:root` |
| `/etc/systemd/system/qbittorrent-nox.service` | loopback-only qBittorrent | `0644 root:root` |
| `/etc/caddy/Caddyfile` | HSTS/CSP/body-limit reverse proxy | `0644 root:root` |

---

## 4. Environment variables

Set in `/etc/homebox/env` (operator) and
`/var/lib/homebox/.config/myserver/env` (admin password, written by the JAR
on first run). systemd loads both files.

### Required

| Var | Meaning |
|-----|---------|
| `SESSION_ENCRYPT_KEY_HEX` | 32 hex chars = 16 bytes, AES-128 cookie encryption (Ktor requires exactly 16). |
| `SESSION_SIGN_KEY_HEX` | ≥ 64 hex chars = ≥ 32 bytes, HMAC-SHA256 cookie signature. |
| `SETTINGS_ENC_KEY_HEX` | 64 hex chars = 32 bytes, AES-256-GCM for server_settings.json. |
| `ADMIN_USERNAME` | web UI login name (default `admin`). |
| `ADMIN_PASSWORD_ARGON2ID` | Argon2id hash generated interactively on first run. |

### Optional (with safe defaults)

| Var | Default | Notes |
|-----|---------|-------|
| `APP_ENV` | `dev` | Must be `prod` for Secure cookies + SameSite=Strict + CORS lockdown. Installer sets this. |
| `HOMEBOX_HOST` | `127.0.0.1` (prod) / `0.0.0.0` (dev) | Bind address. |
| `HOMEBOX_PORT` | `8085` | Backend port. |
| `HOMEBOX_DOWNLOAD_FOLDER` | `$HOME/Downloads` | FileManager base dir. **Never client-controllable.** |
| `HOMEBOX_SETTINGS_FILE` | `$HOME/server_settings.json` | Encrypted settings location. |
| `HOMEBOX_QBITTORRENT_URL` | `http://127.0.0.1:8080` | qBittorrent WebUI endpoint. |
| `HOMEBOX_AUDIT_LOG` | `$HOME/homebox_audit.log` | Append-only JSON audit log. |

---

## 5. Development

### Toolchain

- JDK 17 (`/usr/lib/jvm/java-17-openjdk-amd64` on Ubuntu). The repo does **not**
  compile on JDK 25 due to Gradle 8.14 limitations.
- Gradle wrapper included, no system Gradle needed.

### Build everything

```bash
JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ./gradlew build
```

### Run the backend locally (dev mode)

```bash
export SESSION_ENCRYPT_KEY_HEX=$(openssl rand -hex 16)
export SESSION_SIGN_KEY_HEX=$(openssl rand -hex 32)
export SETTINGS_ENC_KEY_HEX=$(openssl rand -hex 32)
export ADMIN_USERNAME=admin
export APP_ENV=dev
export HOMEBOX_DOWNLOAD_FOLDER=$PWD/.dev-downloads
mkdir -p $PWD/.dev-downloads
./gradlew :server:run
# First run: it will prompt you interactively for an admin password.
```

### Run the web UI locally

```bash
./gradlew :composeApp:wasmJsBrowserDevelopmentRun
# or
./gradlew :composeApp:jsBrowserDevelopmentRun
```

### Run the tests

```bash
./gradlew :server:test
```

27 integration tests cover: auth model, CSRF, request validation, path
traversal defence, settings encryption round-trip + IV randomness, TOTP
unit tests (secret generation, URI format, code validation), 2FA endpoint
auth guards, and settings totpSecret round-trip.

---

## 6. Operations

### Service status

```bash
sudo systemctl status homebox --no-pager
sudo systemctl status qbittorrent-nox --no-pager
sudo systemctl status caddy --no-pager
```

### Logs

```bash
# Audit log (login/logout, deleteFile, deleteTorrent, saveServerSettings)
sudo tail -f /var/log/homebox/audit.log

# App logs
sudo journalctl -u homebox.service -f

# Caddy access log
sudo tail -f /var/log/caddy/access.log
```

### Secret rotation

- **Session keys** (every 90 days or on suspicion): re-run installer with
  `regen-keys`. All users must log in again.
- **Admin password**: delete `/var/lib/homebox/.config/myserver/env`, restart
  the service — it will prompt for a new one on next startup. Must restart
  with a TTY attached once.
- **qBittorrent WebUI password**: edit `/var/lib/qbittorrent/.config/qBittorrent/qBittorrent.conf`,
  update the `WebUI\Password_PBKDF2` entry, then update the saved password
  in the HomeBox UI under *Settings*.

### Backup

Back these up before updating:
- `/etc/homebox/env` — operator secrets
- `/var/lib/homebox/.config/myserver/env` — admin password hash
- `/var/lib/homebox/server_settings.json` — encrypted settings

```bash
sudo tar --preserve-permissions -czf homebox-backup-$(date +%F).tar.gz \
    /etc/homebox/env \
    /var/lib/homebox/.config/myserver/env \
    /var/lib/homebox/server_settings.json
```

---

## 7. Security model

### Authentication

- Session cookies, HttpOnly + Secure (prod) + SameSite=Strict (prod).
- Argon2id for the admin password (m=65536 KiB, t=3, p=1).
- Login is rate-limited to 5 attempts per minute per IP, plus a 500ms
  artificial delay on failure. `verifyAdminCredentials` runs Argon2 even
  for unknown usernames to prevent timing enumeration.

### CSRF

- Double-submit token model. Login response carries `csrfToken`; UI sends
  it back via `X-CSRF-Token` on every POST/PUT/DELETE. Compared constant-time.

### Authorization

- All endpoints except `/api/get` and `/api/login` sit inside
  `authenticate(ADMIN_SESSION) { ... }`. There is exactly one admin account.

### Transport

- CORS locked to the configured prod host; in dev, only localhost origins.
- Request body size capped at 2 MB app-side (Caddy also enforces 2 MB).
- `StatusPages` returns `{error, errorId}` — stack traces never reach the
  client; the full error is logged under `errorId` server-side.
- `CancellationException` is re-raised so coroutine cancellation is not
  swallowed by the 500 handler.

### File system

- `FileManager.configureExistingBase` uses `toRealPath()` at boot to resolve
  symlinks once; every later path is compared against the canonical base.
- Path validation rejects: NUL + all control chars (0x00..0x1F, 0x7F),
  parent traversal, absolute paths, segments > 255 chars, total path > 4096.
- `deleteFile` refuses to delete the base dir itself and filters every
  entry walked to `startsWith(base)`.
- List recursion is capped at depth 10.
- `DirListing` strips the absolute path — clients only see paths relative
  to the base.

### Secrets at rest

- `server_settings.json` is AES-256-GCM, random 12-byte IV per encryption,
  format: `gcm1$base64(iv||ciphertext||tag)`. Key is `SETTINGS_ENC_KEY_HEX`,
  never derived from anything client-visible.
- Legacy ECB-formatted values are discarded on read (installer-written
  configs are always GCM).
- Settings file is chmod 0600 after every write.

### Audit

Every security-relevant action writes a JSON line to `/var/log/homebox/audit.log`:
`login` (ok/fail), `logout`, `logout-all`, `saveServerSettings`, `deleteFile`,
`downloadFile`, `deleteTorrent`. The log file is chmod 0600 and append-only
in practice (file-system `chattr +a` is left to the operator).

### Network

- Backend bound to `127.0.0.1:8085` in prod — Caddy fronts it.
- qBittorrent bound to `127.0.0.1:8080` — also fronted by HomeBox calls.
- UFW default-deny with only 80/443/SSH open from the public internet.
- Plex and qBittorrent WebUI must **never** be port-forwarded on the router.

---

## 8. Threat model: what is NOT in scope

- **Compromise of the admin account** — treat the admin password as the keys
  to the kingdom. If it leaks, rotate via the Admin password section above
  and run `regen-keys`.
- **Host-level compromise** — if someone has shell on the machine as root or
  as the `homebox` user, they can read the session key and decrypt everything.
  The sandbox limits blast radius but doesn't make the box magic-safe.
- **Supply-chain attacks on dependencies** — Jsoup, Ktor, Argon2-jvm, logback
  are all declared in `libs.versions.toml`. Run `./gradlew dependencyUpdates`
  monthly and watch CVE feeds.

---

## 9. Troubleshooting

### Installer fails: `openssl kdf` not found

Older OpenSSL (< 3.0) doesn't ship `kdf` command. The installer falls back
to Python 3 for PBKDF2; make sure `python3` is installed:

```bash
sudo apt install python3
```

### Backend refuses to start: `$SESSION_ENCRYPT_KEY_HEX must decode to exactly 16 bytes`

The app enforces exact key lengths. If you rotated by hand, regenerate with:

```bash
openssl rand -hex 16   # SESSION_ENCRYPT_KEY_HEX
openssl rand -hex 32   # SESSION_SIGN_KEY_HEX
openssl rand -hex 32   # SETTINGS_ENC_KEY_HEX
```

### Logging in from browser: `403 csrf`

The UI didn't receive or lost the CSRF token. Hard-refresh the login page.
If using a non-UI client, call `/api/me` after login to obtain a fresh token
and echo it back via `X-CSRF-Token` on every POST.

### `settings value in legacy format detected; discarding`

You restored a `server_settings.json` written by an older ECB build. The
server now ignores it and treats the fields as empty. Re-enter the FileList
and qBittorrent credentials in the UI Settings page.

### qBittorrent WebUI unreachable from the LAN

By design. The installer binds qBittorrent to 127.0.0.1 only. If you need
local access, add a UFW rule limited to your LAN:

```bash
sudo ufw allow from 192.168.1.0/24 to any port 8080 proto tcp
```

and change `WebUI\Address` in `qBittorrent.conf` to `0.0.0.0`.

### `nmap -p- IP` from the internet shows more than 80/443/SSH

Check two places:
- `sudo ufw status verbose` — expected rules only.
- Your home router — remove any NAT/port-forward rules that send traffic
  to 8080, 8085, 8086, 32400 (Plex) or any UPnP-added rule.

---

## 10. Known not-done

- GitHub Actions / CI with Snyk + gitleaks scanning (plugin is wired but no
  pipeline file exists).
- Durable session revocation list (the in-memory set is cleared on restart).
- fail2ban integration — Caddy's JSON log is easy to tail if you want to
  add a jail yourself.
- Automatic Plex update management. This app does not touch Plex.

---

## License

Not set. Add one if you plan to publish.

## Manual steps(dont delete)
builds:
cd "/home/iosifdaniel07/Personal P/HomeBoxServerUI/HomeBoxServerUi"

JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ./gradlew :server:shadowJar

JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ./gradlew :composeApp:wasmJsBrowserDistribution

Asta e tot. Producele sunt în:
- server/build/libs/server-all.jar
- composeApp/build/dist/wasmJs/productionExecutable/

on pc:
scp -P 2222 "/home/iosifdaniel07/Personal P/HomeBoxServerUI/HomeBoxServerUi/server/build/libs/server-all.jar" homebox@homestreambox.go.ro:/tmp/
ssh -p 2222 homebox@homestreambox.go.ro "mkdir -p /tmp/web-ui"
scp -P 2222 -r "/home/iosifdaniel07/Personal P/HomeBoxServerUI/HomeBoxServerUi/composeApp/build/dist/wasmJs/productionExecutable/." homebox@homestreambox.go.ro:/tmp/web-ui/ 

on server:
sudo cp /tmp/server-all.jar /opt/homebox/server.jar
sudo chown root:media /opt/homebox/server.jar
sudo chmod 0640 /opt/homebox/server.jar
sudo cp -r /tmp/web-ui/. /opt/homebox/web/
sudo systemctl start homebox
sudo systemctl status homebox --no-pager      

check state:
sudo journalctl -u homebox.service -f --no-pager