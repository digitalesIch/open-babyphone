#!/usr/bin/env bash
set -Eeuo pipefail

DOMAIN="babyphone.duckdns.org"
APP_USER="babyphone-relay"
APP_DIR="/opt/open-babyphone-relay"
PORT="8338"
ACME_WEBROOT="/var/www/letsencrypt"
NGINX_SITE="/etc/nginx/sites-available/babyphone-relay"
ACME_SITE="/etc/nginx/sites-available/babyphone-acme"
SYSTEMD_UNIT="/etc/systemd/system/open-babyphone-relay.service"

die() { echo "ERROR: $*" >&2; exit 1; }
need_root() { [ "$EUID" -eq 0 ] || die "Run this script as root."; }
have_cmd() { command -v "$1" >/dev/null 2>&1; }

echo "=== Open Babyphone relay one-time VPS setup ==="
need_root

echo "[1/10] Verifying existing services..."
PORT80="$(ss -ltnp 2>/dev/null | grep -E ':(80)\b' || true)"
PORT443="$(ss -ltnp 2>/dev/null | grep -E ':(443)\b' || true)"
PORT8338="$(ss -ltnp 2>/dev/null | grep -E ':(8338)\b' || true)"
if [ -n "$PORT8338" ]; then
  if systemctl is-active --quiet open-babyphone-relay.service; then
    echo "Existing Babyphone relay owns port 8338; stopping only that service for an idempotent update."
    systemctl stop open-babyphone-relay.service
    PORT8338=""
  else
    die "Port 8338 is already in use by another process. Nothing changed."
  fi
fi
[ -z "$PORT80" ] || echo "$PORT80" | grep -q nginx || die "Port 80 is occupied by a non-nginx process. Nothing changed."
[ -z "$PORT443" ] || echo "$PORT443" | grep -q nginx || die "Port 443 is occupied by a non-nginx process. Nothing changed."
if ! have_cmd nginx || ! have_cmd certbot; then
  have_cmd apt-get || die "apt-get is required to install missing nginx/certbot."
  echo "Missing nginx/certbot detected; installing only the missing VPS packages..."
  export DEBIAN_FRONTEND=noninteractive
  apt-get update
  have_cmd nginx || apt-get install -y nginx
  have_cmd certbot || apt-get install -y certbot
fi
have_cmd nginx || die "nginx installation failed."
have_cmd certbot || die "certbot installation failed."

echo "[2/10] Selecting Node.js 20 for Babyphone only..."
NODE20_VERSION="20.20.2"
PROJECT_NODE_HOME="$APP_DIR/node-v$NODE20_VERSION"
PROJECT_NODE_BIN="$PROJECT_NODE_HOME/bin/node"
NODE20_RUNTIME="/usr/local/bin/open-babyphone-node20"

# Prefer the already-staged project runtime, then an existing nvm Node 20.
if [ -x "$NODE20_RUNTIME" ] && [ "$("$NODE20_RUNTIME" --version 2>/dev/null || true)" = "v$NODE20_VERSION" ]; then
  NODE20_BIN="$NODE20_RUNTIME"
  NODE20_HOME="$PROJECT_NODE_HOME"
  NODE20_DIR="$(dirname "$NODE20_RUNTIME")"
  echo "Using existing Babyphone-only Node.js runtime: $NODE20_RUNTIME"
elif [ -s "/root/.nvm/nvm.sh" ]; then
  # shellcheck disable=SC1091
  source "/root/.nvm/nvm.sh"
  NVM_NODE20_BIN="$(nvm which 20 2>/dev/null || true)"
  if [ -x "$NVM_NODE20_BIN" ] && [ "$("$NVM_NODE20_BIN" --version)" = "v$NODE20_VERSION" ]; then
    NODE20_BIN="$NVM_NODE20_BIN"
    NODE20_HOME="$(cd "$(dirname "$NODE20_BIN")/.." && pwd)"
    NODE20_DIR="$NODE20_HOME/bin"
    echo "Reusing existing nvm Node.js 20: $NODE20_BIN"
  fi
fi

