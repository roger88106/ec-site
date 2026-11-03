# Spec 335：書き方の規則・手順書・skill の字数を減らし、CI の検査などを場面ごとに分ける

種類：整理　規模：M　ステータス：確定

## 背景と目的

整理の第 2 フェーズの最後の作業を、3 つの spec（#334・#335・#336）に分けた。#334 は毎回読む文書と、文書の長さの基準と雛形（[文書の構成](../../05-開発規則/doc-structure.md)の「文書の長さの基準」「文書の種類ごとの雛形」）を扱った。この spec（#335）は、作業の種類ごとに読む文書（書き方の規則、手順書、skill）を扱う。#336 は機能仕様・設計・決定記録・OpenAPI を扱う。

文書はエージェントが読むので、字数がそのまま token の費用になる。減らす量の目標は決めない（利用者の決定、2026-11-03）。

棚卸し（2026-11-03）で、次のことが分かった。

- 書き方の規則と手順書の 10 ファイルは、合わせて 217,409 バイトである。大きいのは `backend-coding.md` の 40,470、`test-writing.md` の 36,776、`setup.md` の 32,711、`ci.md` の 29,977 である（`wc -c`。作業ブランチ `334-shorten-docs` の c02dc7d6 で測った）。
- skill は、`local-dev` の SKILL.md が 30,193 バイト、`sessions` の SKILL.md が 20,705 バイトである。実装のセッションと issue 担当のセッションが、ほぼ毎回読む。
- commit の前や CI が失敗したときに [CI の検査](../../06-手順書/ci.md)を読んでも、必要なのは手元での実行とジョブの表（約 4KB）だけである（利用者の評価、2026-11-03）。
- 冗長な書き方は 3 種類ある。
  - script・設定・コードの転記（`ci.md` と `ci.yml`・`check-all.sh`、`local-dev` と `scripts/dev/` の script の冒頭のコメント）
  - 同じ基準の三重の記述（カバレッジと mutation score の基準が、`ci.md`、`running-tests.md`、`test-writing.md` にある）
  - 規則の文書の間の重複（`backend-coding.md` と `api-conventions.md`）

消費者・運営者に見える振る舞いも、コードの動作も変えない。追跡の issue は #335。

規模は、issue の本文の提案の L でなく M にする。理由：#334 と同じく API とデータベースの構造を変えない文書の整理で、spec.md の中の tasks で足りるため（issue 担当の判断、利用者の委任、2026-11-04）。

## スコープ

| 含める | 含めない |
|--------|----------|
| 1. `ci.md` を場面ごとに分ける | 毎回読む文書・進め方の規則の文書・skill と agent の description（#334） |
| 2. `test-writing.md` を共通・バックエンド・フロントに分ける | 機能仕様・設計・決定記録・OpenAPI（#336）。ただし Open questions 1 のリンクの行を除く |
| 3. カバレッジと mutation score の基準の記述を 1 か所にする | 英語の原文の skill（`writing-for-agents`、`retro`、`handoff`）の本文 |
| 4. 書き方の規則（`backend-coding.md`、`frontend-coding.md`、`comments.md`、`api-conventions.md`）を縮める | 大きさの上限の CI の検査（利用者の決定） |
| 5. 手順書（`setup.md`、`running-tests.md`、`branches.md`、`environment.md`）を縮める | |
| 6. skill `local-dev` と `sessions` を分け、転記を削る | |
| 7. 他の skill と agent の定義の本文を縮める（`review` の SKILL.md・`docs-review.md`・`prompts.md`、`sessions` の `rounds.md`、`docs`、`new-screen`、`zh-mixing`、`agent-retro`、`ja-proofreader`、`ja-proof-verifier`） | |

決定：

