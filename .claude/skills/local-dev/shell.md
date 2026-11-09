# Git Bash と Windows の挙動、Browser での確認

Git Bash で長い処理やパイプを使うとき、Browser で画面を確認するときに読む。

## Git Bash と Windows の挙動

- `cat > <ファイル>` は、heredoc がないと標準入力を待ち続ける。ファイルは Write で書く。
- バックグラウンドで実行した処理（`bootRun` など）を `| tail` などのパイプに通すと、パイプが閉じずに終わらない。出力はファイルにリダイレクトし、標準入力は `< /dev/null` にする。
- `export MSYS_NO_PATHCONV=1` は以後のコマンド（commit など）にも有効になり、パスの変換が必要なコマンドが失敗する。必要なコマンドの前にだけ付ける（`MSYS_NO_PATHCONV=1 <コマンド>`）。
- `grep -P` は使えない。`grep -E` か、Grep のツールを使う。
- Grep・Glob のツールでブレース（`{a,b}`）の指定が 0 件を返すことがある。呼び出しを分けるか、正規表現の `(a|b)` を使う。
- バックグラウンドに移ったコマンドが動いているかは、ツールの通知でなく、実際の処理（ポートの応答、出力のファイル）で確認する。

## Browser での画面の確認

SKILL.md の「プレビューで画面を確認する」の補足。

- localhost の Cookie はポートで分かれないので、スロットや画面を行き来すると蓄積し、400 や 431（ヘッダーが大きすぎる）になる。localhost の Cookie を消してから開き直す。直し方は issue #453。
- CSP の違反は `read_console_messages` で取れないことがある。`javascript_tool` で `securitypolicyviolation` のイベントを登録してから操作し、違反を読む。
- バックグラウンドのタブでは、描画とアニメーションが停止する。タブを前面にしてからスクリーンショットを撮るか、DOM の属性（`data-closed` など）で状態を判定する。
