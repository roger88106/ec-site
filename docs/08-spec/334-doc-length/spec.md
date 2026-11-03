# Spec 334：毎回読む文書を短くし、文書の長さの基準と雛形を決める

種類：整理　規模：M　ステータス：確定

## 背景と目的

整理の段階の第 2 フェーズの最後の整理を、3 つの spec（#334・#335・#336）に分けた。この spec（#334）は、毎回読む文書と進め方の規則を扱う。#335 は書き方の規則・手順書・skill の本文、#336 は機能仕様・設計・決定記録・OpenAPI を扱う。

文書はエージェントが読むので、字数がそのまま token の費用になる。減らす量の目標は決めない（利用者の決定、2026-11-03）。

棚卸し（2026-11-03、`develop` の 605ece4a）で、次のことが分かった。

- 全てのセッションが始めに読む `AGENTS.md`（13,552 バイト）、constitution（19,629 バイト）、`workflow.md`（28,138 バイト）の合計は 61,319 バイトである（`wc -c` で測った）。約 38%（約 23,000 バイト）を減らせる見込みである（棚卸しの推測）。
- `AGENTS.md` は、実装役の定義（`.claude/agents/implementer.md`）に `omitClaudeMd` がないので（定義のファイルの frontmatter で確認した）、実装役の呼び出しのたびにも文脈に入る。
- skill と agent の description は、全てのセッションの文脈に入る（棚卸しのセッションの skill の一覧で確認した）。合計は 4,145 バイト（skill 8 つで 2,600、agent 5 つで 1,545）である（frontmatter の `description:` の値を node で数えた）。
- 冗長の型は次の 6 つである。
  - script・設定・コードから読める内容の転記
  - 同じ規則を 2〜3 か所に書くこと
  - 案内だけの表と空の節
  - 特定の場面だけで使う内容を、毎回読む文書に置くこと
  - 前置きで節の一覧を文で列挙すること
  - 理由の言い換え、経緯、移した内容の記録

この spec で、文書の長さの基準と文書の種類ごとの雛形を決める。#335・#336 もこの基準を使うので、この spec を先にマージする。消費者・運営者に見える振る舞いも、コードの動作も変えない。

## スコープ

| 含める | 含めない |
|--------|----------|
| 1. `workflow.md` を、毎回読む部分と場面ごとの部分に分ける | 書き方の規則（backend-coding など）・手順書・skill の本文（#335） |
| 2. constitution を縮める | 機能仕様・設計・決定記録・OpenAPI（#336） |
| 3. `AGENTS.md` を縮める | 大きさの上限の CI の検査（利用者の決定） |
| 4. skill と agent の description を縮める | |
| 5. `writing.md` を、言い回しの規則と、文書の構成（置き場所・参照・spec と plan の書き方）に分ける | |
| 6. 進め方の規則の文書と記録の重複・冗長を減らす（`subagents.md`、`resources.md`、`review.md`、`notation.md`、`docs/05-開発規則/glossary.md`、`licenses.md`、`docs/07-記録/` の 3 つ、README の MVP の進捗の表） | |
| 7. 文書の長さの基準と、文書の種類ごとの雛形を決めて書く | |

決定：

