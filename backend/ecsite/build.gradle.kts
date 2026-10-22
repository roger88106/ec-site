import net.ltgt.gradle.errorprone.errorprone
import nu.studer.gradle.jooq.JooqGenerate
import org.flywaydb.core.Flyway
import org.jooq.meta.jaxb.ForcedType
import org.jooq.meta.jaxb.Logging
import org.jooq.meta.jaxb.SchemaMappingType
import org.testcontainers.postgresql.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import java.util.zip.ZipFile

/*
 * EC Site バックエンド - ビルド構成定義 (Gradle Kotlin DSL)
 *
 * ※バージョン番号などの定義はすべて gradle/libs.versions.toml で管理する。
 */

buildscript {
    repositories {
        mavenCentral()
    }
    dependencies {
        // jOOQ コード生成用：使い捨ての PostgreSQL を起動し、Flyway でマイグレーションを適用する
        classpath(libs.testcontainers.postgresql)
        classpath(libs.buildscript.flyway.core)
        classpath(libs.buildscript.flyway.database.postgresql)
        classpath(libs.buildscript.postgresql)
    }
}

plugins {
    java
    idea
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.spring.dependency.management)
    alias(libs.plugins.jooq)
    alias(libs.plugins.openapi.generator)
    alias(libs.plugins.dependency.license.report)
    alias(libs.plugins.errorprone)
    jacoco
    checkstyle
    // mock を包むテスト用の実装など、単体テストと統合テストで共有するもの（src/testFixtures）
    `java-test-fixtures`
}

group = "com.ecsite"
version = "0.0.1-SNAPSHOT"

// Boot BOM が管理する jOOQ の版をカタログの版で上書きする
extra["jooq.version"] = libs.versions.jooq.get()

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(libs.versions.java.get()))
    }
}

repositories {
    mavenCentral()
}

dependencyManagement {
    imports {
        mavenBom(libs.spring.modulith.bom.get().toString())
        mavenBom(libs.jmolecules.bom.get().toString())
        mavenBom(libs.awssdk.bom.get().toString())
    }
}

// -----------------------------------------------------------------------------
// source set
//   src/test            : 単体（純 JVM、Docker 不要）
//   src/integrationTest : 統合（Spring コンテキスト + Testcontainers、Docker 必須）
//   ※ dependencies より前に定義すること
// -----------------------------------------------------------------------------
sourceSets {
    create("integrationTest") {
        compileClasspath += sourceSets.main.get().output + sourceSets.test.get().output
        runtimeClasspath += sourceSets.main.get().output + sourceSets.test.get().output
    }
    // src/tools : 開発で流す道具（地域の最初のデータを作る）。本番の jar に入れない
    create("tools") {
        compileClasspath += sourceSets.main.get().output + sourceSets.main.get().compileClasspath
        runtimeClasspath += sourceSets.main.get().output + sourceSets.main.get().runtimeClasspath
    }
}

configurations["integrationTestImplementation"].extendsFrom(configurations.testImplementation.get())
configurations["integrationTestRuntimeOnly"].extendsFrom(configurations.testRuntimeOnly.get())

// IntelliJ に integrationTest をテストソースとして認識させる（@Autowired 等の Spring 補助が効くようになる）
idea {
    module {
        testSources.from(sourceSets["integrationTest"].java.srcDirs)
        testResources.from(sourceSets["integrationTest"].resources.srcDirs)
    }
}

