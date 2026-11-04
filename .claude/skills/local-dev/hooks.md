# 手元の hook の設定

hook を `.claude/settings.local.json` に置く、修正する、他の環境に移すときに読む。毎回の手順ではない。

hook と permission の置き場所は 2 つに分ける。チームの全員に例外なく適用する規則は `.claude/settings.json`（Git に入る）、環境・利用者に固有のものは `.claude/settings.local.json`（Git に入らない）に置く。hook の script は `scripts/dev/hooks/` に置く（例外：zh-mixing の手元の hook は `.claude/skills/zh-mixing/`）。

利用者の環境（`.claude/settings.local.json`）に置く 3 つの hook は次のとおり。読む場所と、変更が反映される時点は、skill `sessions` の[作業ツリーのセッションが読むもの](../sessions/session-reads.md)の表。設定はメインの作業ツリーに置く。

| hook | イベントと matcher | 働き |
|------|--------------------|------|
| `session-start.mjs` | SessionStart、`startup\|resume\|compact` | メインの作業ツリーの `/.local/agent/environment.md` を文脈に入れる |
| `backslash-guard.mjs` | PreToolUse、`Bash` | heredoc と `node -e` の中の `\` を拒否する |
| `post-tool-use.cjs` | PostToolUse、`Write\|Edit\|MultiEdit\|NotebookEdit` | 書いたファイルを層 2 の検査にかけ、見つかれば終了コード 2 で返す |

表の働きの細目（読むファイル、対象、出力）は、各 script の冒頭のコメントにある。

command は、次の例の形で script を探す（`git rev-parse --show-toplevel` の作業ツリー、`$CLAUDE_PROJECT_DIR` の順）。どちらにも見つからないときは、`.claude/settings.json` と違い、ツールの実行を止めずに終了コード 0 で終わる。理由：script を含まないブランチの作業ツリーでも、メインの作業ツリーの script で動かすため。

```json
{
  "hooks": {
    "SessionStart": [
      {
        "matcher": "startup|resume|compact",
        "hooks": [
          { "type": "command", "command": "f=; for d in \"$(git rev-parse --show-toplevel 2>/dev/null)\" \"$CLAUDE_PROJECT_DIR\"; do if [ -n \"$d\" ] && [ -f \"$d/scripts/dev/hooks/session-start.mjs\" ]; then f=\"$d/scripts/dev/hooks/session-start.mjs\"; break; fi; done; [ -z \"$f\" ] || node \"$f\"" }
        ]
      }
    ],
    "PreToolUse": [
      {
        "matcher": "Bash",
        "hooks": [
          { "type": "command", "command": "f=; for d in \"$(git rev-parse --show-toplevel 2>/dev/null)\" \"$CLAUDE_PROJECT_DIR\"; do if [ -n \"$d\" ] && [ -f \"$d/scripts/dev/hooks/backslash-guard.mjs\" ]; then f=\"$d/scripts/dev/hooks/backslash-guard.mjs\"; break; fi; done; [ -z \"$f\" ] || node \"$f\"" }
        ]
      }
    ],
    "PostToolUse": [
      {
        "matcher": "Write|Edit|MultiEdit|NotebookEdit",
        "hooks": [
          { "type": "command", "command": "f=; for d in \"$(git rev-parse --show-toplevel 2>/dev/null)\" \"$CLAUDE_PROJECT_DIR\"; do if [ -n \"$d\" ] && [ -f \"$d/.claude/skills/zh-mixing/post-tool-use.cjs\" ]; then f=\"$d/.claude/skills/zh-mixing/post-tool-use.cjs\"; break; fi; done; [ -z \"$f\" ] || node \"$f\"" }
        ]
      }
    ]
  }
}
```

- 既に `hooks` があれば、イベントごとの配列に項目を追加する。設定を変えたら、新しいセッションを起動する。
- PostToolUse の項目は、中国語で指示する環境だけに置く。環境の印と git の hook は skill `zh-mixing`。
- 他の置き方：同じ項目を利用者の設定（`~/.claude/settings.json`）に置く。他のリポジトリでも呼ばれ、script がないと node が失敗するので、PostToolUse の command を `f="$CLAUDE_PROJECT_DIR/.claude/skills/zh-mixing/post-tool-use.cjs"; [ ! -f "$f" ] || node "$f"` にする。
