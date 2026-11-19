# Spec 569：公開の API を、提供する側の名前と汎用の形にする

種類：整理　規模：M　ステータス：確定

## 背景と目的

公開の API は、提供する側の用語で、呼ぶ側によらない汎用の形で作る。呼ぶ側の型への変換は、呼ぶ側の腐敗防止層だけが行う。この方針は #558 で決めた（決定 6・7、2026-11-18）。名前の規則は[バックエンドの書き方](../../05-開発規則/backend-coding.md)の「名前の形」、モジュール間の公開は[バックエンドのアーキテクチャ](../../02-設計/backend-architecture.md)の「モジュラーモノリス」にある。

issue は、呼ぶ側の形と名前で作られた公開の API として 5 つ（注文の参照、カートの見積もり、レポート用・注文用・店舗用の商品参照）を挙げている。このうちカートの見積もりは、調べた結果、名前と形が方針に合っていた（決定）。この spec で、5 つの API の名前と型を方針に合わせる。消費者・運営者に見える振る舞いは変えない。追跡の issue は #569（全体の issue #544 の sub-issue。段階 2 の「#558 の決定から作った issue」の最初）。

規模は、判定の表では 2 つ以上のモジュールにまたがるので L になる。ただし、API の契約（OpenAPI）、DB の構造、AC を変えない。決定は API ごとの名前と型の対応で書けるので、特例で M とする（issue 担当のセッションが、利用者の委任で承認する）。

## スコープ

| 含める | 含めない |
|--------|----------|
| 5 つの公開の API の名前と型を、提供する側の名前と汎用の形にすること | application の service と view の名前 |
| 呼ぶ側（カート、注文、レポート、店舗）の腐敗防止層の実装を、新しい型に合わせること | 列挙の変換を、共通のメソッドで名前によって行うこと |
| 同じモジュールの公開の型で重複している列挙と record を 1 つにすること | 出荷のイベントからメールアドレスを外し、注文の API で読むこと |
| 設計書・用語集・レポートの機能仕様の、API の名前と説明の修正 | 他の公開の API（支払、在庫など。すでに機能の名前） |

決定：