// -----------------------------------------------------------------------------
// 依存関係
// -----------------------------------------------------------------------------
dependencies {
    // --- Spring Boot ---
    implementation(libs.spring.boot.starter.webmvc)
    implementation(libs.spring.boot.starter.validation)
    // 外部の HTTP の呼び出し（画像の変換の imgproxy）。トレースとメトリクスが付く RestClient.Builder を使う
    implementation(libs.spring.boot.starter.restclient)
    // 認証（OAuth 2.0 の Resource Server。運営者・会員の access token を検証する）
    implementation(libs.spring.boot.starter.security.oauth2.resource.server)
    // --- 可観測性（バックエンドのアーキテクチャ 第 5 節） ---
    // ヘルスチェック（liveness / readiness）、メトリクス（Prometheus）
    implementation(libs.spring.boot.starter.actuator)
    runtimeOnly(libs.micrometer.registry.prometheus)
    // 分散トレーシング（Brave + Zipkin）。エラーレスポンスの traceId にも使う
    implementation(libs.spring.boot.starter.zipkin)

    // --- モジュラーモノリス（バックエンドのアーキテクチャ 第 1 節） ---
    implementation(libs.bundles.spring.modulith)

    // --- 定期ジョブの分散ロック（ShedLock。ロックは PostgreSQL のテーブルに持つ） ---
    implementation(libs.shedlock.spring)
    implementation(libs.shedlock.provider.jdbc.template)

    // --- 決済サービス（Stripe。mock のときも入るが、呼ばない） ---
    implementation(libs.stripe.java)

    // --- メール（出荷通知メール。SMTP で送る） ---
    implementation(libs.spring.boot.starter.mail)

    // --- API（OpenAPI の生成コード） ---
    implementation(libs.jackson.databind.nullable)

    // --- データベース ---
    implementation(libs.spring.boot.starter.jooq)
    implementation(libs.spring.boot.starter.flyway)
    implementation(libs.flyway.database.postgresql)
    // 制約違反の制約名を取り出すため、コンパイル時にも使う（ConstraintViolationTranslator）
    implementation(libs.postgresql)
    jooqGenerator(libs.postgresql)

    // --- 画像のファイル（S3 API） ---
    implementation(libs.awssdk.s3)

    // --- コード生成（Lombok） ---
    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)

    // --- 静的解析（Error Prone。全てのソースセットのコンパイルで動く） ---
    errorprone(libs.errorprone.core)
    errorprone(libs.nullaway)
    compileOnly(libs.errorprone.annotations)
    // @Nullable（org.jspecify.annotations.Nullable）。Spring が間接で持つが、直接使うので明示する。
    // test などの classpath にも要るので、compileOnly でなく implementation にする
    implementation(libs.jspecify)

    // --- DDD（ドメインのオブジェクトの明示。バックエンドのアーキテクチャ 第 2 節） ---
    implementation(libs.bundles.jmolecules)

    // --- テスト：単体 ---
    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.spring.modulith.starter.test)
    testRuntimeOnly(libs.junit.platform.launcher)

    // --- テスト：統合 ---
    "integrationTestImplementation"(testFixtures(project))
    "integrationTestImplementation"(libs.spring.boot.testcontainers)
    "integrationTestImplementation"(libs.testcontainers.postgresql)
    "integrationTestImplementation"(libs.spring.boot.starter.webmvc.test)
    "integrationTestImplementation"(libs.spring.boot.starter.security.test)
    "integrationTestImplementation"(libs.spring.boot.micrometer.tracing.test)
    "integrationTestImplementation"(libs.spring.boot.micrometer.metrics.test)
}

// -----------------------------------------------------------------------------
// テスト task
//   ./gradlew test             → 単体のみ
//   ./gradlew integrationTest  → 統合のみ
//   ./gradlew check / build    → 両方
// -----------------------------------------------------------------------------
tasks.named<Test>("test") {
    useJUnitPlatform()
    // ArchUnit のテスト（src/test/.../architecture）は、テストのクラスごとに本番のクラスを読み込むので、既定（512MB）では足りない
    maxHeapSize = "1g"
    // 契約テスト（AdminApiPermissionContractTest など）が読む OpenAPI の置き場所
    systemProperty("openapi.dir", file("../../api/openapi").absolutePath)
    inputs.dir("../../api/openapi")
    // 契約テスト（AdminRealmPermissionContractTest など）が読む realm の設定の置き場所
    systemProperty("keycloak.realms.dir", file("../../docker/keycloak/realms").absolutePath)
    inputs.dir("../../docker/keycloak/realms")
    // 図をコードと比べる文書（ModuleDiagramTest）。-PupdateDocs を付けると、比べずに文書の図を書き直す
    systemProperty("design.overview", file("../../docs/design/overview.md").absolutePath)
    inputs.files(fileTree("../../docs/design") { include("overview.md") })
    if (project.hasProperty("updateDocs")) {
        systemProperty("ecsite.update-docs", "true")
        outputs.upToDateWhen { false }
    }
}

