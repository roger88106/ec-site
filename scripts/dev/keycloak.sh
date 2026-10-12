#!/usr/bin/env bash
# ローカルの Keycloak（compose。http://localhost:8180）を確かめるための補助。
#
#   scripts/dev/keycloak.sh admin GET /admin/realms/ec-store            管理 API を呼ぶ（master の admin / admin で）
#   scripts/dev/keycloak.sh admin PUT /admin/realms/ec-store -d '{...}'  curl の引数をそのまま渡せる
#   scripts/dev/keycloak.sh token ec-admin admin@example.com admin-pass-1  access token の中身（payload）を出す
#   scripts/dev/keycloak.sh access-token ec-store member@example.com member-pass-1  access token そのものを出す
#
# token：web の client は、パスワードで token を取る方式（Direct Access Grant）を無効にしている。
# 一時的に有効にして token を取り、終わったら無効に戻す。ローカルの確認専用。
set -euo pipefail

KC=${KEYCLOAK_URL:-http://localhost:8180}

# master の管理者の access token
admin_token() {
  curl -s -d client_id=admin-cli -d username=admin -d password=admin -d grant_type=password \
    "$KC/realms/master/protocol/openid-connect/token" | sed -E 's/.*"access_token":"([^"]+)".*/\1/'
}

# 管理 API を呼ぶ
admin() {
  local method=$1 path=$2
  shift 2
  curl -s -X "$method" -H "Authorization: Bearer $(admin_token)" -H 'Content-Type: application/json' "$KC$path" "$@"
}

# base64url を復号する（パディングを補う）
b64url_decode() {
  local s
  s=$(tr '_-' '/+')
  while [ $(( ${#s} % 4 )) -ne 0 ]; do s="$s="; done
  printf '%s' "$s" | base64 -d
}

# realm の web の client で、利用者の access token を取って出す
access_token() {
  local realm=$1 user=$2 password=$3
  local client=${realm}-web
  local id
  id=$(admin GET "/admin/realms/$realm/clients?clientId=$client" | grep -o '"id":"[^"]*"' | head -1 | cut -d'"' -f4)
  admin PUT "/admin/realms/$realm/clients/$id" -d "{\"clientId\":\"$client\",\"directAccessGrantsEnabled\":true}"
  # 終わったら（失敗しても）無効に戻す。EXIT のときには local の変数がないので、値をここで埋め込む
  trap "admin PUT '/admin/realms/$realm/clients/$id' -d '{\"clientId\":\"$client\",\"directAccessGrantsEnabled\":false}'" EXIT

  local secret_args=()
  local secret
  secret=$(admin GET "/admin/realms/$realm/clients/$id/client-secret" | sed -nE 's/.*"value":"([^"]+)".*/\1/p')
  if [ -n "$secret" ]; then secret_args=(-d "client_secret=$secret"); fi

  local response
  response=$(curl -s -d "client_id=$client" "${secret_args[@]}" -d "username=$user" -d "password=$password" \
    -d grant_type=password -d scope=openid "$KC/realms/$realm/protocol/openid-connect/token")
  local access
  access=$(printf '%s' "$response" | sed -nE 's/.*"access_token":"([^"]+)".*/\1/p')
  if [ -z "$access" ]; then
    printf '%s\n' "$response" >&2
    exit 1
  fi
  printf '%s' "$access"
}

# 利用者の access token を取り、payload を出す
token() {
  access_token "$@" | cut -d. -f2 | b64url_decode
  echo
}

command=${1:-}
shift || true
case "$command" in
  admin) admin "$@" ;;
  token) token "$@" ;;
  access-token) access_token "$@" ;;
  *) sed -n '2,10p' "$0"; exit 1 ;;
esac