- 分けるのは `ci.md`、`test-writing.md`、skill `local-dev`、skill `sessions` だけにする。読む場面が明らかに違うため（利用者の決定、2026-11-03・11-04）。他の文書（`backend-coding.md`、`frontend-coding.md`、`comments.md`、`api-conventions.md`、`setup.md` などの手順書、他の skill）は分けずに縮める。
- 分けた・移した節の見出しの名前は変えない。理由：参照の修正が文書の名前（リンク先）の置き換えだけで済み、`doc-refs` が漏れを見つけるため（#334 と同じ）。節が 1 つの文書になったときは、その見出しの名前を文書の名前にする。
- `ci.md` は次の 4 つに分ける。

  | ファイル | 文書の名前 | 中身 | 読む場面 |
  |----------|------------|------|----------|
  | `docs/06-手順書/ci.md` | CI の検査（今のまま） | 手元での実行、CI のジョブの短い表（1 つのセルに 1 文）、失敗したときに読む順 | commit の前、CI が失敗したとき |
  | `docs/06-手順書/ci-checks.md`（新規） | CI の検査の script | `check-all.sh` の判定（1〜2 文）、検査の表と注、Mermaid の図の描画の検査（何をし、どこで実行するかを数行。中身は `mermaid-check.mjs` の冒頭のコメントを指す） | 検査が失敗したとき |
  | `docs/06-手順書/ci-adding-checks.md`（新規） | CI の検査の追加 | 検査を加える手順、補助の script の置き場所 | 検査を加える・修正するとき |
  | `docs/06-手順書/ci-backend-jobs.md`（新規） | バックエンドの CI のジョブ | バックエンドのジョブの構成（必須の検査とスキップ、統合テストの分け方、統合テストのフォークの数）、`docs.yml` を分ける理由、変えても `ci.yml` が実行されないファイル（`ci.yml` の `on.push.paths` のコメントを指す 1 行） | `.github/workflows/` を変えるとき、ジョブの時間を調べるとき |

  - 「GitHub のセキュリティの機能」の節は、[ブランチとリリース](../../06-手順書/branches.md)に移す。理由：リポジトリの設定と Dependabot の節が、既に `branches.md` にあるため。
  - 「カバレッジと mutation score の基準の検査」の節は、[テストの実行方法](../../06-手順書/running-tests.md)の同じ名前の節に統合する（次の決定）。
  - 検査 `ci-docs`（`scripts/ci/ci-docs.mjs`）が読む文書を `ci-checks.md` にし、表を探す節を、節の番号でなく見出しの名前で決める。テストを先に修正してレッドを確認する。
- `test-writing.md` は次の 3 つに分ける。

  | ファイル | 文書の名前 | 中身 | 読む場面 |
  |----------|------------|------|----------|
  | `docs/05-開発規則/test-writing.md` | テストの書き方（今のまま） | テストの種類と基準、テスト名、待ち方 | テストを書く・レビューするとき（毎回） |
  | `docs/05-開発規則/test-writing-backend.md`（新規） | バックエンドのテストの書き方 | 今の「バックエンド」の節 | バックエンドのテストを書く・レビューするとき |
  | `docs/05-開発規則/test-writing-frontend.md`（新規） | フロントのテストの書き方 | 今の「フロント」の節 | 画面と E2E のテストを書く・レビューするとき |

- カバレッジと mutation score の基準は、[テストの実行方法](../../06-手順書/running-tests.md)の「カバレッジと mutation score の基準の検査」の 1 か所に書く。書く内容は、対象、基準の値の意味、許容の幅、失敗したときの手順、基準を変える手順である。
  `ci.md` 系（`ci.md` と、分けた 3 つの文書）と `test-writing.md` は、見出しの参照で指す。
- skill `local-dev` と `sessions` は、一部の場面でだけ読む節を同じフォルダーの別のファイルに移し、SKILL.md には毎回の手順と、移した先へのリンクを残す。

  | skill | 移す先のファイル | 移す節 |
  |-------|------------------|--------|
  | `local-dev` | `.claude/skills/local-dev/hooks.md` | 手元の hook の設定（hook の JSON を含む） |
  | `local-dev` | `.claude/skills/local-dev/screen-data.md` | 決済（mock と Stripe）、機能紹介のページのスクリーンショット、ローカルの画面のデータ |
  | `sessions` | `.claude/skills/sessions/session-reads.md` | 作業ツリーのセッションが読むもの |
  | `sessions` | `.claude/skills/sessions/handover.md` | 予備のセッションに担当を渡すメッセージ |