- 分けるのは、読む場面が明らかに違う文書だけにする（利用者の決定、2026-11-03）。この spec で分けるのは `workflow.md` と `writing.md` で、他の文書は縮めるだけにする。
- 分けた・移した節の見出しの名前は変えない。理由：参照の修正が文書の名前（リンク先）の置き換えだけで済み、`doc-refs` が漏れを見つけるため。
- `workflow.md` は次の 3 つに分ける。

  | ファイル | 文書の名前 | 中身 | 読む時点 |
  |----------|------------|------|----------|
  | `docs/05-開発規則/workflow.md` | 開発の進め方（今のまま） | SDD と TDD の流れ、spec の種類、コードと文書の食い違い、規模、push のタイミング、利用者の確認と中断、実装を始める前の照合、完了の定義 | 毎回 |
  | `docs/05-開発規則/issues.md`（新規） | 起票と記録の操作 | 起票、AC 一覧の操作、アーカイブの操作、構成が決まっていないときの試作 | 起票するとき、spec を書く・閉じるとき、試作のとき |
  | `docs/05-開発規則/merging.md`（新規） | マージと後片付け | マージの許可と後片付け、pull request のレビューの対応 | pull request を作る・マージする・後片付けのとき |

  - 表の括弧の補足。`workflow.md` に残す「コードと文書の食い違い」から、AC 一覧の操作（今の `ac-check --write` 以降の箇条）を除く。「利用者の確認と中断」から、pull request のレビューの対応を除く。`issues.md` のアーカイブの操作は、今の「規模」の tasks・アーカイブの手順の箇条である。`merging.md` のマージの許可と後片付けには、変更の危険度を含む。
  - `workflow.md` にあった文書の置き場所の表は、`doc-structure.md` の同じ名前の節に移す。
  - 起票の節の issue のテンプレートの項目の表は削除し、`.github/ISSUE_TEMPLATE/` を指す。理由：テンプレートの転記のため。
  - 毎回読む `workflow.md` の目安は 15,000 バイト以下にする。
- `writing.md` は次の 2 つに分ける。

  | ファイル | 文書の名前 | 中身 | 読む人 |
  |----------|------------|------|--------|
  | `docs/05-開発規則/writing.md` | 文書の書き方（今のまま） | 冒頭（本文の書き手の規則）、言語、論理名と物理名、表と注、用語の選び方、言い回しと論理 | 文書を書くセッション、校閲役、レビュー |
  | `docs/05-開発規則/doc-structure.md`（新規） | 文書の構成 | 文書の置き場所（`workflow.md` の表を統合する）、参照、spec と plan の書き方、文書の長さの基準と雛形（新規） | 文書を追加する・移す・spec を書くセッション |

  - 「用語の選び方」などにある `scripts/ci/ja-text.json` の語の対の転記は削除し、`ja-text.json` を指す。
  - 「参照」のうち、`doc-refs` が何を確認し何を確認しないかの説明は削り、書き方の規則だけを残す。
  - 「載せない規則」の節は削除し、冒頭の出典の文に、散文の書籍向けの規則は載せないことを加える（issue 担当の判断、利用者の委任、2026-11-04）。
- constitution を次のように縮める。
  - 文書の地図を、1 行が 1 つのフォルダーの表 1 つ（フォルダー、中身、一覧の文書）にする。フォルダーごとの文書の一覧は、各フォルダーの `README.md` に移す（issue 担当の判断、利用者の委任、2026-11-04）。`docs/01-概要/README.md` と `docs/04-決定記録/README.md` は既にある。
  - 文書を追加したときに書く先を、地図から、フォルダーの `README.md` に変える。`writing.md`・skill `docs`・`scripts/ci/docs-folders.mjs` のコメントの記述を合わせる。
  - 設計の文書と同じ表（アプリごとの技術、モジュール）と、`licenses.md` と同じライセンスの区分の表は削除し、正本を指す。技術スタックのバージョンの記述も設計の文書を指す。「リポジトリ構成」の図は縮める。
  - 目安は 10,000 バイト以下にする。
- `AGENTS.md` を次のように縮める。
  - skill の表を削除し、置き場所と追加・削除の手順だけを書く。表を前提にした記述も修正する。対象は、skill `writing-for-agents`・`handoff`・`retro` の日本語の追記にある、AGENTS.md の skill の表を前提にした記述である。英語の原文の本文は変えない。
    - 理由：skill の説明は system prompt に既にあるため。
  - 場面ごとに `workflow.md` の節を案内する表を削除する。
  - Git の表の注 3 と 5、モデルの表の注 1、「環境」の hook と permission の書き先の段落を縮める。hook の書き先の段落の詳細は、skill `local-dev` の「手元の hook の設定」に移す。
  - 目安は 8,000 バイト以下にする。
- description は、「何をするか」と「いつ使うか」だけにする。本文の見出しと同じ内容の列挙と、同じ使う場面を言い換えた文を削る。
  - 対象の skill は 6 つ（local-dev、docs、new-screen、zh-mixing、sessions、agent-retro）。review は既に短い。
  - 対象の agent は 5 つ（implementer、reviewer、researcher、ja-proofreader、ja-proof-verifier）。
  - 英語の原文の writing-for-agents は対象外とする。
