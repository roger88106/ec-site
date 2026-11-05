---
name: zh-mixing
description: 中国語で指示する環境で、日本語の文書・コメント・コミットメッセージへの中国語の字・用語の混入を調べる。script と一覧の使い方、設定、語の追加、誤検出の除去に使う。
---

# 中国語の混入を調べる

利用者が中国語で指示し、日本語で書かせる環境では、会話から文書へ訳すときや、混ざった文を写して広げるときに、中国語の字・用語が日本語の文に混ざる。混ざった行を、記録される前に見つける。

この skill は層 2 の検査である。層 1 と層 2 の分け方と動く環境は、skill `review` の [docs-review.md](../review/docs-review.md) の手順 4。

## 環境の印

リポジトリの git の設定 `ecsite.instructionLanguage` の値が `zh` のときだけ調べる。印がなければ、script は何も調べず、何も出さず、終了コード 0 で終わる。印は Git に入らず、作業ツリーの間で共有される。

```
git config --local ecsite.instructionLanguage zh
```

## script の使い方

リポジトリのルートで実行する。見つかれば終了コード 1 で、ファイル・行・語を標準エラー出力に出す。

| 対象 | コマンド |
|------|----------|
| 指定したファイル | `node .claude/skills/zh-mixing/zh-mixing.cjs file <パス>...` |
| ステージした追加行 | `node .claude/skills/zh-mixing/zh-mixing.cjs staged` |
| コミットメッセージのファイル | `node .claude/skills/zh-mixing/zh-mixing.cjs message <ファイル>` |
| Git が追跡している全てのファイル（アーカイブした spec と、評価の一覧の置き場所 `.claude/skills/review/evals/` を除く） | `node .claude/skills/zh-mixing/zh-mixing.cjs tracked` |

環境の印がなくても調べるときは、`--force` を付ける（一覧を較正するとき）。

見つかった行は、前後を読み、日本語に直す。コードの識別子は変えない。直せない行（引用など）と誤検出は、「一覧の直し方」で許容用語にする。

## 設定の手順

中国語で指示する環境で、次の 3 つを設定する。どれも Git に入らないファイルに書く。環境の印がなければ、設定しても何も調べない。

1. 環境の印：`git config --local ecsite.instructionLanguage zh`。`.git/config` に入り、全ての作業ツリーで共有する。
2. Claude Code の hook：skill `local-dev` の[手元の hook の設定](../local-dev/hooks.md)の PostToolUse の項目を、`.claude/settings.local.json` に置く（環境に固有なので Git に入らない）。
3. git の hook（commit を止める）：[開発環境の起動](../../../docs/06-手順書/setup.md)の「起動」にある `.git/hooks/pre-commit`・`commit-msg` の行（層 1 の `ja-text` の検査）に続けて、次の行を追加する。どちらも、失敗すれば commit が失敗する形にする。

| hook | 追加する行 |
|------|--------|
| `.git/hooks/pre-commit` | `f="$(git rev-parse --show-toplevel)/.claude/skills/zh-mixing/zh-mixing.cjs"; [ ! -f "$f" ] \|\| node "$f" staged \|\| exit 1` |
| `.git/hooks/commit-msg` | `f="$(git rev-parse --show-toplevel)/.claude/skills/zh-mixing/zh-mixing.cjs"; [ ! -f "$f" ] \|\| node "$f" message "$1" \|\| exit 1` |

`.git/hooks/` は全ての作業ツリーで共有し、script のないブランチ（例えば `main` から切る `release/`・`hotfix/` のブランチ）でも hook が動くので、行は script があるときだけ呼ぶ形にする。

設定を外すには、環境の印を `git config --local --unset ecsite.instructionLanguage` で外す。次に、`settings.local.json` の PostToolUse の項目（skill `local-dev` の[手元の hook の設定](../local-dev/hooks.md)の `post-tool-use.cjs` の項目）と、手順 3 で git の hook に追加した行を削除する。

## 一覧の直し方

一覧は `zh-mixing.json` にある。中国語の字をファイルに書くと、この検査が自分自身の追加行で失敗するので、字と用語は `\uXXXX` のエスケープで書く。Windows の Git Bash でも、次のコマンドでエスケープを得られる。

```
node -e "console.log([...process.argv[1]].map(c => '\\u' + c.codePointAt(0).toString(16).padStart(4, '0')).join(''))" <字・用語>
```

| 項目 | 内容 |
|------|------|
| `chars` | 日本語では使わない字（簡体字と繁体字）。日本語と同じ字形の字は入れない |
| `words` | 日本語の文に漢字で混ざりやすい中国語の用語。日本語として普通の語は入れない |
| `allow` | 許容用語。`{ "word": "語", "file": "パス（省略すると全てのファイル）", "reason": "理由" }`。理由がないと一覧の読み込みが失敗する |

Git に入れない手元だけの除外は、`.local/agent/zh-allow.txt` に「語」か「ファイルのパス（タブ）語」を 1 行ずつ書く。メインの作業ツリーの `.local/` にあれば、どの作業ツリーでも読む。

## 書くときの注意

コミットメッセージと spec は、書いた後に検査で失敗しやすい。書く前に次を守る。

- 層 1 の使わない語（`scripts/ci/ja-text.json` の error の語）は、コミットメッセージと spec に書かない。git の hook `commit-msg` の日本語の検査が失敗させる。書いた後は `node scripts/ci/ja-text.mjs` で確認する。
- 利用者の中国語の言葉は、日本語に訳して書く。原文を写さない。理由：写した中国語の字・用語を、層 2 の検査（`commit-msg`・`pre-commit`）が失敗させるため。

## テスト

`node --test .claude/skills/zh-mixing/zh-mixing.test.cjs`。`scripts/ci/check-all.sh` も実行する。テストは、環境の印を注入した値で試し、利用者の環境の git の設定を読まない。

この文書には、層 2 の字・用語を書かない。テストが調べる。