- 縮めるときは、次を削る。規則の中身は保つ。
  - script・設定・コードから読める内容の転記。そのファイルを指す。例：
    - `ci.md` に写した `ci.yml` の paths と、`check-all.sh` の冒頭
    - `test-writing.md` にある、テストの補助だけが呼んでよい手順の一覧（正本は `test-helpers.mjs` の `HELPER_STEPS`）
    - `setup.md` の `slot.mjs` のコマンドの振る舞い
    - `running-tests.md` の E2E のロックの説明（正本は `e2e/scripts/run.ts` の冒頭）
    - `backend-coding.md` のマイグレーションの検出の一覧（正本は `migrations.mjs`）
    - `local-dev` の `break-check.mjs` と `check-frontend.sh` の冒頭の写し
    - `review` の SKILL.md に写した `stats.cjs` の集計の規則
  - 同じ規則の二重・三重の記述。次の正本の表のとおり正本を 1 か所にして、他は見出しの参照で指す。

    | 規則 | 正本 | 今重複している文書 |
    |------|------|----------------|
    | API の契約の形（更新の競合など） | `api-conventions.md` | `backend-coding.md` |
    | import の並びと Checkstyle の修正方法 | `backend-coding.md` の「書式」 | `running-tests.md`、`setup.md`、`ci.md` |
    | commit の hook | `setup.md` の「起動」 | `ci.md`、skill `local-dev` |
    | マイグレーションを起動時に適用しない | `backend-coding.md` | `environment.md` |
    | 開始の条件・資源の配分の規則 | `resources.md`、`workflow.md` | skill `sessions` |

  - 表のセルに詰めた理由・例外・振る舞い（1 つのセルは 300 バイトまで。[文書の構成](../../05-開発規則/doc-structure.md)の「文書の長さの基準」）。
  - 1 つの例外を表・本文・注で繰り返す所（`backend-coding.md` の `CurrentMemberIds`・`MeController` など）。
  - 設定の既定と変えた後を両方書く所（`setup.md` の IntelliJ の import の並び）。変える操作だけを書く。
  - 経緯・実測・日付。後で調べる価値があれば `docs/07-記録/`（`ci-results.md` など）に移し、なければ削る（利用者の決定、2026-11-04）。
  - tools の設定で決まる指示（`ja-proofreader` と `ja-proof-verifier` の「Edit・Write・Bash は使えない」）と、同じ文書の中の繰り返し。
  - 済んだ spec を前提にした記述（`agent-retro` の spec 247）。
- 次の検査・テストが読む形は変えない。見出し・パス・表の形を保つ。

  | 読むもの | 保つもの |
  |----------|----------|
  | `ModuleStructureTest`、`ci.yml`、`build.gradle.kts` | `backend-coding.md` のパスと、見出し「パッケージの大きさ」の表 |
  | `env-defaults.mjs` | `environment.md` のパスと、既定値の表の形 |
  | `local-ports.mjs` | `setup.md`・`running-tests.md` の `:ポート` の書き方と、スロットの表の行 |
  | skill `review` の `checklist.cjs` | `docs/05-開発規則/` の「確認方法：」の行の形 |

- 分けたファイルの冒頭は、何の文書かといつ読むかを 2 文以内で書く（[文書の構成](../../05-開発規則/doc-structure.md)の「文書の長さの基準」）。参照元は、場面に合う文書を指す。参照元は、constitution、`AGENTS.md`、skill、各フォルダーの `README.md`、script のコメントである。新しい文書は、そのフォルダーの `README.md` の表に加える。
- 測り方は `wc -c` のバイト数とする。比べる起点は作業ブランチ `334-shorten-docs` の c02dc7d6。前と後の大きさは、tasks の結果に書く。`docs/05-開発規則/` の文書で目安（15,000 バイト）を超えて残るものは、理由を[毎回読む文書と固定の文脈の大きさ](../../07-記録/context-size.md)の「目安を超えたもの」に書く。
- 大きさの上限の CI の検査は加えない（利用者の決定、2026-11-03）。

## 目的と完了条件

### DS-335-1 読む場面ごとに文書を分ける（D / P1）

- **DC-335-1** `ci.md` の中身が、決定の表の 4 つの文書に分かれている。GitHub のセキュリティの機能が `branches.md` にある。
- **DC-335-2** `test-writing.md` の中身が、決定の表の 3 つの文書に分かれている。
- **DC-335-3** skill `local-dev` と `sessions` の、決定の表の節が、各フォルダーの別のファイルにあり、SKILL.md から 1 行のリンクで指している。
- **DC-335-4** 分けたファイルの冒頭が、何の文書かといつ読むかの 2 文以内である。参照元（constitution、`AGENTS.md`、skill、フォルダーの `README.md`、script のコメント）が場面に合う文書を指している。
- **DC-335-5** `ci-docs` が `ci-checks.md` の表を読み、表と検査の対応を確認している。