- 商品カタログの 3 つの API（注文用、レポート用、店舗用）を、1 つにまとめる。理由：3 つとも商品とバリアントの参照で、汎用の形にすると、レポートの「バリアントの商品 ID」は注文と同じバリアントの参照で求められ、別のクラスにする理由が残らないため。メソッドと型の対応は、後の表「商品カタログの API の対応」。
- 商品カタログの API で、店舗に表示される商品だけを返すメソッドは、名前に「表示」の語（`Shown`）を付ける。理由：表示の状態によらない商品の参照と区別するため。`Shown` は商品カタログの用語「表示条件」「初回表示日時」に合わせた。`Store` は店舗モジュールの名前と読めるので使わない。引数の型の名前も同じ語に合わせる。
- 軸の名前と値の record が、注文用とレポート用の商品参照の型に 1 つずつある。これを `api/model/` の 1 つの record にまとめる。
- 注文の参照は、注文の API の名前と型に変更する。名前の対応は、後の表「注文の API の対応」。
- 注文の参照の型の項目は、注文の持つ値にする。レポートの用途だけで求めていた 2 つの値（消費税の合計、明細の税抜小計）を外し、レポートの腐敗防止層が求める。レポートが求める値は変わらない。消費税の合計の代わりに、税率ごとの消費税額の一覧（金額の内訳）を返す。税抜小計の代わりに、税抜単価を返す。
- 送料の消費税は残す。送料の消費税は、送料の税区分の注文日時の税率から、注文モジュールが求める。理由：呼ぶ側では求められないため。
- キャンセルの 3 つの項目（日時、種類、在庫に戻したか。キャンセルでなければ全て null）を、1 つの入れ子の record（キャンセルでなければ null）にまとめる。理由：3 つは全て null か全て値を持つかのどちらかで、組として扱うため。
- 氏名・住所・電話番号・メールアドレスは、今のとおり返さない。理由：公開の API で読める個人情報を最小にするため。注文の設計書の「他のモジュールへの公開」に同じ規則がある。通知がメールアドレスを読む方法は #572 で決める。
- 性別の列挙は、今の入れ子のまま残す。理由：公開の型の中に重複がないため。
- 注文の状態とキャンセルの種類の列挙は、今、公開の型に同じ値のものが 2 組ある（注文の参照の型と、注文のイベント）。注文の参照の型は、イベントの 2 つの列挙を使い、API の側の列挙を削除する。イベントの側は、項目も型も変えない。
- そのために、層の依存の表（バックエンドのアーキテクチャの「層とパッケージ」）に、`api` から `event` への依存を加える。ArchUnit の表も同じ内容にする（後の表「層の依存の検査」）。理由：domain はすでにイベントに依存し、domain の列挙からイベントの列挙への変換がある。API の変換はこれを使えるので、API の側の値ごとの変換が不要になるため。
- レポートの側は、注文の状態とキャンセルの種類の変換を、どちらも同じ列挙から行う。変換の箇所は今のとおり 2 つ（イベントを受ける application と、腐敗防止層の実装）。理由：層の依存の表で、infrastructure は application の `port`・`model` にしか依存できないため。
- カートの見積もりの API は、名前と形を変えない。理由：「見積もり」は注文モジュールの機能の名前で、名前に呼ぶ側の名前がない。型の項目も注文モジュールの見積もりの値で、店舗の用途だけで求めた値がないため。
- 見積もりの API で変えるのは次の 2 つ。1 つ目は、軸の名前と値、金額の内訳、1 つの税率の消費税額の 3 つの record を、`order/api/model/` に 1 つずつ置き、見積もりと注文の参照で共有すること。理由：今は両方の型の入れ子に、同じ record が重複しているため。2 つ目は、Javadoc と設計書の「カートの表示用」の説明を、呼ぶ側によらない説明にすること。
- 呼ぶ側の port の型（店舗の商品、注文のバリアント情報、レポートの注文など）は変えない。腐敗防止層の実装だけを、新しい API と型に合わせる。対象は、カート・注文・店舗・レポートの商品カタログの腐敗防止層と、レポート・店舗の注文の腐敗防止層。
- テストの期待値は変えない。テストのクラスの名前を、新しい API の名前に合わせる（後の表「テストの変更」）。具体的な名前は実装で決める。
- `scripts/ci/spec/ac-exclusions.json` の AC-281 の文が、店舗用の商品参照のテストの名前を指している。テストの名前を変えたら修正する。

商品カタログの API の対応（今の API を `CatalogApi` にまとめる）：

| 今 | 新しい名前 | 戻り値の型 |
|----|------------|------------|
| `OrderCatalogApi.findVariants` | `CatalogApi.findVariants` | `Map<UUID, VariantDetail>`（今の `OrderableVariant` の項目のまま） |
| `ReportCatalogApi.productIdsOf` | 削除する ※1 | なし |
| `ReportCatalogApi.findProduct` | `CatalogApi.findProduct` | `Optional<ProductVariants>`（今の `ReportProduct` の項目のまま。表示の状態によらない） |
| `StoreCatalogApi.listProducts` | `CatalogApi.listShownProducts` | `PageResult<ShownProductSummary>` |
| `StoreCatalogApi.searchProducts` | `CatalogApi.searchShownProducts` | `PageResult<ShownProductSummary>` |
| `StoreCatalogApi.findProduct` | `CatalogApi.findShownProduct` | `Optional<ShownProductDetail>` |
| `StoreCatalogApi.categoryTree` | `CatalogApi.categoryTree` | `List<CategoryNode>` |

※1：レポートの腐敗防止層は、`CatalogApi.findVariants` が返すバリアントから商品 ID を得る。

引数の型と共有の型の対応：

| 今 | 新しい名前 |
|----|------------|
| `StoreProductQuery` | `ShownProductQuery` |
| `StoreProductSort` | `ShownProductSort` |
| `OrderableVariant.AxisValue`、`ReportProduct.AxisValue` | `AxisValue`（`catalog/api/model/`） |

注文の API の対応：

