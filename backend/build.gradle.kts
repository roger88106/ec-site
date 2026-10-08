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

    // --- データベース ---
    implementation(libs.spring.boot.starter.jooq)
    implementation(libs.spring.boot.starter.flyway)
    implementation(libs.flyway.database.postgresql)
    runtimeOnly(libs.postgresql)
    jooqGenerator(libs.postgresql)

    // --- コード生成（Lombok） ---
    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)

    // --- テスト：単体 ---
    testImplementation(libs.spring.boot.starter.test)
    testRuntimeOnly(libs.junit.platform.launcher)

    // --- テスト：統合 ---
    "integrationTestImplementation"(libs.spring.boot.testcontainers)
    "integrationTestImplementation"(libs.testcontainers.postgresql)
}

// -----------------------------------------------------------------------------
// テスト task
//   ./gradlew test             → 単体のみ
//   ./gradlew integrationTest  → 統合のみ
//   ./gradlew check / build    → 両方
// -----------------------------------------------------------------------------
tasks.named<Test>("test") {
    useJUnitPlatform()
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
val jooqSchemas = listOf("common", "tax", "catalog", "inventory")

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
// コンパイルオプション
// -----------------------------------------------------------------------------
tasks.withType<JavaCompile> {
    // リフレクション用にメソッド引数名を保持
    options.compilerArgs.add("-parameters")
    options.encoding = "UTF-8"
}