val integrationTest = tasks.register<Test>("integrationTest") {
    description = "統合テストを実行する（Docker 必須）"
    group = "verification"
    testClassesDirs = sourceSets["integrationTest"].output.classesDirs
    classpath = sourceSets["integrationTest"].runtimeClasspath
    useJUnitPlatform()
    // IdP の設定の統合テスト（KeycloakIntegrationTest）が Keycloak に取り込む realm の設定とテーマ
    systemProperty("keycloak.realms.dir", file("../../docker/keycloak/realms").absolutePath)
    systemProperty("keycloak.theme.dir", file("../../docker/keycloak/themes/ec-site").absolutePath)
    // 地域の最初のデータの郵便番号の一覧（RegionApiTest が件数を照合する）
    systemProperty("region.postal-codes", file("src/tools/resources/region/postal-codes.txt").absolutePath)
    inputs.dir("../../docker/keycloak")
    // 本番の jar の起動の統合テスト（ProductionJarStartupTest）が別のプロセスで起動する jar
    val bootJarFile = tasks.bootJar.flatMap { it.archiveFile }
    inputs.file(bootJarFile)
    doFirst { systemProperty("ecsite.boot-jar", bootJarFile.get().asFile.absolutePath) }
    shouldRunAfter(tasks.test)

    // 統合テストのフォーク（JVM）の数。各フォークがテストのコンテナを 1 回ずつ起動するので、手元の既定は 1。
    // CI で時間が短くなるときだけ、-PintegrationTestForks=2 で増やす
    maxParallelForks = providers.gradleProperty("integrationTestForks").map { it.toInt() }.orElse(1).get()

    // 統合テストを複数のジョブに分けて流す：-PintegrationTestShard=<番号>/<総数>（例 1/2）。
    // プロパティがなければ全てを流す（手元の既定）
    providers.gradleProperty("integrationTestShard").orNull?.let { spec ->
        val (index, count) = parseShard(spec)
        exclude { element -> !element.isDirectory && shardOf(element.path, count) != index }
    }
}

tasks.check { dependsOn(integrationTest) }

// -----------------------------------------------------------------------------
// 統合テストの分け方（CI でジョブを分けて同時に流して、全体の時間を短くする）
//   テストクラスの完全修飾名の hash で決める。内部クラス（Outer$Inner）は外のクラスと同じ側に入る。
//   名前だけで決まるので、テストクラスを追加しても設定を変えなくてよい。全てのクラスがちょうど 1 つの側に入る。
//   2 つの側の時間が離れたときは、shardOf の salt を変えて分け直す
//   ./gradlew integrationTestShards                                → 各側に入るクラスの一覧（Docker 不要）
//   ./gradlew integrationTestShards -PintegrationTestShardCount=3  → 3 つに分けた一覧
// -----------------------------------------------------------------------------
/** "1/2" → (0, 2)。番号は 1 から数え、内部では 0 から数える */
fun parseShard(spec: String): Pair<Int, Int> {
    val match = Regex("([0-9]+)/([0-9]+)").matchEntire(spec)
        ?: throw GradleException("integrationTestShard は <番号>/<総数> で指定する（例 1/2）：$spec")
    val index = match.groupValues[1].toInt()
    val count = match.groupValues[2].toInt()
    if (count < 1 || index < 1 || index > count) {
        throw GradleException("integrationTestShard の番号は 1〜総数：$spec")
    }
    return (index - 1) to count
}

/** クラスのファイル（com/ecsite/Foo$Bar.class）またはソース（com/ecsite/Foo.java）の相対パスが入る側（0 から数える） */
fun shardOf(path: String, count: Int): Int {
    val outer = path.removeSuffix(".class").removeSuffix(".java").substringBefore('$').replace('/', '.')
    val salt = "a" // 時間の差が大きいときに変えて分け直す（"a" はテストの数がほぼ半々になる値）
    // String.hashCode の下位ビットは文字の偶奇に偏るので（2 つに分けると偏る）、混ぜてから割る
    val mixed = (salt + outer).hashCode() * -1640531535
    return Math.floorMod(mixed xor (mixed ushr 15), count)
}

tasks.register("integrationTestShards") {
    description = "統合テストの各側に入るテストクラスの一覧を出す（Docker 不要）"
    group = "verification"
    val sourceRoot = layout.projectDirectory.dir("src/integrationTest/java")
    val shardCount = providers.gradleProperty("integrationTestShardCount").map { it.toInt() }.orElse(2)
    inputs.dir(sourceRoot)
    doLast {
        val root = sourceRoot.asFile
        val count = shardCount.get()
        val classes = root.walkTopDown().filter { it.isFile && (it.nameWithoutExtension.endsWith("Test") || it.nameWithoutExtension.endsWith("Tests")) }
            .map { it.relativeTo(root).invariantSeparatorsPath.removeSuffix(".java").replace('/', '.') }
            .sorted().toList()
        val shards = classes.groupBy { shardOf(it, count) }
        for (index in 0 until count) {
            val members = shards[index].orEmpty()
            println("shard ${index + 1}/$count: ${members.size} classes")
            members.forEach { println("  $it") }
        }
        println("total: ${classes.size} classes")
    }
}

