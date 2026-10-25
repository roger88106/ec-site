# Plan 186：バックエンドの構成の検査を強める

ステータス：承認済み（tasks 作成済み）　対象：[spec.md](spec.md)

前提：spec 186 が確定した。文書の書き方は skill `docs` と[開発の進め方](../../rules/workflow.md)、番号と記号は[文書の記号と番号](../../rules/notation.md)。レビューは skill `review`。

| 対象 | 本書 | 主な担当 |
|------|------|----------|
| 構成の検査の補助と読み込み | 第 1 節 | DS-186-3（DC-186-8〜10、12） |
| 構成の規則の検査 | 第 2 節 | DS-186-1（DC-186-1〜4）、DS-186-2（DC-186-5〜7） |
| 設定と契約の補助、並び順の既定値 | 第 3 節 | DS-186-3（DC-186-11）、DS-186-5（DC-186-15） |
| テストのコメントの spec の番号 | 第 4 節 | DS-186-4（DC-186-13、14） |
| 文書 | 第 5 節 | 全て |
| 段階と確認の方法 | 第 6 節、第 7 節 | 全て |
| 利用者に確認すること | 第 8 節 | DC-186-15 |

表の DC の列は、番号だけを書く（DC-186-1 を「1」と書く）。

共通の決定：

- パスは、断りがなければ `backend/ecsite/src/` からの相対で書く。`main/`、`test/`、`it/`、`fix/` は、`main/java/com/ecsite/`、`test/java/com/ecsite/`、`integrationTest/java/com/ecsite/`、`testFixtures/java/com/ecsite/` の略である。文書のパスはリポジトリのルートからの相対で書く。
- 行の番号は、2026-10-24 の `develop`（7b4312c6）のものである。本 spec は spec 181 のマージの後に実装する（spec 第 1.1 節）。実装を始める前の照合（[開発の進め方](../../rules/workflow.md) 第 5.6 節）で、次を確認し、差があれば最初のタスクの前に本書と tasks を修正する。
  - spec 181 の変更を取り込み、行の番号と、下の重なりの表を今のコードと合わせる。
  - テストの書き方 第 3 節の構成と、spec 181 が追記した補助の一覧の場所（第 5 節の追記先を決める）。
  - spec 182、183 がマージされていれば取り込み、設定のテスト（第 3.1 節）と、検査の script の共通の関数（第 4 節）を今のコードと合わせる。マージされていなければ、そのまま進め、後でマージする側が解消する。
  - 照合の結果（2026-10-26。`develop` の f8f2b29a を取り込んだ）：spec 181〜184 と #164 はマージ済み。設計が変わる差はない。直したもの：下の重なりの表の (1)、(3)、(5) の扱いを結果に合わせた。第 4 節は spec 183 が作った `stripJavaComments` を使う形にした。第 5 節のテストの書き方の追記先を、テストの書き方 第 3.3 節に決めた。architecture・modulith・contract の検査と設定のテストの行の番号は、承認の時から変わっていない（`AdminRealmPermissionContractTest` はテスト名だけが変わった）。spec 182 が足した設定のテスト（`ImgproxyPropertiesTest` など 3 つ）は `Binder` を使わないので、第 3.1 節の対象は変わらない。
- 補助を先に作り、後の段階で追加する検査は補助を使う。理由：検査を追加してから補助に移すと、同じテストを 2 回書き換えるため。
- 補助へ移すタスクは、テストの確認を変えない（DN-186-4）。理由：移動と確認の変更を同じ commit に混ぜると、テストが弱まっていないことを差分で確認しにくいため。
- 追加・修正した構成の検査は、違反する例のクラスで違反を検出できることを確認する（DC-186-7）。例のクラスは、今の `NamingTest` などと同じく、テストのクラスの入れ子のクラスにし、`new ClassFileImporter().importClasses(...)` で読む。例のクラスの読み込みは、本番のクラスの読み込みでないので、DC-186-8 の対象外である。
- 追加した検査が今のコードで違反を見つけたときは、DN-186-3 のとおり扱う。違反が規則の文書の本文に明らかに反するもの（例：表にない層への依存）は、同じ段階にタスクを追加し、コードを規則に合わせて修正する。規則の文書が例外を認めているか判断できないもの、または修正するとモジュールの設計が変わるものは、作業を止めて利用者に確認し、例外の表に理由を書くかを決める。
- 段階 1〜3 で書くテストのコメントに、spec・plan・タスクの番号を書かない。理由：段階 4 で検査をテストのコメントに広げるため。
- API の契約（`api/openapi/`）とマイグレーション（`db/migration/`）を変えない（DN-186-1）。AC を変えない（DN-186-2）。確認方法は、最後の段階のレビューで、spec の最初の commit からの差分にこの 2 つのディレクトリと AC 一覧のファイルがないことを確認する。
- 実装は実装役（汎用）に 1 タスクずつ任せる。`[リスク]` のタスクはない。理由：本番のコードの変更は並び順の既定値（DC-186-15）だけで、振る舞いを変えず、既存のテストが確認するため。
- 1 タスクを 1 commit にする。例外は `[テスト]` と実装の組で、2 つのタスクを 1 commit にする（[開発の進め方](../../rules/workflow.md) 第 2.1 節）。

