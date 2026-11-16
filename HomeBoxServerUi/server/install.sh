#!/usr/bin/env bash
#
# HomeBox interactive installer / updater.
#
# Walks you through every dependency HomeBox needs: Java, qBittorrent,
# Caddy, UFW, systemd service, admin password. Idempotent — safe to re-run.
#
# Usage:
#   sudo ./bin/install.sh                 # interactive wizard
#   sudo ./bin/install.sh status          # show service state
#   sudo ./bin/install.sh logs            # tail logs
#   sudo ./bin/install.sh credentials     # print the saved credentials summary
#   sudo ./bin/install.sh regen-keys      # DESTRUCTIVE: new session + settings keys

set -euo pipefail

# ---------------------------------------------------------------------------
# Paths. Everything outside the extracted payload so re-running from a fresh
# tar never clobbers state.
# ---------------------------------------------------------------------------
APP_USER="homebox"
APP_GROUP="homebox"
APP_HOME="/var/lib/homebox"
APP_OPT="/opt/homebox"
APP_JAR="${APP_OPT}/server.jar"
APP_DOWNLOADS="/srv/homebox/downloads"

ETC_DIR="/etc/homebox"
ENV_FILE="${ETC_DIR}/env"                            # operator secrets
ADMIN_ENV_FILE="${APP_HOME}/.config/myserver/env"    # admin password hash
AUDIT_LOG="/var/log/homebox/audit.log"
APP_LOG_DIR="/var/log/homebox"

SYSTEMD_UNIT="/etc/systemd/system/homebox.service"
CADDYFILE="/etc/caddy/Caddyfile"

QBT_USER="qbittorrent"
QBT_HOME="/var/lib/qbittorrent"
QBT_DOWNLOADS="${APP_DOWNLOADS}"
QBT_SERVICE="/etc/systemd/system/qbittorrent-nox.service"
QBT_CONF_DIR="${QBT_HOME}/.config/qBittorrent"
QBT_CONF="${QBT_CONF_DIR}/qBittorrent.conf"

CREDENTIALS_SUMMARY="/root/homebox-credentials.txt"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PAYLOAD_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"

# ---------------------------------------------------------------------------
# UI helpers
# ---------------------------------------------------------------------------
if [[ -t 1 ]]; then
    C_RED='\033[1;31m' ; C_GREEN='\033[1;32m' ; C_YELLOW='\033[1;33m'
    C_BLUE='\033[1;34m'; C_CYAN='\033[1;36m'   ; C_BOLD='\033[1m'      ; C_RESET='\033[0m'
else
    C_RED='' ; C_GREEN='' ; C_YELLOW='' ; C_BLUE='' ; C_CYAN='' ; C_BOLD='' ; C_RESET=''
fi

banner() {
    echo
    printf "${C_CYAN}${C_BOLD}%s${C_RESET}\n" "═══════════════════════════════════════════════════════════════"
    printf "${C_CYAN}${C_BOLD}  %s${C_RESET}\n" "$*"
    printf "${C_CYAN}${C_BOLD}%s${C_RESET}\n" "═══════════════════════════════════════════════════════════════"
}
step()  { printf "\n${C_BLUE}${C_BOLD}▶ %s${C_RESET}\n" "$*"; }
info()  { printf "  ${C_CYAN}•${C_RESET} %s\n" "$*"; }
ok()    { printf "  ${C_GREEN}✓${C_RESET} %s\n" "$*"; }
warn()  { printf "  ${C_YELLOW}!${C_RESET} %s\n" "$*" 1>&2; }
die()   { printf "\n  ${C_RED}✗ %s${C_RESET}\n\n" "$*" 1>&2; exit 1; }

# ask_yes_no PROMPT [DEFAULT=y]
ask_yes_no() {
    local prompt="$1" default="${2:-y}" reply
    local hint
    if [[ "$default" == "y" ]]; then hint="[Y/n]"; else hint="[y/N]"; fi
    while true; do
        read -r -p "  $(printf "${C_BOLD}?${C_RESET}") $prompt $hint " reply || reply=""
        reply="${reply:-$default}"
        case "$reply" in
            [Yy]|[Yy][Ee][Ss]) return 0 ;;
            [Nn]|[Nn][Oo])     return 1 ;;
            *) echo "    please answer y or n" ;;
        esac
    done
}