// -----------------------------------------------------------------------------
// 地域の最初のデータ（地域モジュールの設計書「最初のデータ」）
//   ./gradlew generateRegionMigration                      → /.local/masterdata/ の郵便番号のデータから作る
//   ./gradlew generateRegionMigration -PpostalCodeCsv=<CSV> → CSV の置き場所を指定する
//   郵便番号のデータは Git に入れない。作ったマイグレーションは Git に入れる
// -----------------------------------------------------------------------------
tasks.register<JavaExec>("generateRegionMigration") {
    description = "郵便番号のデータから、地域の最初のデータのマイグレーションを作る"
    group = "region"
    classpath = sourceSets["tools"].runtimeClasspath
    mainClass.set("com.ecsite.tools.region.RegionMigrationGenerator")
    val csv = providers.gradleProperty("postalCodeCsv")
        .orElse(layout.projectDirectory.file("../../.local/masterdata/utf_ken_all/utf_ken_all.csv").asFile.path)
    args(
        csv.get(),
        layout.projectDirectory.file("src/tools/resources/region/postal-codes.txt").asFile.path,
        layout.projectDirectory.file("src/main/resources/db/migration/V202610200910__region_seed_master.sql").asFile.path,
    )
}

// -----------------------------------------------------------------------------
// Javadoc の書き漏れ（コメントの書き方 第 2 節）
//   Checkstyle で、本番のコード（src/main/java）のクラスとメンバー（private を含む）に Javadoc があることを確認する。
//   Javadoc があるかだけを見て、@param・@return は見ない。
//   生成したコード（jOOQ、OpenAPI。build/ の下）、上書き（@Override）、テストは見ない。ほかの規則は入れない。
//   規則：config/checkstyle/checkstyle.xml、報告：build/reports/checkstyle/
//   Checkstyle はビルドの道具で、アプリと依存のライセンスの検査の構成には入らない
// -----------------------------------------------------------------------------
checkstyle {
    toolVersion = libs.versions.checkstyle.get()
    sourceSets = listOf(project.sourceSets.main.get())
    isIgnoreFailures = false
    maxWarnings = 0
}

tasks.named<Checkstyle>("checkstyleMain") {
    setSource(fileTree("src/main/java"))
}

// -----------------------------------------------------------------------------
// カバレッジ（テストの書き方 第 1 節。報告だけで、基準の値では失敗させない）
//   単体と統合の実行データを合わせて 1 つの報告にする。生成したコード（jOOQ、OpenAPI）は数えない。
//   報告：build/reports/jacoco/coverageReport/（html、xml）
//   CI はテストをジョブに分けて流すので、他のジョブの実行データとクラスを渡して、報告だけを作る（テストもコンパイルも流さない）：
//   ./gradlew coverageReport -PcoverageExecDir=<*.exec を探すディレクトリ> -PcoverageClassesDir=<build/classes/java/main の写し>
//   各ジョブは別々にコンパイルするが、javac の出力は同じ入力なら同じなので、JaCoCo のクラスの ID は一致する
// -----------------------------------------------------------------------------
jacoco {
    toolVersion = libs.versions.jacoco.get()
}

/** 生成したコード（jOOQ、OpenAPI）は数えない */
val coverageExcludes = listOf("com/ecsite/jooq/**", "com/ecsite/api/**")

val coverageReport = tasks.register<JacocoReport>("coverageReport") {
    description = "単体と統合のテストを合わせたカバレッジの報告を作る"
    group = "verification"
    val execDir = providers.gradleProperty("coverageExecDir").orNull
    val classesDir = providers.gradleProperty("coverageClassesDir").orNull
    if (execDir != null) {
        // 渡された実行データを使う。テストの task には依存しない
        executionData.from(fileTree(execDir) { include("**/*.exec") })
    } else {
        executionData.from(
            tasks.test.map { it.extensions.getByType<JacocoTaskExtension>().destinationFile!! },
            integrationTest.map { it.extensions.getByType<JacocoTaskExtension>().destinationFile!! },
        )
    }
    if (classesDir != null) {
        // 渡されたクラスを使う。compileJava には依存しない
        sourceDirectories.from(layout.projectDirectory.dir("src/main/java"))
        classDirectories.setFrom(fileTree(classesDir) { exclude(coverageExcludes) })
    } else {
        sourceSets(sourceSets.main.get())
        classDirectories.setFrom(sourceSets.main.get().output.classesDirs.asFileTree.matching {
            exclude(coverageExcludes)
        })
    }
    reports {
        xml.required = true
        html.required = true
    }
}