# If neither exists, install Node 20.20.2 inside the Babyphone project only.
if [ -z "${NODE20_BIN:-}" ] || [ ! -x "$NODE20_BIN" ]; then
  have_cmd curl || die "curl is required to download project-local Node.js."
  have_cmd sha256sum || die "sha256sum is required to verify project-local Node.js."
  have_cmd tar || die "tar is required to unpack project-local Node.js."
  ARCH="$(uname -m)"
  case "$ARCH" in
    x86_64) NODE_ARCH="x64"; NODE_SHA="19e56f0825510207dd904f087fe52faa0a4eb6b2aab5f0ea7a33830d04888b8b" ;;
    aarch64|arm64) NODE_ARCH="arm64"; NODE_SHA="47ef73d543ecf6eb19435f6c03a0ac4809b3bf0dd6b26c7c571efc2a6572a74d" ;;
    *) die "Unsupported VPS architecture for project-local Node.js 20: $ARCH" ;;
  esac
  mkdir -p "$APP_DIR"
  NODE_TARBALL="/tmp/node-v$NODE20_VERSION-linux-$NODE_ARCH.tar.gz"
  NODE_URL="https://nodejs.org/dist/v$NODE20_VERSION/node-v$NODE20_VERSION-linux-$NODE_ARCH.tar.gz"
  if [ ! -x "$PROJECT_NODE_BIN" ]; then
    echo "Node.js 20.20.2 is not present; downloading it only for Babyphone..."
    curl -fL "$NODE_URL" -o "$NODE_TARBALL"
    printf '%s  %s\n' "$NODE_SHA" "$NODE_TARBALL" | sha256sum -c -
    rm -rf "$PROJECT_NODE_HOME"
    mkdir -p "$APP_DIR"
    tar -xzf "$NODE_TARBALL" -C "$APP_DIR"
    mv "$APP_DIR/node-v$NODE20_VERSION-linux-$NODE_ARCH" "$PROJECT_NODE_HOME"
    rm -f "$NODE_TARBALL"
  fi
  NODE20_BIN="$PROJECT_NODE_BIN"
  NODE20_HOME="$PROJECT_NODE_HOME"
  NODE20_DIR="$NODE20_HOME/bin"
  echo "Installed project-local Node.js: $NODE20_BIN"
fi

[ -x "$NODE20_BIN" ] || die "Node.js 20 runtime is unavailable."
echo "Using $NODE20_BIN"
echo "Node home: $NODE20_HOME"
"$NODE20_BIN" --version

# Stage the exact project-selected Node 20 binary outside /root so systemd can
# execute it as the unprivileged relay user. This does not alter system/default Node.
if [ "$NODE20_BIN" != "$NODE20_RUNTIME" ]; then
  RUNTIME_VERSION=""
  if [ -x "$NODE20_RUNTIME" ]; then
    RUNTIME_VERSION="$("$NODE20_RUNTIME" --version 2>/dev/null || true)"
  fi
  if [ "$RUNTIME_VERSION" != "v$NODE20_VERSION" ]; then
    install -m 0755 "$NODE20_BIN" "$NODE20_RUNTIME"
  fi
fi
"$NODE20_RUNTIME" --version
NODE20_BIN="$NODE20_RUNTIME"
NODE20_DIR="/usr/local/bin"

echo "[3/10] Verifying DNS..."
RESOLVED_IP="$(getent hosts "$DOMAIN" | awk 'NR==1{print $1}')"
[ -n "$RESOLVED_IP" ] || die "$DOMAIN does not resolve."
echo "$DOMAIN -> $RESOLVED_IP"

echo "[4/10] Preparing ACME webroot..."
mkdir -p "$ACME_WEBROOT"

CERT="/etc/letsencrypt/live/$DOMAIN/fullchain.pem"
KEY="/etc/letsencrypt/live/$DOMAIN/privkey.pem"
HAVE_CERT=false
if [ -f "$CERT" ] && [ -f "$KEY" ]; then
  HAVE_CERT=true
fi

