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
// -----------------------------------------------------------------------------
tasks.named<Test>("test") {
    useJUnitPlatform()
}

// -----------------------------------------------------------------------------
// コンパイルオプション
// -----------------------------------------------------------------------------
tasks.withType<JavaCompile> {
    // リフレクション用にメソッド引数名を保持
    options.compilerArgs.add("-parameters")
    options.encoding = "UTF-8"
}
