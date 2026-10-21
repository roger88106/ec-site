#!/usr/bin/env bash
# ============================================================================
# フロントの確認（依存の入れ直し、ライセンス、lint、型チェック、テスト、ビルド）を実行し、手順ごとに結果の要約だけを出す
#
#   使い方：scripts/dev/check-frontend.sh [shared|store|admin|e2e|all] [手順...]
#     例：scripts/dev/check-frontend.sh                    # install licenses の後に、共有パッケージで lint typecheck test、
#                                                          # 両方のアプリで lint typecheck test build、e2e で lint typecheck
#         scripts/dev/check-frontend.sh admin test         # 管理画面のテストだけ
#         scripts/dev/check-frontend.sh store lint typecheck
#
# e2e（E2E のコード）は lint と typecheck だけを流し、ほかの手順は飛ばす。E2E のテストは scripts/dev/e2e.sh で流す。
#
# install（pnpm install --frozen-lockfile）と licenses（依存のライセンスの検査）は、ルートで 1 回だけ流す。
# 手元は依存が入った後なので、CI のインストールでだけ失敗すること（allowBuilds の漏れなど）を先に見つけるため。
#
# 成功した手順は 1 行だけ出す。失敗した手順は、出力の末尾（TAIL_LINES 行。既定 30）を添える。
# 出力の全体は LOG_DIR（既定は一時ディレクトリ）に手順ごとに残す。
# 失敗した手順があれば、すべての手順を実行したあとで終了コード 1 を返す。
# ============================================================================
set -u

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"

# 管理画面の本番ビルドは IdP の設定がなければ失敗するので、CI と同じローカルの値を渡す（すでにあれば、その値を使う）
export VITE_OIDC_AUTHORITY="${VITE_OIDC_AUTHORITY:-http://localhost:8180/realms/ec-admin}"
export VITE_OIDC_CLIENT_ID="${VITE_OIDC_CLIENT_ID:-ec-admin-web}"

TAIL_LINES="${TAIL_LINES:-30}"
LOG_DIR="${LOG_DIR:-$(mktemp -d -t check-frontend.XXXXXX)}"
mkdir -p "$LOG_DIR"

target="${1:-all}"
[ $# -gt 0 ] && shift
case "$target" in
  all) apps=(shared store admin e2e) ;;
  shared | store | admin | e2e) apps=("$target") ;;
  *)
    echo "対象は shared、store、admin、e2e、all のどれか：$target" >&2
    exit 2
    ;;
esac
steps=("$@")
[ ${#steps[@]} -eq 0 ] && steps=(install licenses lint typecheck test build)

failed=0

# 1 つの手順を流し、結果を 1 行で出す（失敗したら出力の末尾を添える）
#   $1：表示の名前、$2：ログの名前、残り：流すコマンド
run_step() {
  local name="$1" logname="$2"
  shift 2
  local log="$LOG_DIR/$logname.log"
  local start=$SECONDS
  "$@" >"$log" 2>&1
  local status=$?
  local elapsed=$((SECONDS - start))
  if [ $status -eq 0 ]; then
    echo "OK   $name (${elapsed}s)"
  else
    failed=1
    echo "FAIL $name (${elapsed}s, exit $status) 全体：$log"
    tail -n "$TAIL_LINES" "$log" | sed 's/^/     | /'
  fi
}

# E2E（scripts/dev/e2e.sh）が実行中か。実行中を示すファイルがあり、その中のプロセスが動いていれば実行中
e2e_running() {
  local running="$ROOT/.local/e2e-running"
  [ -f "$running" ] && kill -0 "$(cat "$running")" 2>/dev/null
}

app_steps=()
for step in "${steps[@]}"; do
  case "$step" in
    install) run_step "install" install bash -c "cd '$ROOT' && pnpm install --frozen-lockfile" ;;
    licenses) run_step "licenses" licenses bash -c "cd '$ROOT' && node scripts/ci/licenses/check-frontend.mjs" ;;
    *) app_steps+=("$step") ;;
  esac
done

for app in "${apps[@]}"; do
  dir="$ROOT/frontend/$app"
  [ "$app" = e2e ] && dir="$ROOT/e2e"
  for step in "${app_steps[@]}"; do
    # e2e は lint と typecheck だけ（テストとビルドの手順はない）。共有パッケージはビルドしない（各画面がソースを読む）
    if [ "$app" = e2e ] && [ "$step" != lint ] && [ "$step" != typecheck ]; then continue; fi
    if [ "$app" = shared ] && [ "$step" = build ]; then continue; fi
    # E2E の実行中は、画面をビルドし直さない（E2E が配っている dist が入れ替わり、E2E が失敗するため）
    if [ "$step" = build ] && e2e_running; then
      failed=1
      echo "FAIL $app build：E2E の実行中（.local/e2e-running）なので、ビルドしない。E2E が終わってから流し直す"
      continue
    fi
    run_step "$app $step" "$app-$step" bash -c "cd '$dir' && pnpm '$step'"
  done
done

exit $failed
