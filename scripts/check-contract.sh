#!/usr/bin/env bash
# Compara docs/api/openapi.json del working tree contra el de una referencia git (default origin/main, o HEAD si no hay remoto).
# Uso: scripts/check-contract.sh [base-ref]
set -euo pipefail
root="$(git rev-parse --show-toplevel)"
# Sin remoto (repo local) se compara contra HEAD.
default_ref=origin/main
git -C "$root" rev-parse --verify -q "$default_ref" >/dev/null || default_ref=HEAD
base_ref="${1:-$default_ref}"
tmp="$(mktemp)"
trap 'rm -f "$tmp"' EXIT
git -C "$root" show "${base_ref}:docs/api/openapi.json" > "$tmp"
python3 "$root/scripts/openapi-compat.py" "$tmp" "$root/docs/api/openapi.json"