spec 181 との重なりの扱い（spec 第 7 節の (1)〜(5)）：

| 重なり | 扱い |
|--------|------|
| (1) エラーの種類の検査を `fix/` に移す（spec 181） | 本 spec の契約の補助（第 3.1 節）は `test/contract/` の中だけで使う。`fix/` に移った検査（`fix/config/security/ErrorCodeCheck`）は、契約をクラスパスから読み、既定の置き場所を持たないので、変えない（照合で確認した） |
| (2) 管理の realm の権限の契約テストのテスト名（spec 181） | テスト名は spec 181 の結果のまま変えない。本 spec は置き場所の読み取りだけを変える |
| (3) 起動のテストの schema の一覧（spec 181） | spec 181 は、起動のテスト（`it/EcSiteApplicationTests`）に schema の一覧を直接書き、共有の補助にしなかった（照合で確認した）。本 spec でも共有しない。理由：`SchemaOwnershipTest` は単体テスト、起動のテストは統合テストで、ソースセットが違うため |
| (4) コメントの書き換え | 段階 4（第 4 節）で行う。spec 181 の後なので、衝突はない |
| (5) テストの書き方の補助の一覧 | spec 181 がテストの書き方 第 3.2 節に追記した表は、統合テストのテストデータの補助の一覧である。本 spec の単体テストの補助はテストデータを作らないので、その表に加えず、テストの書き方 第 3.3 節（テストの置き場所）に書く（第 5 節） |

## 1. 構成の検査の補助と読み込み（DS-186-3：DC-186-8〜10、12）

### 1.1 本番のクラスの補助（DC-186-8、10）

新しい補助 `test/ProductionClasses`（public。`architecture/`、`modulith/`、`contract/`、`common/message/` から使うので、今の `MutableClock` などと同じく `test/` の直下に置く）に、次を置く。

| 要素 | 内容 | 今の置き場所 |
|------|------|--------------|
| `all()` | `com.ecsite` の本番のクラス（テストを除く）。生成したコードを含む | `ModuleStructureTest:81`、`SchemaOwnershipTest:64`、`MessageContractTest:67`、`ErrorCodeContractTest:82` |
| `handwritten()` | `all()` から生成したコード（`com.ecsite.api`、`com.ecsite.jooq`）を除いたもの | `ConfigurationPropertiesTest:25`、`CurrentTimeTest:31`、`NamingTest:25`、`StaticOnlyClassTest:25` |
| `modules()` | `com.ecsite.module` の本番のクラス | `MoneyTypeTest:43`、`ModuleRoleTest:85` |
| `applicationModules()` | Spring Modulith のモジュールの一覧（テストのクラスを除く） | `ModulithTest:27`、`ModuleDiagramTest:47` |
| `TEST_CLASSES` | モジュールの一覧から除くテストのクラスの条件 | `ModulithTest:24` |
| `LEVEL_A_MODULES` | レベル A のモジュールの一覧 | `ModuleStructureTest:52`、`ModuleRoleTest:27` |
| `modulesWith(String... layers)` | 指定した層のどれかに本番のクラスがあるモジュールの名前 | `ModuleStructureTest`（`modulesWith`）、`ModuleRoleTest`（`modulesWithPresentation`） |
| `moduleName(String packageName)` | パッケージの名前からモジュールの名前を取り出す | `ModuleStructureTest:62`（`MODULE_SUBPACKAGE`）、`SchemaOwnershipTest`、`MessageContractTest` の正規表現 |
| `isGenerated(String packageName)` | 生成したコードのパッケージかを返す | `ModuleStructureTest:290` と、`handwritten()` の 4 つの読み込みの条件 |
| `requireNotEmpty(Collection<?> targets, String description)` | 絞り込んだ検査の対象が 1 件以上あることを確認し、0 件なら説明を付けて失敗する（DC-186-6） | なし（新規。`NamingTest:74` の `isNotEmpty()` の確認を一般にしたもの） |

