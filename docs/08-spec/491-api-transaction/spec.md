# Spec 491：モジュールをまたぐ API のトランザクションの前提を、実行時に守る

種類：整理　規模：M　ステータス：確定

## 背景と目的

#458 のレビューで、モジュールの公開の API（`module/*/api/`）のトランザクションの前提が Javadoc にしかないことが挙がった。例として、支払の API の支払画面の URL（`pageUrl`）と売上確定（`capture`）は、トランザクションの外で呼ぶ前提である。中から呼んでも動作し、決済サービスの応答を待つ間、DB の接続と行ロックを保持し続ける。逆に、支払の作成（`create`）・取消（`cancel`）と、在庫の引当（`reserve`）・減算（`commit`）は、呼んだ側のトランザクションに加わる前提である。外から呼ぶと単独でコミットされる。実行時に守っているのは、メディアの画像の登録（`MediaService.registerImage` の `Propagation.MANDATORY`）だけである。

目的は、誤った呼び方を実行時の例外にすることである。消費者・運営者に見える振る舞いは変えない。本番のコードの呼び方は、今は全て前提に合っている（調べた結果は、「スコープ」の決定の後に書く）。追跡の issue は #491（全体の issue #544 の sub-issue。段階 2 の 5 番目）。

規模は、判定の表では 2 つ以上のモジュールにまたがるので L である。plan に書く中身がなく、API の契約と DB の構造を変えず、全てのモジュールに同じ機械的な基準を適用するので、特例で M にした（issue 担当のセッションが、利用者の委任で承認した）。振る舞いを変えず、注釈とテストの呼び方の整理だけなので、冒頭に「危険度：高」の行は書かない。

## スコープ

| 含める | 含めない |
|--------|----------|
| 支払・在庫・カート・出荷・メディアの公開の API について、トランザクションの前提を実行時の例外で守ること | イベントのリスナーと定期のジョブの伝播 |
| 前提を破っていたテストの呼び方の修正 | モジュールの中だけで使う service のメソッドの伝播 |
| 前提の基準を、規則の文書（バックエンドの書き方）に書くこと | 注釈と Javadoc の一致を機械で検査すること |

決定：

- 基準は、公開の API を持つ全てのモジュールに同じものを適用する。
  - 外部サービス（決済・画像の変換・ファイルの保存）を呼ぶメソッドは、トランザクションの外で呼ぶ前提にし、`Propagation.NEVER` で守る。根拠は、バックエンドの書き方の「トランザクションは application」の外部サービスの箇条（BR-40、AC-306）。
  - 呼んだ側の更新と一緒に確定しないと整合しない書き込みは、呼んだ側のトランザクションに加わる前提にし、`Propagation.MANDATORY` で守る。
  - 参照だけのメソッドと、DB も外部サービスも使わないメソッドは、前提を持たない（注釈を加えない）。
  - 呼んだ側のトランザクションがあれば加わり、なければ自分で始めるメソッド（メディアのアセットを解放する `release`）は、今の `@Transactional`（既定の伝播）のままにする。
- 注釈は、`api/` のクラスでなく、`api/` が呼び出す application の service のメソッドに付ける。
  - 理由：バックエンドの書き方の「トランザクションは application」が「api と presentation には付けない」と決めていて、ArchUnit（`AnnotationPlacementTest`）が確認している。対象の service のメソッドは、本番のコードでは `api/` からだけ呼ばれている。メディアの画像の登録が、すでにこの形（`MediaService.registerImage`）である。
  - 外部の呼び出しの service（`XxxGatewayService`）は、クラスに `@Transactional` を付けない規則（`NamingTest`）なので、メソッドに付ける。
- `api/` のメソッドの Javadoc に、前提を決まった文で書き、注釈と 1 対 1 にする。
  - `MANDATORY`：「呼んだ側のトランザクションに加わる（トランザクションの外から呼ぶと例外）。」
  - `NEVER`：「トランザクションの外で呼ぶ（トランザクションの中から呼ぶと例外）。」
  - 前提を持たないメソッドには、トランザクションの文を書かない。`StockReservationApi` のクラスの Javadoc にある「書き込みの操作は、呼んだ側のトランザクションに加わる」は、各メソッドの文に移す。
  - service のメソッドの Javadoc にも、同じ前提を書く。
