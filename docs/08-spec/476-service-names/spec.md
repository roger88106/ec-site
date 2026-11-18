# Spec 476：service の名前を、参照・更新・外部の呼び出しのどれかで終える

種類：整理　規模：M　ステータス：確定

## 背景と目的

今の規則（[バックエンドの書き方](../../05-開発規則/backend-coding.md)の「名前の形」の ※3）は、レベル A の更新の service に、用途だけの名前を認めている。そのため、名前から参照か更新かが分からない service がある。レベル A のモジュールは参照と更新を分けている（CQRS）ので、名前でどちらかが分かるようにする。レベル B でも、参照だけ・更新だけと決まっている service は同じ名前の形にする（issue #476 のコメント）。

消費者・運営者に見える振る舞いを変えない。追跡の issue は #476（全体の issue #544 の一部）で、出典は #458 のレビューである。

## スコープ

| 含める | 含めない |
|--------|----------|
| service の名前の規則の修正（バックエンドの書き方、開発の用語集） | service の統合・分割（order の更新を 1 つにまとめることなど） |
| 用途だけの名前の service 11 個の改名と、そのテストのクラスの改名 | 読み書きが混ざるレベル B の service（カート、メディア、出荷）の改名 |
| service の名前の末尾と、クラスへのトランザクションの付け方を確認する自動の検査（ArchUnit） | service 以外の名前の形（ハンドラー、ジョブなど）の検査 |

決定（issue 担当のセッションが確認した）：

1. レベル A の service は、名前を参照・更新・外部の呼び出しのどれかで終える。用途の名前は前に付けてよい。
2. 外部の呼び出しの service は、例外の行を新しく設けない。理由：バックエンドの書き方の「名前の形」の表に、既に行があるため。代わりに、同じ節の ※3 を書き換え、次の 2 点を書く。
   - 外部の呼び出しの service は、クラスにトランザクションを付けない。
   - 他のモジュールを経て外部のサービス（画像の変換・保存）を呼ぶものも含む。
3. 商品画像の service は、外部の呼び出しの名前にする。理由：メディアのモジュールを経て画像の変換・保存をトランザクションの外で呼び、画像を加えられることの確認と保存を、参照・更新の service に任せている（決済と同じ形）ため。
4. 注文の更新の service（取消、与信の成功の反映など）は 1 つにまとめず、それぞれを改名するだけにする。理由：機械的な改名に範囲を限るため。また、各 service が呼ぶ他のモジュールが違い、まとめると 1 つの service の依存が増える。
5. レベル B では、参照だけ・更新だけと決まっている service を同じ形に改名する。issue に挙がった 2 つ（出荷の通知の反映、集計の記録）に加え、店舗画面の service（参照だけ）と出荷通知メールの service（送ったメールの記録と送信だけ）も改名する。
6. 改名の対象の service と、規則の検査（参照・更新の service がクラスにトランザクションを付けていること、名前の末尾）は、ArchUnit のテストで確認する。読み書きが混ざるレベル B の service は、テストの例外の表に置く。
7. DB を使わない参照の service（レポートの設定の参照）は、クラスにトランザクションを付けない。テストの例外の表に、理由とともに置く。理由：設定の値だけを返し、トランザクションを付けると不要な DB の接続を取るため。店舗の情報の取得も、同じ理由でメソッドに付けている。

名前の末尾の形：

| 区分 | 名前の末尾 | 例 |
|------|------------|----|
| 参照 | `QueryService` | `QuoteQueryService` |
| 更新 | `CommandService` | `OrderPlacementCommandService` |
| 外部の呼び出し | `GatewayService` | `ProductImageGatewayService` |

改名の一覧：

| モジュール | 今の名前 | 新しい名前 |
|------------|----------|------------|
| catalog | `ProductImageService` | `ProductImageGatewayService` |
| catalog | `ProductPublicationService` | `ProductPublicationCommandService` |
| order | `CheckoutService` | `CheckoutCommandService` |
| order | `OrderPlacementService` | `OrderPlacementCommandService` |
| order | `QuoteService` | `QuoteQueryService` |
| order | `OrderCancellationService` | `OrderCancellationCommandService` |
| order | `OrderPaymentService` | `OrderPaymentCommandService` |
| shipping | `ShipmentNotificationService` | `ShipmentNotificationCommandService` |
| reporting | `ReportCollectionService` | `ReportCollectionCommandService` |
| storefront | `StorefrontService` | `StorefrontQueryService` |
| notification | `ShipmentMailService` | `ShipmentMailCommandService` |