# ask VAR_NAME PROMPT [DEFAULT]
ask() {
    local var="$1" prompt="$2" default="${3:-}" reply
    if [[ -n "$default" ]]; then
        read -r -p "  $(printf "${C_BOLD}?${C_RESET}") $prompt [${default}]: " reply || reply=""
        reply="${reply:-$default}"
    else
        while [[ -z "${reply:-}" ]]; do
            read -r -p "  $(printf "${C_BOLD}?${C_RESET}") $prompt: " reply || reply=""
            [[ -z "$reply" ]] && echo "    required."
        done
    fi
    printf -v "$var" '%s' "$reply"
}

# ask_secret VAR_NAME PROMPT  (hides input)
ask_secret() {
    local var="$1" prompt="$2" reply reply2
    while true; do
        read -r -s -p "  $(printf "${C_BOLD}?${C_RESET}") $prompt: " reply; echo
        [[ ${#reply} -lt 8 ]] && { echo "    password must be at least 8 chars"; continue; }
        read -r -s -p "  $(printf "${C_BOLD}?${C_RESET}") repeat: " reply2; echo
        [[ "$reply" == "$reply2" ]] && break
        echo "    passwords did not match — try again"
    done
    printf -v "$var" '%s' "$reply"
}

need_root() {
    [[ "${EUID:-$(id -u)}" -eq 0 ]] || die "run as root: sudo $0 $*"
}

detect_pkg_mgr() {
    if command -v apt >/dev/null 2>&1; then echo apt
    elif command -v dnf >/dev/null 2>&1; then echo dnf
    else die "no supported package manager (apt/dnf) found"
    fi
}

random_hex()  { openssl rand -hex "$1"; }
random_pass() { openssl rand -base64 18 | tr -d '/+=' | head -c 20; }

# ---------------------------------------------------------------------------
# qBittorrent WebUI password hashing.
#
# qBittorrent stores WebUI\Password_PBKDF2 as:
#   @ByteArray(<base64_salt>:<base64_hash>)
# where hash = PBKDF2-HMAC-SHA512(password, salt=16 bytes random, iters=100000, dkLen=64 bytes).
# ---------------------------------------------------------------------------
qbt_password_hash() {
    local password="$1" salt_hex salt_b64 hash_hex hash_b64
    salt_hex="$(openssl rand -hex 16)"
    salt_b64="$(printf '%s' "$salt_hex" | xxd -r -p | base64 -w0)"
    # pbkdf2-hmac-sha512 via openssl kdf
    hash_hex="$(printf '%s' "$password" | openssl kdf \
        -keylen 64 \
        -kdfopt digest:SHA512 \
        -kdfopt pass:"$password" \
        -kdfopt hexsalt:"$salt_hex" \
        -kdfopt iter:100000 \
        PBKDF2 2>/dev/null | tr -d ':' | tr -d '[:space:]')"
    if [[ -z "$hash_hex" ]]; then
        # Fallback via python3 (ubiquitous on servers).
        if command -v python3 >/dev/null 2>&1; then
            local salt_py; salt_py="$(xxd -r -p <<<"$salt_hex" | base64 -w0)"
            hash_b64="$(python3 -c "
import hashlib, base64, sys
pw=sys.argv[1].encode(); salt=base64.b64decode(sys.argv[2])
dk=hashlib.pbkdf2_hmac('sha512', pw, salt, 100000, 64)
print(base64.b64encode(dk).decode())
" "$password" "$salt_py")"
            salt_b64="$salt_py"
            printf '@ByteArray(%s:%s)' "$salt_b64" "$hash_b64"
            return 0
        fi
        die "neither OpenSSL kdf nor python3 can derive PBKDF2 — install one"
    fi
    hash_b64="$(printf '%s' "$hash_hex" | xxd -r -p | base64 -w0)"
    printf '@ByteArray(%s:%s)' "$salt_b64" "$hash_b64"
}

record_credential() {
    # record_credential LABEL VALUE
    umask 077
    [[ -f "$CREDENTIALS_SUMMARY" ]] || cat > "$CREDENTIALS_SUMMARY" <<EOF
# HomeBox initial credentials — generated $(date -Iseconds)
# IMPORTANT: this file lives at $CREDENTIALS_SUMMARY, chmod 600, root only.
#            Copy these into the HomeBox UI under Settings, then delete the
#            file (shred -u) once you've saved them in a password manager.

EOF
    printf '%-24s %s\n' "$1" "$2" >> "$CREDENTIALS_SUMMARY"
    chmod 0600 "$CREDENTIALS_SUMMARY"
}

# ---------------------------------------------------------------------------
# Pre-flight
# ---------------------------------------------------------------------------
preflight() {
    banner "HomeBox interactive installer"
    info "This wizard will walk you through installing HomeBox and its"
    info "dependencies. You can safely re-run it; already-installed pieces"
    info "are detected and skipped. Secrets are generated only once."
    echo
    info "Target layout:"
    info "   user           ${APP_USER}"
    info "   app home       ${APP_HOME}"
    info "   jar            ${APP_JAR}"
    info "   downloads      ${APP_DOWNLOADS}"
    info "   secrets        ${ENV_FILE}"
    info "   audit log      ${AUDIT_LOG}"
    echo

    command -v systemctl >/dev/null 2>&1 || die "systemd is required"
    command -v openssl   >/dev/null 2>&1 || die "openssl is required"
    command -v curl      >/dev/null 2>&1 || info "note: curl will be installed if missing"

    ask_yes_no "Continue?" y || die "aborted by user"
}

# ---------------------------------------------------------------------------
# Steps
# ---------------------------------------------------------------------------

install_java() {
    step "Java 17 runtime"
    if command -v java >/dev/null 2>&1; then
        local ver
        ver="$(java -version 2>&1 | awk -F\" '/version/ { print $2 }')"
        case "$ver" in
            17.*|21.*|25.*)
                ok "Java already installed: $ver"
                return
                ;;
        esac
    fi
    info "installing openjdk-17-jre-headless"
    case "$(detect_pkg_mgr)" in
        apt)
            export DEBIAN_FRONTEND=noninteractive
            apt-get update -y >/dev/null
            apt-get install -y openjdk-17-jre-headless curl openssl ca-certificates >/dev/null
            ;;
        dnf)
            dnf install -y java-17-openjdk-headless curl openssl ca-certificates >/dev/null
            ;;
    esac
    ok "Java installed ($(java -version 2>&1 | head -n1))"
}

