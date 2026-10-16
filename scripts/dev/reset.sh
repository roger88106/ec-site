#!/usr/bin/env bash
# ============================================================================
# ローカルのデータを Git の正本から作り直す（開発環境の起動 第 4 節）
#
#   使い方：scripts/dev/reset.sh [db|keycloak|all]（どこから流してもよい）
#     db（既定）：DB ec_site を消して作る。seed は次のバックエンドの起動で流れる。
#                 バックエンドが動いていれば、止めるよう求めて何もせずに終わる。
#                 E2E の DB（ec_site_e2e）、IdP（keycloak の database）、画像には触れない
#     keycloak  ：ローカルの IdP の database を作り直し、realm の JSON（docker/keycloak/realms/）を取り込み直す。
#                 ローカルで登録した会員も消える。DB ec_site と E2E の IdP には触れない
#     all       ：docker compose down -v の後に up -d。DB・IdP・画像を、すべて Git の正本から作り直す
#                 （DB の seed：backend/ecsite/src/main/resources/db/seed/、画像：docker/seaweedfs/seed/、
#                 IdP：docker/keycloak/realms/）。E2E の DB は次の E2E の準備で作られる
# ============================================================================
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$ROOT"

target="${1:-db}"

# バックエンド（:8080）が動いているか
backend_running() {
  curl -s -o /dev/null --max-time 2 http://localhost:8080/actuator/health
}

psql_postgres() {
  docker exec ec-site-postgres psql -v ON_ERROR_STOP=1 -U ec_site -d postgres "$@"
}

case "$target" in
  db)
    if backend_running; then
      echo "バックエンド（:8080）が動いている。止めてから流し直す（接続を持ったまま DB を消すと、起動中の処理が失敗する）" >&2
      exit 1
    fi
    psql_postgres -c "drop database if exists ec_site with (force)" -c "create database ec_site owner ec_site"
    echo "DB ec_site を作り直した。次のバックエンドの起動で seed が流れる"
    ;;
  keycloak)
    docker compose stop keycloak
    psql_postgres -c "drop database if exists keycloak with (force)" -c "create database keycloak owner keycloak"
    docker compose up -d --wait keycloak
    echo "ローカルの IdP を作り直し、realm を取り込み直した"
    ;;
  all)
    docker compose down -v
    docker compose up -d --wait
    echo "DB・IdP・画像を作り直した。DB の seed は次のバックエンドの起動で流れる"
    ;;
  *)
    echo "対象は db、keycloak、all のどれか：$target" >&2
    exit 2
    ;;
esac
