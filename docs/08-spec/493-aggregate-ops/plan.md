# Plan 493：集約の操作と照会の構造を整える

ステータス：承認済み（tasks 作成済み）　対象：[spec.md](spec.md)

前提：spec は確定した（2026-11-18。Open questions はない）。読む文書は[文書の書き方](../../05-開発規則/writing.md)、[文書の構成](../../05-開発規則/doc-structure.md)、[文書の記号と番号](../../05-開発規則/notation.md)。

| 本書の節 | 主な担当 |
|----------|----------|
| 戻り値と保存 | DS-493-1（DC-493-1〜4） |
| 商品の型の分割 | DS-493-2（DC-493-5〜8） |
| 照会の型と骨組み | DS-493-3（DC-493-9〜10）、DS-493-4（DC-493-11〜14） |
| 段階と変えるファイル | 全て |
| 利用者に確認すること | [spec](spec.md)の「Open questions」（なし） |

共通の決定：

- 危険度は通常にする。API と DB を変えない整理のため。spec に危険度の行は書かない。
- レビューの profile は厚めにする（issue 担当の指示）。段階ごとに最後のレビュー、spec の最後のレビュー、pull request の前のレビューを行う。
- 実装は、実装役（汎用）に 1 タスクずつ任せる。spec 担当は合否を判定する。
- 最初の push は、draft の pull request を作る直前にする。pull request の向き先は全体のブランチ `agent/544-cleanup-before-release`。
- 重い検査（`./gradlew check`、`scripts/ci/check-all.sh`）は、issue 担当の合図の後に実行する。段階の途中は、変えたモジュールのテストだけを `--tests` で絞って実行する。
- 段階 2 と段階 3 は振る舞いを変えない。今の振る舞いは、各節の表の既存のテストが固定している。足りない所だけ、固定のテストを先に追加する。
- 実装を始める前に、全体のブランチを取り込み、実装を始める前の照合をする。
- 新しい型の例外のメッセージとログに、DB の値と個人の情報を含めない（DN-493-4）。段階の最後のレビューで確認する。
- 並行する #547（イベントと在庫）・#548（出荷）とは、変えるファイルが重ならない。実装を始める前の照合で、両方の変えるファイルとの重なりを確認する。本 spec は #491 より先に全体のブランチに入れる。

## 戻り値と保存（DS-493-1：DC-493-1〜4）

| 何を | どこに | 確認方法 |
|------|--------|----------|
| 規則 2 つ（戻り値と保存、照会の型）を追加する | `docs/05-開発規則/backend-coding.md` の新しい小節 2 つ（照会の型は「層とパッケージ」、戻り値と保存は「入力の検証とエラー」の「更新の競合は `version`」の後） | 文書を読む |
| 取消の申請を、変えたかを返す形にする | `order/domain/order/Order.java` の `requestCancellation` | `OrderTest` |
| 取消の申請の service が、変えたときだけ保存する | `order/application/OrderCancellationCommandService.java` の `request` | 統合テスト（申請中の注文に再度申請しても、注文のバージョンが進まない） |
| 支払の 5 つの操作を、変えたかを返す形にする | `payment/domain/payment/Payment.java` の `capture`、`expireAuthorization`、`cancel`、`markVoided`、`markRefunded` | `PaymentTest` |
| 与信の反映を、変えたかを返す形にする（支払なしの状態から与信の取消待ちにしたときも真） | `Payment.java` の `authorize`（2 つ） | `PaymentTest` |
| 支払の service が、変えたときだけ保存する。引数を `Consumer` から `Predicate` にする | `payment/application/PaymentCommandService.java` の `authorized`、`authorizeFromGateway`、`cancel`、`modify` | 統合テスト（重ねて届いた通知・再実行で、支払のバージョンが進まない） |
| 与信の反映の service が返す「与信済みにしたか」を、変えたかと変えた後の状態（与信済み）から判定する | `PaymentCommandService.authorizeFromGateway` | 既存の `PaymentExpiryJobTest`（AC-409-5 の 2 件）がそのまま通る |

統合テストの置き場所（バージョンは、操作の後にリポジトリから読み直した注文・支払で確認する）：

| 場合 | テストのクラス ※1 |
|------|----------------|
| 申請中の注文への再度の申請 | `CancellationApiTest` |
| 与信の通知が重ねて届く | `PaymentApiTest` |
| 売上の確定の通知が重ねて届く | `PaymentCaptureTest` |
| 与信の取消済みの再実行 | `PaymentVoidTest` |
| 返金済みの再実行 | `PaymentRefundTest` |
| 取消済みの注文の支払の取消（再実行） | `CancellationApiTest` か `CapturedOrderCancellationRefundTest` |
| 与信の期限切れ（与信済みでない支払に期限切れが届く） | `PaymentCaptureTest` |