- 規則の重複は、次の表の正本に 1 か所だけ書く。他の文書は見出しの参照で指す。

  | 規則 | 正本 | 今ほかにある所 |
  |------|------|----------------|
  | 業務規則の番号（BR）の付け方 | `notation.md` の「業務規則の番号（BR）」 | `writing.md`、`workflow.md` |
  | 単独の「高」を使わない | `review.md` | `writing.md`、`glossary.md` |
  | 単線の定義 | `resources.md` | `subagents.md`、skill `sessions` |
  | spec 担当はコードを書かない | `subagents.md` の「役割」 | `subagents.md` の他の節、`notation.md` |

- 用語集（`docs/05-開発規則/glossary.md`）と `licenses.md` は、コード・設定から読める内容を削除し、ファイルを指す。削除するのは、テストの補助のパス、jest-dom の読み込み方、`policy.json` と `build.gradle.kts` の設定の説明などである。
- 記録（`docs/07-記録/`）は、空の節、移した先を指すだけの節、節の一覧を文で列挙する前置きを削る。`context-size.md` は、古い基準を表 1 つに縮め、この spec の結果を加える。
- README の MVP の進捗の表は、`docs/08-spec/archive/mvp/mvp.md` への 1 行の案内にする。
- 文書の長さの基準を、`doc-structure.md` の新しい節に書く。
  - 大きさの目安：`AGENTS.md` 8,000 バイト、constitution 10,000 バイト、`docs/05-開発規則/` の 1 つの文書 15,000 バイト。
  - 冒頭（1 行目の見出しから最初の `##` まで）は、何の文書かと、いつ読むかの 2 文以内。節の一覧を文で列挙しない。
  - 表の 1 つのセルは 300 バイトまで。
  - 1 つの規則の正本は 1 か所。他の文書は見出しの参照で指す。
  - 理由は `理由：〜ため。` の 1 文で書く。
  - script・設定・コードから読める内容は書かず、そのファイルを指す。
  - 目安を超えたときは、読む場面で分けるか、縮める。CI の検査は加えない。利用者が定期的にレビューする（利用者の決定、2026-11-03）。
  - 人が読む文書は短く保つ。エージェントだけが調べる細目（検査の範囲の説明、経緯、測定の詳細など）は、後で調べる価値があれば `docs/07-記録/` などの別の文書に移し、なければ削る（利用者の方針、2026-11-04）。
  - 今の大きさの記録は `docs/07-記録/context-size.md` に置く。
- 文書の種類ごとの雛形を、同じ節に書く。
  - `AGENTS.md`：節の構成（最初に読むもの、skill、本書にだけある規則、モデルの区分、Git、環境）と、各節の長さの目安。
  - constitution：各節は 1 文と要点 3 つまで。表は文書の地図の 1 つだけ。
  - 規則の文書：冒頭、規則の節、根拠の記録への参照。
  - 用語集：列は用語・識別子・意味の 3 つ。意味は 1〜2 文。
  - 記録：冒頭・測り方・結果・判断の 4 つの節。
  - README：概要、起動とテストの案内、進捗の案内。
  - フォルダーの `README.md`：文書と内容の表。

  `writing.md` にあった「文書の置き場所」の、文書ごとの書く範囲の表は、この雛形に合わせる。
- 測り方は `wc -c` のバイト数とする。比べる起点は `develop` の 605ece4a で、token の換算は `docs/07-記録/context-size.md` の測り方による。結果は `context-size.md` と、tasks の結果に書く。
- `AGENTS.md` の「最初に読むもの」の項目 5 は、言い回しの規則（`writing.md`）と、置き場所・参照・spec の書き方（`doc-structure.md`）を場面で分けて指す。

## 目的と完了条件

### DS-334-1 毎回読む文書を短くする（D / P1）