tasks.check { finalizedBy(coverageReport) }

// -----------------------------------------------------------------------------
// 依存のライセンスの検査（依存とライセンス）
//   方針の正本は scripts/ci/licenses/policy.json（フロントの検査と共通）。
//   依存が書くライセンスの名前を policy の normalize で SPDX の識別子にそろえ、許可の一覧と、
//   条件付きの一覧（部品ごと）に合わない部品があれば checkLicense が失敗する。
//   どれか 1 つのライセンスが通れば通る（複数のライセンスから選べる部品のため）。
//   報告：build/reports/dependency-license/
// -----------------------------------------------------------------------------
val licensePolicyFile = rootProject.layout.projectDirectory.file("../../scripts/ci/licenses/policy.json").asFile

@Suppress("UNCHECKED_CAST")
val licensePolicy = groovy.json.JsonSlurper().parse(licensePolicyFile) as Map<String, Any>

/** 方針の部品の名前の形（maven:group:name、* は任意の文字列）→ checkLicense の正規表現（group:name） */
fun componentPattern(key: String): String =
    key.removePrefix("maven:").split("*").joinToString(".*") { Regex.escape(it) }

/** policy の normalize を、LicenseBundleNormalizer の bundle（名前の完全一致 → SPDX の識別子）にする */
fun licenseNormalizerBundle(): String {
    @Suppress("UNCHECKED_CAST")
    val normalize = (licensePolicy["normalize"] as Map<String, String>).filterKeys { it != "\$comment" }
    val ids = normalize.values.toSortedSet()
    return groovy.json.JsonOutput.toJson(
        mapOf(
            "bundles" to ids.map { mapOf("bundleName" to it, "licenseName" to it) },
            "transformationRules" to normalize.map { (name, id) ->
                mapOf("bundleName" to id, "licenseNamePattern" to Regex.escape(name))
            },
        )
    )
}

/** policy の許可と条件付きの一覧を、checkLicense の許可の一覧（allowedLicensesFile）にする */
fun allowedLicenses(): String {
    @Suppress("UNCHECKED_CAST")
    val allowed = licensePolicy["allowed"] as List<String>
    @Suppress("UNCHECKED_CAST")
    val conditional = licensePolicy["conditional"] as Map<String, Any>
    @Suppress("UNCHECKED_CAST")
    val conditionalLicenses = conditional["licenses"] as List<String>
    @Suppress("UNCHECKED_CAST")
    val components = (conditional["components"] as Map<String, String>).keys.filter { it.startsWith("maven:") }
    val entries = allowed.map { mapOf("moduleLicense" to Regex.escape(it), "moduleName" to ".*") } +
        components.flatMap { component ->
            conditionalLicenses.map { mapOf("moduleLicense" to Regex.escape(it), "moduleName" to componentPattern(component)) }
        }
    return groovy.json.JsonOutput.toJson(mapOf("allowedLicenses" to entries))
}

/**
 * POM にライセンスの名前があれば、POM だけを使う（MANIFEST と、jar の中のライセンスのファイルを捨てる）。
 * jar のライセンスのファイルは、同梱した他の部品のライセンスの文も含むことがあり（mysql-connector-j の
 * GPL の LICENSE の中の Apache-2.0 など）、それで通ってしまうのを防ぐ。POM に名前がなければ、他から読む。
 */
class PreferPomLicenses : com.github.jk1.license.filter.DependencyFilter {
    override fun filter(source: com.github.jk1.license.ProjectData): com.github.jk1.license.ProjectData {
        source.configurations.flatMap { it.dependencies }.forEach { module ->
            if (module.poms.any { pom -> pom.licenses.any { !it.name.isNullOrBlank() } }) {
                module.manifests.clear()
                module.licenseFiles.clear()
            }
        }
        return source
    }
}

val allowedLicensesOutput =layout.buildDirectory.file("license-policy/allowed-licenses.json")

licenseReport {
    // 実行時、コンパイル時、テスト、注釈の処理、jOOQ のコード生成。
    // Error Prone の errorprone 構成は解決できないが、annotationProcessor が継承するので、そこで検査に入る
    // buildscript の classpath（生成で使う Testcontainers・Flyway）は構成に出ないが、同じ部品が統合テストと実行時の構成に入る
    configurations = arrayOf(
        "compileClasspath", "runtimeClasspath",
        "testCompileClasspath", "testRuntimeClasspath",
        "integrationTestCompileClasspath", "integrationTestRuntimeClasspath",
        "testFixturesCompileClasspath", "testFixturesRuntimeClasspath",
        "annotationProcessor", "jooqGenerator",
    )
    filters = arrayOf(
        PreferPomLicenses(),
        com.github.jk1.license.filter.LicenseBundleNormalizer(licenseNormalizerBundle().byteInputStream(), false),
    )
    renderers = arrayOf(com.github.jk1.license.render.JsonReportRenderer("licenses.json", false))
    allowedLicensesFile = allowedLicensesOutput.get().asFile
}