※1：テストのクラスは、実装役が既存のテストの準備の形に合わせて選んでよい。表の 7 つの場合は全て覆う。

- 範囲は、状態によって何もしないことがある操作だけにする。理由：運営者の編集は、spec の Non-goals のとおり対象にしないため。
- 与信の反映の判定を、変えたかと変えた後の状態から行う。理由：変えたときの状態が与信済みなら、変える前は未払いだったので、今の「未払いから与信済みにしたら真」と同じ結果になるため。呼び出し元は、決済サービスから与信を取得したときのログだけ（`PaymentGatewayService.recoverAuthorization`）。
- 何も変わらない操作の後に、注文と支払のバージョンが進まなくなる。画面と応答は変わらない。理由：注文と支払のバージョンと更新日時は、API の応答にない（`api/openapi/` の注文と支払の型に項目がない）ため。他のモジュールが注文と支払のバージョンを読まないことは、実装の前の照合で `version` の呼び出し元を検索して確認する。

## 商品の型の分割（DS-493-2：DC-493-5〜8）

| 何を | どこに | 確認方法 |
|------|--------|----------|
| 公開の状態（公開状態、公開期間、初回表示日時）を 1 つの値の型にする（record） | `catalog/domain/product/vo/Publication.java`（新規） | 単体テスト `PublicationTest`（新規） |
| 公開状態の列挙を `vo/` に移す | `catalog/domain/product/PublicationStatus.java` → `vo/` | コンパイル |
| 値の型の操作：状態を変える操作（公開、非公開、販売終了、公開期間の変更）は新しい値を返す。判定は、表示中か、一度でも表示したか、今できる操作の計算に使う値 | `Publication` のメソッド | `PublicationTest` |
| 商品の集約は値の型に処理を任せ、値が変わったときだけイベントを登録して真を返す | `Product.java` | 既存の `ProductPublicationTest`、`ProductActionsTest`、`ProductTest` |
| 公開中で初回表示日時も公開終了もないことを拒む不変条件を、値の型のコンストラクターに移す | `Publication` | `PublicationTest`、既存の `ProductInvariantsTest`、`ProductRestoreFailureTest` |
| バリアントの一覧を一覧の型にする（package-private の record） | `catalog/domain/product/Variants.java`（新規） | 単体テスト `VariantsTest`（新規） |
| 画像の一覧を一覧の型にする（package-private の record） | `catalog/domain/product/ProductImages.java`（新規） | 単体テスト `ProductImagesTest`（新規） |
| 一覧の型の操作：ID で探す・次の表示順・表示順の整列と、追加・削除・並べ替え。バリアントの一覧は、1 件を置き換えた一覧と、品番・軸の値の組み合わせの重複を調べる操作と、軸の値の数を調べる操作も持つ | `Variants`、`ProductImages` | 各単体テスト |
| 画像の数の上限（定数を含む）は画像の一覧の型に置く。軸の数は商品が持ち、バリアントの一覧への問い合わせに渡す | `ProductImages`、`Product` | 単体テスト、`ProductInvariantsTest`、`ProductRestoreFailureTest`（画像の数が上限を超える行の場合を追加する） |
| 復元（変えない見込み） | `ProductFactory`、`JooqProductRepository` | `ProductRestoreFailureTest` に、公開の状態に違反する行の場合を追加する |
| 保存は、今の差分の書き方のままにする。型の取り出し方だけ合わせる | `JooqProductRepository` | 既存の `JooqProductRepositoryTest` |

- 一覧の型の「ID で探す」は、見つからなければ業務のエラーにする。
- 復元で新しい型を作る処理は、商品のコンストラクターの中にあるので、今の復元の補助の中で検査される。そのため復元の部分は変えない見込みである。
- 公開の状態の値の型を record にする。理由：公開の状態は識別子を持たない値で、#555 の規則（entity と集約は class）の対象でなく、状態を変える操作が新しい値を返すと、商品は前後の値を比べて変えたかを決められるため。
- 一覧の型を package-private の record にする。理由：税率の一覧と同じ形にそろえるため。中の要素（バリアント、画像）は entity なので、一覧の型は集約のパッケージに置く（domain の直下に置く規則は、[バックエンドの書き方](../../05-開発規則/backend-coding.md)の「標準の構成」にある）。
- 公開状態の列挙を `vo/` に移す。理由：値の型が持つ列挙は `vo/` に置く規則のため。呼び出し元は、公開状態の列挙を参照する全てのファイルで、import の書き換えだけで済む。
- パッケージの大きさは、集約のパッケージが 7 から 8（`Variants` と `ProductImages` を追加し、`PublicationStatus` を `vo/` に移す）、`vo/` が 6 から 8 になり、目安の 10 以下に収まる。
- 商品のクラスの行数は減る見込みで、目標値は決めない。

