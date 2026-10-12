#!/usr/bin/env bash
# ============================================================================
# フロントの確認（lint、型チェック、テスト、ビルド）を実行し、手順ごとに結果の要約だけを出す
#
#   使い方：scripts/check-frontend.sh [store|admin|all] [手順...]
#     例：scripts/check-frontend.sh                    # 両方のアプリで lint typecheck test build
#         scripts/check-frontend.sh admin test         # 管理画面のテストだけ
#         scripts/check-frontend.sh store lint typecheck
#
# 成功した手順は 1 行だけ出す。失敗した手順は、出力の末尾（TAIL_LINES 行。既定 30）を添える。
# 出力の全体は LOG_DIR（既定は一時ディレクトリ）に手順ごとに残す。
# 失敗した手順があれば、すべての手順を実行したあとで終了コード 1 を返す。
# ============================================================================
set -u

ROOT="$(cd "$(dirname "$0")/.." && pwd)"

TAIL_LINES="${TAIL_LINES:-30}"
LOG_DIR="${LOG_DIR:-$(mktemp -d -t check-frontend.XXXXXX)}"
mkdir -p "$LOG_DIR"

target="${1:-all}"
[ $# -gt 0 ] && shift
case "$target" in
  all) apps=(store admin) ;;
  store | admin) apps=("$target") ;;
  *)
    echo "対象は store、admin、all のどれか：$target" >&2
    exit 2
    ;;
esac
steps=("$@")
[ ${#steps[@]} -eq 0 ] && steps=(lint typecheck test build)

failed=0
for app in "${apps[@]}"; do
  for step in "${steps[@]}"; do
    log="$LOG_DIR/$app-$step.log"
    start=$SECONDS
    (cd "$ROOT/frontend/$app" && pnpm "$step") >"$log" 2>&1
    status=$?
    elapsed=$((SECONDS - start))
    if [ $status -eq 0 ]; then
      echo "OK   $app $step (${elapsed}s)"
    else
      failed=1
      echo "FAIL $app $step (${elapsed}s, exit $status) 全体：$log"
      tail -n "$TAIL_LINES" "$log" | sed 's/^/     | /'
    fi
  done
done

exit $failed