val licensePolicyTask = tasks.register("licensePolicy") {
    description = "policy.json から checkLicense の許可の一覧を作る"
    group = "verification"
    inputs.file(licensePolicyFile)
    outputs.file(allowedLicensesOutput)
    doLast {
        allowedLicensesOutput.get().asFile.writeText(allowedLicenses())
    }
}

tasks.named("checkLicense") {
    dependsOn(licensePolicyTask)
    // 依存の名前の正規化は設定のときに読むので、方針が変わったら報告も再作成する
    inputs.file(licensePolicyFile)
}
tasks.named("generateLicenseReport") { inputs.file(licensePolicyFile) }

tasks.check { dependsOn("checkLicense") }

// -----------------------------------------------------------------------------
// jOOQ コード生成
//   スキーマの正本は Flyway のマイグレーション（バックエンドのアーキテクチャ 第 6.2 節）。
//   生成時に Testcontainers で使い捨ての PostgreSQL を起動し、マイグレーションを適用してから生成する。
//   → compose の DB を起動していなくてもビルドできる（Docker は必要）
//   生成コードはコミットしない（build/ 配下）。
// -----------------------------------------------------------------------------
val migrationDir = layout.projectDirectory.dir("src/main/resources/db/migration")

// 生成の対象にする schema（モジュールごと）。Flyway の履歴（public）は対象外
val jooqSchemas = listOf("common", "tax", "catalog", "media", "inventory", "cart", "ordering", "member", "payment", "shipping", "notification", "reporting", "region")

jooq {
    version.set(libs.versions.jooq.get())

    configurations {
        create("main") {
            jooqConfiguration.apply {
                logging = Logging.WARN

                // 接続情報 ※url / user / password は generateJooq 実行時にコンテナのものへ差し替える
                jdbc.apply {
                    driver = "org.postgresql.Driver"
                }

                generator.apply {
                    name = "org.jooq.codegen.JavaGenerator"

                    database.apply {
                        name = "org.jooq.meta.postgres.PostgresDatabase"
                        schemata.addAll(jooqSchemas.map { SchemaMappingType().withInputSchema(it) })
                        // 楽観ロック用カラム
                        recordVersionFields = "version"
                        // 型の強制変換：timestamptz → Instant
                        forcedTypes.add(ForcedType().apply {
                            name = "INSTANT"
                            includeTypes = "(?i)timestamptz|timestamp\\ with\\ time\\ zone"
                        })
                    }

                    generate.apply {
                        isFluentSetters = true
                        isJavaTimeTypes = true
                    }

                    target.apply {
                        packageName = "com.ecsite.jooq"
                    }
                }
            }
        }
    }
}

tasks.named<JooqGenerate>("generateJooq") {
    // 入力（マイグレーションと生成の設定）が変わらなければ省く。プラグインの既定は毎回生成し直す（約 6 秒）。
    // 接続先はコンテナを起動した後に差し替えるので、入力の比較には入らない
    allInputsDeclared.set(true)
    // マイグレーションが変わったら再生成する
    inputs.dir(migrationDir)

    // 使い捨て PostgreSQL（docker/compose.yaml と同じイメージ）
    val container = PostgreSQLContainer(DockerImageName.parse("postgres:18-alpine"))

    // jooq { } ブロックで組んだ Configuration と同一インスタンスを参照している
    val jdbc = jooq.configurations.getByName("main").jooqConfiguration.jdbc
    val migrationLocation = "filesystem:${migrationDir.asFile.absolutePath}"

    doFirst {
        container.start()
        Flyway.configure()
            .dataSource(container.jdbcUrl, container.username, container.password)
            .locations(migrationLocation)
            .load()
            .migrate()
        jdbc.url = container.jdbcUrl
        jdbc.user = container.username
        jdbc.password = container.password
    }
    doLast {
        container.stop()
    }
}