create_user_and_dirs() {
    step "System user and directories"
    if ! getent passwd "$APP_USER" >/dev/null; then
        useradd --system --home-dir "$APP_HOME" --shell /usr/sbin/nologin \
                --create-home "$APP_USER"
        ok "created system user '$APP_USER' (home=$APP_HOME)"
    else
        ok "user '$APP_USER' already exists"
    fi

    install -d -o root     -g root       -m 0750 "$ETC_DIR"
    install -d -o "$APP_USER" -g "$APP_GROUP" -m 0750 "$APP_HOME"
    install -d -o "$APP_USER" -g "$APP_GROUP" -m 0750 "$APP_HOME/.config/myserver"
    install -d -o "$APP_USER" -g "$APP_GROUP" -m 0750 "$APP_DOWNLOADS"
    install -d -o root     -g root       -m 0755 "$APP_OPT"
    install -d -o "$APP_USER" -g "$APP_GROUP" -m 0750 "$APP_LOG_DIR"
    ok "directories ready"
}

ensure_env_file() {
    step "HomeBox secrets"
    if [[ -f "$ENV_FILE" ]]; then
        ok "existing secrets kept at $ENV_FILE (nothing rotated)"
        return
    fi
    info "generating session + settings keys"
    local session_enc session_sign settings_enc
    session_enc="$(random_hex 16)"
    session_sign="$(random_hex 32)"
    settings_enc="$(random_hex 32)"

    umask 077
    cat > "$ENV_FILE" <<EOF
# HomeBox operator secrets. Generated $(date -Iseconds).
# NEVER commit this file. Keys live for the life of the install unless
# rotated with: $0 regen-keys  (which breaks all sessions + stored creds).

SESSION_ENCRYPT_KEY_HEX=${session_enc}
SESSION_SIGN_KEY_HEX=${session_sign}
SETTINGS_ENC_KEY_HEX=${settings_enc}

ADMIN_USERNAME=admin

HOMEBOX_DOWNLOAD_FOLDER=${APP_DOWNLOADS}
HOMEBOX_SETTINGS_FILE=${APP_HOME}/server_settings.json
HOMEBOX_AUDIT_LOG=${AUDIT_LOG}

HOMEBOX_HOST=127.0.0.1
HOMEBOX_PORT=8085

HOMEBOX_QBITTORRENT_URL=http://127.0.0.1:8080

APP_ENV=prod
EOF
    chown root:"$APP_GROUP" "$ENV_FILE"
    chmod 0640 "$ENV_FILE"
    ok "wrote $ENV_FILE (0640 root:$APP_GROUP)"
}