### DS-335-2 規則と基準の正本を 1 か所にする（D / P1）

- **DC-335-6** カバレッジと mutation score の基準が `running-tests.md` の 1 か所にだけ書かれ、`ci.md` 系と `test-writing.md` は見出しの参照で指している。
- **DC-335-7** 決定の「正本の表」に挙げた規則が、正本の 1 か所にだけ書かれ、他の文書は見出しの参照で指している。

### DS-335-3 書き方の規則・手順書・skill を縮める（D / P1）

- **DC-335-8** 対象の文書に、決定に挙げた転記・経緯・繰り返しがない。
- **DC-335-9** 整理の前と後の、対象のファイルの大きさが tasks の結果にある。`docs/05-開発規則/` で目安を超えて残る文書の理由が、`context-size.md` にある。
- **DC-335-10** 英語の原文の skill の本文と、skill と agent の description が変わっていない。

### DS-335-4 検査と校閲を通す（D / P1）

- **DC-335-11** `node scripts/ci/doc-refs.mjs`、`scripts/ci/check-all.sh`、`./gradlew check` が成功する。
- **DC-335-12** 変えたファイルの変えた行の `ja-text` の warning が 0 である（`node scripts/ci/ja-text.mjs --changed`）。
- **DC-335-13** 変えた日本語が校閲（検出役、確認役の順）を通っている。本文は、日本語だけの文脈のサブエージェントが書いている。
- **DC-335-14** ② 設計（`docs/02-設計/`）を変えたときは、push の後に GitHub の表示を確認し、結果を issue 担当のセッションに報告する。

## 変える範囲

| まとまり | ファイル |
|----------|----------|
| 書き方の規則 | `docs/05-開発規則/backend-coding.md`、`frontend-coding.md`、`comments.md`、`api-conventions.md`、`test-writing.md`、`test-writing-backend.md`（新規）、`test-writing-frontend.md`（新規）、`README.md` |
| 手順書 | `docs/06-手順書/ci.md`、`ci-checks.md`（新規）、`ci-adding-checks.md`（新規）、`ci-backend-jobs.md`（新規）、`setup.md`、`environment.md`、`running-tests.md`、`branches.md`、`README.md` |
| skill `local-dev` | `.claude/skills/local-dev/`（SKILL.md、`hooks.md` と `screen-data.md` は新規） |
| skill `sessions` | `.claude/skills/sessions/`（SKILL.md、`rounds.md`、`session-reads.md` と `handover.md` は新規） |
| 他の skill | `.claude/skills/review/`（SKILL.md、`docs-review.md`、`prompts.md`）、`.claude/skills/docs/SKILL.md`、`.claude/skills/new-screen/SKILL.md`、`.claude/skills/zh-mixing/SKILL.md`、`.claude/skills/agent-retro/SKILL.md` |
| agent | `.claude/agents/ja-proofreader.md`、`.claude/agents/ja-proof-verifier.md`（本文） |
| 検査 | `scripts/ci/ci-docs.mjs` と `ci-docs.test.mjs` |
| 記録 | `docs/07-記録/context-size.md`、`docs/07-記録/ci-results.md` など、細目を移す先 |
| 参照の修正 | constitution、`AGENTS.md`、`docs/05-開発規則/workflow.md`、`glossary.md`、`.github/workflows/` のコメント、script のコメントとテスト、Open questions 1 のリンクの行（`doc-refs` が示すもの） |
| spec | `docs/08-spec/335-shorten-guides/spec.md`（アーカイブで `docs/08-spec/archive/1to10/335-shorten-guides/` に移す） |

## 非機能要件

- **DN-335-1** 消費者・運営者に見える振る舞いを変えない。script・CI・テストの動作を変えない。例外は `ci-docs` の読む文書と表を探す節（決定のとおり）。コメント・メッセージ・テストにある文書の名前とパスは修正してよい。
- **DN-335-2** 規則の意味を変えない。移す・縮めるときは規則の中身を保ち、転記・経緯・繰り返しの削除だけを行う。
- **DN-335-3** アーカイブした spec を変えない。
- **DN-335-4** 英語の原文の skill（`writing-for-agents`、`retro`、`handoff`）の本文と、skill と agent の description（#334 で縮めた）を変えない。
- **DN-335-5** #336 が変える `docs/02-設計/`、`docs/03-機能仕様/`、`api/`、生成した型を変えない。例外は Open questions 1 で決めるリンクの行だけ。
- **DN-335-6** `.claude/` の変更は作業ツリーで行い、メインの作業ツリーを変えない。
- **DN-335-7** 大きさの上限の CI の検査を加えない。