echo "[5/10] Preparing nginx ACME endpoint..."
if $HAVE_CERT; then
  # A previous run may have left the temporary ACME site enabled. Remove only
  # our own temporary symlink so it cannot conflict with the final relay site.
  if [ -L /etc/nginx/sites-enabled/babyphone-acme ] && [ "$(readlink -f /etc/nginx/sites-enabled/babyphone-acme)" = "$ACME_SITE" ]; then
    rm -f /etc/nginx/sites-enabled/babyphone-acme
  fi
  echo "Existing Babyphone certificate found; temporary ACME site not enabled."
else
  if [ -e "$ACME_SITE" ]; then
    grep -q "server_name $DOMAIN;" "$ACME_SITE" || die "$ACME_SITE exists and is not recognized. Nothing changed."
  else
    cat > "$ACME_SITE" <<EOF
server {
    listen 80;
    listen [::]:80;
    server_name $DOMAIN;

    location ^~ /.well-known/acme-challenge/ {
        root $ACME_WEBROOT;
        default_type "text/plain";
        try_files \$uri =404;
    }

    location / {
        return 404;
    }
}
EOF
  fi
  ln -sf "$ACME_SITE" /etc/nginx/sites-enabled/babyphone-acme
  nginx -t
  systemctl reload nginx
fi

echo "[6/10] Obtaining/checking TLS certificate..."
if $HAVE_CERT; then
  echo "Existing Babyphone certificate found; no certificate download."
else
  certbot certonly --webroot -w "$ACME_WEBROOT" --non-interactive --agree-tos --keep-until-expiring -d "$DOMAIN"
  [ -f "$CERT" ] && [ -f "$KEY" ] || die "Certbot did not create the expected certificate."
  HAVE_CERT=true
  if [ -L /etc/nginx/sites-enabled/babyphone-acme ] && [ "$(readlink -f /etc/nginx/sites-enabled/babyphone-acme)" = "$ACME_SITE" ]; then
    rm -f /etc/nginx/sites-enabled/babyphone-acme
  fi
fi

echo "[7/10] Installing relay application..."
mkdir -p "$APP_DIR"
if id -u "$APP_USER" >/dev/null 2>&1; then
  echo "Existing relay user found; keeping it."
else
  useradd --system --home "$APP_DIR" --shell /usr/sbin/nologin "$APP_USER"
fi

cat > "$APP_DIR/package.json" <<'EOF'
{
  "name": "open-babyphone-relay",
  "version": "1.0.0",
  "private": true,
  "type": "module",
  "engines": { "node": ">=20" },
  "scripts": { "start": "node server.js" },
  "dependencies": { "ws": "^8.18.3" }
}
EOF

cat > "$APP_DIR/server.js" <<'EOF'
import http from "node:http";
import { WebSocketServer } from "ws";

const PORT = Number(process.env.PORT || 8338);
const sessions = new Map();
const MAX_PENDING_BYTES = 512 * 1024;

const validSessionId = (value) =>
  typeof value === "string" && /^[A-Za-z0-9_-]{20,128}$/.test(value);

function createSession() {
  return {
    child: null,
    parent: null,
    pendingForChild: [],
    pendingForParent: [],
    pendingBytes: 0
  };
}

function pendingQueueFor(session, role) {
  return role === "child" ? session.pendingForChild : session.pendingForParent;
}

function queueForPeer(session, peerRole, data) {
  const queue = pendingQueueFor(session, peerRole);
  const copy = Buffer.from(data);
  if (session.pendingBytes + copy.length > MAX_PENDING_BYTES) {
    queue.length = 0;
    session.pendingBytes = 0;
    return;
  }
  queue.push(copy);
  session.pendingBytes += copy.length;
}

function flushPending(session, role, ws) {
  const queue = pendingQueueFor(session, role);
  if (ws.readyState !== 1 || queue.length === 0) return;
  for (const data of queue) {
    if (ws.readyState !== 1) break;
    ws.send(data, { binary: true });
  }
  session.pendingBytes -= queue.reduce((sum, data) => sum + data.length, 0);
  queue.length = 0;
}

