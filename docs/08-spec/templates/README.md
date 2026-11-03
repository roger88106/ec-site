# spec の雛形と見本

spec・plan・tasks を新しく書くときの雛形と、書き方の見本。書き方の規則は[文書の構成](../../05-開発規則/doc-structure.md)の「spec と plan の書き方」、番号と記号は[文書の記号と番号](../../05-開発規則/notation.md)。

構造は、検査の script `node scripts/ci/spec-structure.mjs` が確認する。`scripts/ci/check-all.sh` からも実行される。

## 雛形と見本

| ファイル | 使う場面 |
|----------|----------|
| `spec-feature.md` | 機能の spec（規模 L・M） |
| `spec-refactor.md` | 整理の spec（規模 L・M） |
| `plan.md` | 規模 L の plan |
| `tasks-stage.md` | 規模 L の段階のファイル（`tasks/NN-<名前>.md`）と、規模 M の `## tasks` の中のタスク |
| `examples/feature-l/` | 機能・規模 L の見本（spec、plan、`tasks/01-ship.md`） |
| `examples/refactor-m/spec.md` | 整理・規模 M の見本（`## tasks` の節を含む） |

見本の番号 900 は見本用で、実在の spec ではない。

## 規模ごとに使うファイル

| 規模 | 書くもの |
|------|----------|
| S | issue だけ（本文を spec とする） |
| M | spec の雛形（機能は `spec-feature.md`、整理は `spec-refactor.md`）と、最後の `## tasks` の節 ※1 |
| L | spec の雛形、`plan.md`、`tasks-stage.md`（段階ごとに 1 ファイル） |

※1：`tasks-stage.md` の「タスク」と同じ形で、`## tasks` の下にタスクを書く。段階はないので、タスク番号の段階は `01` にする。plan は書かず、決定は、spec の「スコープ」の表の後に、「決定：」の行と箇条で書く。

規模の決め方は[開発の進め方](../../05-開発規則/workflow.md)の「規模」。

## 雛形の使い方

1. 雛形を `docs/08-spec/<番号>-<名前>/` にコピーする（`spec-feature.md` は `spec.md` に、`tasks-stage.md` は `tasks/NN-<名前>.md` に名前を変える）。
2. 見出し・表・箇条の中の `<…>` は埋める。`<…>` だけの段落（書き方の説明）と HTML コメントは削除する。見出しの名前と順は変えない。
3. 同じ種類の見本を読み、書き方を合わせる。
4. `node scripts/ci/spec-structure.mjs` で構造を確認する。

## アーカイブの spec を元にしない理由

アーカイブした spec は、書いた当時の規則と用語で書いてある。コピーすると、古い規則と用語が新しい spec に残る。
