#!/usr/bin/env bash
set -euo pipefail

MODE="${1:-static}"
PORT="${PORT:-8000}"
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
RUN_DIR="$ROOT_DIR/.nginx"

case "$MODE" in
  dev) APP_ROOT="" ;;
  static) APP_ROOT="$ROOT_DIR/dist-mock" ;;
  prod) APP_ROOT="$ROOT_DIR/dist" ;;
  *) echo "usage: $0 dev|static|prod" >&2; exit 2 ;;
esac

if [ "$MODE" != "dev" ] && [ ! -f "$APP_ROOT/index.html" ]; then
  echo "Missing $APP_ROOT/index.html. Run npm run build:mock (static) or npm run build (prod)." >&2
  exit 2
fi

mkdir -p "$RUN_DIR/logs"
cat > "$RUN_DIR/mime.types" <<'EOF'
types {
  text/html html;
  text/css css;
  application/javascript js mjs;
  application/json json;
  image/svg+xml svg;
  image/jpeg jpg jpeg;
  image/png png;
  video/mp2t ts;
  application/vnd.apple.mpegurl m3u8;
  font/woff2 woff2;
  application/octet-stream bin;
}
EOF

if [ "$MODE" = "dev" ]; then
  WEB_LOCATION='proxy_pass http://127.0.0.1:5173;
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "upgrade";
        proxy_set_header Host $host;'
else
  WEB_LOCATION="root \"$APP_ROOT\";
        try_files \$uri /index.html;"
fi

cat > "$RUN_DIR/nginx.conf" <<EOF
worker_processes 1;
pid $RUN_DIR/nginx.pid;
error_log $RUN_DIR/logs/error.log info;

events { worker_connections 512; }

http {
  include $RUN_DIR/mime.types;
  default_type application/octet-stream;
  access_log $RUN_DIR/logs/access.log;
  sendfile on;

  server {
    listen $PORT;
    server_name localhost;

    location /api/ {
      proxy_pass http://127.0.0.1:8080;
      proxy_set_header Host \$host;
    }

    location /media/ {
      rewrite ^/media/(.*)\$ /tvc-local-output/\$1 break;
      proxy_pass http://127.0.0.1:4566;
      proxy_set_header Host \$host;
    }

    location / {
      $WEB_LOCATION
    }
  }
}
EOF

echo "nginx $MODE on http://localhost:$PORT"
exec nginx -c "$RUN_DIR/nginx.conf" -p "$RUN_DIR" -g 'daemon off;'