- 読み込みは、遅延の static（holder のクラス）で、JVM ごとに 1 回だけ行う。`handwritten()` と `modules()` は、`all()` の結果を絞って作り、読み直さない。理由：今は `ClassFileImporter` で 10 か所、`ApplicationModules.of` で 2 か所が読み込み、多くを static に持つので、同じクラスの複製が heap に重ねて残るため。
- ArchUnit の JUnit 5 の仕組み（`@AnalyzeClasses` の結果の再利用）は使わない。理由：全ての検査を `@ArchTest` の形に書き換える必要があり、`assertThat` で一覧を確認する今の検査（パッケージの大きさなど）が合わないため。
- `ModuleRoleTest:79-84` の「static に持たない」理由のコメントは、補助を使うので削除する。
- レベル A の一覧は、設計書と一致することを補助のテストで確認する。設計書（`docs/design/modules/<モジュール>.md`）は、7 行目付近の表の行 `| レベル | A（…） |` にレベルを書いている。テストは、設計書のディレクトリを system property `design.modules.dir` で受け取り、各設計書の「レベル」の行の最初の文字が A のモジュールの集合が、`LEVEL_A_MODULES` と一致することを確認する。理由：一覧が 1 か所になっても、設計書と食い違えば検査の範囲がずれるため。
- `design.modules.dir` は、`build.gradle.kts` の `unitTestSystemProperties` に追加し、test の task と pitest の `inputs` に設計書のディレクトリを宣言する（`design.overview` と同じ。ないと、設計書を変えてもテストが再実行されない）。
- 確認方法：`./gradlew test`。最後の段階のレビューで、`test/` の中で `new ClassFileImporter()` を本番のパッケージに対して呼ぶ所と `ApplicationModules.of` を呼ぶ所が、補助の中だけであることを検索で確認する。

### 1.2 heap の上限（DC-186-9）

1.1 の後、単体テストの heap の上限（`build.gradle.kts` の `unitTestMaxHeap`。test の task の `maxHeapSize` と、pitest の `jvmArgs` の `-Xmx` が使う）を測って決める。

- 測り方：`unitTestMaxHeap` を 768m、512m の順に一時的に変え、それぞれで `./gradlew test --rerun-tasks` と `./gradlew pitest` を実行する。
- 決め方：両方が通る最小の値に 256m を加えた値にする（今の 1024m を超えない）。512m で通れば 768m、768m で通れば 1024m（今のまま）である。
- 1024m のままにするときは、`unitTestMaxHeap` のコメントに、測った値（通らなかった値と、落ちたテストか PIT）を書く。
- 測った値と結果は commit の本文に書く。
- 確認方法：`./gradlew check`、`./gradlew pitest` が通る。

### 1.3 モジュールの図のテスト（DC-186-12）

- `ModuleDiagramTest.render` を、`ApplicationModules` でなく、モジュールの名前、依存の組、イベントの組を持つ小さな record（`Diagram` など。テストのクラスの入れ子）から描く形に分ける。`ApplicationModules` から record を作る処理と、record から描く処理を別のメソッドにする。
- `rendersDependenciesAndEvents` は、固定した小さな record（例：3 つのモジュールと 1 つのイベント）を描き、実線、点線、同じ組の線を 1 本にまとめることを確認する。本番のモジュールの依存とイベントの名前を、固定の文字列で確認しない。
- `ModulithTest:32` の `modules.forEach(System.out::println)` を削除する。
- `TEST_CLASSES` は 1.1 の補助に移し、2 つのテストが補助を使う。
- 確認方法：`./gradlew test`。描画のテストは、一時的に描く処理の点線を実線に変えて失敗することを確認する。

## 2. 構成の規則の検査（DS-186-1、2：DC-186-1〜7）

検査は、1.1 の補助の `all()` か `modules()` を使う。

