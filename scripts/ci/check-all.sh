#!/usr/bin/env bash
# ============================================================================
# CI の検査の script を、手元と CI で同じようにまとめて流す（CI の検査）
#
#   使い方：scripts/ci/check-all.sh（どこから流してもよい）
#
# 流すもの：
#   1. script のテスト（scripts/ci/ の下のすべての *.test.mjs と、skill（.claude/skills/）の下のすべての *.test.cjs）
#   2. scripts/ci/ の直下の *.mjs のうち、*.test.mjs 以外のすべて（検査）
# 直下には引数なしで流せる検査だけを置く。引数が必要な補助は report/ などの下のディレクトリに置く。
# 検査を追加するときは、直下に置けばここを直さずに流れる。
#
# 成功したものは 1 行と、警告（「警告：」で始まる行）だけを出す。失敗したものは、出力の末尾（TAIL_LINES 行。既定 30）を添える。
# 1 つでも失敗すれば、すべてを流したあとで終了コード 1 を返す。
# ============================================================================
set -u

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$ROOT" || exit 2

TAIL_LINES="${TAIL_LINES:-30}"
LOG="$(mktemp -t check-all.XXXXXX)"
trap 'rm -f "$LOG"' EXIT

failed=0

# 1 つを流し、結果を 1 行で出す（失敗したら出力の末尾を添える）
#   $1：表示の名前、残り：流すコマンド
run_check() {
  local name="$1"
  shift
  local start=$SECONDS
  "$@" >"$LOG" 2>&1
  local status=$?
  local elapsed=$((SECONDS - start))
  if [ $status -eq 0 ]; then
    echo "OK   $name (${elapsed}s)"
    # 通っても、警告（「警告：」で始まる行）は出す
    grep '^警告：' "$LOG" | sed 's/^/     | /'
  else
    failed=1
    echo "FAIL $name (${elapsed}s, exit $status)"
    tail -n "$TAIL_LINES" "$LOG" | sed 's/^/     | /'
  fi
}

run_check "script のテスト" node --test "scripts/ci/**/*.test.mjs" ".claude/skills/**/*.test.cjs"

for script in scripts/ci/*.mjs; do
  case "$script" in
    *.test.mjs) continue ;;
  esac
  run_check "$(basename "$script" .mjs)" node "$script"
done

exit $failed