deploy_jar() {
    step "Deploy fat JAR"
    local src
    src="$(find "$PAYLOAD_ROOT/lib" -maxdepth 1 \( -name '*-all.jar' -o -name 'server-*.jar' \) | head -n1 || true)"
    [[ -n "$src" ]] || die "no server JAR found under $PAYLOAD_ROOT/lib"
    install -o root -g "$APP_GROUP" -m 0640 "$src" "$APP_JAR"
    ok "$(basename "$src") → $APP_JAR"
}

install_systemd_unit() {
    step "systemd unit"
    cat > "$SYSTEMD_UNIT" <<EOF
[Unit]
Description=HomeBox Server (Ktor backend)
After=network-online.target
Wants=network-online.target

[Service]
Type=simple
User=${APP_USER}
Group=${APP_GROUP}
WorkingDirectory=${APP_HOME}

EnvironmentFile=${ENV_FILE}
EnvironmentFile=-${ADMIN_ENV_FILE}

ExecStart=/usr/bin/java -jar ${APP_JAR}

# --- Sandbox ---
NoNewPrivileges=true
PrivateTmp=true
PrivateDevices=true
ProtectSystem=strict
ProtectHome=true
ProtectKernelTunables=true
ProtectKernelModules=true
ProtectControlGroups=true
ProtectClock=true
ProtectHostname=true
LockPersonality=true
MemoryDenyWriteExecute=true
RestrictRealtime=true
RestrictSUIDSGID=true
RestrictNamespaces=true
SystemCallArchitectures=native
SystemCallFilter=@system-service
SystemCallFilter=~@privileged @resources
ReadWritePaths=${APP_HOME} ${APP_DOWNLOADS} ${APP_LOG_DIR}
ReadOnlyPaths=${ETC_DIR}
RestrictAddressFamilies=AF_INET AF_INET6 AF_UNIX
LimitNOFILE=4096
MemoryMax=512M
TasksMax=200

Restart=on-failure
RestartSec=5

[Install]
WantedBy=multi-user.target
EOF
    chmod 0644 "$SYSTEMD_UNIT"
    systemctl daemon-reload
    ok "wrote $SYSTEMD_UNIT"
}

ensure_admin_password() {
    step "HomeBox admin account"
    if [[ -f "$ADMIN_ENV_FILE" ]] && grep -q '^ADMIN_PASSWORD_ARGON2ID=' "$ADMIN_ENV_FILE"; then
        ok "admin password already set (hash present)"
        return
    fi
    info "launching one-shot interactive password setup — the JAR exits after saving"
    echo

    set -a
    # shellcheck disable=SC1090
    source "$ENV_FILE"
    set +a

    sudo -u "$APP_USER" --preserve-env \
        env HOME="$APP_HOME" \
        /usr/bin/java -jar "$APP_JAR" || true

    if [[ -f "$ADMIN_ENV_FILE" ]] && grep -q '^ADMIN_PASSWORD_ARGON2ID=' "$ADMIN_ENV_FILE"; then
        chmod 0640 "$ADMIN_ENV_FILE"
        chown "$APP_USER":"$APP_GROUP" "$ADMIN_ENV_FILE"
        ok "admin password saved to $ADMIN_ENV_FILE"
        record_credential "HomeBox admin user" "admin"
        record_credential "HomeBox admin pass" "(the one you just typed)"
    else
        die "admin password setup failed — the JAR did not write $ADMIN_ENV_FILE"
    fi
}