- 前提を破っていたテストの呼び方は、テストの側でトランザクションを開始して修正する（`TransactionTemplate`）。同時実行のテストは、スレッドごとにトランザクションを開始する。
- ロールバックするテスト（クラスに `@Transactional`）では、テストデータの補助が本番の経路で「外」のメソッドを呼ぶと例外になる。呼ぶ前に未コミットのテストデータを読むので、トランザクションを開始しても中断しても修正できない。そこで、補助は「外」の経路を HTTP で通らずに、同じ状態を作る。補助の Javadoc に、その理由を書く。
  - 支払画面（`OrderTestData.openPaymentPage`）：mock の決済で支払画面を作り、`PaymentCommandService.recordPage` で記録する（`PaymentGatewayService.pageUrl` の中の処理を、テストのトランザクションの中で順に呼ぶ）。
  - 商品画像（`CatalogTestData` の画像の追加）：`MediaApi.prepareImage` は DB を読まないので、テストのトランザクションを中断して（`PROPAGATION_NOT_SUPPORTED`）呼び、登録は `ProductImageCommandService` でテストのトランザクションの中で行う。
  - 出荷（`ShippingTestData.ship`）：`OrderPaymentGatewayService.ship` の中の処理（売上確定の対象の取得、mock の決済での売上確定、`PaymentCommandService.markCaptured`、`OrderShipmentCommandService.ship`）を、テストのトランザクションの中で順に呼ぶ。
  - エンドポイント自身のテストは、HTTP のまま、コミットするテストで残す。支払画面の URL は `OrderPaymentApiTest`、画像の登録は `ProductImageApiTest` と `ProductImageTransactionApiTest`、出荷の登録は `ShipmentApiTest`。ロールバックするテストでエンドポイントを呼んでいる `ProductImageExternalErrorApiTest` と `ShipmentApiTest` は、コミットするテストに変える。
- 規則の文書は、バックエンドの書き方の「トランザクションは application」の節を書き換え、上の基準、注釈の場所、Javadoc の文を加える。前提を破る呼び方が例外になることは、メソッドごとの統合テストで確認する。基準を正しく適用しているかは、レビューで確認する。

対象のメソッドは次の表のとおり。「外」はトランザクションの外で呼ぶ前提（`NEVER`）、「加わる」は呼んだ側のトランザクションに加わる前提（`MANDATORY`）。

| モジュール | API のメソッド | 前提 | service のメソッド |
|------------|----------------|------|--------------------|
| 支払 | `PaymentApi.pageUrl` | 外 | `PaymentGatewayService.pageUrl` |
| 支払 | `PaymentApi.capture` | 外 | `PaymentGatewayService.capture` |
| 支払 | `PaymentApi.create` | 加わる | `PaymentCommandService.create` |
| 支払 | `PaymentApi.cancel` | 加わる | `PaymentCommandService.cancel` |
| 在庫 | `StockReservationApi.reserve` | 加わる | `StockReservationCommandService.reserve` |
| 在庫 | `StockReservationApi.release` | 加わる | `StockReservationCommandService.release` |
| 在庫 | `StockReservationApi.commit` | 加わる | `StockReservationCommandService.commit` |
| 在庫 | `StockReservationApi.restore` | 加わる | `StockReservationCommandService.restore` |
| カート | `CartContentsApi.removeItems` | 加わる | `CartService.removeItems` |
| 出荷 | `ShippingApi.create` | 加わる | `ShipmentService.create` |
| メディア | `MediaApi.prepareImage` | 外 | `MediaService.prepareImage` |
| メディア | `MediaApi.discardImage` | 外 ※ | `MediaService.discardImage` |
| メディア | `MediaApi.registerImage` | 加わる（済み） | `MediaService.registerImage`（変えない） |

※：今の Javadoc には前提がない。ファイルの保存は外部サービスなので、基準を適用して「外」にし、Javadoc にも前提を書く。

前提を持たない（変えない）メソッドは次のとおり。

- 全ての参照のメソッド（カタログ・在庫・注文・支払・出荷・地域・税・メディアの URL）。
- `MediaApi.requestImageUpload`：署名付き URL を作るだけで、DB も外部サービスも使わない。
- `MediaApi.release`：既定の伝播のまま。
- `ShippingApi.carrierNameKey`。
- `TaxRateApi` の計算。
- 会員の `CurrentMemberIds`：presentation からだけ呼ぶ。参照と作成は、それぞれ別のトランザクションで実行する。

本番のコードの呼び出し元は、全て前提に合っている（DN-491-1 の根拠）。

- 「加わる」のメソッドは、注文モジュールの更新の service（`CheckoutCommandService` など、クラスに `@Transactional`）と、商品カタログの `ProductImageCommandService` から呼ばれている。
- 「外」のメソッドは、`OrderPaymentGatewayService` と `ProductImageGatewayService` から、トランザクションの外で呼ばれている。
- 前提を破る呼び方は、`api/` を直接呼ぶ統合テスト（在庫の `StockOrderApiTest`、支払の `PaymentApiTest`、出荷の `ShippingApiTest`、テストデータ `PaymentTestData` など）にある。対象のファイルは実装で確定する。
- メディアの `MediaApiTest` には、画像の登録をトランザクションの外から呼ぶと `IllegalTransactionStateException` になることを確認するテストがすでにある。新たに注釈を付けるメソッドにも、同じ形のテストを追加する。

## 目的と完了条件

### DS-491-1 公開の API のトランザクションの前提を、誤った呼び方の例外で守る（D / P1）

- **DC-491-1** 決定の表の「外」のメソッドを、トランザクションの中から呼ぶと例外になる。
- **DC-491-2** 決定の表の「加わる」のメソッドを、トランザクションの外から呼ぶと例外になる。
- **DC-491-3** 前提のある全てのメソッドに、`api/` の Javadoc で決まった文があり、前提のないメソッドにはない（注釈と Javadoc が 1 対 1 である）。