function cleanup(sessionId, ws, reason = "peer disconnected") {
  const session = sessions.get(sessionId);
  if (!session) return;

  if (session.child !== ws && session.parent !== ws) return;

  const peer = session.child === ws ? session.parent : session.child;
  sessions.delete(sessionId);
  session.pendingForChild.length = 0;
  session.pendingForParent.length = 0;
  session.pendingBytes = 0;

  if (peer && peer.readyState === 1) {
    console.log(`Closing relay peer for session ${sessionId.slice(0, 12)}…: ${reason}`);
    try {
      peer.terminate();
    } catch {
      // Ignore peer-close races.
    }
  }
}

const server = http.createServer((req, res) => {
  if (req.url === "/healthz") {
    res.writeHead(200, {
      "content-type": "text/plain; charset=utf-8",
      "cache-control": "no-store"
    });
    res.end("OK\n");
    return;
  }
  res.writeHead(404);
  res.end("Not found\n");
});

const wss = new WebSocketServer({
  noServer: true,
  perMessageDeflate: false,
  maxPayload: 65536
});

wss.on("connection", (ws, _req, sessionId, role) => {
  let session = sessions.get(sessionId);
  if (!session) {
    session = createSession();
    sessions.set(sessionId, session);
  }

  if (session[role]) {
    console.warn(`Rejecting duplicate relay ${role} connection for session ${sessionId.slice(0, 12)}…`);
    ws.close(1008, "role already connected");
    return;
  }

  session[role] = ws;
  console.log(`Relay ${role} connected for session ${sessionId.slice(0, 12)}…`);
  flushPending(session, role, ws);

  ws.on("message", (data, isBinary) => {
    if (!isBinary) return;
    const peerRole = role === "child" ? "parent" : "child";
    const peer = session[peerRole];
    if (peer?.readyState === 1) {
      peer.send(data, { binary: true });
    } else {
      queueForPeer(session, peerRole, data);
    }
  });

  ws.on("close", () => cleanup(sessionId, ws, `${role} closed`));
  ws.on("error", (error) => {
    console.warn(`Relay ${role} socket error for session ${sessionId.slice(0, 12)}…:`, error.message);
    cleanup(sessionId, ws, `${role} error`);
  });
});

server.on("upgrade", (req, socket, head) => {
  try {
    const url = new URL(req.url, "http://relay.invalid");
    if (url.pathname !== "/relay") {
      socket.write("HTTP/1.1 404 Not Found\r\nConnection: close\r\n\r\n");
      socket.destroy();
      return;
    }

    const sessionId = url.searchParams.get("session");
    const role = url.searchParams.get("role");

    if (!validSessionId(sessionId) || !["child", "parent"].includes(role)) {
      socket.write("HTTP/1.1 400 Bad Request\r\nConnection: close\r\n\r\n");
      socket.destroy();
      return;
    }

    wss.handleUpgrade(req, socket, head, (ws) => {
      wss.emit("connection", ws, req, sessionId, role);
    });
  } catch {
    socket.destroy();
  }
});

server.listen(PORT, "127.0.0.1", () => {
  console.log(`Open Babyphone relay listening on 127.0.0.1:${PORT}`);
});
EOF

chown -R "$APP_USER:$APP_USER" "$APP_DIR"

echo "[8/10] Installing only missing npm dependency..."
NPM_CLI_JS=""
for candidate in \
  "$NODE20_HOME/lib/node_modules/npm/bin/npm-cli.js" \
  "$NODE20_HOME/node_modules/npm/bin/npm-cli.js"; do
  if [ -r "$candidate" ]; then
    NPM_CLI_JS="$candidate"
    break
  fi
done

if [ -d "$APP_DIR/node_modules/ws" ]; then
  echo "Existing ws dependency found; no npm download."
elif [ -n "$NPM_CLI_JS" ]; then
  echo "ws is missing; running npm CLI directly with Node 20..."
  "$NODE20_BIN" "$NPM_CLI_JS" --prefix "$APP_DIR" install --omit=dev
  chown -R "$APP_USER:$APP_USER" "$APP_DIR"
else
  die "Node 20 npm CLI was not found. Checked the standard nvm npm locations."
fi

cat > "$SYSTEMD_UNIT" <<EOF
[Unit]
Description=Open Babyphone Internet Relay
After=network-online.target
Wants=network-online.target