# ---------------------------------------------------------------------------
# qBittorrent
# ---------------------------------------------------------------------------
install_qbittorrent() {
    step "qBittorrent WebUI"
    if ! ask_yes_no "Install and configure qBittorrent-nox (needed for torrent downloads)?" y; then
        info "skipped — HomeBox will not be able to manage torrents"
        return
    fi

    if ! command -v qbittorrent-nox >/dev/null 2>&1; then
        info "installing qbittorrent-nox"
        case "$(detect_pkg_mgr)" in
            apt) DEBIAN_FRONTEND=noninteractive apt-get install -y qbittorrent-nox >/dev/null ;;
            dnf) dnf install -y qbittorrent-nox >/dev/null ;;
        esac
    else
        ok "qbittorrent-nox already installed"
    fi

    if ! getent passwd "$QBT_USER" >/dev/null; then
        useradd --system --home-dir "$QBT_HOME" --shell /usr/sbin/nologin --create-home "$QBT_USER"
        ok "created system user '$QBT_USER'"
    fi
    # Give qbittorrent write access to the shared downloads folder.
    usermod -a -G "$APP_GROUP" "$QBT_USER" || true
    install -d -o "$QBT_USER" -g "$QBT_USER" -m 0750 "$QBT_CONF_DIR"
    chown -R "$QBT_USER":"$QBT_USER" "$QBT_HOME"
    # The downloads folder is shared with HomeBox — let the group write.
    chmod g+w "$APP_DOWNLOADS"

    local qbt_pass
    if [[ -f "$CREDENTIALS_SUMMARY" ]] && grep -q "qBittorrent WebUI pass" "$CREDENTIALS_SUMMARY"; then
        info "qBittorrent WebUI password already recorded — leaving config untouched"
    else
        if ask_yes_no "Generate a random qBittorrent WebUI password? (recommended)" y; then
            qbt_pass="$(random_pass)"
            ok "generated qBittorrent password"
        else
            ask_secret qbt_pass "Enter qBittorrent WebUI password (min 8 chars)"
        fi
        write_qbittorrent_config "$qbt_pass"
        record_credential "qBittorrent WebUI user" "admin"
        record_credential "qBittorrent WebUI pass" "$qbt_pass"
    fi

    # systemd service — loopback only.
    cat > "$QBT_SERVICE" <<EOF
[Unit]
Description=qBittorrent WebUI (loopback only)
After=network-online.target
Wants=network-online.target

[Service]
Type=exec
User=${QBT_USER}
Group=${QBT_USER}
UMask=0007
ExecStart=/usr/bin/qbittorrent-nox --webui-port=8080
Restart=on-failure
RestartSec=5

NoNewPrivileges=true
PrivateTmp=true
ProtectSystem=strict
ProtectHome=true
ReadWritePaths=${QBT_HOME} ${APP_DOWNLOADS}

[Install]
WantedBy=multi-user.target
EOF
    chmod 0644 "$QBT_SERVICE"
    systemctl daemon-reload
    systemctl enable qbittorrent-nox.service >/dev/null
    systemctl restart qbittorrent-nox.service
    sleep 2
    if systemctl is-active --quiet qbittorrent-nox.service; then
        ok "qbittorrent-nox listening on 127.0.0.1:8080"
    else
        warn "qbittorrent-nox did not start — check: journalctl -u qbittorrent-nox"
    fi
}

write_qbittorrent_config() {
    local qbt_pass="$1"
    local hash; hash="$(qbt_password_hash "$qbt_pass")"
    cat > "$QBT_CONF" <<EOF
[LegalNotice]
Accepted=true

[Preferences]
WebUI\Address=127.0.0.1
WebUI\Port=8080
WebUI\LocalHostAuth=true
WebUI\CSRFProtection=true
WebUI\ClickjackingProtection=true
WebUI\HostHeaderValidation=true
WebUI\Username=admin
WebUI\Password_PBKDF2="${hash}"

Downloads\SavePath=${APP_DOWNLOADS}
Downloads\TempPathEnabled=false
EOF
    chown "$QBT_USER":"$QBT_USER" "$QBT_CONF"
    chmod 0600 "$QBT_CONF"
    ok "wrote $QBT_CONF (loopback bind, CSRF on, clickjacking on)"
}

