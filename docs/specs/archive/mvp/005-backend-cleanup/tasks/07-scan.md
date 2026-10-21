# 段階 7：全体の走査

- 前提：段階 6
- 完了条件：DC-41。下の一覧の全ての項目に、修正したか、修正しない理由がある。`./gradlew check` が通る。
- 参照：[plan](../plan.md) 第 4.3 節、`docs/style/`

## タスク

- [x] **T5701** バックエンドの全体を `docs/style/` と本 spec の規則に照合して読み、見つけたもの（重複、使われていないコード、規則に合わない所）を下の「見つけたもの」に 1 項目ずつ追加する。読む量が多いので、別のセッションで行ってもよい。
- [x] **T5702** 見つけたものを修正する（項目ごとに commit）。修正しないものには理由を書く。

## 見つけたもの

（T5701 で追加する。最初から決まっているもの：）

- [x] カートと注文の両方にある `isAccessibleBy`：修正しない（モジュールの境界を越えて型を共有しない。constitution 6）。
- [x] 手動で書いた型の変換：修正しない（spec の Non-goals）。
- [x] `member/api/MemberApi`：T5502 の後、本番のコードから使われていない（統合テスト `MemberApiTest` だけが使う）。削除した。統合テストは `MemberService` を確認する形に移した（T5502 で見つけた）。
- [x] `common/paging/Sorting`：本番のコードから使われていない（並びの最後の ID は、各 Repository が並びの指定に直接書いている）。削除し、そのテストも削除した。
- [x] Javadoc のない項目とメソッドが 21（`ImageFormat` の形式の印 7、`MediaApiDirectory`・`JooqAssetRepository`・`S3ObjectStorage` の @Override のメソッド、`MediaService.requestImageUpload`、`ApiPathConfig.configurePathMatch`）。書いた（コメントだけの commit）。
- [x] 日時の変換（`Instant` と API の `OffsetDateTime`）が、4 つの mapper（商品、税、管理と店舗の注文）に同じ形である。共通の部品（`common/presentation/ApiDateTimes`）にした。
- [x] 店舗の API の価格（`Price`）を作る変換が、注文と店舗の mapper に同じ形である。共通の部品（`common/presentation/StorePrices`）にした。
- [x] 税率の正規化（末尾の 0 を除く）が、税の `RatePercent` と注文の `JooqOrderRepository` にある：修正しない（注文は税の domain の型を使わない。モジュールの境界を越えて型を共有しない。constitution 6）。
- [x] 規則の文書（第 5.2 節）は、レベル A の service の名前を `XxxCommandService`・`XxxQueryService` としている。order（`CheckoutService`、`OrderPlacementService` など）と、段階 5 で分けた catalog の service は、用途の名前を使っている。規則を、用途の名前も認める形に修正した。
- [x] `StorefrontService` に `@Transactional` がない：修正しない（自分の DB を持たず、ほかのモジュールの api を組み合わせるだけ。各 api の application が取引を持つ）。
- [x] 3 つの Factory の復元で `catch (Exception e)`：修正しない（DB の行から集約を作れない理由は何でも、復元のエラーとして記録する。意図した範囲）。
- [x] `Product.isVisibleAt`・`representativeImage` がテストだけから使われる：修正しない（表示の条件と代表画像の業務の規則を集約に持ち、参照の SQL（`ProductVisibility` など）と同じ結果になることをテストで確認している）。
- [x] `TaxRateScheduleCache.evict`、`MediaStorageProperties.withUploadUrlTtl` がテストだけから使われる：修正しない（テストで時刻と期限を動かすための窓口。Javadoc に理由がある）。