- **DC-334-1** `workflow.md` に毎回読む部分だけがあり、場面ごとの部分は `issues.md` と `merging.md` にある（中身は決定の表のとおり）。
- **DC-334-2** `workflow.md`・constitution・`AGENTS.md` の大きさが、決定の目安（15,000・10,000・8,000 バイト）以下である。超えたときは、超えた理由が tasks の結果にある。
- **DC-334-3** constitution の文書の地図が、1 行が 1 つのフォルダーの表 1 つで、設計の文書と `licenses.md` と同じ表がない。各フォルダーに文書の一覧の `README.md` がある。
- **DC-334-4** `AGENTS.md` に、skill の表と、場面ごとの案内の表がない。表を前提にした規則が、skill に残っていない。
- **DC-334-5** skill と agent の description の合計が整理の前（4,145 バイト）より短く、各 description が何をするかといつ使うかを書いている。
- **DC-334-6** 整理の前と後の、毎回読む 3 つの文書と description の大きさ（バイトと token の換算）が、`docs/07-記録/context-size.md` にある。

### DS-334-2 文書の書き方を、読む場面で分ける（D / P1）

- **DC-334-7** `writing.md` に言い回しの規則が、`doc-structure.md` に置き場所・参照・spec と plan の書き方がある。
- **DC-334-8** `writing.md` に、`ja-text.json` の語の対の転記と「載せない規則」の節がない。

### DS-334-3 規則の重複を減らす（D / P1）

- **DC-334-9** 正本の表にある 4 つの規則が、正本の 1 か所にだけ書かれ、他の文書は見出しの参照で指す。
- **DC-334-10** 用語集と `licenses.md` に、コード・設定から読める内容の転記がない。
- **DC-334-11** `docs/07-記録/` の 3 つに、空の節と、移した先を指すだけの節がない。README の MVP の進捗が 1 行の案内である。

### DS-334-4 文書の長さの基準と雛形を決める（D / P1）

- **DC-334-12** `doc-structure.md` に、文書の長さの基準（大きさの目安、冒頭、表のセル、正本、理由）がある。
- **DC-334-13** `doc-structure.md` に、文書の種類ごとの雛形がある。
- **DC-334-14** この spec で変えた文書の冒頭が、2 文以内である。

### DS-334-5 検査と校閲を通す（D / P1）

- **DC-334-15** `node scripts/ci/doc-refs.mjs` と `scripts/ci/check-all.sh` が成功する。
- **DC-334-16** 変えたファイルの変えた行の `ja-text` の warning が 0 である（`node scripts/ci/ja-text.mjs --changed`）。
- **DC-334-17** 変えた日本語が校閲（検出役、確認役の順）を通っている。本文は、日本語だけの文脈のサブエージェントが書いている。
- **DC-334-18** 作業ツリーで起動した `claude -p` で、変えた skill と agent の description が読まれる（skill `sessions` の「作業ツリーのセッションが読むもの」）。
- **DC-334-19** ① 概要（`docs/01-概要/`）と ② 設計（`docs/02-設計/`）の `README.md` を変えるので、push の後に、内蔵のブラウザーで GitHub の表示を確認し、結果を issue 担当のセッションに報告する。スクリーンショットは、`334-shorten-docs` から `develop` への pull request で issue 担当のセッションが送る。

## 変える範囲

| まとまり | ファイル |
|----------|----------|
| 規則 | `docs/05-開発規則/workflow.md`、`issues.md`（新規）、`merging.md`（新規）、`writing.md`、`doc-structure.md`（新規）、`subagents.md`、`resources.md`、`review.md`、`notation.md`、`glossary.md`、`licenses.md` |
| 大綱 | `docs/constitution.md` |
| フォルダーの一覧（新規） | `docs/02-設計/README.md`、`docs/03-機能仕様/README.md`、`docs/05-開発規則/README.md`、`docs/06-手順書/README.md`、`docs/07-記録/README.md`、`docs/08-spec/README.md` |
| フォルダーの一覧（既存） | `docs/01-概要/README.md`、`docs/04-決定記録/README.md` |
| 記録 | `docs/07-記録/context-size.md`、`subagents-results.md`、`ci-results.md` |
| エージェントの手順 | `AGENTS.md`、`README.md` |
| skill と agent | `.claude/skills/*/SKILL.md` の description、`writing-for-agents`・`handoff`・`retro` の日本語の追記、skill `local-dev` の「手元の hook の設定」、`.claude/agents/*.md` の description |
| 参照の修正 | 分けた・移した節を指す文書・skill・雛形・script のコメントとテスト（`doc-refs` が示すもの） |
| spec | `docs/08-spec/334-doc-length/spec.md`（アーカイブで `docs/08-spec/archive/1to10/334-doc-length/` に移す） |