# ---------------------------------------------------------------------------
# Storage — mount additional disks and pool them under APP_DOWNLOADS
# ---------------------------------------------------------------------------
install_storage() {
    step "Storage (additional disks)"
    if ! ask_yes_no "Mount additional disks into the downloads folder?" n; then
        info "using default: ${APP_DOWNLOADS}"
        return
    fi

    info "Available block devices:"
    lsblk -o NAME,SIZE,TYPE,FSTYPE,MOUNTPOINT 2>/dev/null | grep -v loop | sed 's/^/   /'
    echo

    local disks=()
    local idx=1
    while true; do
        local dev mp
        ask dev "Device path to add (e.g. /dev/sdb1) — leave empty to finish"
        [[ -z "$dev" ]] && break
        [[ -b "$dev" ]] || { warn "$dev is not a block device — skipping"; continue; }

        mp="/mnt/disk${idx}"
        mkdir -p "$mp"

        local uuid fstype
        uuid="$(blkid -s UUID -o value "$dev" 2>/dev/null || true)"
        fstype="$(blkid -s TYPE -o value "$dev" 2>/dev/null || echo ext4)"

        if [[ -z "$uuid" ]]; then
            warn "Cannot read UUID from $dev — skipping (is it formatted?)"
            continue
        fi

        if ! grep -q "UUID=$uuid" /etc/fstab; then
            printf 'UUID=%s\t%s\t%s\tdefaults,nofail\t0\t2\n' "$uuid" "$mp" "$fstype" >> /etc/fstab
            ok "added $dev (UUID=$uuid) → $mp in /etc/fstab"
        else
            ok "$dev already in /etc/fstab"
        fi
        mount "$mp" 2>/dev/null || mount -a 2>/dev/null || true
        disks+=("$mp")
        ((idx++))
    done

    if [[ ${#disks[@]} -eq 0 ]]; then
        info "no disks added"
        return
    fi

    # Install mergerfs to pool all disks into APP_DOWNLOADS.
    if ! command -v mergerfs >/dev/null 2>&1; then
        info "installing mergerfs"
        case "$(detect_pkg_mgr)" in
            apt) DEBIAN_FRONTEND=noninteractive apt-get install -y mergerfs >/dev/null ;;
            dnf) dnf install -y mergerfs >/dev/null ;;
        esac
    fi

    local branches
    branches="$(IFS=:; echo "${disks[*]}")"
    if ! grep -q "mergerfs" /etc/fstab; then
        printf '%s\t%s\tfuse.mergerfs\tdefaults,allow_other,use_ino,category.create=mfs,nofail\t0\t0\n' \
            "$branches" "$APP_DOWNLOADS" >> /etc/fstab
        ok "mergerfs pool: $branches → $APP_DOWNLOADS"
    fi
    mount -a 2>/dev/null || true
    chown "$APP_USER":"$APP_GROUP" "$APP_DOWNLOADS"
    chmod 0750 "$APP_DOWNLOADS"
    ok "downloads folder ready at $APP_DOWNLOADS"
}

# ---------------------------------------------------------------------------
# Plex Media Server
# ---------------------------------------------------------------------------
install_plex() {
    step "Plex Media Server"
    if ! ask_yes_no "Install Plex Media Server?" n; then
        info "skipped"
        return
    fi

    if systemctl is-active --quiet plexmediaserver 2>/dev/null; then
        ok "Plex already running — skipping install"
        return
    fi

    info "fetching latest Plex version from plex.tv..."
    local url
    url="$(curl -sL 'https://plex.tv/api/downloads/5.json' | grep -oE 'https://[^"]+amd64\.deb' | head -1 || true)"
    [[ -n "$url" ]] || die "could not determine Plex download URL — check internet connectivity"

    info "downloading Plex (~150 MB)..."
    wget -q "$url" -O /tmp/plexmediaserver.deb || die "failed to download Plex"

    info "installing Plex..."
    DEBIAN_FRONTEND=noninteractive dpkg -i /tmp/plexmediaserver.deb >/dev/null
    rm -f /tmp/plexmediaserver.deb

    # Allow Plex to read the downloads/media folder.
    usermod -a -G "$APP_GROUP" plex 2>/dev/null || true

    systemctl enable plexmediaserver >/dev/null
    systemctl start plexmediaserver
    sleep 3

    if systemctl is-active --quiet plexmediaserver; then
        ok "plexmediaserver running"
        warn "Plex WebUI (first-time setup): http://192.168.1.129:32400/web"
        warn "Do NOT port-forward 32400 — access Plex only from your LAN or via its relay."
        record_credential "Plex local WebUI" "http://192.168.1.129:32400/web"
    else
        warn "Plex did not start — check: journalctl -u plexmediaserver"
    fi
}