| DC | 決定 | 置き場所 | 例のクラスで確認すること（DC-186-7） |
|----|------|----------|---------------------------------------|
| 1 | モジュールの domain の下を、パッケージごとの slice に分け、`beFreeOfCycles()` を確認する。slice は `slices().assignedFrom(...)` で、クラスのパッケージの名前から `<モジュール>.domain` 以下のパッケージの名前（domain の直下は `<モジュール>.domain`、集約は `<モジュール>.domain.<集約>`、値は `<モジュール>.domain.<集約>.vo`）を返す関数で作る。理由：`matching("..domain.(**)")` は domain の直下のクラスを slice に入れないため | 新しい `test/architecture/PackageCycleTest` | 集約のパッケージと `vo/` が互いに依存する例で失敗する |
| 2 | 目安（10）を超えるパッケージの一覧と数を、[バックエンドの書き方](../../rules/backend-coding.md) 第 1.4 節の表から読み、本番のクラスから数えた一覧と数に一致することを確認する。表は、バックエンドの書き方 第 1.4 節の見出しの後の最初の Markdown の表で、1 列目がバッククォートで囲んだパッケージの名前（`com.ecsite.` を除いた形）、2 列目が数である。文書のパスは system property `rules.backend-coding` で渡す（`design.overview` と同じく、`unitTestSystemProperties` に追加し、test の task と pitest の `inputs` に宣言する） | `test/architecture/ModuleStructureTest` | 表の外のパッケージが 11 になったとき、表のパッケージが 10 以下になったとき、数が違うときの 3 つで失敗する（数えた結果と表を引数に取る判定の関数を、例の値で確認する） |
| 3 | 層の依存の表を、1 つの定数（層 → 同じモジュールの中で依存してよい層とその下のパッケージ）にする。モジュールごと、層ごとに「同じモジュールの中で、表にない層とパッケージに依存しない」ことを 1 つの規則で確認する。共通コードが設定に依存しない規則を追加する | `test/architecture/ModuleStructureTest` | 表にない組（api → presentation、infrastructure → application の service）の例で失敗する |
| 4 | `api/` に interface がないことと、`api/` の型を実装・継承するクラスが `api/` の中だけにあることを確認する。`api/` の record と列挙は、interface でないので対象にならない | 新しい `test/architecture/ApiTypeTest`。理由：`ModuleStructureTest` が大きいため | `api/` の interface の例と、`api/` の外から `api/` の型を継承する例で失敗する |
| 5 | 金額の型の検査の対象を、「モジュールの `api`・`event` のクラス」と「`application.model` 以下のクラスで、名前が `View` か `Command` で終わるもの（入れ子の record を含む）」にする。パッケージの名前の `.view` に頼らない | `test/architecture/MoneyTypeTest` | `application.model` の直下の view に整数の価格がある例で失敗する |
| 6 | `assertThat` で一覧を集めて確認する構成の検査は、絞り込んだ対象が 1 件以上あることを、補助の `requireNotEmpty` で確認する。対象は、`NamingTest` の 4 つ、`StaticOnlyClassTest`、`ModuleStructureTest` の一覧の検査、金額の型の検査 | 左の各ファイル | 対象が 0 件の例で、補助が失敗する（T186-01-01） |

DC-186-3 で置き換える既存の検査と、新しい規則が含む組：

| 既存の検査 | 確認している組 | 扱い |
|------------|----------------|------|
| `domainDependsOnNoOtherLayer` | domain → api・presentation・application・infrastructure | 新しい規則に含まれる（domain は event だけ）ので削除する |
| `applicationDependsOnNeitherPresentationNorInfrastructure` | application → presentation・infrastructure | 同上 |
| `applicationDoesNotDependOnApi` | application → api | 同上 |
| `presentationAndApplicationModelDoNotDependOnPort` | presentation・application.model → application.port | presentation は新しい規則に含まれる。application.model → application.port は層の中の依存なので、この検査を残す |
| `entryLayersAndInfrastructureAreIndependent` | presentation・api・event ↔ infrastructure | 新しい規則に含まれるので削除する |
| `levelADomainIsFrameworkFree`、`modulesDependOnNoConfig`、`commonAndConfigDependOnNoModule` | 層の間でない規則 | 残す |

新しい規則の定数（[バックエンドの書き方](../../rules/backend-coding.md) 第 1.1 節の表を定数にする）：