| 今 | 新しい名前 |
|----|------------|
| `OrderReportApi.find` | `OrderApi.find` |
| `OrderReportSnapshot` | `OrderSnapshot` |
| `OrderReportSnapshot.Status`、`OrderReportSnapshot.CancellationKind` | 削除する（`OrderStatusChanged.Status`、`OrderStatusChanged.CancellationKind` を使う） |
| `CartQuote` と `OrderReportSnapshot` の入れ子の軸・金額の内訳・税額 | `AxisValue`、`Amounts`、`TaxAmount`（`order/api/model/`） |

層の依存の検査：

| 対象 | 変える内容 |
|------|------------|
| `backend-architecture.md` の層の依存の表 | api の行を分け、依存先に `event` を加える |
| `LayerDependencyTest` の `ALLOWED_LAYER_DEPENDENCIES` | 同じ内容にする |

テストの変更：

| 今 | 変更後 |
|----|--------|
| `OrderCatalogApiTest`、`StoreCatalogApiTest` | `CatalogApi` のテストにする |
| `OrderReportApiTest` | `OrderApi` のテストにする |
| `OrderReportApiTest` の、消費税の合計と税抜小計を確認する部分 | 同じ期待値で `reporting/infrastructure/directory/OrderApiDirectoryTest` に移す |

## 目的と完了条件

### DS-569-1 公開の API が、提供する側の名前と汎用の形になっている（D / P1）

- **DC-569-1** 呼ぶ側の名前（注文・レポート・店舗の用途）を持つ公開の API のクラスと型がない。名前は、表「商品カタログの API の対応」「引数の型と共有の型の対応」「注文の API の対応」のとおり。
- **DC-569-2** 公開の API の型に、呼ぶ側の用途だけで求めた値（消費税の合計、明細の税抜小計）がない。
- **DC-569-3** 同じモジュールの公開の型に、同じ値の列挙と record が重複していない（注文の状態・キャンセルの種類・軸の名前と値・金額の内訳・1 つの税率の消費税額）。
- **DC-569-4** 呼ぶ側の port の型を変えず、腐敗防止層の実装だけが新しい型から変換している。

### DS-569-2 文書で新しい名前と依存を読めるようにする（D / P1）

- **DC-569-5** 商品カタログと注文の設計書、用語集、レポートの機能仕様で、API の名前が新しい名前になり、説明が呼ぶ側によらない内容になっている。
- **DC-569-6** バックエンドのアーキテクチャの層の依存の表に、`api` から `event` への依存があり、ArchUnit の表と一致する。

### DS-569-3 既存の検査が通る（D / P1）

- **DC-569-7** `./gradlew check` と統合テストが、期待値を変えずに通る。
- **DC-569-8** 文書の検査（`doc-refs.mjs`、`note-numbers.mjs`、`ja-text.mjs`）が通る。

## 変える範囲

| まとまり | ファイル |
|----------|----------|
| 公開の API | `backend/ecsite/src/main/java/com/ecsite/module/{catalog,order}/api/`（`model/` を含む） |
| 腐敗防止層 | `backend/ecsite/src/main/java/com/ecsite/module/{cart,order,reporting,storefront}/infrastructure/directory/` |
| 層の依存の検査 | `backend/ecsite/src/test/java/com/ecsite/architecture/LayerDependencyTest.java` |
| テスト | `backend/ecsite/src/{test,integrationTest}/java/com/ecsite/module/{catalog,order,reporting,storefront}/` の、名前を変えた API を使うテスト |
| 設計と規則 | `docs/02-設計/{catalog,order,backend-architecture}.md`、`docs/03-機能仕様/{glossary,reporting}.md` |
| 検査の設定 | `scripts/ci/spec/ac-exclusions.json` |

## 非機能要件

- **DN-569-1** 消費者・運営者に見える振る舞いを変えない。
- **DN-569-2** API の契約（OpenAPI）、DB の構造、イベントの項目と型を変えない。
- **DN-569-3** 既存のテストの期待値を変えない。変えるのは、型と名前と、テストの置き場所（レポートの用途の値を求める場所を移したもの）だけ。
- **DN-569-4** #491 で `api/` が呼ぶ service に付けたトランザクションの伝播の注釈（`NEVER`・`MANDATORY`）を変えない。対象の 5 つの API のメソッドは全て参照で、伝播の注釈はない。

## 依存