# ---------------------------------------------------------------------------
# Caddy + UFW
# ---------------------------------------------------------------------------
install_caddy() {
    step "Caddy (HTTPS reverse proxy)"
    if ! ask_yes_no "Install Caddy for automatic HTTPS?" y; then
        info "skipped — remember to put your own TLS terminator in front of :8085"
        return
    fi
    local domain email
    ask domain "Domain name (public DNS pointing to this server)"
    ask email  "Email for Let's Encrypt certificate" "homebox@${domain}"

    if ! command -v caddy >/dev/null 2>&1; then
        info "installing Caddy from the official repo"
        case "$(detect_pkg_mgr)" in
            apt)
                apt-get install -y debian-keyring debian-archive-keyring apt-transport-https curl gpg >/dev/null
                curl -1sLf 'https://dl.cloudsmith.io/public/caddy/stable/gpg.key' \
                    | gpg --dearmor -o /usr/share/keyrings/caddy-stable-archive-keyring.gpg
                curl -1sLf 'https://dl.cloudsmith.io/public/caddy/stable/debian.deb.txt' \
                    | tee /etc/apt/sources.list.d/caddy-stable.list >/dev/null
                apt-get update -y >/dev/null
                apt-get install -y caddy >/dev/null
                ;;
            dnf)
                dnf install -y 'dnf-command(config-manager)' >/dev/null
                dnf config-manager --add-repo https://dl.cloudsmith.io/public/caddy/stable/rpm.repo >/dev/null
                dnf install -y caddy >/dev/null
                ;;
        esac
    fi

    cat > "$CADDYFILE" <<EOF
