# 手元の hook の設定

hook を `.claude/settings.local.json` に置く、修正する、ほかの環境に移すときに読む。毎回の手順ではない。

hook と permission の書き先は 2 つに分ける。チームの例外のない規則は `.claude/settings.json`（Git に入る）、環境・利用者に固有のものは `.claude/settings.local.json`（Git に入らない）に置く。hook の script は `scripts/dev/hooks/` に置く（Claude Code の `agent-guard.mjs`、git の `pre-commit`・`commit-msg`、手元の hook の `session-start.mjs`・`backslash-guard.mjs`）。例外は zh-mixing の手元の hook で、script は `.claude/skills/zh-mixing/` に置く。

利用者の環境（`.claude/settings.local.json`）に置く 3 つの hook は次のとおり。読む場所と、変更が反映される時点は、skill `sessions` の「作業ツリーのセッションが読むもの」の表。設定はメインの作業ツリーに置く。

| hook | イベントと matcher | 働き |
|------|--------------------|------|
| `session-start.mjs` | SessionStart、`startup\|resume\|compact` | メインの作業ツリーの `/.local/agent/environment.md` を文脈に入れる。hook が動くセッションでは、どの作業ツリーでも同じファイルを読む。ファイルがなければ何も出さない |
| `backslash-guard.mjs` | PreToolUse、`Bash` | heredoc と `node -e` の中の `\` を拒否する。コマンドの他の箇所の `\`（grep の `\|` など）は通す |
| `post-tool-use.cjs` | PostToolUse、`Write\|Edit\|MultiEdit\|NotebookEdit` | 書いたファイル（`tool_input.file_path`）を層 2 の検査にかけ、見つかれば終了コード 2 で返す（詳細は表の下の箇条） |

command は、script のパスを、まず `git rev-parse --show-toplevel` の作業ツリーから探し、なければ `$CLAUDE_PROJECT_DIR`（メインの作業ツリー）の下を使う形にする。探し方は `.claude/settings.json` と同じだが、どちらにも見つからないときは、`settings.json` はコマンドを止め、この例は何もせず通す。理由：script を含まないブランチの作業ツリーでも、メインの作業ツリーの script で動かすため。

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
- `post-tool-use.cjs` は、書いたファイルのうち、リポジトリのファイルと memory のファイル（`~/.claude/projects/<プロジェクト>/memory/` の下）を調べる。`/.local/` の下とリポジトリの外の他のファイルは調べない。見つかれば終了コード 2 と標準エラー出力（ファイル、行、語、直し方）で返し、Claude Code がエージェントに渡す。script は `.claude/skills/zh-mixing/` にある。
- 他の置き方として、同じ項目を利用者の設定（`~/.claude/settings.json`）に置く方法もある。その場合は他のリポジトリでも呼ばれ、script がないリポジトリでは node が失敗するので、PostToolUse の command を `f="$CLAUDE_PROJECT_DIR/.claude/skills/zh-mixing/post-tool-use.cjs"; [ ! -f "$f" ] || node "$f"` にする。script があるリポジトリでも、環境の印がなければ、入口は何も出さず終了コード 0 で終わり、リポジトリの外で呼ばれても同じである（テストが調べる）。