名前を残すもの（読み書きが混ざるレベル B）：

| モジュール | 名前 |
|------------|------|
| cart | `CartService` |
| media | `MediaService` |
| shipping | `ShipmentService` |

## 目的と完了条件

### DS-476-1 service の名前から、参照・更新・外部の呼び出しのどれかが分かる（D / P1）

- **DC-476-1** 規則が、決定 1・2・5 の内容になっている。対象は、バックエンドの書き方の「名前の形」と「トランザクションは application」、開発の用語集の語「参照の service」「更新の service」である。
- **DC-476-2** application の service の名前が、参照・更新・外部の呼び出しのどれかで終わる。例外は、読み書きが混ざるレベル B の 3 つだけである。
- **DC-476-3** 参照と更新の service は、クラスにトランザクションを付けている（参照は読み取り専用）。外部の呼び出しの service は、クラスに付けていない。DB を使わない参照の service（決定 7）は除く。
- **DC-476-4** DC-476-2・DC-476-3 を ArchUnit のテストが確認し、例外にした service の理由がテストの例外の表にある。

### DS-476-2 振る舞いを変えずに改名を終える（D / P1）

- **DC-476-5** 旧名が、コード・テスト・文書（アーカイブと記録を除く）に残っていない。
- **DC-476-6** 既存のテストと検査（`./gradlew check`）が通る。

## 変える範囲

パスの基準は、リポジトリのルートである。呼び出し元は、実装のときに全て調べて変える。

| まとまり | ファイル |
|----------|----------|
| 規則 | `docs/05-開発規則/backend-coding.md` |
| 規則 | `docs/05-開発規則/glossary.md` |
| 規則 | `docs/03-機能仕様/glossary.md`（出荷通知メールの行） |
| 検査 | `backend/ecsite/src/test/java/com/ecsite/architecture/` の ArchUnit のテスト（新規か、既存の `AnnotationPlacementTest.java` への追加。実装のときに決める） |
| catalog | `backend/ecsite/src/main/java/com/ecsite/module/catalog/application/product/ProductImageService.java` |
| catalog | `backend/ecsite/src/main/java/com/ecsite/module/catalog/application/product/ProductPublicationService.java` |
| catalog のテスト | `backend/ecsite/src/integrationTest/java/com/ecsite/module/catalog/presentation/ProductImageServiceErrorApiTest.java` |
| order | `backend/ecsite/src/main/java/com/ecsite/module/order/application/checkout/CheckoutService.java` |
| order | `backend/ecsite/src/main/java/com/ecsite/module/order/application/checkout/OrderPlacementService.java` |
| order | `backend/ecsite/src/main/java/com/ecsite/module/order/application/checkout/QuoteService.java` |
| order | `backend/ecsite/src/main/java/com/ecsite/module/order/application/OrderCancellationService.java` |
| order | `backend/ecsite/src/main/java/com/ecsite/module/order/application/payment/OrderPaymentService.java` |
| order のテスト | `backend/ecsite/src/test/java/com/ecsite/module/order/application/checkout/OrderPlacementServiceTest.java` |
| order のテスト | `backend/ecsite/src/test/java/com/ecsite/module/order/application/checkout/QuoteServiceTest.java` |
| レベル B | `backend/ecsite/src/main/java/com/ecsite/module/shipping/application/ShipmentNotificationService.java` |
| レベル B | `backend/ecsite/src/main/java/com/ecsite/module/reporting/application/ReportCollectionService.java` |
| レベル B | `backend/ecsite/src/main/java/com/ecsite/module/storefront/application/StorefrontService.java` |
| レベル B | `backend/ecsite/src/main/java/com/ecsite/module/notification/application/ShipmentMailService.java` |
| レベル B のテスト | `backend/ecsite/src/test/java/com/ecsite/module/reporting/application/ReportCollectionServiceTest.java` |
| レベル B のテスト | `backend/ecsite/src/test/java/com/ecsite/module/storefront/application/StorefrontServiceShopTest.java` |
| レベル B のテスト | `backend/ecsite/src/test/java/com/ecsite/module/notification/application/ShipmentMailServiceTest.java` |

