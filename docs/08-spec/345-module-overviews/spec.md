# Spec 345：注文以外のモジュールの概要の文書を作る

種類：整理　規模：M　ステータス：確定

## 背景と目的

`docs/01-概要/` にあるモジュールの流れの文書は `order.md` だけである。出典は、pull request #329 の「起票する issue の案」と、spec 326 の Non-goals である。この spec で、3 つの流れ（商品の登録と公開、会員登録とログイン、運営者のレポート）の文書と、画像を撮る script を加える。消費者・運営者に見える振る舞いは変えない。追跡の issue は #345。

## スコープ

| 含める | 含めない |
|--------|----------|
| 1. 画面のスクリーンショットを撮る script（3 つの流れ） | カート、在庫、決済など、3 つ以外のモジュールの流れの文書 |
| 2. 商品の登録と公開の流れの文書 | `order.md` とその画像の変更 |
| 3. 会員登録とログインの流れの文書 | 画面とバックエンドの変更 |
| 4. レポートの流れの文書 | |
| 5. 一覧（`docs/01-概要/README.md`）と全体像（`docs/01-概要/system.md`）の「詳しく知るには」からのリンク | |

決定：

- 文書は次の表のとおり。

  | ファイル | 文書の名前 | 画像の置き場所 | 撮影の script |
  |----------|------------|----------------|---------------|
  | `docs/01-概要/catalog.md` | 商品の登録と公開の流れ | `docs/01-概要/img/catalog/` | `e2e/docs/catalog-flow.spec.ts` |
  | `docs/01-概要/member.md` | 会員登録とログインの流れ | `docs/01-概要/img/member/` | `e2e/docs/member-flow.spec.ts` |
  | `docs/01-概要/reporting.md` | レポートの流れ | `docs/01-概要/img/reporting/` | `e2e/docs/reporting-flow.spec.ts` |

  理由：ファイル名は `docs/02-設計/` と `docs/03-機能仕様/` のモジュールの文書のファイル名に合わせ、script は `e2e/docs/order-flow.spec.ts` と同じく文書ごとに 1 つにするため。
- 節は雛形 `docs/01-概要/template.md` の骨組み（登場する人、業務の流れ、画面の操作の流れ、詳しく知るには）のとおりにする。`catalog.md` には、`order.md` の「注文の状態」と同じ形で、公開状態（非公開、公開、販売終了）の表を加える。
- 各文書の「画面の操作の流れ」の節で、次の場面を撮る。画像のファイル名は、`order` と同じく 2 桁の連番と英語の名前にする。
  - 商品：商品の登録の画面、画像の登録、バリアントの追加、在庫の調整、公開の操作、店舗画面の一覧、店舗画面の詳細
  - 会員：店舗画面の「会員登録」のリンク、会員登録の画面、確認メールの送信を知らせる表示、パスワードの設定の画面、ログインした店舗画面、ログインの画面
  - レポート：売上の推移、バリアントの売れ行き、地域・年齢層・性別
- 商品の流れの撮影では、商品を新しく登録する。画像は、ローカルのストレージに seed として入っている商品の画像（`docker/seaweedfs/seed/assets/` の WebP）を使う。理由：E2E のテスト用の画像（`e2e/fixtures/photo.avif`）は 64x32 の 2 色の画像で、画面の説明に向かないため。
- 会員の流れの撮影では、撮影のたびに新しいメールアドレスで登録する（`e2e/src/auth.ts` の `memberEmail` と同じ考え方）。確認のメール自体は撮らない。理由：同じメールアドレスは再度登録できないため。メールは Mailpit の画面であり、製品の画面ではないため。
- レポートの流れの撮影では、seed のレポート用の注文を使う。撮影中は注文しない。seed の期間の定数は `e2e/showcase/capture.spec.ts` から `e2e/src/screenshot.ts` に移して共有する。
- 撮影は `pnpm capture:docs`（`e2e/playwright.docs.config.ts`）で実行する。前提はスロット 0、全てのサーバー、local の seed で、続けて撮るときは DB を再作成する。この前提を、[開発環境の起動](../../06-手順書/setup.md)の「機能紹介のページのスクリーンショット」に 1 行で書く。理由：商品の流れが同じ品番で再度登録できないため。
- `pnpm capture:docs` は `order-flow` も撮るので、`docs/01-概要/img/order/` の差分は commit しない（作業ツリーで元に戻す）。

## 目的と完了条件

### DS-345-1 非エンジニアが 3 つの流れを概要の文書で読める（D / P1）

- **DC-345-1** 決定の表の 3 つの文書があり、雛形の節のとおりである。
- **DC-345-2** 文書に、雛形が「置かないもの」とする数値、項目の一覧、エラー、API、識別子がない。
- **DC-345-3** 各文書の「業務の流れ」の Mermaid の図は、ノードが 15 個程度までで、内容が機能仕様と食い違わない。
- **DC-345-4** 3 つの文書が、`docs/01-概要/README.md` の表と `docs/01-概要/system.md` の「詳しく知るには」の 2 か所からリンクされている。

### DS-345-2 画像を script で撮り直せる（D / P1）

