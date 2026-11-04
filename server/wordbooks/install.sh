#!/bin/sh
# Run on the server only after reviewing README.md and choosing a deployment directory.
set -eu
DEST=${TEAWORDS_SERVICE_DIR:-/srv/teawords-wordbooks/v1}
UNIT=/etc/systemd/system/teawords-wordbooks.service
[ "$(id -u)" = 0 ] || { echo 'Run the reviewed installer as root'; exit 1; }
[ ! -e "$DEST" ] || { echo "Refusing to overwrite existing path: $DEST"; exit 1; }
[ ! -e "$UNIT" ] || { echo "Refusing to overwrite existing service: $UNIT"; exit 1; }
command -v python3 >/dev/null
mkdir -p "$DEST"
cp -R server.py public "$DEST/"
chmod -R a+rX "$DEST"
cat > "$UNIT" <<EOF
[Unit]
Description=teaWords read-only wordbook API
After=network.target
[Service]
Type=simple
DynamicUser=yes
ExecStart=/usr/bin/python3 $DEST/server.py --public $DEST/public --host 127.0.0.1 --port 8080
Restart=on-failure
NoNewPrivileges=yes
ProtectSystem=strict
ProtectHome=yes
PrivateTmp=yes
[Install]
WantedBy=multi-user.target
EOF
systemctl daemon-reload
systemctl enable --now teawords-wordbooks.service
echo 'Started on 127.0.0.1:8080. Configure your HTTPS reverse proxy separately.'
