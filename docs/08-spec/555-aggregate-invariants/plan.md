# Plan 555：集約の不変条件と同一性の判定を整える

ステータス：承認済み（tasks 作成済み）　対象：[spec.md](spec.md)

前提：spec 555 は 2026-11-18 に確定した（issue 担当が利用者に代わって承認。規模 L。危険度は高にしない）。spec の Open questions の決定（3 つとも案 A）を、本書の各節に反映した。equals を Lombok で作る規則は変えない。読む文書は[文書の書き方](../../05-開発規則/writing.md)、[文書の構成](../../05-開発規則/doc-structure.md)、[文書の記号と番号](../../05-開発規則/notation.md)。

| 本書の節 | 主な担当 |
|----------|----------|
| 規則の文書 | DS-555-1、DS-555-2（DC-555-1、DC-555-9） |
| entity と集約の同一性 | DS-555-2（DC-555-7、DC-555-8） |
| テストの等価の検査 | DS-555-2（DC-555-10） |
| 不変条件の置き場所 | DS-555-1（DC-555-2〜6） |
| 段階と変えるファイル | 全て |
| 利用者に確認すること | [spec](spec.md)の「Open questions」（なし）と、plan の方法の確認 |

共通の決定：

- 1 タスクを 1 commit にする。
- 金額（明細の小計、金額の合計）と支払の状態を扱うタスクに `[リスク]` を付け、高性能のモデルに任せる（[エージェントの作業フロー](../../05-開発規則/subagents.md)の「任せる作業とモデル」）。それ以外の実装は汎用のモデルに任せる。
- 重い検査（`./gradlew check`、`scripts/ci/check-all.sh`、E2E）は、並行する全てのセッションを通して同時に 1 つだけ実行する。
- 重い検査は、実行の前に issue 担当に知らせ、合図を待つ。
- `./gradlew --stop` は実行しない。
- 途中の確認は、変えたモジュールのテストのクラスを指定して実行する。
- 実装は、#476 が全体のブランチにマージされた後の、issue 担当の開始の合図の後に始める。
- 実装を始める前に、全体のブランチを取り込み、実装を始める前の照合をする。
- #567 が入った後は、Gradle の JDK が 25 になる。
- 次は、文書や知識によるもので、実行して確認していない。確認するタスクを添える。
  - `-Werror` のとき、Lombok の callSuper の警告で失敗になること（T555-01-04 で確認する）
  - AssertJ の `assertThat(T)` の呼び出しで、引数の静的な型がクラスファイルに残らないこと（T555-01-06 の試作で確認する）
  - 新しい依存のライセンスが、ライセンスの検査に通ること（T555-01-06 で確認する）
- 本番のコードに参照の同一性に頼る所がないことは、spec の調査（grep による）の結果で、T555-01-01 で確認し直す。
- レビューの profile は厚め（issue 担当の指定）。
- 最初の push は、draft の pull request を作る直前にする。pull request の向き先は全体のブランチ `agent/544-cleanup-before-release`。
- 段階の順は、段階 1 で規則と同一性、段階 2 で不変条件にする。理由：record を class に変えるとコンストラクターを書き換えるので、不変条件の検査を加える前に変えておくと、同じコンストラクターを 2 回書き換えずに済むため。規則の文書は段階 1 の最初に書く（規則の文書を先に修正する。#475 の本文）。

## 規則の文書（DS-555-1：DC-555-1、DS-555-2：DC-555-9）

| 何を | どこに | 確認方法 |
|------|--------|----------|
| 不変条件の置き場所の 3 つの規則（spec の DC-555-1）を、新しい節に書く | `docs/05-開発規則/backend-coding.md` の「入力の検証とエラー」の下 | 文書を読む（この規則の確認方法はレビュー） |
| entity と集約を record にしない規則を加える | `docs/05-開発規則/backend-coding.md` の「entity と集約の同一性」 | 文書を読む |
| テストで entity と集約を等価で比べず、項目ごとか `usingRecursiveComparison` で比べる規則を書く | `docs/05-開発規則/test-writing-backend.md` の「テストの本文の書き方」 | 文書を読む |

- 確認方法を自動に変える時期：「entity と集約の同一性」の規則は `EntityEqualityTest` が通った後（T555-01-05）、テストの等価の規則は検査が動いた後（T555-01-07）。T555-01-02 では規則の本文だけを書き、確認方法はレビューにしておく。理由：検査が組み込めなかったとき、文書が誤りにならないようにするため。
- record にしない理由：Lombok の `@EqualsAndHashCode` を record に付けられず、record の `equals` は全ての項目で比べるため。
- 規則の文書には、コードが変わると修正する必要のある具体的なクラス名を書かない（[書き方の規則とレビュー](../../05-開発規則/review.md)の「規則の書き方」）。

