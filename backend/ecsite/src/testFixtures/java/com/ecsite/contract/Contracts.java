package com.ecsite.contract;

import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * 契約のテストが使う補助。契約（OpenAPI）、realm の設定、Keycloak のテーマの置き場所の取得と、契約の読み込みを、各テストに重複して書かないために置く。<br />
 * 単体テストと統合テストが共有する。置き場所は Gradle の設定が system property で渡す。渡されないときに既定の相対パスで読むと、Gradle の設定の誤りに気づけないので、失敗させる。
 */
public final class Contracts {

    /**
     * OpenAPI の置き場所を渡す system property の名前
     */
    private static final String OPENAPI_DIR = "openapi.dir";

    /**
     * realm の設定の置き場所を渡す system property の名前
     */
    private static final String REALMS_DIR = "keycloak.realms.dir";

    /**
     * Keycloak のテーマの置き場所を渡す system property の名前
     */
    private static final String THEME_DIR = "keycloak.theme.dir";

    /**
     * インスタンスを作らない。
     */
    private Contracts() {
    }

    /**
     * OpenAPI の置き場所を返す。
     *
     * @return system property {@code openapi.dir} の値
     * @throws IllegalStateException system property が渡されていないとき
     */
    public static Path openApiDir() {
        return directory(OPENAPI_DIR, System.getProperty(OPENAPI_DIR));
    }

    /**
     * realm の設定の置き場所を返す。
     *
     * @return system property {@code keycloak.realms.dir} の値
     * @throws IllegalStateException system property が渡されていないとき
     */
    public static Path realmsDir() {
        return directory(REALMS_DIR, System.getProperty(REALMS_DIR));
    }

    /**
     * Keycloak のテーマの置き場所を返す。
     *
     * @return system property {@code keycloak.theme.dir} の値
     * @throws IllegalStateException system property が渡されていないとき
     */
    public static Path themeDir() {
        return directory(THEME_DIR, System.getProperty(THEME_DIR));
    }

    /**
     * realm の設定の JSON を読む。
     *
     * @param file realm の設定のファイルの名前（realm の設定の置き場所の下。例：{@code ec-admin-realm.json}）
     * @return 読んだ JSON の文字列
     * @throws UncheckedIOException ファイルを読めないとき
     */
    public static String realm(String file) {
        try {
            return Files.readString(realmsDir().resolve(file));
        } catch (IOException e) {
            throw new UncheckedIOException("realm の設定を読めない: " + file, e);
        }
    }

    /**
     * 契約の YAML を読む。
     *
     * @param file 契約のファイルの名前（OpenAPI の置き場所の下。例：{@code admin.yaml}）
     * @return 読んだ契約
     * @throws UncheckedIOException ファイルを読めないとき
     */
    public static Map<String, Object> openApi(String file) {
        try {
            return new Yaml().load(Files.readString(openApiDir().resolve(file)));
        } catch (IOException e) {
            throw new UncheckedIOException("契約を読めない: " + file, e);
        }
    }

    /**
     * system property の値から置き場所を作る。値がなければ、原因を説明して失敗させる。
     *
     * @param property system property の名前
     * @param value    system property の値（渡されなければ null）
     * @return 置き場所
     * @throws IllegalStateException 値がないとき
     */
    static Path directory(String property, String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("system property " + property
                    + " が渡されていない。build.gradle.kts の systemProperty を確認する");
        }
        return Path.of(value);
    }
}
