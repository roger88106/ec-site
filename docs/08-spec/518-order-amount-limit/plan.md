# Plan 518：注文の総額を、決済サービスが受け付ける範囲に限る

ステータス：承認済み（tasks 作成済み）　対象：[spec.md](spec.md)

前提：spec 518 は 2026-11-19 に確定した。上限の値は BR-518-1 のとおりで、新しいエラーコード（400）を注文手続きの開始と注文に加える。文言と画面の表示は、利用者が確認した下の決定のとおり。読む文書は[文書の書き方](../../05-開発規則/writing.md)、[文書の構成](../../05-開発規則/doc-structure.md)、[文書の記号と番号](../../05-開発規則/notation.md)。

| 本書の節 | 主な担当 |
|----------|----------|
| 範囲の定め方と取得 | US-518-1（AC-518-1〜5、BR-518-1） |
| 注文手続きの開始と注文の判定 | US-518-1（AC-518-1〜5、BR-518-2） |
| 店舗画面 | US-518-1（AC-518-6、7） |
| 支払画面 | US-518-1（AC-518-8） |
| 文書の更新 | US-518-1（AC-518-1〜8） |
| 段階と変えるファイル | 全て |
| 利用者に確認すること | なし |

共通の決定：

- 危険度は高にしない。理由：新しいエラーコードを加えるだけで、範囲の中の注文の振る舞いは変わらないため。
- レビューの profile は厚めにする（issue 担当のセッションの指示）。
- 実装は実装役（汎用）に 1 タスクずつ任せる。spec 担当のセッションはコードを書かない。
- push は、draft の pull request を作る直前に行う。pull request の向き先は全体のブランチ `agent/544-cleanup-before-release`。
- 並行する #570 と重なりうるファイルは、決済の公開の API、注文の決済の腐敗防止層、注文のエラーコード、OpenAPI である。同じファイルでは変える行を重ねず、マージの前に全体のブランチを取り込んで衝突を解く。マージの先後は issue 担当のセッションが決める。

## 範囲の定め方と取得（US-518-1：AC-518-1〜5、BR-518-1）

| 何を | どこに | 確認方法 |
|------|--------|----------|
| 支払 1 回の金額の範囲（BR-518-1）と、範囲の中かの判定を、値オブジェクトに置く | 決済の domain（新しい `PaymentAmountRange`） | 単体テスト（境界の 49・50・99,999,999・100,000,000 円） |
| 決済の公開の API に、範囲を返す操作と、その戻り値の型を加える | `PaymentApi`、`payment/api/model` | 統合テスト（`PaymentApiTest`） |
| 注文の腐敗防止層（決済）に、範囲を取得する操作と、注文の側の型を加える | `order/application/port/PaymentDirectory`、`order/infrastructure/directory/PaymentApiDirectory` | 次の節の統合テスト |

- 範囲は設定にしない（spec の Non-goals）。
- 決済サービスの mock も、決済の domain の同じ範囲を使う。理由：範囲は決済サービスの実装によらないため。
- 支払の作成では範囲を判定しない。理由：支払は注文の成立の中でだけ作られ、注文の成立の前に範囲を判定するため。

## 注文手続きの開始と注文の判定（US-518-1：AC-518-1〜5、BR-518-2）

| 何を | どこに | 確認方法 |
|------|--------|----------|
| 新しいエラーコード `ORDER_AMOUNT_OUT_OF_RANGE`（業務規則のエラー、400、差し込む値は min と max）を加える | `OrderErrorCode` | `ErrorCodeContractTest`、`MessageContractTest` |
| 文言 `ecsite.order.order_amount_out_of_range` を加える | `messages.properties` | `MessageContractTest`、統合テスト（値が桁区切りで差し込まれる。例：50〜99,999,999 円） |
| 注文手続きの開始で、購入できるかの判定の後に、見積もりの総額が範囲の中かを判定する。範囲の外なら拒否し、注文手続きを作らず、引当もしない | `CheckoutCommandService`（開始） | 統合テスト（新しい `CheckoutAmountRangeApiTest`） |
| 注文で、確認した総額との比較の後に、総額が範囲の中かを判定する。範囲の外なら拒否し、注文と支払を作らず、在庫を減らさない | `OrderPlacementCommandService` | 統合テスト（新しい `PlaceOrderAmountRangeApiTest`）、単体テスト（`OrderPlacementCommandServiceTest`。判定の順） |
| 注文手続きの開始と注文で、範囲の判定を共通にする。置き場所は、購入できるかの判定と同じにする | `order/application/checkout/` | レビュー |
| OpenAPI の 2 つの操作（注文手続きの開始、注文）に、`x-error-codes`・説明・エラーコードの列挙を加える | `api/openapi/store.yaml` | `ErrorCodeContractTest`、CI の OpenAPI の検査 |

