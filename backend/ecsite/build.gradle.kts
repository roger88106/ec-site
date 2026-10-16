import nu.studer.gradle.jooq.JooqGenerate
import org.flywaydb.core.Flyway
import org.jooq.meta.jaxb.ForcedType
import org.jooq.meta.jaxb.Logging
import org.jooq.meta.jaxb.SchemaMappingType
import org.testcontainers.postgresql.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName

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
    jacoco
    checkstyle
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

    // --- DDD（ドメインのオブジェクトの明示。バックエンドのアーキテクチャ 第 2 節） ---
    implementation(libs.bundles.jmolecules)

    // --- テスト：単体 ---
    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.spring.modulith.starter.test)
    testRuntimeOnly(libs.junit.platform.launcher)

    // --- テスト：統合 ---
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
    // 契約テスト（AdminApiPermissionContractTest など）が読む OpenAPI の置き場所
    systemProperty("openapi.dir", file("../../api/openapi").absolutePath)
    inputs.dir("../../api/openapi")
    // 契約テスト（AdminRealmPermissionContractTest など）が読む realm の設定の置き場所
    systemProperty("keycloak.realms.dir", file("../../docker/keycloak/realms").absolutePath)
    inputs.dir("../../docker/keycloak/realms")
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
    inputs.dir("../../docker/keycloak")
    shouldRunAfter(tasks.test)
}

tasks.check { dependsOn(integrationTest) }

// -----------------------------------------------------------------------------
// Javadoc の書き漏れ（コメントの書き方 第 2 節）
//   Checkstyle で、本番のコード（src/main/java）の public のクラスとメソッドに Javadoc があることを確かめる。
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
// -----------------------------------------------------------------------------
jacoco {
    toolVersion = libs.versions.jacoco.get()
}

val coverageReport = tasks.register<JacocoReport>("coverageReport") {
    description = "単体と統合のテストを合わせたカバレッジの報告を作る"
    group = "verification"
    executionData.from(
        tasks.test.map { it.extensions.getByType<JacocoTaskExtension>().destinationFile!! },
        integrationTest.map { it.extensions.getByType<JacocoTaskExtension>().destinationFile!! },
    )
    sourceSets(sourceSets.main.get())
    classDirectories.setFrom(sourceSets.main.get().output.classesDirs.asFileTree.matching {
        exclude("com/ecsite/jooq/**", "com/ecsite/api/**")
    })
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
    // buildscript の classpath（生成で使う Testcontainers・Flyway）は構成に出ないが、同じ部品が統合テストと実行時の構成に入る
    configurations = arrayOf(
        "compileClasspath", "runtimeClasspath",
        "testCompileClasspath", "testRuntimeClasspath",
        "integrationTestCompileClasspath", "integrationTestRuntimeClasspath",
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
    // 依存の名前の正規化は設定のときに読むので、方針が変わったら報告も作り直す
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
val jooqSchemas = listOf("common", "tax", "catalog", "media", "inventory", "cart", "ordering", "member", "payment", "shipping", "notification", "reporting")

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
// 生成と同じファイルをリソースに写す（classpath:openapi/admin.yaml）
tasks.processResources {
    from(openApiDir.file("admin.yaml")) { into("openapi") }
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
// コンパイルオプション
// -----------------------------------------------------------------------------
tasks.withType<JavaCompile> {
    // リフレクション用にメソッド引数名を保持
    options.compilerArgs.add("-parameters")
    options.encoding = "UTF-8"
}