// -----------------------------------------------------------------------------
// OpenAPI からのコード生成
//   API 契約の正本はリポジトリのルートの api/openapi/（API の規約）。
//   Controller が実装するインターフェースとモデルを生成する。生成コードはコミットしない（build/ 配下）。
// -----------------------------------------------------------------------------
val openApiDir = rootProject.layout.projectDirectory.dir("../../api/openapi")

// 生成する API：名前 → パッケージ（com.ecsite.api.<名前>）
val openApiSpecs = listOf("admin", "store")

val openApiGenerateTasks = openApiSpecs.map { spec ->
    tasks.register<org.openapitools.generator.gradle.plugin.tasks.GenerateTask>("openApiGenerate${spec.replaceFirstChar { it.uppercase() }}") {
        description = "api/openapi/$spec.yaml から Spring のインターフェースとモデルを生成する"
        group = "openapi"
        generatorName.set("spring")
        inputSpec.set(openApiDir.file("$spec.yaml").asFile.absolutePath)
        outputDir.set(layout.buildDirectory.dir("generated/openapi/$spec").get().asFile.absolutePath)
        apiPackage.set("com.ecsite.api.$spec")
        modelPackage.set("com.ecsite.api.$spec.model")
        // 生成するのはソースだけ（README、pom など不要）
        globalProperties.set(mapOf("apis" to "", "models" to "", "supportingFiles" to "ApiUtil.java"))
        configOptions.set(
            mapOf(
                // Controller が実装するインターフェースだけを生成する
                "interfaceOnly" to "true",
                // 未実装のメソッドがコンパイルエラーになるよう、既定の実装（501）を作らない
                "skipDefaultInterface" to "true",
                "useSpringBoot4" to "true",
                "useJackson3" to "true",
                // tag ごとにインターフェースを分ける
                "useTags" to "true",
                // 入力検証は Bean Validation で行う（API の規約）
                "useBeanValidation" to "true",
                // Swagger のアノテーションは付けない
                "documentationProvider" to "none",
                "annotationLibrary" to "none",
                "hideGenerationTimestamp" to "true",
                // JSON の出力・null の扱いはアプリ全体の設定に任せる（項目ごとのアノテーションは付けない）
                "generateJsonIncludeAnnotations" to "false",
                "generateJsonSetterNullsAnnotations" to "false",
                // nullable の項目は JsonNullable で表す（既定）。jackson-databind-nullable は Jackson 3 に対応済み
                "openApiNullable" to "true",
            )
        )
        // 契約が変わったら再生成する
        inputs.dir(openApiDir)
    }
}

sourceSets.main {
    java.srcDirs(openApiSpecs.map { layout.buildDirectory.dir("generated/openapi/$it/src/main/java") })
}

tasks.named("compileJava") { dependsOn(openApiGenerateTasks) }

// 管理 API の権限の判定は、起動時に admin.yaml の x-required-permission を読む。
// 店舗 API の token の要否は、起動時に store.yaml の security を読む。
// 生成と同じファイルをリソースに写す（classpath:openapi/admin.yaml、classpath:openapi/store.yaml）
tasks.processResources {
    from(openApiDir) {
        include("admin.yaml", "store.yaml")
        into("openapi")
    }
}

// OpenAPI の構文の検証。check に含めるので、ローカルと CI で同じ検証が走る
val openApiValidateTasks = openApiSpecs.map { spec ->
    tasks.register<org.openapitools.generator.gradle.plugin.tasks.ValidateTask>("openApiValidate${spec.replaceFirstChar { it.uppercase() }}") {
        description = "api/openapi/$spec.yaml の構文を検証する"
        group = "openapi"
        inputSpec.set(openApiDir.file("$spec.yaml").asFile.absolutePath)
        // 推奨事項（ルールの違反ではないもの）でも失敗させる
        recommend.set(true)
    }
}

tasks.check { dependsOn(openApiValidateTasks) }

// -----------------------------------------------------------------------------
// 本番の jar の中身
// -----------------------------------------------------------------------------
// ローカル・E2E・Stripe の sandbox の設定は、mock のフラグとローカル用の秘密情報を持つので、本番の jar に入れない。
// bootRun とテストは、今どおりリソースから読む
val localOnlyResources = listOf("application-local.yml", "application-e2e.yml", "application-stripe.yml")

tasks.bootJar {
    exclude(localOnlyResources)
}