## 依存

| 対象 | 関係 |
|------|------|
| #334 | 文書の長さの基準と雛形を決めた。マージ済み（作業ブランチ `334-shorten-docs`） |
| #336 | 並行で進める。変えるフォルダーが分かれる |
| #336 と重なるリンクの行 | `docs/02-設計/` と `docs/03-機能仕様/` から `test-writing.md` と `ci.md` を指すリンクの行（Open questions 1） |
| #336 と重なる可能性がある文書 | constitution、`AGENTS.md`、フォルダーの `README.md`、`context-size.md`。変える前に issue 担当のセッションに知らせる |
| 作業ブランチ `334-shorten-docs` | pull request の base。マージは issue 担当のセッションが行う |

## Non-goals

| 項目 | 理由 |
|------|------|
| 毎回読む文書・進め方の規則・description | #334 で扱った |
| 機能仕様・設計・決定記録・OpenAPI | #336 で扱う |
| `ci.md`・`test-writing.md`・`local-dev`・`sessions` 以外の文書を分けること（`backend-coding.md` の「設定の値」、`setup.md` のスロット、`new-screen` の店舗画面と管理画面、`docs-review.md` の手順 4 など） | 分けるのは場面が明らかに違う文書だけ（利用者の決定） |
| 英語の原文の skill の本文 | 原文の言語のまま置く決定のため |
| 大きさの上限の CI の検査 | 利用者が定期的にレビューするため（利用者の決定、2026-11-03） |

## Open questions

なし。次の 1 つは、推奨の案 A に決めた（issue 担当の判断、利用者の委任、2026-11-04）。#336 の spec 担当には、issue 担当のセッションが知らせる。

1. `docs/02-設計/` と `docs/03-機能仕様/` にある、分ける文書の節へのリンクの扱い。対象は 2 組ある。`test-writing.md` の節へのリンクは、`docs/02-設計/apps/admin.md`（2 か所）、`apps/store.md`、`frontend-architecture.md` にある。`ci.md` の検査の表へのリンクは、`docs/03-機能仕様/acceptance-criteria.md`、`region.md` にある。
   - 案 A（推奨）：この spec がリンクの行だけ（リンク先のファイル名）を修正し、#336 の spec 担当に知らせる。衝突は、後にマージする spec の担当が解消する。リンクの行をこの spec で修正する理由：`test-writing.md` の節を移すと、そのリンクが切れて、この spec の作業ブランチで `doc-refs` が失敗するため。`ci.md` へのリンクは切れないが、リンク先に検査の表がなくなる。
   - 案 B：この spec では修正せず、#336 が修正する。この spec のマージは #336 の後にする。

## tasks

実装は実装役（汎用）に 1 タスクずつ任せる。文書の本文は日本語の要点から、日本語だけの文脈のサブエージェント（汎用）が書く。移した節を指す参照は、そのタスクで修正し、`doc-refs` が通る状態で終える。pull request の base は作業ブランチ `334-shorten-docs` にする。

- [ ] **T335-01-01** `ci.md` を 4 つに分け、GitHub のセキュリティの機能を `branches.md` に移す。`ci-docs` のテストを `ci-checks.md` に合わせて修正し、レッドを確認してから script を修正する（DC-335-1、DC-335-5）。
  - 依存：なし
  - 範囲：`docs/06-手順書/ci.md`、`ci-checks.md`、`ci-adding-checks.md`、`ci-backend-jobs.md`、`branches.md`、`README.md`、`scripts/ci/ci-docs.mjs`、`ci-docs.test.mjs`
  - 確認方法：移す前と後の文を並べて読む（`git show`）、移す前と後の見出しの一覧（`grep '^#'`）を比べる、`node --test scripts/ci/ci-docs.test.mjs`、`node scripts/ci/ci-docs.mjs`、`node scripts/ci/doc-refs.mjs`
