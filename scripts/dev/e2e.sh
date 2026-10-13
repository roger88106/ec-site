#!/usr/bin/env bash
# ============================================================================
# E2E（e2e/ の pnpm test）を流し、テストごとの結果と失敗の要点だけを出す
#
#   使い方：scripts/dev/e2e.sh [Playwright の引数...]
#     例：scripts/dev/e2e.sh                              # すべて
#         scripts/dev/e2e.sh tests/payment.spec.ts        # 1 つのファイル
#         scripts/dev/e2e.sh tests/payment.spec.ts -g AC-148
#
# バックエンド・管理画面・店舗画面の起動のログは多いので、出力の全体は LOG_DIR（既定は一時ディレクトリ）に残し、
# 次だけを出す：準備の結果、テストごとの ok / x、失敗の Error の行と error-context.md のパス、件数のまとめ。
# サーバーが起動できなかったときは、サーバーのログのうち誤りの行を出す（例：ポートが WinNAT の予約範囲で EACCES）。
# 終了コードは pnpm test のもの。
# ============================================================================
set -u

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
TAIL_LINES="${TAIL_LINES:-30}"
LOG_DIR="${LOG_DIR:-$(mktemp -d -t e2e.XXXXXX)}"
mkdir -p "$LOG_DIR"
log="$LOG_DIR/e2e.log"

start=$SECONDS
(cd "$ROOT/e2e" && pnpm test "$@") >"$log" 2>&1
status=$?
elapsed=$((SECONDS - start))

# 準備、テストごとの結果、失敗の要点、件数のまとめ
grep -E 'E2E の準備|^\s+(ok|x|-|✘|✓)\s+[0-9]+ |^\s+Error:|error-context\.md|^\s+[0-9]+ (passed|failed|flaky|skipped|did not run)' "$log"

if [ $status -ne 0 ]; then
  # テストまで進まなかった（サーバーが起動できない、準備の失敗など）ときは、誤りの行か末尾を出す
  if ! grep -qE '^\s+(ok|x|-|✘|✓)\s+[0-9]+ ' "$log"; then
    errors=$(grep -iE 'error|exception|failure|went wrong|EACCES|EADDRINUSE' "$log" | grep -vE '^\[backend\].*(DEBUG|INFO)' | head -n "$TAIL_LINES")
    if [ -n "$errors" ]; then
      echo "$errors" | sed 's/^/     | /'
    else
      tail -n "$TAIL_LINES" "$log" | sed 's/^/     | /'
    fi
  fi
  echo "FAIL e2e (${elapsed}s, exit $status) 全体：$log"
else
  echo "OK   e2e (${elapsed}s) 全体：$log"
fi
exit $status