## 非機能要件

- **DN-476-1** 消費者・運営者に見える振る舞いを変えない。API・DB・メッセージを変えない。
- **DN-476-2** アーカイブした spec と記録（`docs/07-記録/`）を変えない。
- **DN-476-3** 改名はクラスの名前とその参照だけにし、メソッドの中身・トランザクションの付け方を変えない（今の付け方が、決定 2・6・7 と既に合っているため）。

## 依存

| 対象 | 関係 |
|------|------|
| #553（設定の record） | バックエンドの作業の順で先にマージする。実装は #553 のマージの後に始める。照合のときに全体のブランチを取り込み、改名の対象と呼び出し元を再び数える |
| #555 と、段階 3 の支払と注文の修正（#549 など） | 本 spec の後にマージする。後の修正が新しい名前で書けるように、名前を先に変える |
| #567（JDK 25） | #544 の全体のブランチにマージされたら、Gradle を使う前に取り込む |

## Non-goals

| 項目 | 理由 |
|------|------|
| 注文の更新の service を 1 つにまとめる | 決定 4 のとおり |
| 読み書きが混ざるレベル B の service の分割 | 既に読み取りと書き込みが混ざり、参照と更新のどちらの名前にも当てはまらないため（issue #476 のコメント） |
| ハンドラー・ジョブなど、service 以外の名前の自動の検査 | レビューに頼る規則を検査にする作業は #562 で扱うため |

## Open questions

なし。

## tasks

実装は実装役（汎用）に 1 タスクずつ任せる。

- [x] **T476-01-01** 規則の文書を改める（DC-476-1）。
  - 依存：開始の合図（#553 をマージした後）と、実装を始める前の照合
  - 範囲：規則の 2 文書（バックエンドの書き方、開発の用語集）。機能仕様の用語集は、識別子がコードにあることを検査が確認するので、T476-01-05 で変える
  - 確認方法：skill `docs` の「進め方」の手順 4 の検査
- [x] **T476-01-02** [テスト] ArchUnit のテストを書く。検査の対象は service の名前の末尾と、トランザクションの付け方である。今のコードで失敗することを確認する（DC-476-2、DC-476-3、DC-476-4）。
  - 依存：T476-01-01。#567 が全体のブランチにあれば、取り込んだ後
  - 範囲：`backend/ecsite/src/test/java/com/ecsite/architecture/`
  - 確認方法：対象のテストだけを実行し、改名の対象の 11 個の service が違反として報告されることを確認する
- [ ] **T476-01-03** catalog の service を改名する（DC-476-2、DC-476-5）。
  - 依存：T476-01-02
  - 範囲：catalog の service、テストのクラス（統合テストを含む）、呼び出し元
  - 確認方法：コンパイルと、catalog のテスト
- [ ] **T476-01-04** order の service とテストのクラスを改名する（DC-476-2、DC-476-5）。
  - 依存：T476-01-02
  - 範囲：order の service、テストのクラス、呼び出し元
  - 確認方法：コンパイルと、order のテスト
- [ ] **T476-01-05** レベル B の service とテストのクラスを改名する（DC-476-2、DC-476-5）。
  - 依存：T476-01-02
  - 範囲：レベル B の 4 つのモジュールの service、テストのクラス、呼び出し元、機能仕様の用語集
  - 確認方法：コンパイルと、T476-01-02 のテストが通ること
- [ ] **T476-01-06** 旧名の残りを検索し、全体の検査を通す（DC-476-5、DC-476-6）。
  - 依存：T476-01-03〜05
  - 範囲：リポジトリの全体
  - 確認方法：旧名で始まる識別子（テストのクラス名を含む。アーカイブと記録を除く）が `git grep -E` で 0 件であること、`./gradlew check` が通ること（全体を通して同時に 1 つだけ実行する検査なので、issue 担当のセッションが実行を許可してから行う）
- [ ] **T476-01-07** spec の最後のレビュー。指摘を修正し、draft の pull request を作る。
  - 依存：T476-01-06
- [ ] **T476-01-08** pull request の前のレビュー。指摘を修正し、draft を外す。
  - 依存：T476-01-07
- [ ] **T476-01-09** spec をアーカイブに移す。
  - 依存：T476-01-08