| 対象 | 関係 |
|------|------|
| #491（マージ済み） | `api/` が呼ぶ service の伝播の前提。この spec は前提を変えない |
| #570（後に行う） | 同名の列挙の変換を共通のメソッドにする。同じファイルを変えるので、この spec を先にマージする。値ごとの変換は今の形で残す |
| #572（同時に進めない） | 注文の `api/` を変える（通知がメールアドレスを読む方法を加える）。この spec の注文の API に加える見込み |
| #549（並行。支払の期限切れのジョブ） | 変えるファイルは重ならない見込み。実装を始める前の照合で確認する |
| #502（並行。`messages.properties` の文） | 変えるファイルは重ならない見込み。実装を始める前の照合で確認する |

## Non-goals

| 項目 | 理由 |
|------|------|
| application の service と view の名前（`OrderReportQueryService`、`OrderableVariantView`、`StoreProductQueryService` など） | #558 の決定 7 は、公開の API の名前だけを対象にするため。用途を付けた service の名前は、バックエンドの書き方の「名前の形」の ※3 が許す |
| 列挙の変換を名前で行う共通のメソッド | #570 で行うため |
| 通知がメールアドレスを注文の API で読むこと | #572 で行うため |
| 他の公開の API の名前と形 | すでに機能の名前で、#558 の決定 6 は 5 つの API だけを対象にするため |
| レポートの 2 つの性別の列挙（`ReportGender`、`ReportBuyerGender`） | 値が違う（不明にまとめるかどうか）ので重複でないため |

## Open questions

なし。4 つの問い（商品カタログの API の数、注文の参照の個人情報、列挙の統一と層の依存、見積もりの API の名前）は、2026-11-19 に issue 担当のセッションが利用者の委任で決めた。結果は「決定」にある。

## tasks

実装は実装役（汎用）に 1 タスクずつ任せる。

- [x] **T569-01-01** 層の依存の表に `api` から `event` への依存を加える（DC-569-6）。
  - 依存：なし
  - 範囲：`docs/02-設計/backend-architecture.md`、`backend/ecsite/src/test/java/com/ecsite/architecture/LayerDependencyTest.java`
  - 確認方法：`LayerDependencyTest` が通る。文書の検査が通る
- [x] **T569-01-02** 注文の API を `OrderApi`・`OrderSnapshot` にし、共有の型（`AxisValue`、`Amounts`、`TaxAmount`）を作り、レポート・店舗の腐敗防止層とテストを合わせる（DC-569-1〜4）。
  - 依存：T569-01-01
  - 範囲：`order/api/`、`reporting`・`storefront` の `infrastructure/directory/`、関係するテスト
  - 確認方法：変えたモジュールのテスト（単体と統合を `--tests` で絞る）が通る
- [ ] **T569-01-03** 商品カタログの API を `CatalogApi` にまとめ、カート・注文・レポート・店舗の腐敗防止層とテストを合わせる（DC-569-1、DC-569-3、DC-569-4）。
  - 依存：T569-01-02
  - 範囲：`catalog/api/`、4 つのモジュールの `infrastructure/directory/`、関係するテスト、`scripts/ci/spec/ac-exclusions.json`
  - 確認方法：変えたモジュールのテスト（単体と統合を `--tests` で絞る）が通る
- [ ] **T569-01-04** 設計書・用語集・レポートの機能仕様の名前と説明を修正する（DC-569-5、DC-569-8）。
  - 依存：T569-01-03
  - 範囲：`docs/02-設計/{catalog,order}.md`、`docs/03-機能仕様/{glossary,reporting}.md`
  - 確認方法：変更前の名前を `git grep` で探し、アーカイブの外に残らないことを確認する。文書の検査が通る
- [ ] **T569-01-05** `./gradlew check` と統合テストを通す（DC-569-7）。
  - 依存：T569-01-04
  - 範囲：`backend/ecsite/`
  - 確認方法：issue 担当のセッションから重い検査の合図を受けてから実行する
- [ ] **T569-01-06** spec の最後のレビュー。指摘を修正し、draft の pull request を作る。
  - 依存：T569-01-05
- [ ] **T569-01-07** pull request の前のレビュー。指摘を修正し、draft を外す。
  - 依存：T569-01-06
- [ ] **T569-01-08** spec をアーカイブに移す。
  - 依存：T569-01-07
