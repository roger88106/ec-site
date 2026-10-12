# 段階 7：全体の走査

- 前提：段階 6
- 完了条件：DC-41。下の一覧のすべての項目に、直したか、直さない理由がある。`./gradlew check` が通る。
- 参照：[plan](../plan.md) 第 4.3 節、`docs/style/`

## タスク

- [x] **T5701** バックエンドの全体を `docs/style/` と本 spec の規則に照らして読み、見つけたもの（重複、使われていないコード、規則に合わない所）を下の「見つけたもの」に 1 項目ずつ足す。読む量が多いので、別のセッションで行ってもよい。
- [ ] **T5702** 見つけたものを直す（項目ごとに commit）。直さないものには理由を書く。

## 見つけたもの

（T5701 で足す。初めから決まっているもの：）

- [x] カートと注文の両方にある `isAccessibleBy`：直さない（モジュールの境界を越えて型を共有しない。constitution 6）。
- [x] 手で書いた型の変換：直さない（spec の Non-goals）。
- [x] `member/api/MemberApi`：T5502 の後、本番のコードから使われていない（統合テスト `MemberApiTest` だけが使う）。消した。統合テストは `MemberService` を確かめる形に移した（T5502 で見つけた）。
- [x] `common/paging/Sorting`：本番のコードから使われていない（並びの最後の ID は、各 Repository が並びの指定に直接書いている）。消し、そのテストも消した。
- [ ] Javadoc のない項目とメソッドが 21（`ImageFormat` の形式の印 7、`MediaApiDirectory`・`JooqAssetRepository`・`S3ObjectStorage` の @Override のメソッド、`MediaService.requestImageUpload`、`ApiPathConfig.configurePathMatch`）。書く（コメントだけの commit）。
- [ ] 日時の変換（`Instant` と API の `OffsetDateTime`）が、4 つの mapper（商品、税、管理と店舗の注文）に同じ形である。共通の部品にする。
- [ ] 店舗の API の価格（`Price`）を作る変換が、注文と店舗の mapper に同じ形である。共通の部品にする。
- [x] 税率の正規化（末尾の 0 を除く）が、税の `RatePercent` と注文の `JooqOrderRepository` にある：直さない（注文は税の domain の型を使わない。モジュールの境界を越えて型を共有しない。constitution 6）。
- [ ] 規則の文書（第 5.2 節）は、レベル A の service の名前を `XxxCommandService`・`XxxQueryService` としている。order（`CheckoutService`、`OrderPlacementService` など）と、段階 5 で分けた catalog の service は、用途の名前を使っている。規則を、用途の名前も認める形に直す。
- [x] `StorefrontService` に `@Transactional` がない：直さない（自分の DB を持たず、ほかのモジュールの api を組み合わせるだけ。各 api の application が取引を持つ）。
- [x] 3 つの Factory の復元で `catch (Exception e)`：直さない（DB の行から集約を作れない理由は何でも、復元のエラーとして記録する。意図した広さ）。
- [x] `Product.isVisibleAt`・`representativeImage` がテストだけから使われる：直さない（表示の条件と代表画像の業務の規則を集約に持ち、参照の SQL（`ProductVisibility` など）と同じ結果になることをテストで確かめている）。
- [x] `TaxRateScheduleCache.evict`、`MediaStorageProperties.withUploadUrlTtl` がテストだけから使われる：直さない（テストで時刻と期限を動かすための口。Javadoc に理由がある）。