### DS-491-2 前提の基準を、規則の文書で読めるようにする（D / P1）

- **DC-491-4** バックエンドの書き方の「トランザクションは application」に、基準、注釈の場所、Javadoc の文がある。

### DS-491-3 既存の検査が通る（D / P1）

- **DC-491-5** `./gradlew check` と統合テストが通る（前提を破っていたテストの呼び方を修正したうえで、期待値は変えない）。

## 変える範囲

| まとまり | ファイル |
|----------|----------|
| service | `backend/ecsite/src/main/java/com/ecsite/module/{payment,inventory,cart,shipping,media}/application/` の、決定の表の service |
| 公開の API の Javadoc | `backend/ecsite/src/main/java/com/ecsite/module/{payment,inventory,cart,shipping,media}/api/` の、決定の表の API |
| 統合テスト | `backend/ecsite/src/integrationTest/java/com/ecsite/` の、対象のメソッドを呼ぶテストとテストデータ |
| 規則 | `docs/05-開発規則/backend-coding.md` |

## 非機能要件

- **DN-491-1** 消費者・運営者に見える振る舞いを変えない。本番のコードの呼び出し元は変えない（今は全て前提に合っているため）。前提に合わない呼び出し元が見つかったら、変えずに報告する。
- **DN-491-2** API の契約（OpenAPI）と DB の構造を変えない。
- **DN-491-3** 既存のテストの期待値を変えない。変えるのは、テストの側でトランザクションを開始する箇所だけである。

## 依存

| 対象 | 関係 |
|------|------|
| #547（在庫が商品カタログのトランザクションの中で同期で受けるイベントの例外。マージ済み） | 在庫のリスナー（`CatalogEventHandler`）がすでに `MANDATORY`。この spec では変えない ※1 |
| #550（入力の検査）、#501（前方一致の検索）。並行して進行中 | `backend-coding.md` を変える可能性がある。重ならない見込み ※2 |

※1：公開の API でなくイベントのリスナーであり、考え方（呼んだ側のトランザクションに加わる）は、この spec と同じである。

※2：この spec が変えるのは「トランザクションは application」の節だけで、他の範囲（presentation、catalog の検索、マイグレーション）とは重ならない。

## Non-goals

| 項目 | 理由 |
|------|------|
| イベントのリスナーと定期のジョブの伝播 | 公開の API でない。リスナーの規則は、バックエンドの書き方の外部サービスの箇条にすでにある |
| モジュールの中だけで使う service のメソッドの伝播 | issue の範囲は、モジュールをまたぐ API である |
| 注釈と Javadoc の一致を機械で検査すること | Javadoc はクラスファイルに残らず ArchUnit で読めない。統合テストとレビューで確認する |
| `api/` のクラスに注釈を付けられるよう規則を変えること | 規則（api に付けない）と ArchUnit の検査を保ち、メディアの既存の形に合わせるため |

## Open questions

なし。

## tasks

実装は実装役（汎用）に 1 タスクずつ任せる。

- [ ] **T491-01-01** [テスト] 前提を破る呼び方が例外になる統合テストを、決定の表のメソッドごとに書き、レッドを確認する（DC-491-1、DC-491-2）。
  - 依存：なし
  - 範囲：`backend/ecsite/src/integrationTest/java/com/ecsite/module/{payment,inventory,cart,shipping,media}/api/` のテスト
  - 確認方法：書いたテストだけを実行し、例外にならずに失敗することを確認する
- [ ] **T491-01-02** 決定の表の service のメソッドに伝播を付け、前提を破っていたテストの呼び方を修正する（DC-491-1、DC-491-2、DC-491-5）。
  - 依存：T491-01-01
  - 範囲：決定の表の service、統合テスト（呼び方）
  - 確認方法：T491-01-01 のテストと、変えたテストのファイルが通る。統合テストの全体は、issue 担当のセッションから重い検査の実行の合図を受けてから実行する
- [ ] **T491-01-03** `api/` と service の Javadoc を、決まった文にそろえる（DC-491-3）。
  - 依存：T491-01-02
  - 範囲：決定の表の API と service
  - 確認方法：表と Javadoc を並べて読む
- [ ] **T491-01-04** バックエンドの書き方の「トランザクションは application」を書き換える（DC-491-4）。
  - 依存：T491-01-03
  - 範囲：`docs/05-開発規則/backend-coding.md`
  - 確認方法：文書の検査（`doc-refs.mjs`、`ja-text.mjs`）
- [ ] **T491-01-05** `./gradlew check` と統合テストを通す（DC-491-5）。
  - 依存：T491-01-04
  - 範囲：`backend/ecsite/`
  - 確認方法：issue 担当のセッションから重い検査の実行の合図を受けてから実行する
- [ ] **T491-01-06** spec の最後のレビュー。指摘を修正し、draft の pull request を作る。
  - 依存：T491-01-05
- [ ] **T491-01-07** pull request の前のレビュー。指摘を修正し、draft を外す。
  - 依存：T491-01-06
- [ ] **T491-01-08** spec をアーカイブに移す。
  - 依存：T491-01-07
