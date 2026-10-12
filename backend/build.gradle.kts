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
    // 認証（OAuth 2.0 の Resource Server。運営者・会員の access token を検証する。plan 003 第 3.1 節）
    implementation(libs.spring.boot.starter.security.oauth2.resource.server)
    // --- 可観測性（constitution 5-9） ---
    // ヘルスチェック（liveness / readiness）、メトリクス（Prometheus）
    implementation(libs.spring.boot.starter.actuator)
    runtimeOnly(libs.micrometer.registry.prometheus)
    // 分散トレーシング（Brave + Zipkin）。エラーレスポンスの traceId にも使う
    implementation(libs.spring.boot.starter.zipkin)

    // --- モジュラーモノリス（constitution 4.1） ---
    implementation(libs.bundles.spring.modulith)

    // --- API（OpenAPI の生成コード） ---
    implementation(libs.jackson.databind.nullable)

    // --- データベース ---
    implementation(libs.spring.boot.starter.jooq)
    implementation(libs.spring.boot.starter.flyway)
    implementation(libs.flyway.database.postgresql)
    // 制約違反の制約名を取り出すため、コンパイル時にも使う（ConstraintViolationTranslator）
    implementation(libs.postgresql)
    jooqGenerator(libs.postgresql)

    // --- 画像のファイル（S3 API。plan 001 第 7 節） ---
    implementation(libs.awssdk.s3)

    // --- コード生成（Lombok） ---
    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)

    // --- DDD（ドメインのオブジェクトの明示。constitution 4.2） ---
    implementation(libs.bundles.jmolecules)

    // --- テスト：単体 ---
    testImplementation(libs.spring.boot.starter.test)
    testRuntimeOnly(libs.junit.platform.launcher)

    // --- テスト：統合 ---
    "integrationTestImplementation"(libs.spring.boot.testcontainers)
    "integrationTestImplementation"(libs.testcontainers.postgresql)
    "integrationTestImplementation"(libs.spring.boot.starter.webmvc.test)
    "integrationTestImplementation"(libs.spring.boot.starter.security.test)
    "integrationTestImplementation"(libs.spring.modulith.starter.test)
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
    systemProperty("openapi.dir", file("../api/openapi").absolutePath)
    inputs.dir("../api/openapi")
    // 契約テスト（AdminRealmPermissionContractTest など）が読む realm の設定の置き場所
    systemProperty("keycloak.realms.dir", file("../keycloak/realms").absolutePath)
    inputs.dir("../keycloak/realms")
}

val integrationTest = tasks.register<Test>("integrationTest") {
    description = "統合テストを実行する（Docker 必須）"
    group = "verification"
    testClassesDirs = sourceSets["integrationTest"].output.classesDirs
    classpath = sourceSets["integrationTest"].runtimeClasspath
    useJUnitPlatform()
    shouldRunAfter(tasks.test)
}

tasks.check { dependsOn(integrationTest) }

// -----------------------------------------------------------------------------
// jOOQ コード生成
//   スキーマの正本は Flyway のマイグレーション（constitution 7.2）。
//   生成時に Testcontainers で使い捨ての PostgreSQL を起動し、マイグレーションを適用してから生成する。
//   → compose の DB を起動していなくてもビルドできる（Docker は必要）
//   生成コードはコミットしない（build/ 配下）。
// -----------------------------------------------------------------------------
val migrationDir = layout.projectDirectory.dir("src/main/resources/db/migration")

// 生成の対象にする schema（モジュールごと）。Flyway の履歴（public）は対象外
val jooqSchemas = listOf("common", "tax", "catalog", "media", "inventory", "cart", "ordering")

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
    // マイグレーションが変わったら再生成する
    inputs.dir(migrationDir)

    // 使い捨て PostgreSQL（compose.yaml と同じイメージ）
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
//   API 契約の正本はリポジトリのルートの api/openapi/（constitution 9）。
//   Controller が実装するインターフェースとモデルを生成する。生成コードはコミットしない（build/ 配下）。
// -----------------------------------------------------------------------------
val openApiDir = rootProject.layout.projectDirectory.dir("../api/openapi")

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
                // 入力検証は Bean Validation で行う（constitution 9）
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

// 管理 API の権限の判定（plan 003 第 3.2 節）は、起動時に admin.yaml の x-required-permission を読む。
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
