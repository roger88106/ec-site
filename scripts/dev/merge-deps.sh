#!/usr/bin/env bash
# ============================================================================
# Dependabot の pull request を、最新にして CI を待ち、マージのコマンドを出力する
#
#   使い方：bash scripts/dev/merge-deps.sh 101 102
#   引数  ：pull request の番号（1 つ以上）。1 つずつ順に処理する
#   振る舞い：BEHIND なら gh pr update-branch で最新にし、CI が終わるのを待つ。
#     マージできる状態（CLEAN で失敗がない）なら、次の 1 行を標準出力に出す。
#       gh pr merge <番号> --merge --match-head-commit <SHA>
#     DIRTY、update-branch の後に head が変わらない、待っても CI が終わらない（上限に達する）、失敗がある、
#     のときは理由を標準エラー出力に出して終了コード 1 で止まる（マージのコマンドは出さない）
#
#   この script はマージを実行しない。理由：Claude Code の hook
#   （scripts/dev/hooks/agent-guard.mjs）は Bash のコマンドの文字列だけを読むので、
#   script の中のマージを判定できない。エージェントは、出力されたコマンドを 1 つずつ実行する。
#   gh は PATH のものを使う。リポジトリは gh の既定（作業ツリーの remote）に任せる。
# ============================================================================
set -u

if ! command -v gh >/dev/null 2>&1; then
  echo "gh が PATH にない。GitHub CLI を入れて PATH に通してから実行する" >&2
  exit 1
fi
if [ "$#" -eq 0 ]; then
  echo "使い方：bash scripts/dev/merge-deps.sh <番号>..." >&2
  exit 1
fi

repo=$(gh repo view --json nameWithOwner -q .nameWithOwner) || { echo "リポジトリを求められない" >&2; exit 1; }

merge_state() { gh pr view "$1" -R "$repo" --json mergeStateStatus -q .mergeStateStatus; }
head_sha() { gh pr view "$1" -R "$repo" --json headRefOid -q .headRefOid; }

for n in "$@"; do
  state=$(merge_state "$n")
  if [ "$state" = "BEHIND" ]; then
    old=$(head_sha "$n")
    gh pr update-branch "$n" -R "$repo" >/dev/null || { echo "#$n: update-branch に失敗した" >&2; exit 1; }
    # head が変わる（最新が反映される）まで待つ
    changed=0
    for _ in $(seq 1 30); do
      sleep 10
      if [ "$(head_sha "$n")" != "$old" ]; then changed=1; break; fi
    done
    if [ "$changed" = "0" ]; then
      echo "#$n: update-branch の後に head が変わらないまま待つ上限に達した（最新が反映されていない）" >&2
      exit 1
    fi
  elif [ "$state" = "DIRTY" ]; then
    echo "#$n: 衝突している（DIRTY）" >&2
    exit 1
  fi

  # バックエンドの検査が表示されて、pending が 0 になるまで待つ
  ready=0
  for _ in $(seq 1 90); do
    sleep 20
    checks=$(gh pr checks "$n" -R "$repo" 2>/dev/null)
    pending=$(echo "$checks" | grep -cE "pending|queued|in_progress")
    backend=$(echo "$checks" | grep -c "バックエンド")
    state=$(merge_state "$n")
    if [ "$backend" != "0" ] && [ "$pending" = "0" ] && [ "$state" != "UNKNOWN" ]; then ready=1; break; fi
  done
  if [ "$ready" = "0" ]; then
    echo "#$n: CI を待つ上限に達した（バックエンドの検査が出ない、または pending が 0 にならない。state=$state、pending=$pending、バックエンドの検査=$backend）" >&2
    exit 1
  fi

  fails=$(gh pr checks "$n" -R "$repo" 2>/dev/null | grep -c fail)
  if [ "$state" = "CLEAN" ] && [ "$fails" = "0" ]; then
    echo "gh pr merge $n --merge --match-head-commit $(head_sha "$n")"
  else
    echo "#$n: マージできない（state=$state、失敗=$fails、または CI が終わらない）" >&2
    exit 1
  fi
done
