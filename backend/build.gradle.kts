/*
 * EC Site バックエンド - ビルド構成定義 (Gradle Kotlin DSL)
 *
 * ※バージョン番号などの定義はすべて gradle/libs.versions.toml で管理する。
 */

plugins {
    java
    idea
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.spring.dependency.management)
}

group = "com.ecsite"
version = "0.0.1-SNAPSHOT"

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

    // --- コード生成（Lombok） ---
    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)

    // --- テスト：単体 ---
    testImplementation(libs.spring.boot.starter.test)
    testRuntimeOnly(libs.junit.platform.launcher)
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
// コンパイルオプション
// -----------------------------------------------------------------------------
tasks.withType<JavaCompile> {
    // リフレクション用にメソッド引数名を保持
    options.compilerArgs.add("-parameters")
    options.encoding = "UTF-8"
}
