# Spec 18：Error Prone と NullAway を入れる

ステータス：確定

## 1. 背景と目的

バックエンド（`backend/ecsite`）には、null の扱いなどの誤りをコンパイルの時点で見つける静的解析がない。今ある検査は、Checkstyle（Javadoc の書き漏れだけ）、ArchUnit、`scripts/ci/java-rules.mjs` である。

出典は外部レビューの意見（2026-10-20）で、追跡の issue は [#18](https://github.com/roger88106/ec-site/issues/18)、作業ブランチは `18-errorprone` である。本 spec は整理の spec で、規模は M である。API の契約・データベース・AC を変えないため（[開発の進め方](../../rules/workflow.md) 第 4.2 節）。

実装の順序と前提：

- spec 19（Java のフォーマッター）と並行して実装する（issue 担当のセッションの指示、2026-10-23。承認の時点では spec 19 の後の予定だった）。後で `develop` にマージする側が、`develop` を取り込むときにフォーマッターをかけ直して衝突を解く。
- CI は spec 131（作業ブランチ `131-ci-tidy`、[#131](https://github.com/roger88106/ec-site/issues/131)）の分割の後の形（バックエンド（ビルドと単体テスト）、バックエンド（統合テスト 1/2・2/2）、バックエンド）を前提にする。
- spec 17（カバレッジ）も `build.gradle.kts` を変えるので、実装の順序は issue 担当のセッションが決める。

### 試しの結果

2026-10-22 に、作業ブランチで一時的に入れてコンパイルだけを実行し、元に戻した。版は Error Prone 2.50.0、NullAway 0.14.2、Gradle のプラグイン net.ltgt.errorprone 5.1.1、Gradle 9.8.1、JDK 21 である。

| 試し | 結果 |
|------|------|
| Error Prone の既定の検査 | main でエラー 8（CheckReturnValue 5：jOOQ の `values(...)`、ReturnValueIgnored 2：`Optional.orElseThrow`、FormatString 1）、警告 40（MissingSummary 24、ImmutableEnumChecker 13 など）。test の警告 14、integrationTest の警告 10、testFixtures と tools は 0。合計 72 件。生成したコード（jOOQ、OpenAPI）からは報告がない |
| 検査 `StringConcatToTextBlock` | Lombok の `@NonNull` の引数を持つクラス（`I18nMessage`）で例外を投げ、コンパイルが失敗した |
| NullAway（main だけ。生成したコードのパッケージを注釈なしとする） | 115 件。module.order 29、catalog 21、payment 13、common 11、reporting 9、inventory 9、cart 6、tax 6、shipping 4、config 3、storefront 3、member 1。種類は「@Nullable の値を @NonNull の引数に渡す」が 108 件（`null` の直書き、`Optional.orElse(null)`、nullable の record の成分。この内訳は、生成したコードのパッケージを注釈なしにする前の 143 件のもので、115 件の内訳は数えていない）で、ほかに @Nullable を返す 19、@Nullable の参照の外し 7、OpenAPI の生成した interface との引数の注釈の不一致 5 |
| NullAway をテスト（test、integrationTest）にも当てる | 437 件。大半は、検証の失敗を確認するため意図して null を渡すテストで、コードの誤りではない |
| コンパイルの時間（`--rerun`、2 回目） | compileJava 17 秒 → 24 秒、compileIntegrationTestJava 7 秒 → 23 秒（他のセッションと同じマシンで測ったので揺れがある） |

### 今の null の注釈

- `jakarta.annotation.Nullable` が 67 か所、`lombok.NonNull` が 35 か所、JSpecify（`org.jspecify.annotations`）が 4 か所ある（2026-10-22 の import の数）。
- OpenAPI の生成したコードは `org.springframework.lang.Nullable` を使う。
- Spring Framework 7・Spring Boot 4 の API は、JSpecify の注釈が付いている（`@NullMarked`）。

## 2. スコープ

| # | 目的 | 対象 | 優先度 |
|---|------|------|--------|
| DS-18-1 | よくある誤り（戻り値の無視、書式の文字列の誤りなど）を、全てのソースセットでコンパイルの時点で見つける | D | P1 |
| DS-18-2 | 本番のコード（main）の null の誤りを、コンパイルの時点で見つける | D | P1 |
| DS-18-3 | null の注釈と、検査を抑える書き方の規則が、バックエンドの書き方にある | D | P1 |

## 3. 目的と完了条件

### DS-18-1 Error Prone（D / P1）

- **DC-18-1** Error Prone を全てのソースセット（main、test、integrationTest、testFixtures、tools）のコンパイルで動かす。既定の検査を使い、エラーと警告のどちらでもコンパイルが失敗する（警告を失敗にするのは javac の `-Werror`）。生成したコード（`build/generated*` の下の jOOQ、OpenAPI）は検査しない。
- **DC-18-2** 既存のコードの報告（試しで 72 件）を全て修正し、`./gradlew checkLicense build` が通る。修正するときに振る舞いを変えない（DN-18-1）。
- **DC-18-3** 無効にする検査は `StringConcatToTextBlock` だけである（Lombok の生成したコードで Error Prone が例外を投げるため）。無効にする検査は、`build.gradle.kts` に理由のコメントと一緒に書く。他の検査の無効化・重大度の変更は、先にこの spec の第 6 節に追加し、利用者の確認を受けてから行う。
- **DC-18-4** わざと誤り（`Optional.orElseThrow()` の戻り値の無視など）を main と integrationTest に入れると、コンパイルが失敗する。手動で入れて確認し、戻す。結果をタスクに書く。

### DS-18-2 NullAway（D / P1）

- **DC-18-5** NullAway を main のコンパイルだけで動かし、報告はエラー（コンパイルが失敗する）にする。対象は `com.ecsite` の下の全てのパッケージ（`AnnotatedPackages`）で、生成したコードのパッケージ（`com.ecsite.jooq`、`com.ecsite.api`）は注釈なしとする（`UnannotatedSubPackages`）。テストのソースセットと tools では動かさない。
- **DC-18-6** Spring の推奨の設定に合わせ、`CustomContractAnnotations=org.springframework.lang.Contract` を付ける（`Assert.notNull` などの呼び出しの後で、NullAway が引数を null でないと判断できる）。JSpecify mode は使わない（JDK 22 以上が必要。第 5 節）。
- **DC-18-7** 既存のコードの報告（試しで 115 件）を全て修正する。修正の選び方：今の振る舞いで null が正しい値（API の応答の省略できる項目、DB の null を取りうる列など）なら `@Nullable` を付け、null を渡す必要がない所（`null` の直書きの引数など）は null を作らない書き方にする。どちらでも振る舞い（API の応答、DB に書く値）を変えない。`@SuppressWarnings("NullAway")` は、NullAway が誤って報告する場所（フレームワークが初期化するフィールドなど）だけにし、理由をコメントに書く。使った数をタスクに書く。
- **DC-18-8** わざと null の誤り（@Nullable の値の参照を外す）を main に入れると、コンパイルが失敗する。手動で入れて確認し、戻す。結果をタスクに書く。
- **DC-18-9** null の注釈は JSpecify（`org.jspecify.annotations.Nullable`）に統一し、`jakarta.annotation.Nullable`（67 か所）を置き換える。main とテストで、他の `Nullable`（`jakarta.annotation`、`javax.annotation`、`org.springframework.lang`）の import を `scripts/ci/java-rules.mjs` で禁止する（生成したコードは対象外）。java-rules のテストを先に書く。
- **DC-18-10** Lombok が作るコード（コンストラクタ、getter）で、フィールドの `@Nullable` が、生成されるコンストラクタの引数と getter の戻り値に引き継がれることを確認する。引き継がれなければ `lombok.config` の `copyableAnnotations` に追加する。
- **DC-18-11** jOOQ のレコードの getter は注釈なしなので、null を取りうる列の値を NullAway は確認しない。null を取りうる列は、リポジトリの実装で `@Nullable` を付けたローカル変数、または `Optional` で受ける。この規則をバックエンドの書き方に書く（DC-18-13）。

### 共通（D / P1）

- **DC-18-12** 依存のライセンスを、次のとおり扱う。
  - Error Prone（Apache-2.0）、NullAway（MIT）、JSpecify（Apache-2.0）、Gradle のプラグイン（Apache-2.0）と、それらの間接の依存を、依存のライセンスの検査（`checkLicense`）の構成に入れる。Gradle のプラグインは `errorprone` の構成を 1 つだけ作り、全てのソースセットで使う（試しで確認）。`errorprone` の構成は解決できない構成なので `licenseReport` に直接は入れられず、それを継承する `annotationProcessor`（既に検査の構成にある）で検査に入る（T18-01-01 で確認）。
  - 間接の依存の Checker Framework の dataflow（`dataflow-errorprone`、`dataflow-nullaway`。POM のライセンスは GPL-2.0 with Classpath-exception）は、条件付きの一覧（`scripts/ci/licenses/policy.json`）に理由を書いて載せる。
  - [依存とライセンス](../../design/licenses.md) の 3.1 の表に追加する。
- **DC-18-13** [バックエンドの書き方](../../rules/backend-coding.md) に、null の扱いの節（JSpecify の `@Nullable`、main では注釈のないものを null でないとみなす、`Optional` を使う場面（今のコードの書き方）、jOOQ の null を取りうる列、`@SuppressWarnings` の書き方と理由のコメント）と、Error Prone の検査を抑えるときの書き方を追加し、規則の一覧の表に行を追加する（確認方法：自動（Error Prone・NullAway）など）。
- **DC-18-14** [constitution](../../constitution.md) 第 2 節の技術スタック（バックエンド）に「静的解析」の行（Error Prone、NullAway、Checkstyle）を追加する。[CI の検査](../../guides/ci.md) の、バックエンド（ビルドと単体テスト）の「実行するもの」に Error Prone と NullAway を追加する。[テストの実行方法](../../guides/running-tests.md) に、コンパイルの失敗の読み方を書く必要があれば追加する。
- **DC-18-15** CI のバックエンドの 3 つのジョブ（ビルドと単体テスト、統合テスト 1/2・2/2）の、テストを全て実行する実行の時間の増加が、それぞれ 1 分以下である。作業ブランチの 3 回の最大と、導入の前の `develop` の直近 3 回の最大を比べ、タスクの結果に書く。手元の compileJava と compileIntegrationTestJava の `--rerun` の時間も書く。
- **DC-18-16** 依存の版はカタログ（`backend/ecsite/gradle/libs.versions.toml`）に書く。版は実装の時点で、出てから 2 週間以上たった最新の版にする（Error Prone の 2.51.0 は 2026-10-22 の公開で、2 週間たっていない）。Dependabot が上げる。

## 4. 守る制約

- **DN-18-1** 消費者・運営者に見える振る舞い、API の契約、データベースを変えない。
- **DN-18-2** テストの数と、テストが確認する内容を減らさない。報告をなくすためにテストを弱めない。
- **DN-18-3** 生成したコード（jOOQ、OpenAPI）の生成の設定を、報告をなくすためだけに変えない。
- **DN-18-4** 手元で `./gradlew check` などを実行するのは、同時に 1 つのセッションだけ（skill `local-dev`）。

## 5. Non-goals

| 項目 | 理由 |
|------|------|
| JSpecify mode（ジェネリクス、配列の null） | JDK 22 以上が必要で、開発中の機能。JDK を上げたときに再検討する（1→10 で起票。spec の最後の起票の案に入れる）。Spring の文書は、JDK 22 以上を使うことと、標準の設定で警告をなくした後に JSpecify mode を入れることを勧めている |
| テストのソースセットの NullAway | 試しで 437 件。大半は、検証の失敗を確認するため意図して null を渡すテストで、コードの誤りではない |
| `lombok.NonNull` を外す | 実行時に null を検査する今の振る舞いの一部である。注釈のないコード（テスト、Jackson）からの呼び出しにも効くので、外さない |
| Error Prone の既定でない検査（実験的な検査など）を追加する | まず既定で運用する |
| フロントの静的解析 | Biome がある |

## 6. 決めたこと

spec の確認で、利用者が 6 つとも案 A に決めた（2026-10-22）。規模 M も同じ時に承認した。Open questions はない。利用者の方針：既存の業務（消費者・運営者に見える振る舞い）に影響しないこと（DN-18-1）を守り、実装で問題が分かったら、その時に調整する（調整は第 6 節に追加し、利用者の確認を受ける。DC-18-3 と同じ）。

1. **Error Prone の警告**：
   - 案 A（推奨。採用）：既存の 72 件を修正し、警告も失敗にする。警告がたまらない。
   - 案 B：警告は報告だけにし、エラーだけ失敗にする。警告がたまる。
2. **NullAway の対象のソースセット**：
   - 案 A（推奨。採用）：main だけ。テストの報告の大半は、検証の失敗を確認するため意図して null を渡すテストで、コードの誤りではない。
   - 案 B：main とテスト。437 件の大半はコードの誤りでないので、多くを `@SuppressWarnings` で抑えることになる。
3. **対象のパッケージの指定**：
   - 案 A（推奨。採用）：`AnnotatedPackages=com.ecsite` と、生成したコードを除く `UnannotatedSubPackages`。`com.ecsite` の下の新しいパッケージも、自動で対象になる。
   - 案 B：パッケージごとの `package-info.java` に `@NullMarked`（`OnlyNullMarked`）。main のパッケージは 148 あり、`@NullMarked` は下のパッケージに及ばないので、148 個の `package-info.java` が必要で、付け忘れたパッケージは検査されない。
4. **既存の NullAway の報告**：
   - 案 A（推奨。採用）：本 spec で全て修正する。モジュールごとのタスクに分け、並行して実装する。
   - 案 B：除外するクラスに `@NullUnmarked` を付け、後の spec で少しずつ外す（起票する）。
5. **null の注釈**：
   - 案 A（推奨。採用）：JSpecify に統一し、他を検査で禁止する。Spring 7 と同じ注釈で、NullAway・IntelliJ が読む。
   - 案 B：今の注釈を残す。NullAway は名前が Nullable の注釈を全て読むので動くが、注釈が混ざる。
6. **ライセンスの検査**：
   - 案 A（推奨。採用）：`errorprone` の構成を `checkLicense` に入れる。注釈の処理（Lombok の annotationProcessor）と同じく、コンパイルのときに動く依存なので、[依存とライセンス](../../design/licenses.md) 第 2 節の方針の「ビルド時の依存」に当たる。
   - 案 B：Checkstyle と同じく構成に入れず、手動で確認して文書に書く。

## 7. tasks

### 段階 1：Error Prone

- 前提：spec の確定
- 完了条件：DC-18-1〜4 と、DC-18-12・16 のうち Error Prone の分を満たし、CI が通る。
- 参照：第 3 節の DS-18-1

- [x] **T18-01-01** Error Prone のプラグインと依存をカタログと `build.gradle.kts` に入れ、全てのソースセットで動かす。生成したコードを除き、`StringConcatToTextBlock` を理由のコメントと一緒に無効にする。`errorprone` の構成の依存がライセンスの検査に入ることを確認し、`policy.json` に `dataflow-errorprone` を理由と一緒に載せる。既存のエラー（main の 8 件）を、振る舞いを変えずに修正する（DN-18-1）。警告の失敗はまだ入れない（DC-18-1〜3、12、16）。
  - 依存：なし
  - 範囲：`backend/ecsite/gradle/libs.versions.toml`、`backend/ecsite/build.gradle.kts`、`scripts/ci/licenses/policy.json`、`backend/ecsite/src/main/java/` の該当のクラス
- [x] **T18-01-02** [P] main の警告を、振る舞いを変えずに修正する（DC-18-2）。
  - 依存：T18-01-01
  - 範囲：`backend/ecsite/src/main/java/`
- [x] **T18-01-03** [P] テストのソースセット（test、integrationTest）の警告を修正する。テストの数と確認する内容を減らさない（DN-18-2）（DC-18-2）。
  - 依存：T18-01-01
  - 範囲：`backend/ecsite/src/test/java/`、`backend/ecsite/src/integrationTest/java/`
- [x] **T18-01-04** 警告も失敗にする（`-Werror`）。javac 自身の警告が出たら、修正するか、作業を中断して報告する（生成したコードの警告は、生成の設定を変えずに報告する。DN-18-3）。わざと誤りを main と integrationTest に入れてコンパイルの失敗を確認し、戻す。結果をこのタスクの下に書く（DC-18-1、4）。
  - 依存：T18-01-02、T18-01-03
  - 範囲：`backend/ecsite/build.gradle.kts`、`docs/specs/18-errorprone/spec.md`
  - 結果：2026-10-23 に、`-Werror` を入れた状態で手動で確認し、戻した（`git diff` は空）。main（`MessageHelper.formatValue` に `Optional.of(1).orElseThrow();`）は `compileJava` が失敗し、エラーは ReturnValueIgnored と OptionalOfRedundantMethod。integrationTest（`CatalogTestData.taxCategory` に同じ行）は `compileIntegrationTestJava` が失敗し、エラーは同じ 2 つ。警告も失敗になることは、main に `"A".toLowerCase()` の未使用の変数を入れ、StringCaseLocaleUsage と UnusedVariable の警告が「warnings found and -Werror specified」で `compileJava` を失敗させることで確認した。javac 自身の警告は 0 件だった
- [x] **T18-01-05** 段階の最後のレビュー（profile「厚め」）。
  - 依存：T18-01-04
  - 範囲：なし（読むだけ）
  - 結果：profile は「標準」で行った（Pro のプランで、使用量を抑えるため。一般の観点を汎用で 1 回、重要な観点を高性能で 1 回）。高 0、必須 3（重複を除く）。外部サービスの失敗のテスト 2 つで、待ち受けと接続先のアドレスが別の所から決まるようになっていたのを直した。`I18nMessage` の抑える理由のコメントが事実と合わなかったので、コンストラクターで List.copyOf にして直した。

### 段階 2：NullAway

- 前提：段階 1
- 完了条件：DC-18-5〜11、13 と、DC-18-12・16 のうち NullAway・JSpecify の分を満たし、CI が通る。
- 参照：第 3 節の DS-18-2
- [P] のタスクは、作業ツリーを分けて同時に編集してよい。ただし Gradle の実行は同時に 1 つだけにする（DN-18-4）。

- [x] **T18-02-01** [バックエンドの書き方](../../rules/backend-coding.md) に、null の扱いの節（JSpecify の `@Nullable`、main では注釈のないものを null でないとみなす、`Optional` を使う場面、jOOQ の null を取りうる列の受け方、`@SuppressWarnings("NullAway")` の書き方と理由のコメント、修正の選び方の基準）と、Error Prone の検査を抑えるときの書き方を追加し、規則の一覧の表に行を追加する。文書が先なので、修正のタスクより先に書く（DC-18-11、13）。
  - 依存：なし
  - 範囲：`docs/rules/backend-coding.md`
- [x] **T18-02-02** [テスト] java-rules に、他の `Nullable` の import の禁止のテストを書き、レッドを確認する（DC-18-9）。
  - 依存：なし
  - 範囲：`scripts/ci/java-rules.test.mjs`
- [x] **T18-02-03** T18-02-02 のテストが通る実装を書く。JSpecify の依存を追加し、`jakarta.annotation.Nullable` を JSpecify に置き換える（機械的）（DC-18-9、12、16）。
  - 依存：T18-02-02
  - 範囲：`scripts/ci/java-rules.mjs`、`backend/ecsite/src/`、`backend/ecsite/gradle/libs.versions.toml`、`backend/ecsite/build.gradle.kts`
- [x] **T18-02-04** NullAway を main に入れる。全ての報告を修正するまでは、Gradle のプロパティ（例：`-Pnullaway`）を付けたときだけ動かし、そのときは報告を警告にして `-Werror` を外す（既定のビルドと CI は通ったままにする）。`CustomContractAnnotations` を付ける。NullAway の依存をカタログとライセンスの検査に入れ、`policy.json` に `dataflow-nullaway` を載せる。Lombok の注釈の引き継ぎを確認する（DC-18-5、6、10、12、16）。
  - 依存：T18-02-01、T18-02-03
  - 範囲：`backend/ecsite/build.gradle.kts`、`backend/ecsite/gradle/libs.versions.toml`、`scripts/ci/licenses/policy.json`、`backend/ecsite/lombok.config`
  - 結果：`-Pnullaway` で compileJava を流すと報告は 110 件（common 10、config 3、catalog 19、order 28、payment 13、inventory 9、reporting 9、cart 6、tax 6、shipping 4、storefront 2、member 1）。javac は警告を 100 件で打ち切るので、`-Pnullaway` のときは `-Xmaxwarns` を上げた。`dataflow-nullaway` は groupId が `org.checkerframework` で、`policy.json` に載せた。Lombok は `lombok.config` を変えずに引き継ぐ：`Order`（`@Getter` と `@Accessors(fluent = true)`）の `@Nullable` のフィールド `cancellation` から作られる getter の戻り値に、`org.jspecify.annotations.Nullable` が付く（クラスファイルで確認した）。そのため `OrderCancellationService` の `order.cancellation().restocked()` が、null の可能性のある参照として正しく報告される。
- [x] **T18-02-05** common、config の報告を修正する（DC-18-7）。
  - 依存：T18-02-04
  - 範囲：`backend/ecsite/src/main/java/com/ecsite/common/`、`backend/ecsite/src/main/java/com/ecsite/config/`
- [x] **T18-02-06** [P] order、payment の報告を修正する。OpenAPI の生成した interface との不一致は、生成の設定を変えずに実装の側で合わせる（DN-18-3）。他のモジュールの型の注釈を変えたら、呼び出し側の報告も同じタスクで修正する（DC-18-7）。
  - 依存：T18-02-05
  - 範囲：`backend/ecsite/src/main/java/com/ecsite/module/order/`、`backend/ecsite/src/main/java/com/ecsite/module/payment/`
- [x] **T18-02-07** [P] catalog、inventory、cart の報告を修正する。OpenAPI の生成した interface との不一致は、生成の設定を変えずに実装の側で合わせる（DN-18-3）。他のモジュールの型の注釈を変えたら、呼び出し側の報告も同じタスクで修正する（DC-18-7）。
  - 依存：T18-02-05
  - 範囲：`backend/ecsite/src/main/java/com/ecsite/module/catalog/`、`backend/ecsite/src/main/java/com/ecsite/module/inventory/`、`backend/ecsite/src/main/java/com/ecsite/module/cart/`
- [x] **T18-02-08** [P] reporting、tax、shipping、member、storefront の報告を修正する。OpenAPI の生成した interface との不一致は、生成の設定を変えずに実装の側で合わせる（DN-18-3）。他のモジュールの型の注釈を変えたら、呼び出し側の報告も同じタスクで修正する（DC-18-7）。
  - 依存：T18-02-05
  - 範囲：`backend/ecsite/src/main/java/com/ecsite/module/reporting/`、`backend/ecsite/src/main/java/com/ecsite/module/tax/`、`backend/ecsite/src/main/java/com/ecsite/module/shipping/`、`backend/ecsite/src/main/java/com/ecsite/module/member/`、`backend/ecsite/src/main/java/com/ecsite/module/storefront/`
- [x] **T18-02-09** NullAway をプロパティなしで常に動かし、報告をエラーにする（プロパティをなくす）。T18-02-05〜08 の範囲の外のパッケージや、他の修正で増えた報告が残っていれば修正する。わざと null の誤りを main に入れて失敗を確認し、戻す。`@SuppressWarnings("NullAway")` の数と、確認の結果をこのタスクの下に書く（DC-18-5、7、8）。
  - 依存：T18-02-06、T18-02-07、T18-02-08
  - 範囲：`backend/ecsite/build.gradle.kts`、`backend/ecsite/src/main/java/`、`docs/specs/18-errorprone/spec.md`
  - 結果：`-Pnullaway` とその分岐、`-Xmaxwarns` をなくし、main の compileJava で NullAway を常にエラーとして動かす（`-Werror` は戻した。test、integrationTest、testFixtures、tools では無効）。範囲の外のパッケージの報告はなかった（全て T18-02-05〜08 の範囲）。`@SuppressWarnings("NullAway")` は 0 件（`grep -rn 'SuppressWarnings("NullAway")' backend/ecsite/src/main | wc -l`）。確認：Cart.memberId() に `@Nullable` のフィールドの参照を外す行を入れると、compileJava が `[NullAway] dereferenced expression 'memberId' is @Nullable` のエラーで失敗した。その行は戻した（git diff が空）。5 つのソースセットのコンパイル、checkstyleMain、単体テストが通る。
- [x] **T18-02-10** 段階の最後のレビュー（profile「厚め」）。
  - 依存：T18-02-09
  - 範囲：なし（読むだけ）
  - 結果：profile は「標準」（一般の観点を汎用で 1 回、重要な観点を高性能で 1 回。Pro のプランで使用量を抑えるため）。高 0、必須 5。NullAway が確認できない所（生成した interface の任意の引数、`JsonNullable#orElse(null)`、`required = false` の要求の部分）から null が入る引数に `@Nullable` を付けた（31 か所）。バックエンドの書き方の `Optional` の記述を、今のコードの書き分けに合わせた。CI の検査の文書の java-rules の行は T18-03-01 で直す。機械の検査（NullAway の `AcknowledgeRestrictiveAnnotations`、`required = false` の検査）に移すかは、spec の最後の起票の案で扱う。

common の型（`BusinessException` など）の引数に `@Nullable` を付けると、他のモジュールの報告が変わる。そのため T18-02-05 を先に行い、T18-02-06〜08 を同時に行う。

### 段階 3：文書と時間

- 前提：段階 1、2
- 完了条件：DC-18-12（文書の分）、14、15 を満たし、CI が通る。
- 参照：第 3 節の共通

- [x] **T18-03-01** [P] constitution の技術スタック、依存とライセンスの表、CI の検査、テストの実行方法を修正する（DC-18-12、14）。
  - 依存：なし
  - 範囲：`docs/constitution.md`、`docs/design/licenses.md`、`docs/guides/ci.md`、`docs/guides/running-tests.md`
- [x] **T18-03-02** [P] 時間を測る。作業ブランチの最終のコードで、CI のテストを全て実行する実行を 3 回行い、導入の前の `develop` の直近 3 回と比べる。手元の compileJava と compileIntegrationTestJava の `--rerun` は、他のセッションが Gradle を動かしていないときに測る。結果をこのタスクの下に書く（DC-18-15）。
  - 依存：なし（段階の前提で、段階 1、2 が済んでいる）
  - 範囲：`docs/specs/18-errorprone/spec.md`
  - 結果：作業ブランチの最終のコード（d5cb81df。実行 39822803512 と、その再実行 2 回）で、ビルドと単体テストが 181・131・193 秒、統合テスト 1/2 が 335・344・280 秒、統合テスト 2/2 が 376・285・354 秒。比べる先は、ジョブの分割（spec 131）の後で、テストを全て実行した `develop` の実行が 1 回（23a121ff：108・296・337 秒）しかないので、同じジョブの構成の spec 131 の作業ブランチの最後の 3 回（b328aea1 の 2 回と 4388612f：ビルドと単体テスト 168・132・169 秒、統合テストの遅い方 338・322・349 秒）で比べた。最大どうしの増加は、ビルドと単体テストが 24 秒（193 − 169）、統合テストが 27 秒（376 − 349）で、どちらも 1 分以下。同じ commit でもビルドと単体テストが 131〜193 秒とばらつくので、増加は測定のばらつきと同じ程度である。`develop` の 23a121ff（108 秒）との差は 85 秒だが、マージの commit で、変わっていないタスクを Gradle のキャッシュから読んだとみられる（作業ブランチは、コンパイルの設定が変わるので全てのコンパイルを実行する）。手元（他のセッションが動いているマシン。設定の評価を含む時間）は、compileJava が 31・45 秒、compileIntegrationTestJava が 32・32 秒で、試し（第 1 節。Gradle の BUILD の時間で 17 秒と 7 秒）より長い。測る条件が揃わないので、手元の数字は目安にとどめる。
- [ ] **T18-03-03** 段階の最後のレビュー（profile「厚め」）。
  - 依存：T18-03-01、T18-03-02
  - 範囲：なし（読むだけ）

### 段階 4：完了

- 前提：段階 1〜3
- 完了条件：spec の最後のレビューの指摘に対応し、spec をアーカイブに移し、pull request を作っている。
- 参照：[エージェントの作業フロー](../../rules/subagents.md) 第 4 節

- [ ] **T18-04-01** spec の最後のレビュー（profile「厚め」。ファイルをまたぐ影響の観点を含める）を行い、指摘に対応する。
  - 依存：T18-03-03
  - 範囲：指摘で変えるファイル
- [ ] **T18-04-02** 後回しにする作業と起票のまとめ方の案（JSpecify mode を含める）を書き、spec を `docs/specs/archive/1to10/` に移し、pull request を作る。
  - 依存：T18-04-01
  - 範囲：`docs/specs/`