[Service]
Type=simple
User=$APP_USER
Group=$APP_USER
WorkingDirectory=$APP_DIR
Environment=NODE_ENV=production
Environment=PORT=$PORT
Environment=PATH=$NODE20_DIR:/usr/local/bin:/usr/bin:/bin
ExecStart=$NODE20_RUNTIME $APP_DIR/server.js
Restart=always
RestartSec=2
NoNewPrivileges=true
PrivateTmp=true
ProtectSystem=strict
ProtectHome=read-only
ReadWritePaths=$APP_DIR
LimitNOFILE=65536

[Install]
WantedBy=multi-user.target
EOF

# Disable only the temporary ACME site; the final relay site serves ACME too.
if [ -L /etc/nginx/sites-enabled/babyphone-acme ] && [ "$(readlink -f /etc/nginx/sites-enabled/babyphone-acme)" = "$ACME_SITE" ]; then
  rm -f /etc/nginx/sites-enabled/babyphone-acme
fi

echo "[9/10] Installing isolated nginx site..."
if [ -e "$NGINX_SITE" ]; then
  grep -q "server_name $DOMAIN;" "$NGINX_SITE" || die "$NGINX_SITE exists and is not recognized. Nothing changed."
fi

cat > "$NGINX_SITE" <<EOF
server {
    listen 80;
    listen [::]:80;
    server_name $DOMAIN;

    location ^~ /.well-known/acme-challenge/ {
        root $ACME_WEBROOT;
        default_type "text/plain";
        try_files \$uri =404;
    }

    location / {
        return 301 https://\$host\$request_uri;
    }
}

server {
    listen 443 ssl;
    listen [::]:443 ssl;
    server_name $DOMAIN;

    ssl_certificate $CERT;
    ssl_certificate_key $KEY;
    ssl_protocols TLSv1.2 TLSv1.3;

    location = /healthz {
        proxy_pass http://127.0.0.1:$PORT/healthz;
        proxy_http_version 1.1;
        proxy_set_header Host \$host;
        proxy_set_header X-Forwarded-Proto https;
        proxy_buffering off;
    }

    location /relay {
        proxy_pass http://127.0.0.1:$PORT;
        proxy_http_version 1.1;
        proxy_set_header Upgrade \$http_upgrade;
        proxy_set_header Connection "upgrade";
        proxy_read_timeout 1h;
        proxy_send_timeout 1h;
        proxy_buffering off;
        proxy_request_buffering off;
    }
}
EOF

ln -sf "$NGINX_SITE" /etc/nginx/sites-enabled/babyphone-relay
nginx -t

echo "[10/10] Starting/reloading only Babyphone components..."
systemctl daemon-reload
systemctl enable open-babyphone-relay.service
systemctl restart open-babyphone-relay.service
systemctl reload nginx

echo
echo "=== Verification ==="
systemctl is-active --quiet open-babyphone-relay.service || {
  systemctl --no-pager --full status open-babyphone-relay.service || true
  exit 1
}
curl -fsS "http://127.0.0.1:$PORT/healthz"
echo
curl -fsS "https://$DOMAIN/healthz"
echo
echo "Testing public WSS upgrade..."
"$NODE20_BIN" --input-type=module - <<'NODE'
import WebSocket from "/opt/open-babyphone-relay/node_modules/ws/index.js";
const session = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
const url = "wss://babyphone.duckdns.org/relay?session=" + session + "&role=child";
await new Promise((resolve, reject) => {
  const ws = new WebSocket(url);
  const timer = setTimeout(() => {
    ws.terminate();
    reject(new Error("WSS upgrade timed out"));
  }, 10000);
  ws.once("open", () => {
    clearTimeout(timer);
    ws.close(1000, "setup verification");
  });
  ws.once("close", () => resolve());
  ws.once("error", (error) => {
    clearTimeout(timer);
    reject(error);
  });
});
NODE
echo "WSS upgrade OK"
"$NODE20_BIN" --version
echo
echo "READY"
echo "WSS endpoint: wss://$DOMAIN/relay"
echo "Node 18/default was not changed."