| 層 | 同じモジュールの中で依存してよい先 |
|----|-----------------------------------|
| api・event | application、domain |
| presentation | application（`port/` を除く）、domain |
| application | domain、event |
| domain | event |
| infrastructure | application の `port/` と `model/`、application の設定の値の型（`@ConfigurationProperties` を付けたクラス）、domain |

- 他のモジュールへの依存は Spring Modulith の検査（`ModulithTest`）と、他のモジュールの api の検査（`otherModulesApiIsUsedOnlyByDirectories`）が確認するので、この規則は同じモジュールの中の依存だけを対象にする。
- 層の中の自分自身への依存は許す。presentation の domain への依存は「エラーの定義と値の型」に限るが、集約に依存しないことは `ModuleRoleTest` が確認しているので、この規則は domain の全体を許す。
- 2026-10-24 のコードを import の検索で調べ、api・event → presentation、event → api、infrastructure → event、application の service への infrastructure の依存、共通コード → 設定は、見つからなかった。infrastructure から application への依存は、6 つのモジュールの設定の値の型だけだった。
- 規則の一覧（バックエンドの書き方の冒頭）の、バックエンドの書き方 第 1.1 節の行と、バックエンドの書き方 第 1.4 節の行の「自動」の範囲を、追加した検査に合わせて修正する（第 5 節）。
- 確認方法：`./gradlew test`。例のクラスの確認は、各検査のテストの中で行う。

## 3. 設定と契約の補助、並び順の既定値（DS-186-3、5：DC-186-11、15）

### 3.1 設定と契約の補助（DC-186-11）

設定の補助と契約の補助を、それぞれ 1 つのクラスにする。

| 補助 | 内容 | 使う側 |
|------|------|--------|
| 新しい `test/TestSettings`（public） | `load(String... profiles)`：`application.yml` に `application-<profile>.yml` を重ねて読んだ値。`bind(String prefix, Class<T> type, Map<String, String> values)`：設定の型への値の当てはめ | `config/ProfileFlagsTest`（`flag`）、`config/ManagementPortConfigTest`（`value`）、`config/CleanupJobSettingsTest`、`config/event/EventCleanupJobSettingsTest`、`config/event/EventResubmissionSettingsTest`、`module/payment/application/PaymentExpiryJobLoggingTest`、`module/payment/infrastructure/stripe/StripeConfigTest` の `bind` |
| 新しい `test/contract/Contracts`（package-private） | `openApiDir()`、`realmsDir()`：system property（`openapi.dir`、`keycloak.realms.dir`）の値。渡されなければ例外で失敗する。`openApi(String file)`：契約の YAML を読んだ `Map` | `contract/` の 6 つのテスト（`OPENAPI_DIR`、`REALMS_DIR` と `new Yaml().load(...)`） |

- 契約の置き場所の既定の値（`../../api/openapi`、`../../docker/keycloak/realms`）を削除する。理由：Gradle の設定から system property を渡す行が消えても、テストの作業ディレクトリ（`backend/ecsite/`）から見た既定の相対パスに契約があるので通ってしまい、設定の誤りに気づけないため（`build.gradle.kts:184-189` は今は渡している）。
- `AdminApiPermissionContractTest:136` の `listOperations` は、本番の `OpenApiOperations`（`config.security` の package-private のクラスで、メソッドとパスから操作を探す用途）と役割が違うので、置き換えない。`listOperations` の Javadoc に、本番のクラスを使わない理由を書く。
- 統合テストの `it/config/security/StoreApiTokenContractTest:44` も、契約を固定の相対パス（`../../api/openapi/store.yaml`）で読む。DC-186-11 は単体テストの補助を対象とするので、本 spec では変えず、起票の案に入れる（照合で見つけた）。
- 確認方法：`./gradlew test`。`openapi.dir` を渡さずに契約のテストを 1 つ実行し、失敗することを一時的に確認する。

### 3.2 並び順の既定値（DC-186-15）

今は、店舗のモジュールの presentation（`main/module/storefront/presentation/StorefrontMapper.java:46-55`、指定がなければ新着順）と、カタログのモジュールの永続化（`main/module/catalog/infrastructure/persistence/JooqStoreProductQueryRepository.java:133-134`、`null` なら新着順）の 2 か所にある。カタログの公開の API の入力（`StoreProductQuery.sort`）と application の条件（`StoreProductListQuery.sort`）は `@Nullable` で、公開の API（`StoreCatalogApi.toSort`）は `null` をそのまま渡す。カタログの公開の API の呼び出し元は、店舗の腐敗防止層（`main/module/storefront/infrastructure/directory/CatalogApiDirectory.java`）だけである。