文言は、次のとおり（`{min}` と `{max}` に範囲の値を差し込む）。

```properties
ecsite.order.order_amount_out_of_range=注文の総額は {min}〜{max} 円にしてください。カートの商品か数量を変えてください。
```

- 境界のテストは、商品の価格と数量、設定の送料（テストでだけ差し替える）で総額を作る。下限の外は 49 円、下限は 50 円、上限は 99,999,999 円、上限の外は 100,000,000 円。
- 上限ちょうどの注文のテストで、保存した注文の総額と支払の金額を読み戻して確認する。issue の「上限の金額を保存して読み戻すリポジトリのテスト」を兼ねる。
- 判定の順（BR-518-2）は、総額が変わり、かつ範囲の外になる場合に、総額が変わったエラーを返すことで確認する。

## 店舗画面（US-518-1：AC-518-6、7）

| 何を | どこに | 確認方法 |
|------|--------|----------|
| OpenAPI から型を再生成する | `frontend/store/src/api/generated/`（`pnpm generate:api`） | 型の検査 |
| 新しいエラーコードを、注文が拒否されたときにカートへの導線を表示するエラーコードの一覧に加える | `frontend/store/src/features/checkout/placeOrderErrors.ts` | 単体テスト（`placeOrderErrors.test.ts`） |
| 注文手続きの開始が拒否されたとき、カートの画面は既存の失敗の表示で文言を示す（コードは変えない見込み） | `frontend/store/src/features/cart/components/StartCheckoutForm.tsx` | 単体テスト（`StartCheckoutForm.test.tsx` に、このエラーの文言を表示するテストを加える） |

- 管理画面は変えない（spec のスコープ）。

## 支払画面（US-518-1：AC-518-8）

| 何を | どこに | 確認方法 |
|------|--------|----------|
| 上限ちょうどの金額で支払画面を作ると、決済サービスに上限の金額を 1 件の明細で渡す（コードは変えない見込み） | `StripePaymentGateway` | 単体テスト（`StripePaymentGatewayTest` に、99,999,999 円を送るテストを加える） |

## 文書の更新（US-518-1：AC-518-1〜8）

| 何を | どこに |
|------|--------|
| 支払の金額の範囲の規則（BR-518-1） | [決済モジュール](../../03-機能仕様/payment.md)の「業務規則」 |
| 注文手続きと注文の判定の項目に、総額の範囲を加える（BR-518-2） | [注文モジュール](../../03-機能仕様/order.md)の「注文手続き」「注文の成立」 |
| 判定の順に、総額の範囲を加える | [注文の設計](../../02-設計/order.md)の「注文手続きと注文の成立」 |
| 公開の API に範囲の操作を加える | [決済の設計](../../02-設計/payment.md)の「他のモジュールへの公開」 |

## 段階と変えるファイル

| 段階 | ファイル | 内容 | AC | 開始の条件 |
|------|----------|------|----|------------|
| 1 | `tasks/01-backend.md` | 範囲、公開の API、注文手続きの開始と注文の判定、エラーコードと文言、OpenAPI、支払画面のテスト | AC-518-1〜5、8 | plan と tasks の承認、issue 担当のセッションの開始の合図、実装を始める前の照合 |
| 2 | `tasks/02-store-and-docs.md` | 店舗画面、文書の更新、spec の最後のレビュー、pull request、アーカイブ | AC-518-6、7 | 段階 1 の完了 |

| まとまり | ファイル |
|----------|----------|
| 決済（範囲） | `backend/ecsite/src/main/java/com/ecsite/module/payment/domain/payment/`、`backend/ecsite/src/main/java/com/ecsite/module/payment/api/` |
| 注文（判定、エラーコード、文言） | `backend/ecsite/src/main/java/com/ecsite/module/order/`、`backend/ecsite/src/main/resources/messages.properties` |
| バックエンドのテスト | `backend/ecsite/src/test/java/com/ecsite/`、`backend/ecsite/src/integrationTest/java/com/ecsite/module/` |
| OpenAPI | `api/openapi/store.yaml` |
| 店舗画面 | `frontend/store/src/features/` |
| 文書 | `docs/02-設計/`、`docs/03-機能仕様/` |

## 利用者に確認すること

なし。