## entity と集約の同一性（DS-555-2：DC-555-7、DC-555-8）

| 何を | どこに | 確認方法 |
|------|--------|----------|
| 識別子の項目に `@EqualsAndHashCode.Include` を付け、クラスに `@EqualsAndHashCode(onlyExplicitlyIncluded = true)` を付ける | 下の表の 14 個 | ArchUnit のテスト（`EntityEqualityTest`） |
| record の 7 個を final の class に変える | 下の表の record の行 | コンパイルと既存のテスト |
| レベル B の 4 個に jMolecules の注釈（`@org.jmolecules.ddd.annotation.AggregateRoot`）と、識別子の項目に `@Identity` を付ける | Cart、Shipment、Asset、Member | `EntityEqualityTest` |
| 基底クラス（`AggregateRoot`）を比較に含めない（callSuper = false）。各クラスに書かず、`lombok.equalsAndHashCode.callSuper = skip` を置く | `backend/ecsite/lombok.config` | コンパイル（`-Werror` で Lombok の警告が失敗になる） |
| 注釈の付いたクラスが、record でなく、`equals` と `hashCode` を宣言することを検査する | `backend/ecsite/src/test/java/com/ecsite/architecture/EntityEqualityTest.java` | テスト |
| 参照の同一性か record の全項目の等価に頼る所と、その対応の一覧を書く | tasks の T555-01-01 の結果と pull request | 文書を読む |

- 注釈の付いたクラス：jMolecules の Entity・AggregateRoot の注釈が付いたクラスと、基底クラスを継承するクラス。
- class に変えるとき、getter は他の集約と同じく `@Getter` と `@Accessors(fluent = true)` で作り、名前（`id()` など）を変えない。コンストラクターは公開のまま残す。

対象の 14 個：

| モジュール | 型 | 今 | 変えること |
|------------|----|----|------------|
| catalog | Category、Product | class | Lombok の equals |
| catalog | ProductImage、Variant | record（@Entity） | class に変える |
| inventory | StockAdjustment | record（@Entity） | class に変える |
| order | Order | class | Lombok の equals |
| order | Checkout | record（@AggregateRoot） | class に変える |
| payment | Payment | class | Lombok の equals |
| tax | TaxCategory | class | Lombok の equals |
| tax | TaxRate | record（@Entity） | class に変える |
| cart | Cart | class、注釈なし | 注釈を付ける |
| shipping | Shipment | class、注釈なし | 注釈を付ける |
| media | Asset | record、注釈なし | 注釈を付け、class に変える |
| member | Member | record、注釈なし | 注釈を付け、class に変える |

record を class に変えるときの呼び出し元（[文書の構成](../../05-開発規則/doc-structure.md)の「呼び出し元」）：

| 型 | コンストラクターを `new` で呼ぶファイルの数 ※1 |
|----|------------------------------------------------|
| Checkout | 3 |
| StockAdjustment | 2 |
| TaxRate | 4 |
| Variant | 8 |
| ProductImage | 6 |
| Asset | 3 |
| Member | 2 |

※1：main・test・integrationTest・testFixtures の合計。

- getter の名前は変えないので、getter の呼び出しは変わらない。
- jOOQ の `into(型.class)` でのマッピングと、record のパターン（`instanceof X(...)`、`case X(...)`）で使う所はない。
- 全項目の等価に頼るテストは 6 ファイルある（spec の「同一性の判定の調査の結果」）。項目ごとか `usingRecursiveComparison` で比べる形に書き換える。
- 本番のコードに参照の同一性に頼る所があるかは、spec の「同一性の判定の調査の結果」を参照する。

理由：

- 基底を比較に含めない理由：基底の項目はイベントの一覧（永続化しない）だけで、同一性に関わらないため。
- `lombok.config` で一括にする理由：各クラスに書くと、規則の注釈の形（`@EqualsAndHashCode(onlyExplicitlyIncluded = true)`）から外れるため。
- レベル B の 4 個に注釈を付ける理由：注釈を手がかりに ArchUnit のテストで規則を自動で検査するため（spec の Open questions の決定）。

## テストの等価の検査（DS-555-2：DC-555-10）