val checkBootJar = tasks.register("checkBootJar") {
    description = "本番の jar に、ローカル・E2E・Stripe の sandbox の設定ファイルがないことを確認する"
    group = "verification"
    val jar = tasks.bootJar.flatMap { it.archiveFile }
    inputs.file(jar)
    doLast {
        val names = ZipFile(jar.get().asFile).use { zip -> zip.entries().asSequence().map { it.name }.toList() }
        val found = names.filter { name -> localOnlyResources.any { name.endsWith("/$it") } }
        if (found.isNotEmpty()) {
            throw GradleException("本番の jar に入れない設定ファイルがある：$found")
        }
    }
}

tasks.check { dependsOn(checkBootJar) }

// -----------------------------------------------------------------------------
// テストのクラスの名前の重なり
// -----------------------------------------------------------------------------
// 単体テスト・統合テスト・テスト用の共通コードの間で、同じパッケージに同じ名前のクラスを置くと、
// クラスパスで片方が隠れ、テストが流れないか、別のクラスを読む
val testSourceSetNames = listOf("test", "integrationTest", "testFixtures")

val checkTestClassNames = tasks.register("checkTestClassNames") {
    description = "テストのソースセットの間に、同じ完全修飾名のクラスがないことを確認する"
    group = "verification"
    val roots = testSourceSetNames.associateWith { name -> layout.projectDirectory.dir("src/$name/java") }
    roots.values.forEach { root -> inputs.dir(root).optional() }
    doLast {
        val owners = mutableMapOf<String, MutableList<String>>()
        roots.forEach { (name, root) ->
            val dir = root.asFile
            if (!dir.isDirectory) return@forEach
            dir.walkTopDown().filter { it.isFile && it.extension == "java" }.forEach { file ->
                val fqn = file.relativeTo(dir).invariantSeparatorsPath.removeSuffix(".java").replace('/', '.')
                owners.getOrPut(fqn) { mutableListOf() }.add(name)
            }
        }
        val duplicated = owners.filterValues { it.size > 1 }
        if (duplicated.isNotEmpty()) {
            throw GradleException("同じ完全修飾名のテストのクラスがある：" +
                    duplicated.entries.joinToString { (fqn, sets) -> "$fqn（${sets.joinToString("、")}）" })
        }
    }
}

tasks.check { dependsOn(checkTestClassNames) }

// -----------------------------------------------------------------------------
// コンパイルオプション
// -----------------------------------------------------------------------------
tasks.withType<JavaCompile> {
    // リフレクション用にメソッド引数名を保持
    options.compilerArgs.add("-parameters")
    options.encoding = "UTF-8"
    // 警告も失敗にする（Error Prone の警告も、javac の -Werror で失敗になる）
    options.compilerArgs.add("-Werror")
    options.errorprone {
        // 生成したコード（jOOQ、OpenAPI。build/generated の下）は検査しない
        excludedPaths.set(".*[/\\\\]build[/\\\\]generated.*")
        disableWarningsInGeneratedCode.set(true)
        // Lombok の @NonNull の引数を持つクラスで Error Prone が例外を投げ、コンパイルが失敗するので無効にする
        disable("StringConcatToTextBlock")
    }
}

// -----------------------------------------------------------------------------
// NullAway（main のコンパイルだけ）
// -----------------------------------------------------------------------------
// 全ての報告を直すまでの一時の形：Gradle のプロパティ -Pnullaway を付けたときだけ compileJava で動かし、
// 報告を警告にして -Werror を外す（既定のビルドと CI は今のまま通る）。
// T18-02-09 で、プロパティなしで常に動かし、報告をエラーにする。
val nullawayEnabled = providers.gradleProperty("nullaway").isPresent

tasks.withType<JavaCompile>().configureEach {
    options.errorprone {
        if (name == "compileJava" && nullawayEnabled) {
            check("NullAway", net.ltgt.gradle.errorprone.CheckSeverity.WARN)
            // com.ecsite.jooq（jOOQ の生成）と com.ecsite.api（OpenAPI の生成）は、注釈のない生成コードとして扱う
            option("NullAway:AnnotatedPackages", "com.ecsite")
            option("NullAway:UnannotatedSubPackages", "com.ecsite.jooq,com.ecsite.api")
            option("NullAway:CustomContractAnnotations", "org.springframework.lang.Contract")
            // JSpecifyMode は付けない（JDK 22 以上が要る。ここは JDK 21）
        } else {
            // test、integrationTest、testFixtures、tools は検査しない
            disable("NullAway")
        }
    }
    if (name == "compileJava" && nullawayEnabled) {
        options.compilerArgs.remove("-Werror")
        // javac は警告を 100 件で打ち切るので、全ての報告を出す
        options.compilerArgs.addAll(listOf("-Xmaxwarns", "100000"))
    }
}