## 照会の型と骨組み（DS-493-3：DC-493-9〜10、DS-493-4：DC-493-11〜14）

| 何を | どこに | 確認方法 |
|------|--------|----------|
| 商品の詳細：保存されている値の型を新しく作る | 新規 `catalog/application/model/StoredProductDetail.java`（仮の名前） | 既存の `ProductApiTest`、`ProductStatusApiTest`、`ProductVariantApiTest` |
| 商品の詳細：リポジトリは保存されている値だけの型を返す | `ProductQueryRepository`、`JooqProductQueryRepository` | 同上 |
| 商品の詳細：service が view を作る | `ProductQueryService`、`ProductDetailView`（`withCurrentState` を削除） | 同上 |
| 税区分：リポジトリは保存されている値だけの型を返す。service が今の税率を加えて view を作る | 新規 `tax/application/model/StoredTaxCategory.java`（仮の名前）、`TaxCategoryQueryRepository`、`JooqTaxCategoryQueryRepository`、`TaxCategoryQueryService`、`TaxCategoryView` | 既存の `TaxCategoryApiTest`、`TaxRateApiTest` |
| 店舗の商品の一覧と検索で、骨組みを共通のクラスにする | 新規 `catalog/infrastructure/persistence/StoreProductPages.java`（package-private）、`JooqStoreProductQueryRepository.findVisible`、`PostgresProductSearch.search` | 既存の `StoreCatalogApiTest`、`StoreProductSearchTest`、`StoreCatalogApiSortTest`、`PriceInclTaxFieldTest`、`ProductVisibilityTest`、`StorefrontApiTest`、`StorefrontShopTest` |
| 売上の集計の範囲（商品、税抜き、送料除外）を値の型にする。「商品を選んだら送料を除く」は、その型の判定のメソッドにだけ置く | 新規 `reporting/domain/sales/SalesScope.java` | 単体テスト `SalesScopeTest`（新規） |
| 集計の条件の型、service、リポジトリの port と実装が、範囲の型を受け取る。controller が範囲の型を作る | `ReportController`、`SalesTrendQuery`、`CustomerBreakdownQuery`、`VariantSalesQuery`、3 つの service、`ReportQueryRepository`、`JooqReportQueryRepository` | 既存の `SalesTrendQueryTest`、`CustomerBreakdownQueryTest`、`VariantSalesQueryTest` |
| 商品で絞る結合と条件を、リポジトリの 1 つの private メソッドにする | `JooqReportQueryRepository` | 同上 |

- 商品の詳細の view は、service が税込価格と今できる操作を加えて作る。今できる操作は null でなくなる。
- 店舗の商品の骨組みは、[spec](spec.md)の DC-493-11 の 5 つである。呼び出し元は、絞り込みの条件と並べ替えだけを渡す。
- 保存されている値の型を、application の model に置く。理由：参照用のリポジトリの port は application にあり、port の型は application の model に置く今の形に合わせるため。
- 骨組みの共通のクラスを、infrastructure の persistence に置く。理由：jOOQ を使えるのは `infrastructure/persistence/` だけのため。このパッケージは目安の例外で（[バックエンドの書き方](../../05-開発規則/backend-coding.md)の「パッケージの大きさ」）、15 になっても上限の 20 以下に収まる。
- 集計の範囲の型を、domain に置く。理由：期間と同じく集計の条件の値で、規則（商品を選んだら送料を除く）を持つため。
- バリアント別の集計は、商品を必ず選ぶ範囲として同じ型で渡す。送料は常に除く（今と同じ）。
- 新しい型の名前（表の `StoredProductDetail` など）は仮である。実装のタスクで確定し、plan を修正する。

## 段階と変えるファイル

| 段階 | ファイル | 内容 | DC | 開始の条件 |
|------|----------|------|----|------------|
| 1 | `tasks/01-save-on-change.md` | 規則の追加、注文と支払の戻り値と保存 | DC-493-1〜4、DC-493-9（規則の文） | plan と tasks の承認、issue 担当の開始の合図、実装を始める前の照合 |
| 2 | `tasks/02-product-parts.md` | 商品の公開の値の型、一覧の型 | DC-493-5〜8 | 段階 1 の完了 |
| 3 | `tasks/03-queries.md` | 照会の型、一覧と検索の骨組み、集計の範囲、最後の確認と pull request | DC-493-10〜16 | 段階 2 の完了 |

変えるファイルは、[spec](spec.md)の「変える範囲」の表のとおり。設計の文書（`docs/02-設計/` の catalog、reporting、tax のパッケージの構成）は、説明が変わらない見込みである。段階 3 で確認する。

## 利用者に確認すること

なし。