| 何を | どこに | 確認方法 |
|------|--------|----------|
| Error Prone の独自の検査（`EntityEqualityAssertion`）を作る。テストのソースセット（test、integrationTest、testFixtures）のコンパイルだけで有効にし、エラーにする | 新しいソースセット `backend/ecsite/src/errorprone/java/` | 検査のテストと、テストのソースセットのコンパイル |
| 検査のテストを書く。下の例の表を全て入れる | `backend/ecsite/src/test/java/com/ecsite/errorprone/EntityEqualityAssertionTest.java`（Error Prone の `CompilationTestHelper`） | テスト |
| 依存（`error_prone_check_api` と `error_prone_test_helpers`）を加える。ライセンスは Apache-2.0 で、依存とライセンスの区分に合う | `gradle/libs.versions.toml`、`build.gradle.kts` | ライセンスの検査（CI） |

検査の置き場所：

- 検査のクラスの登録（`META-INF/services`）は、新しいソースセットに置く。
- `build.gradle.kts` で、そのソースセットの出力を `errorprone` の構成に加える。

判定：

- 対象は、静的な型が entity・集約である式。entity・集約は、jMolecules の Entity・AggregateRoot の注釈が付いたクラスと、基底クラスを継承するクラス。
- AssertJ の `assertThat(x)` の x が対象のとき、続く `isEqualTo`・`isNotEqualTo`・`isIn`・`isNotIn` を検出する。
- AssertJ の `assertThat` の引数が要素の一覧で、要素の型が対象のとき、続く `contains`・`containsExactly`・`containsExactlyInAnyOrder`・`containsOnly`・`doesNotContain` など、要素の等価で比べるメソッドを検出する。
- JUnit の `assertEquals`・`assertNotEquals` の引数が対象のときを検出する。
- 間に `usingRecursiveComparison()`・`usingElementComparator(...)` など、比較の方法を替える呼び出しがあれば、検出しない。
- 判定の区分は、誤りの防止（誤って書いたものを拒否し、意図して作った回避は対象にしない）にする（[文書の構成](../../05-開発規則/doc-structure.md)の「形を判定する検査」）。

例（検出すべき例と検出してはいけない例）：

| 例 | 検出 | 理由 |
|----|------|------|
| `assertThat(order).isEqualTo(expected)`（どちらも注文） | する | 識別子だけで比べ、項目の違いを見落とす |
| `assertThat(product).isNotEqualTo(other)` | する | 識別子が違うことだけを見て、項目の違いを確認しない |
| `assertThat(images).containsExactly(new ProductImage(...))`（要素が商品の画像） | する | 要素の等価が識別子だけになる |
| `assertThat(checkouts).contains(checkout)` | する | 要素が識別子だけで一致し、項目の違いを見落とす |
| `assertEquals(expectedPayment, payment)` | する | JUnit の比較も、識別子だけで一致する |
| `assertThat(order.id()).isEqualTo(id)` | しない | 値は識別子（UUID）で、entity でない |
| `assertThat(order).usingRecursiveComparison().isEqualTo(expected)` | しない | 項目ごとに比べる |
| `assertThat(images).extracting(ProductImage::id).containsExactly(id1)` | しない | 要素は識別子 |
| `assertThat(payment.toSnapshot()).isEqualTo(before)` | しない | スナップショットは record の値で、entity でない |
| `assertThat(order).isSameAs(loaded)` | しない | 参照の同一性の確認で、等価でない |
| main のコードの `equals` の呼び出し | しない | 対象はテストのソースセットだけ |

理由：

- ArchUnit で書かない理由：ArchUnit はクラスファイルの呼び出しを見るが、`assertThat(T)` は型引数のため、呼び出しのメソッド記述子では引数が Object になり、静的な型が分からないため。
- ソースの文字で判定する script（`scripts/ci/java/java-rules.mjs` への追加）にしない理由：変数の型が分からず、漏れが多いため。
- Error Prone を使う理由：コンパイラーの型の情報で判定でき、既にコンパイルで動いているため。

## 不変条件の置き場所（DS-555-1：DC-555-2〜6）