# HomeBox — generated by install.sh on $(date -Iseconds).
${domain} {
    tls ${email}
    encode gzip zstd

    header {
        Strict-Transport-Security "max-age=63072000; includeSubDomains; preload"
        X-Content-Type-Options "nosniff"
        X-Frame-Options "DENY"
        Referrer-Policy "no-referrer"
        Permissions-Policy "camera=(), microphone=(), geolocation=()"
        Content-Security-Policy "default-src 'self'; script-src 'self' 'wasm-unsafe-eval'; style-src 'self' 'unsafe-inline'; img-src 'self' data:; connect-src 'self'; frame-ancestors 'none'; form-action 'self'; base-uri 'self'"
        -Server
    }

    request_body {
        max_size 2MB
    }

    handle /api/* {
        reverse_proxy 127.0.0.1:8085
    }
    handle {
        reverse_proxy 127.0.0.1:8086
    }

    log {
        output file /var/log/caddy/access.log {
            roll_size 100mb
            roll_keep 10
        }
        format json
    }
}
EOF
    chmod 0644 "$CADDYFILE"
    caddy validate --config "$CADDYFILE" >/dev/null || die "Caddyfile failed validation"
    systemctl enable caddy >/dev/null
    systemctl restart caddy
    ok "Caddy running at https://${domain}/"
    record_credential "Public URL" "https://${domain}/"
}

configure_ufw() {
    step "Firewall (UFW)"
    if ! ask_yes_no "Reset firewall to deny-all + allow 80/443/22 only?" y; then
        info "skipped — make sure 8080, 8085, 8086 are NOT reachable from the internet"
        return
    fi
    local ssh_port
    ask ssh_port "Which port is your SSH on?" "22"

    if ! command -v ufw >/dev/null 2>&1; then
        info "installing ufw"
        case "$(detect_pkg_mgr)" in
            apt) apt-get install -y ufw >/dev/null ;;
            dnf) dnf install -y ufw >/dev/null ;;
        esac
    fi

    ufw --force reset >/dev/null
    ufw default deny incoming >/dev/null
    ufw default allow outgoing >/dev/null
    ufw allow 80/tcp >/dev/null
    ufw allow 443/tcp >/dev/null
    ufw allow "${ssh_port}/tcp" >/dev/null
    ufw --force enable >/dev/null
    ok "UFW active: only 80/443/${ssh_port} reachable"
    ufw status verbose | sed 's/^/     /'
}

# ---------------------------------------------------------------------------
# Startup + wrap up
# ---------------------------------------------------------------------------
start_service() {
    step "Start HomeBox"
    systemctl enable homebox.service >/dev/null
    systemctl restart homebox.service
    sleep 2
    if ! systemctl is-active --quiet homebox.service; then
        warn "service is NOT active — last 30 lines of the journal:"
        journalctl -u homebox.service --no-pager -n 30 | sed 's/^/     /'
        die "service failed to start"
    fi
    ok "homebox.service is running"
}

print_summary() {
    banner "All done"
    echo
    if [[ -f "$CREDENTIALS_SUMMARY" ]]; then
        info "Credentials have been saved at ${CREDENTIALS_SUMMARY} (root-only)."
        info "Paste them into the HomeBox UI under Settings, then shred the file:"
        info "    shred -u $CREDENTIALS_SUMMARY"
        echo
        echo "----------------------------------------------------------------"
        cat "$CREDENTIALS_SUMMARY"
        echo "----------------------------------------------------------------"
    fi
    echo
    info "Service status: $0 status"
    info "Logs:           $0 logs"
    info "Rotate keys:    $0 regen-keys   (destructive)"
    echo
}

cmd_install_or_update() {
    need_root "$@"
    preflight
    install_java
    create_user_and_dirs
    install_storage
    ensure_env_file
    deploy_jar
    install_systemd_unit
    ensure_admin_password
    install_qbittorrent
    install_plex
    install_caddy
    configure_ufw
    start_service
    print_summary
}

cmd_status() {
    echo "--- homebox.service ---"
    systemctl status homebox.service --no-pager --lines=5 2>&1 || true
    echo
    echo "--- qbittorrent-nox.service ---"
    systemctl status qbittorrent-nox.service --no-pager --lines=5 2>&1 || true
    echo
    echo "--- caddy.service ---"
    systemctl status caddy.service --no-pager --lines=5 2>&1 || true
    echo
    echo "--- plexmediaserver.service ---"
    systemctl status plexmediaserver --no-pager --lines=3 2>&1 || true
    echo
    echo "--- listening sockets ---"
    ss -tlnp 2>/dev/null | grep -E ':(8085|8086|8080|443|80)\b' || echo "  (no matches)"
    echo
    if command -v ufw >/dev/null 2>&1; then
        echo "--- ufw ---"
        ufw status verbose | sed 's/^/    /'
    fi
}

cmd_logs() {
    echo "--- audit log (last 20 lines) ---"
    if [[ -f "$AUDIT_LOG" ]]; then tail -n 20 "$AUDIT_LOG"
    else echo "  (no audit log yet)"
    fi
    echo
    echo "--- homebox journal (last 30 lines) ---"
    journalctl -u homebox.service --no-pager -n 30
}

cmd_credentials() {
    [[ -f "$CREDENTIALS_SUMMARY" ]] || die "no credentials file at $CREDENTIALS_SUMMARY"
    cat "$CREDENTIALS_SUMMARY"
}

cmd_regen_keys() {
    need_root "$@"
    warn "This will invalidate ALL sessions and make every stored password unreadable."
    if ! ask_yes_no "Rotate keys now?" n; then die "aborted"; fi
    rm -f "$ENV_FILE"
    ensure_env_file
    systemctl restart homebox.service
    ok "keys rotated, service restarted"
}

# ---------------------------------------------------------------------------
# CLI dispatch
# ---------------------------------------------------------------------------
case "${1:-install}" in
    install)     cmd_install_or_update ;;
    status)      cmd_status ;;
    logs)        cmd_logs ;;
    credentials) cmd_credentials ;;
    regen-keys)  cmd_regen_keys ;;
    -h|--help)
        awk '/^set -euo/ {exit} /^#/ {sub(/^# ?/, ""); print}' "$0"
        ;;
    *) die "unknown command: $1 (try: install | status | logs | credentials | regen-keys)" ;;
esac
