#!/usr/bin/env bash
# ============================================================================
# CI の検査の script を、手元と CI で同じようにまとめて流す（CI の検査）
#
#   使い方：scripts/ci/check-all.sh（どこから流してもよい）
#   commit の前の hook は、環境変数 CHECK_TESTS・CHECK_SCRIPTS で実行するものを絞る（実行する所のコメント）。
#
# 流すもの：
#   1. script のテスト（scripts/ci/・scripts/dev/・scripts/shared/ の下のすべての *.test.mjs と、skill（.claude/skills/）の下のすべての *.test.cjs）
#   2. 検査のフォルダ（scripts/ci/{docs,spec,ja,java,config,frontend}/）の直下の *.mjs のうち、*.test.mjs 以外のすべて（検査）
#   3. 差分の追加行の文体の検査（ja-text --changed）。警告だけを出し、失敗にしない（警告があっても終了コードは 0）
#
# 検査のフォルダの直下には引数なしで流せる検査だけを置く。引数が必要な補助は report/ などの下のディレクトリに置く。
# 検査を追加するときは、検査のフォルダの直下に置けばここを直さずに流れる（新しいフォルダを作るときは、ここと pre-commit-select.mjs の CHECK_GROUPS を直す）。
#
# 検査は、最後の行に「<何>：<件数>」の要約の行を出す。終了コードが 0 でも、要約の行を出さずに終わった検査は失敗にする。
# 成功したものは 1 行と、警告（「警告：」で始まる行）だけを出す。失敗したものは、出力の末尾（TAIL_LINES 行）を添える。
# ja-text --changed は、通っても、CI では注釈と要約の行を、手元では件数を示す「警告：」の行を出力する（理由は実行する所のコメント）。
# 1 つでも失敗すれば、すべてを流したあとで終了コード 1 を返す。
# ============================================================================
set -u

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$ROOT" || exit 2

TAIL_LINES="${TAIL_LINES:-30}"
LOG="$(mktemp -t check-all.XXXXXX)"
trap 'rm -f "$LOG"' EXIT

failed=0
# 直前の run_check が通ったか（1 なら通った）
last_ok=0

# 1 つを流し、結果を 1 行で出す（失敗したら出力の末尾を添える）
#   $1：表示の名前、$2：判定の種類（summary は要約の行を求める、tests は node --test のテストの数が 1 件以上を求める）、残り：流すコマンド
#   終了コードが 0 でなければ、node を呼ばずに失敗にする。
#   summary のものは、終了コードが 0 でも、最後の行が要約の行（「<何>：<件数>」）でなければ失敗にする（判定は scripts/shared/check-result.mjs。成功は OK の 1 行で示す）
run_check() {
  local name="$1"
  local mode="$2"
  shift 2
  local start=$SECONDS
  "$@" >"$LOG" 2>&1
  local status=$?
  local elapsed=$((SECONDS - start))
  local reason ok=0
  last_ok=0
  if [ "$status" -ne 0 ]; then
    reason="終了コード $status"
  else
    # 要約の行の判定だけを node に任せる。OK の行が出なければ、判定の script が動かなかったものとして失敗にする
    reason="$(node scripts/shared/check-result.mjs "$LOG" "$mode")"
    if [ $? -eq 0 ] && [ "$reason" = "OK" ]; then
      ok=1
    elif [ -z "$reason" ]; then
      reason="判定の script が結果を出さなかった"
    fi
  fi
  if [ "$ok" -eq 1 ]; then
    last_ok=1
    echo "OK   $name (${elapsed}s)"
    # 通っても、警告（「警告：」で始まる行）は出す
    grep '^警告：' "$LOG" | sed 's/^/     | /'
  else
    failed=1
    echo "FAIL $name (${elapsed}s, $reason)"
    tail -n "$TAIL_LINES" "$LOG" | sed 's/^/     | /'
  fi
}

# script のテストは node --test の出力で、要約の行の形ではないので、テストの数（tests N）が 1 件以上かで判定する（tests）。
# glob が 1 件も一致しないと、node --test は tests 0 で終了コード 0 になるため
#
# commit の前の hook は、選んだテストと検査だけを CHECK_TESTS・CHECK_SCRIPTS（空白で区切る）で渡す。渡さなければ全てを実行する。
# 選んだものが空なら実行しない（hook の選び方は scripts/dev/hooks/pre-commit-select.mjs）
if [ -z "${CHECK_TESTS+set}" ]; then
  run_check "script のテスト" tests node --test "scripts/ci/**/*.test.mjs" "scripts/dev/**/*.test.mjs" "scripts/shared/**/*.test.mjs" ".claude/skills/**/*.test.cjs"
elif [ -n "$CHECK_TESTS" ]; then
  # shellcheck disable=SC2086 # 空白で区切った一覧を、ファイルごとの引数にする
  run_check "script のテスト" tests node --test $CHECK_TESTS
fi

for script in scripts/ci/{docs,spec,ja,java,config,frontend}/*.mjs; do
  case "$script" in
    *.test.mjs) continue ;;
  esac
  name="$(basename "$script" .mjs)"
  if [ -n "${CHECK_SCRIPTS+set}" ] && [[ " $CHECK_SCRIPTS " != *" $name "* ]]; then
    continue
  fi
  run_check "$name" summary node "$script"
done

# commit の前の hook は、追加行の warning を自分で表示するので、差分の文体の検査を実行しない
if [ -n "${CHECK_SCRIPTS+set}" ]; then
  exit $failed
fi

# 差分の追加行の文体の検査は、引数（--changed）が必要なので、直下の検査とは別に実行する。
# 警告は GitHub Actions の注釈の行（「::warning file=<ファイル>,line=<行>::<内容>」）で出る。通ったときの出し方は、実行する所で分ける。
#   GitHub Actions（GITHUB_ACTIONS=true）では、注釈の行を加工せず行頭から出力し、最後に要約の行（件数）を出力する。GitHub が注釈を pull request の画面の行に表示するため。
#   手元では、件数を示す「警告：」の行だけを出力し、1 件ずつは出力しない（既定の ja-text の warning と同じ）。
run_check "ja-text --changed" summary node scripts/ci/ja/ja-text.mjs --changed
if [ "$last_ok" -eq 1 ]; then
  if [ "${GITHUB_ACTIONS:-}" = "true" ]; then
    grep '^::warning ' "$LOG"
    awk 'NF { last = $0 } END { print last }' "$LOG" | sed 's/^/     | /'
  else
    changed_count="$(awk 'NF { last = $0 } END { sub(/^.*：/, "", last); print last }' "$LOG")"
    echo "     | 警告：差分の追加行の文体の警告が ${changed_count} 件ある（一覧：node scripts/ci/ja/ja-text.mjs --changed）"
  fi
fi

exit $failed