## 非機能要件

- **DN-334-1** 消費者・運営者に見える振る舞いを変えない。script と CI の動作を変えない。ただし、コメント・メッセージ・テストにある文書の名前とパスは修正してよい。
- **DN-334-2** 規則の意味を変えない。移す・縮めるときは規則の中身を保ち、転記・経緯・言い換えの削除だけを行う。
- **DN-334-3** アーカイブした spec を変えない。
- **DN-334-4** 英語の原文の skill（writing-for-agents、retro、handoff）の本文を変えない。日本語の追記は修正してよい。
- **DN-334-5** `.claude/` の変更は作業ツリーで行い、メインの作業ツリーを変えない。
- **DN-334-6** 大きさの上限の CI の検査を加えない。

## 依存

| 対象 | 関係 |
|------|------|
| #335、#336 | この spec の基準を使う。この spec のマージの後に、並行で始める |
| 作業ブランチ `334-shorten-docs` | この spec の pull request の base。`develop` の 605ece4a から作った。`develop` へのマージは issue 担当のセッションが行う |

## Non-goals

| 項目 | 理由 |
|------|------|
| 書き方の規則（backend-coding など）・手順書・skill の本文の圧縮 | #335 で扱う |
| 機能仕様・設計・決定記録・OpenAPI の圧縮 | #336 で扱う |
| 大きさの上限の CI の検査 | 利用者が定期的にレビューするため（利用者の決定、2026-11-03） |
| この spec で変えない文書を基準に合わせること | #335・#336 で扱う |
| `subagents.md` などの、読む場面で分けること | 分けるのは場面が明らかに違う文書だけ（利用者の決定） |

## Open questions

なし。

## tasks

実装は実装役（汎用）に 1 タスクずつ任せる。文書の本文は、日本語の要点から、日本語だけの文脈のサブエージェント（汎用）が書く。この spec は、spec 担当が作業中に読む文書を変えるので、分けた後の文書を作業ツリーでもう一度読んでから次のタスクに進む。移した節を指す参照は、そのタスクで修正し、`doc-refs` が通る状態で終える。pull request の base は作業ブランチ `334-shorten-docs` にする。

- [x] **T334-01-01** `doc-structure.md` を作り、`writing.md` から置き場所・参照・spec と plan の書き方を移す。文書の長さの基準と雛形の節を書く。`writing.md` の転記と「載せない規則」を削る。`doc-refs` が何を確認するかの説明を削る。冒頭の出典の文に、散文の書籍向けの規則は載せないことを加える（DC-334-7、DC-334-8、DC-334-12、DC-334-13）。
  - 依存：なし
  - 範囲：`docs/05-開発規則/writing.md`、`docs/05-開発規則/doc-structure.md`
  - 確認方法：移す前と後の文を並べて読む（`git show`）、`node scripts/ci/doc-refs.mjs`、移す前と後の見出しの一覧（`grep '^#'`）を比べ、見出しの名前が変わっていない
- [x] **T334-01-02** `workflow.md` を分け、`issues.md` と `merging.md` を作る。「文書の置き場所」の表を `doc-structure.md` に移す。issue のテンプレートの項目の表を削除し、`.github/ISSUE_TEMPLATE/` を指す（DC-334-1、DC-334-2）。
  - 依存：T334-01-01
  - 範囲：`docs/05-開発規則/workflow.md`、`issues.md`、`merging.md`、`doc-structure.md`
  - 確認方法：移す前と後の文を並べて読む、`wc -c`、移す前と後の見出しの一覧（`grep '^#'`）を比べ、見出しの名前が変わっていない