- 推奨：店舗の presentation に残し、カタログの公開の API の入力から永続化までの並び順を必須（`@Nullable` を外す）にする。理由：「指定がなければ」は消費者の要求の入力の解釈で、入力を受ける presentation の役割であるため。カタログの側の `null` の扱いがなくなり、型で必須と分かる。利用者が 2026-10-24 に推奨を選んだ（第 8 節）。
- 推奨のときに変えるもの：`StoreProductQuery`、`StoreCatalogApi.toSort`、`StoreProductListQuery`、`JooqStoreProductQueryRepository.orderBy`（以上カタログ）。店舗の `CatalogApiDirectory` は、presentation が既定を決めた並び順を渡すので、変えなくてよいかを実装の時に確認する。
- カタログの単体テスト `StoreCatalogApiSortTest:26` の「`toSort(null)` が `null` を返す」確認は、`null` を受け付けなくなるので削除し、3 つの並び順の変換の確認だけを残す（常に成り立つ確認でなく、仕様の変更による削除なので、理由を commit の本文に書く）。カタログの統合テスト（`StoreCatalogApiTest`、`StoreProductSearchTest`）で並び順に `null` を渡している所は、新着順を渡す形に修正する。
- 別案のとき：カタログの application の条件に既定を置き、店舗の presentation の既定を削除して指定をそのまま渡す。変えるものは `StorefrontMapper.toSort`、`StoreProductListQuery`（既定を入れる）、`JooqStoreProductQueryRepository.orderBy`（`null` の扱いを削除）である。
- 消費者に見える並び順は、店舗の API の統合テスト（`StorefrontApiTest`）の既定の並びの確認で変わらないことを確認する。
- 確認方法：`./gradlew check`。

## 4. テストのコメントの spec の番号（DS-186-4：DC-186-13、14）

段階 4 で行う（spec 第 9 節の決定）。

- **Java のコメントだけを残す関数**：spec 183 が `scripts/ci/lib/strip-comments.mjs` に作った `stripJavaComments`（コメントを同じ長さの空白にする。文字列、テキストブロック、文字リテラルの中はコメントにしない）を使い、同じファイルに、逆にコメントの外を空白にする関数 `keepJavaComments` を追加する。元のソースと `stripJavaComments` の結果を文字ごとに比べ、空白にされた文字（コメント）だけを残し、改行は残す。理由：コメントの判定を 1 か所に保ち、位置と行番号が元のソースと同じになるため。
- **検査（DC-186-13）**：`scripts/ci/spec-numbers.mjs` の対象に、`backend/ecsite/src/test/`、`src/integrationTest/`、`src/testFixtures/` の Java のコメントを加える。コメントの外（`@DisplayName` の AC・BR などの番号、テストデータの文字列）は見ない。画面のテスト（`*.test.ts(x)`）は対象にしない。理由：画面のテストは `test('AC-…')` の文字列に AC 番号を書き、番号を見るにはコメントと文字列を分ける別の解析が必要になるため。
- **書き換え（DC-186-14）**：検査が見つけた番号を、規則を言葉で書く形にする。例：`（docs/rules/backend-coding.md 第 1 節、spec 005 DC-27〜30）` は `（docs/rules/backend-coding.md 第 1 節）`。`spec 013 は既存のテストを変えないので` のように、spec の作業の事情を書いたものは、今のテストに当てはまる理由を言葉で書くか、当てはまらなければ削除する。規則の文書の節の参照は残す。
- **書き換えの量**：2026-10-24 の概算は、`test/` に約 50 行、`it/` に約 100 行（文字列の中を含むので多めの可能性）。T186-04-01 で作る関数で件数を数え直し、T186-04-02〜04 の 3 つの分け方（モジュールの外と `fix/`、商品カタログのモジュール、注文のモジュール）の釣り合いを確認する。
- **順序**：書き換え（T186-04-02〜04）を先にし、検査を広げる（T186-04-05）のを最後にする。広げた時点で 0 件になるようにする。理由：検査を先に広げると、書き換えが終わるまで `check-all.sh` が落ち続け、他のタスクの commit が止まるため。`spec-numbers.test.mjs` は commit の前の hook が実行するので、検査を広げるテスト（`[テスト]`）は、広げる実装と同じタスクにする。
- **文書**：[コメントの書き方](../../rules/comments.md) 第 1 節の対象（23 行目の「本番のコード」）に、テストのコメントを加える。テスト名には番号を書くこと（テストの書き方 第 2.1 節）は変えない。
- 確認方法：`node scripts/ci/spec-numbers.mjs` が 0 件。`scripts/ci/check-all.sh` が通る。