- [ ] **T335-01-02** カバレッジと mutation score の基準を `running-tests.md` に 1 つにまとめ、`test-writing.md` を 3 つに分ける（DC-335-2、DC-335-6）。
  - 依存：T335-01-01（`ci.md` の基準の節を移すため）
  - 範囲：`docs/05-開発規則/test-writing.md`、`test-writing-backend.md`、`test-writing-frontend.md`、`README.md`、`docs/06-手順書/running-tests.md`、`ci.md` 系
  - 確認方法：移す前と後の文を並べて読む、見出しの一覧を比べる、`node scripts/ci/doc-refs.mjs`
- [ ] **T335-01-03** 書き方の規則を縮める（`backend-coding.md`、`frontend-coding.md`、`comments.md`、`api-conventions.md` と、分けた `test-writing` の 3 つ）（DC-335-7、DC-335-8）。
  - 依存：T335-01-02
  - 範囲：`docs/05-開発規則/backend-coding.md`、`frontend-coding.md`、`comments.md`、`api-conventions.md`、`test-writing.md`、`test-writing-backend.md`、`test-writing-frontend.md`
  - 確認方法：前と後の文を並べて読み、規則が残っていることを確認する、`node .claude/skills/review/checklist.cjs` が成功する、`./gradlew check`（`ModuleStructureTest`）
- [ ] **T335-01-04** 手順書を縮める（`setup.md`、`running-tests.md`、`branches.md`、`environment.md`、`ci.md` 系）（DC-335-7、DC-335-8）。
  - 依存：T335-01-01、T335-01-02
  - 範囲：`docs/06-手順書/setup.md`、`running-tests.md`、`branches.md`、`environment.md`、`ci.md`、`ci-checks.md`、`ci-adding-checks.md`、`ci-backend-jobs.md`
  - 確認方法：前と後の文を並べて読む、`node scripts/ci/env-defaults.mjs`、`node scripts/ci/local-ports.mjs`
- [ ] **T335-01-05** skill `local-dev` を分けて縮める（DC-335-3、DC-335-8）。
  - 依存：T335-01-01（`ci-checks.md` を指すため）
  - 範囲：`.claude/skills/local-dev/`、`hooks.md` を指す文書（`AGENTS.md`、skill `zh-mixing` など）
  - 確認方法：移す前と後の文を並べて読む、SKILL.md から移した先へのリンクを読む、`node scripts/ci/doc-refs.mjs`
- [ ] **T335-01-06** skill `sessions` を分けて縮める（DC-335-3、DC-335-8）。
  - 依存：なし
  - 範囲：`.claude/skills/sessions/`、`session-reads.md` を指す文書（`AGENTS.md`、`subagents.md`、記録など）
  - 確認方法：移す前と後の文を並べて読む、SKILL.md から移した先へのリンクを読む、`node scripts/ci/doc-refs.mjs`
- [ ] **T335-01-07** 他の skill と agent の定義の本文を縮める（DC-335-8、DC-335-10）。
  - 依存：T335-01-01（skill `docs` の検査の説明が `ci-checks.md` を指すため）
  - 範囲：skill `review`、`docs`、`new-screen`、`zh-mixing`、`agent-retro`、`.claude/agents/ja-proofreader.md`、`ja-proof-verifier.md`
  - 確認方法：前と後の文を並べて読む、frontmatter の description が変わっていない（`git diff`）
- [ ] **T335-01-08** 参照を修正し、検査を通し、大きさを記録する（DC-335-4、DC-335-9、DC-335-11、DC-335-12）。
  - 依存：T335-01-01〜07
  - 範囲：変えたファイルを指す文書・skill・script のコメントとテスト（`doc-refs` が示すもの）、`docs/07-記録/context-size.md`
  - 確認方法：`node scripts/ci/doc-refs.mjs`、`scripts/ci/check-all.sh`、`./gradlew check`、`node scripts/ci/ja-text.mjs --changed`、`wc -c`、分けたファイルの冒頭を読む
- [ ] **T335-01-09** spec の最後のレビュー。指摘を修正し、draft の pull request を作る（DC-335-13、DC-335-14）。
  - 依存：T335-01-08
  - 確認方法：校閲（検出役、確認役の順）を差分の追加行に行い、修正の後に `doc-refs`・`check-all.sh`・`ja-text --changed` を再度実行する
- [ ] **T335-01-10** pull request の前のレビュー。指摘を修正し、draft を外す。
  - 依存：T335-01-09
- [ ] **T335-01-11** spec をアーカイブに移す。
  - 依存：T335-01-10
