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
# 前提のコンテナ（DB、メール、画像）が止まっていれば、名前と起動のコマンドを出して終わる（終了コード 2）。
# それ以外の終了コードは pnpm test のもの。
# ============================================================================
set -u

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
TAIL_LINES="${TAIL_LINES:-30}"
LOG_DIR="${LOG_DIR:-$(mktemp -d -t e2e.XXXXXX)}"
mkdir -p "$LOG_DIR"
log="$LOG_DIR/e2e.log"

# 前提のコンテナ（DB、メール、画像の置き場所と変換）が動いているか。E2E の IdP は準備の script が起動する
stopped=()
for container in ec-site-postgres ec-site-mailpit ec-site-seaweedfs ec-site-imgproxy; do
  [ "$(docker inspect -f '{{.State.Running}}' "$container" 2>/dev/null)" = true ] || stopped+=("$container")
done
if [ ${#stopped[@]} -gt 0 ]; then
  echo "動いていないコンテナ：${stopped[*]}" >&2
  echo "ルートで起動してから流し直す：docker compose up -d ${stopped[*]//ec-site-/}" >&2
  exit 2
fi

# 実行中を示すファイル。E2E は管理画面のビルドの結果（dist）を配るので、実行中に check-frontend.sh がビルドし直さないよう、
# そちらがこのファイルを見て止まる。中身はこの script のプロセス ID（異常終了で残っても、プロセスがなければ無視される）
running="$ROOT/.local/e2e-running"
mkdir -p "$ROOT/.local"
echo $$ >"$running"
trap 'rm -f "$running"' EXIT

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