## 5. 文書

| 文書 | 変えること | 段階 |
|------|------------|------|
| [バックエンドの書き方](../../rules/backend-coding.md) | 規則の一覧の、バックエンドの書き方 第 1.1 節の行（層の依存の表の全ての組、`api/` の interface、domain の循環は自動）と、バックエンドの書き方 第 1.4 節の行（目安と表の一致は自動）。バックエンドの書き方 第 1.4 節に、表をテストが照合することと、表の書式（1 列目のバッククォートで囲んだパッケージの名前、2 列目の数）を変えないこと | 2 |
| [コメントの書き方](../../rules/comments.md) | 第 1 節の対象にテストのコメントを加える | 4 |
| [テストの書き方](../../rules/test-writing.md) | 単体テストの補助（`ProductionClasses`、`TestSettings`）と、構成の検査は本番のクラスを補助で読むことを書く。追記先はテストの書き方 第 3.3 節（照合で決めた。第 3.2 節の表は統合テストのテストデータの補助の一覧なので） | 1、3 |
| 用語集 | 用語の検査（`glossary-ids`）が識別子を求めるときだけ、補助の名前を追加する | 1、3 |

## 6. 段階

| 段階 | 内容 | tasks |
|------|------|-------|
| 1 | 構成の検査の補助、読み込みの 1 か所化、図のテスト、heap | [01-helpers.md](tasks/01-helpers.md) |
| 2 | 構成の規則の検査 | [02-rules.md](tasks/02-rules.md) |
| 3 | 設定と契約の補助、並び順の既定値 | [03-settings-sort.md](tasks/03-settings-sort.md) |
| 4 | テストのコメントの spec の番号 | [04-spec-numbers.md](tasks/04-spec-numbers.md) |
| 5 | 完了（全体の検査、レビュー、起票の案、アーカイブ） | [05-review.md](tasks/05-review.md) |

- 段階 2 は段階 1 の補助（`ProductionClasses`）を使う。段階 3 は段階 1 の補助を使わないが、テストの書き方への追記（第 5 節）が段階 1 と同じ節なので、段階 1 の後に行う。段階 4 は最後に行う（spec 第 9 節）。

## 7. レビュー

profile「厚め」（[エージェントの作業フロー](../../rules/subagents.md) の profile の表）。各段階の最後に段階のレビューを行う。

| 段階 | 観点 | レビュー役 |
|------|------|------------|
| 1、3 | 一般の観点（規則、重複と抽象）と、補助へ移したテストの確認が変わっていないこと | 汎用のレビュー役を、別のサブエージェントで 2 回立てる |
| 2 | 検査が規則の文書と一致すること、例のクラスで違反を検出できること、対象が 0 件で通る規則がないこと | 同上 |
| 4 | 書き換えたコメントが、番号を消しただけで意味を失っていないこと | 汎用のレビュー役を 1 回。理由：コメントだけの変更で、検査が 0 件を確認するため |
| 5 | spec の最後のレビュー（ファイルをまたぐ影響の観点を含める）と、pull request の前のレビュー | [05-review.md](tasks/05-review.md) のとおり |

## 8. 利用者に確認すること

- **Q1（DC-186-15）** 並び順の既定値を残す層。答え：推奨（利用者、2026-10-24）。推奨と理由、変えるものは第 3.2 節にある。
  - 推奨：店舗の presentation に残し、カタログの側を必須にする。
  - 別案：カタログの側（application の条件）に残し、店舗は指定をそのまま渡す。利点は、カタログの公開の API が「指定がなければ新着順」を自分で守り、他の呼び出し元が増えても既定が同じになること。欠点は、`@Nullable` が公開の API から application まで残ること。今の呼び出し元は店舗だけである。