| 何を | どこに | 確認方法 |
|------|--------|----------|
| 対象の型の検査を、コンストラクター（class は手書きのコンストラクター、record はコンパクトコンストラクター）に移す・加える | [spec](spec.md)の「検査を変える型」 | コンストラクターを直接呼ぶ単体テスト（違反を拒む、境界の値を受け付ける） |
| ファクトリーメソッドから移した検査は、今と同じ例外（業務のエラーとエラーコード）を投げる。配送先の正規化はファクトリーメソッドに残し、コンストラクターは正規化の後の形を検査する | 移す型（配送先、在庫の調整、税率の一覧、追跡番号、集計の期間、注文のメールアドレス） | 既存のテスト（API の応答のテストを含む） |
| 新しく加える検査は `IllegalArgumentException` を投げ、メッセージに値を入れない（項目の名前だけ） | 加える型 | 単体テスト |
| 復元の全体を、集約ごとに 1 つの `RestoreGuard.restore` の中で行う。中で作る型は、spec の「復元の経路の調査の結果」の型 | 注文・注文手続き・支払・出荷のリポジトリ、商品・税区分のファクトリーの `restore` | `*RestoreFailureTest` の形のテスト（違反する行で復元のエラーになり、出力に DB の値がない） |
| `RestoreGuard.restore` の中で、既に復元のエラーになった例外は、そのまま投げる（例外を二重に変換しない） | `common/model/base/RestoreGuard.java` | `RestoreGuardTest` |
| 注文と支払の、状態と項目の整合の表は、状態を変えるメソッドの遷移から作る。注文は T555-02-03、支払は T555-02-04 の結果に書く | `Order`、`Payment` | 単体テストと、段階の最後のレビューで遷移と照合する |
| 商品の 4 つの条件をコンストラクターで検査する。状態を変えるメソッドの今の検査（業務のエラー）は、操作の前の判定として残す | `Product` | 単体テストと、既存の API のテスト |
| ローカルの seed と E2E のデータに違反する行がないかを読む。違反があれば直さずに止めて報告する | `backend/ecsite/src/main/resources/db/seed/`、`e2e/src/data.ts` | ローカルの seed で起動し、店舗画面と管理画面を開く |

理由：

- 移した検査の例外を変えない理由：API の応答（エラーコード）を変えないため（DN-555-1）。
- 新しい検査を `IllegalArgumentException` にする理由：入力からは起きず、起きればプログラムの誤りか DB の値の誤りで、今の domain も同じ例外で投げているため（`Category` のコンストラクターの深さの検査など）。
- 新しい検査で業務のエラーコードを増やさない理由：増やすと `messages.properties` が増えるため。
- 復元の全体を `RestoreGuard.restore` の中で行う理由：spec の「復元の経路の調査の結果」の型が `RestoreGuard.restore` の外で作られ、違反が復元のエラーにならないため。
- 既に復元のエラーになった例外をそのまま投げる理由：外側でもう一度変換すると、既存の復元の失敗のテストの期待（例外の種類とメッセージ）が変わるため。
- 状態の整合の表を、注文と支払のテストのタスクで作る理由：遷移のメソッドが多く、表がないとテストの漏れが分からないため。

## 段階と変えるファイル

| 段階 | ファイル | 内容 | DC | 開始の条件 |
|------|----------|------|----|------------|
| 1 | `tasks/01-identity.md` | 規則の文書、同一性、テストの等価の検査 | DC-555-1、DC-555-7〜10 | 開始の合図と、実装を始める前の照合 |
| 2 | `tasks/02-invariants.md` | 不変条件、復元、seed の確認、全体の検査 | DC-555-2〜6、DC-555-11、DC-555-12 | 段階 1 の完了 |

| まとまり | ファイル |
|----------|----------|
| 規則 | `docs/05-開発規則/backend-coding.md`、`docs/05-開発規則/test-writing-backend.md` |
| domain | `backend/ecsite/src/main/java/com/ecsite/module/{catalog,inventory,order,payment,tax,cart,shipping,media,member,reporting}/domain/` |
| 復元 | 各モジュールの `infrastructure/persistence/` のリポジトリ、`common/model/base/RestoreGuard.java` |
| 検査 | `backend/ecsite/src/errorprone/java/`、`backend/ecsite/src/test/java/com/ecsite/architecture/EntityEqualityTest.java`、`build.gradle.kts`、`gradle/libs.versions.toml`、`lombok.config` |
| テスト | 対象の型の単体テスト、`*RestoreFailureTest`、`RestoreGuardTest` |

並行する spec との重なり：

- #493（集約の操作の戻り値と保存）が同じ集約のクラスを変える。#493 は本 spec の後に実装する。
- #476 は service だけを変え、本 spec は service を変えない。
- #553（設定の record）が、承認の期間を作る設定の型を変えうる。#553 はバックエンドの線で本 spec より先に入る。#553 が全体のブランチに入った後に T555-02-04 を実装し、その時点の設定の型に合わせる。

## 利用者に確認すること

| # | 確認 | 案 | 推奨と理由 |
|---|------|----|------------|
| 1 | テストの等価の検査の方法（DC-555-10） | 案 A：Error Prone の独自の検査。案 B：ソースの文字で判定する script。案 C：レビューだけにする（DC-555-10 を変える） | 案 A。型の情報で判定でき、漏れと誤検出が少ないため。試作で組み込めなければ止めて報告する |