- **DC-345-5** `pnpm capture:docs` が 4 つの流れの全てで成功し、3 つの文書の画像が決定の置き場所に出力される。
- **DC-345-6** 撮った画像を目視で確認し、文書が載せる画像の全てがリポジトリにある。

### DS-345-3 検査と校閲を通す（D / P1）

- **DC-345-7** `node scripts/ci/doc-refs.mjs`、`scripts/ci/check-all.sh`、`scripts/dev/check-frontend.sh e2e` が成功する。
- **DC-345-8** 変えた日本語が校閲（検出役、確認役の順）を通っている。本文は、日本語だけの文脈のサブエージェントが書いている。

## 変える範囲

| まとまり | ファイル |
|----------|----------|
| 文書 | `docs/01-概要/catalog.md`・`member.md`・`reporting.md`（新規）、`README.md`、`system.md` |
| 画像 | `docs/01-概要/img/catalog/`・`member/`・`reporting/`（新規） |
| 撮影の script | `e2e/docs/catalog-flow.spec.ts`・`member-flow.spec.ts`・`reporting-flow.spec.ts`（新規）、`e2e/src/screenshot.ts`、`e2e/showcase/capture.spec.ts` |
| 手順書 | `docs/06-手順書/setup.md` |
| spec | `docs/08-spec/345-module-overviews/spec.md`（アーカイブで `docs/08-spec/archive/1to10/345-module-overviews/` に移す） |

## 非機能要件

- **DN-345-1** 消費者・運営者に見える振る舞いを変えない。画面とバックエンドのコードを変えない。
- **DN-345-2** E2E のテスト（`e2e/tests/`）と、機能紹介のページの画像（`showcase/img/`）を変えない。`capture.spec.ts` は定数を移すだけで、撮るものを変えない。
- **DN-345-3** `order.md` と `docs/01-概要/img/order/` を変えない。
- **DN-345-4** アーカイブした spec を変えない。

## 依存

| 対象 | 関係 |
|------|------|
| spec 326 | 概要の雛形と `order.md` を作った。マージ済み |
| 並行する spec | なし |

## Non-goals

| 項目 | 理由 |
|------|------|
| 3 つ以外のモジュールの流れの文書 | issue #345 の表が 3 つを挙げているため。必要になったら別に起票する |
| CI での撮影 | 撮影は E2E とは別で、CI では実行しない（[開発環境の起動](../../06-手順書/setup.md)） |
| 画像の差分の自動の検査 | 撮影を CI で実行しないため、撮った画像は目視で確認する |

## Open questions

なし。

## tasks

実装は実装役（汎用）に 1 タスクずつ任せる。文書の本文は日本語の要点から、日本語だけの文脈のサブエージェント（汎用）が書く。

- [ ] **T345-01-01** seed の期間の定数を `e2e/src/screenshot.ts` に移し、3 つの撮影の script を加えて撮り、画像を目視で確認する（DC-345-5、DC-345-6）。
  - 依存：なし
  - 範囲：`e2e/src/screenshot.ts`、`e2e/showcase/capture.spec.ts`、`e2e/docs/`、`docs/01-概要/img/`
  - 確認方法：スロット 0 で DB を再作成し、`e2e/` で `pnpm capture:docs` を実行する。`e2e/` で lint と型チェックを実行する。画像を目視で確認する
- [ ] **T345-01-02** 商品の登録と公開の流れの文書を書く（DC-345-1〜3）。
  - 依存：T345-01-01
  - 範囲：`docs/01-概要/catalog.md`
  - 確認方法：機能仕様（`docs/03-機能仕様/catalog.md`）と照合して読む、`node scripts/ci/mermaid/mermaid-check.mjs`
- [ ] **T345-01-03** 会員登録とログインの流れの文書を書く（DC-345-1〜3）。
  - 依存：T345-01-01
  - 範囲：`docs/01-概要/member.md`
  - 確認方法：機能仕様（`docs/03-機能仕様/member.md`）と照合して読む、`node scripts/ci/mermaid/mermaid-check.mjs`
- [ ] **T345-01-04** レポートの流れの文書を書く（DC-345-1〜3）。
  - 依存：T345-01-01
  - 範囲：`docs/01-概要/reporting.md`
  - 確認方法：機能仕様（`docs/03-機能仕様/reporting.md`）と照合して読む、`node scripts/ci/mermaid/mermaid-check.mjs`
- [ ] **T345-01-05** 一覧と全体像からリンクし、手順書に撮影の前提を書く（DC-345-4）。
  - 依存：T345-01-02〜04
  - 範囲：`docs/01-概要/README.md`、`system.md`、`docs/06-手順書/setup.md`
  - 確認方法：`node scripts/ci/doc-refs.mjs`
- [ ] **T345-01-06** 検査を通し、spec の最後のレビューをして、指摘を修正し、draft の pull request を作る（DC-345-7、DC-345-8）。
  - 依存：T345-01-05
  - 確認方法：`CI=true scripts/ci/check-all.sh`、`node scripts/ci/doc-refs.mjs`、`scripts/dev/check-frontend.sh e2e`、差分の追加行を校閲する
- [ ] **T345-01-07** pull request の前のレビューをする。指摘を修正し、draft を外す。
  - 依存：T345-01-06
- [ ] **T345-01-08** spec をアーカイブに移す。
  - 依存：T345-01-07
