#!/usr/bin/env bash
# ============================================================================
# CI の検査の script を、手元と CI で同じようにまとめて流す（CI の検査）
#
#   使い方：scripts/ci/check-all.sh（どこから流してもよい）
#
# 流すもの：
#   1. script のテスト（scripts/ci/ と scripts/dev/ の下のすべての *.test.mjs と、skill（.claude/skills/）の下のすべての *.test.cjs）
#   2. scripts/ci/ の直下の *.mjs のうち、*.test.mjs 以外のすべて（検査）
# 直下には引数なしで流せる検査だけを置く。引数が必要な補助は report/ などの下のディレクトリに置く。
# 検査を追加するときは、直下に置けばここを直さずに流れる。
#
# 検査は、最後の行に「<何>：<件数>」の要約の行を出す。終了コードが 0 でも、要約の行を出さずに終わった検査は失敗にする。
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
#   $1：表示の名前、$2：要約の行を求めるか（summary か none）、残り：流すコマンド
#   summary のものは、終了コードが 0 でも、最後の行が要約の行（「<何>：<件数>」）でなければ失敗にする（判定は lib/check-result.mjs）
run_check() {
  local name="$1"
  local mode="$2"
  shift 2
  local start=$SECONDS
  "$@" >"$LOG" 2>&1
  local status=$?
  local elapsed=$((SECONDS - start))
  local reason
  reason="$(node scripts/ci/lib/check-result.mjs "$status" "$LOG" "$mode")"
  if [ $? -eq 0 ]; then
    echo "OK   $name (${elapsed}s)"
    # 通っても、警告（「警告：」で始まる行）は出す
    grep '^警告：' "$LOG" | sed 's/^/     | /'
  else
    failed=1
    echo "FAIL $name (${elapsed}s, $reason)"
    tail -n "$TAIL_LINES" "$LOG" | sed 's/^/     | /'
  fi
}

# script のテストは node --test の出力で、要約の行の形ではないので、終了コードだけで判定する（none）
run_check "script のテスト" none node --test "scripts/ci/**/*.test.mjs" "scripts/dev/**/*.test.mjs" ".claude/skills/**/*.test.cjs"

for script in scripts/ci/*.mjs; do
  case "$script" in
    *.test.mjs) continue ;;
  esac
  run_check "$(basename "$script" .mjs)" summary node "$script"
done

exit $failed