- [ ] **T334-01-03** constitution を縮め、フォルダーの `README.md` を作る。設計の文書と `licenses.md` と同じ表を削除し、技術スタックのバージョンの記述は設計の文書を指し、「リポジトリ構成」の図を縮める（DC-334-2、DC-334-3）。
  - 依存：T334-01-01、T334-01-02（新しい文書を README に載せ、`doc-structure.md` を同じく変えるため）
  - 範囲：`docs/constitution.md`、各フォルダーの `README.md`、`writing.md`・`doc-structure.md`・skill `docs`・`scripts/ci/docs-folders.mjs` のコメントの「地図に書く」の記述
  - 確認方法：移す前の地図の全ての文書が、どれかの `README.md` にある、`wc -c`
- [ ] **T334-01-04** `AGENTS.md` を縮める。skill の表を前提にした規則を修正し、hook の書き先の段落を skill `local-dev` に移す。「最初に読むもの」の項目 5 を、`writing.md` と `doc-structure.md` に分けて指す（DC-334-2、DC-334-4）。
  - 依存：T334-01-02（`AGENTS.md` が指す workflow.md の節が決まるため）
  - 範囲：`AGENTS.md`、skill `writing-for-agents`・`handoff`・`retro` の日本語の追記、skill `local-dev`
  - 確認方法：`wc -c`、`git grep -n "skill の表"`
- [ ] **T334-01-05** skill と agent の description を縮める（DC-334-5）。
  - 依存：T334-01-03、T334-01-04、T334-01-06（同じ skill の SKILL.md を変えるため）
  - 範囲：対象の skill 6 つの SKILL.md と、`.claude/agents/` の 5 つの frontmatter
  - 確認方法：description のバイト数を node で数える、各 description を読み、何をするかといつ使うかが書いてある
- [ ] **T334-01-06** 進め方の規則の文書の重複と転記を減らす（DC-334-9、DC-334-10）。
  - 依存：T334-01-01、T334-01-02
  - 範囲：`writing.md`、`workflow.md`、`subagents.md`、`resources.md`、`review.md`、`notation.md`、`glossary.md`、`licenses.md`、skill `sessions`
  - 確認方法：正本の表にある 4 つの規則を `git grep` で検索し、正本の外に言い換えがないことを読む、用語集と `licenses.md` で、決定に挙げた転記（テストの補助のパス、jest-dom の読み込み方、`policy.json` と `build.gradle.kts` の設定の説明）がないことを読む
- [ ] **T334-01-07** 記録と README を縮める（DC-334-11）。
  - 依存：なし
  - 範囲：`docs/07-記録/` の 3 つ、`README.md`
  - 確認方法：文書を読む
- [ ] **T334-01-08** 参照を修正し、検査を通し、大きさを記録する。作業ツリーの `claude -p` で description を確認する（DC-334-6、DC-334-14、DC-334-15、DC-334-16、DC-334-18）。
  - 依存：T334-01-01〜07
  - 範囲：変えたファイルと、参照する文書・skill・雛形・script のコメントとテスト、`docs/07-記録/context-size.md`
  - 確認方法：`node scripts/ci/doc-refs.mjs`、`scripts/ci/check-all.sh`、`node scripts/ci/ja-text.mjs --changed`、`wc -c`、`claude -p`、この spec で変えた文書の冒頭（最初の `##` まで）を読み、2 文以内である
- [ ] **T334-01-09** spec の最後のレビュー。指摘を修正し、draft の pull request を作る（DC-334-17 の校閲を含む、DC-334-19）。
  - 依存：T334-01-08
  - 確認方法：校閲（検出役、確認役の順）を差分の追加行に行い、修正の後に `doc-refs`・`check-all.sh`・`ja-text --changed` を再度実行する。push の後に GitHub の表示を確認する（DC-334-19）
- [ ] **T334-01-10** pull request の前のレビュー。指摘を修正し、draft を外す。
  - 依存：T334-01-09
- [ ] **T334-01-11** spec をアーカイブに移す。
  - 依存：T334-01-10
