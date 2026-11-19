#!/bin/sh
# Launcher used inside the Flatpak bundle
# bin/<name> must match NUVIO_LINUX_APP_NAME in linux-shortcut-definition.sh.
exec /app/opt/Nuvio/bin/NuvioCodeineXO "$@"
