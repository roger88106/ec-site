package com.ecsite.contract;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 契約の補助 {@link Contracts} の単体テスト
 *
 * <p>置き場所が渡されないとき、既定の場所で読まずに失敗する。
 */
class ContractsTest {

    // ============================== 定数 ==============================

    /**
     * リポジトリのルートからの相対パスの例（日本語を含む）
     */
    private static final String DOCUMENT = "docs/02-設計/overview.md";

    // ============================== テスト ==============================

    @Nested
    @DisplayName("リポジトリのルートからの相対パス")
    class RelativePath {

        @Test
        @DisplayName("ルートが渡されないとき、property の名前と設定の場所を示して失敗する")
        void pathFailsWithoutRoot() {
            // 操作と確認：
            assertThatThrownBy(() -> Contracts.resolve("repo.root", null, DOCUMENT))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("repo.root")
                    .hasMessageContaining("build.gradle.kts");
        }

        @Test
        @DisplayName("ルートが空のとき、失敗する")
        void pathFailsWithBlankRoot() {
            // 操作と確認：
            assertThatThrownBy(() -> Contracts.resolve("repo.root", " ", DOCUMENT))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("repo.root");
        }

        @Test
        @DisplayName("ルートが渡されたとき、ルートに相対パスをつないだ置き場所を返す")
        void pathResolvesUnderRoot() {
            // 操作と確認：
            assertThat(Contracts.resolve("repo.root", "/repo", DOCUMENT))
                    .isEqualTo(Path.of("/repo", "docs", "02-設計", "overview.md"));
        }

        @Test
        @DisplayName("Gradle が渡したルートで、日本語を含む相対パスの文書が読める")
        void pathFindsDocumentFromGradleProperty() {
            // 操作と確認：ファイルとディレクトリの両方
            assertThat(Contracts.path(DOCUMENT)).isRegularFile();
            assertThat(Contracts.path("docs/03-機能仕様")).isDirectory();
        }
    }

    @Nested
    @DisplayName("system property で渡す置き場所")
    class Directory {

        @Test
        @DisplayName("渡されないとき、property の名前を示して失敗する")
        void failsWithoutProperty() {
            // 操作と確認：
            assertThatThrownBy(() -> Contracts.directory("openapi.dir", null))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("openapi.dir");
        }

        @Test
        @DisplayName("空のとき、失敗する")
        void failsWithBlankProperty() {
            // 操作と確認：
            assertThatThrownBy(() -> Contracts.directory("keycloak.realms.dir", " "))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("keycloak.realms.dir");
        }

        @Test
        @DisplayName("渡されたとき、その値を返す")
        void returnsGivenPath() {
            // 操作と確認：
            assertThat(Contracts.directory("openapi.dir", "/contracts")).isEqualTo(Path.of("/contracts"));
        }

        @Test
        @DisplayName("Gradle が渡した置き場所で、契約と realm の設定が読める")
        void readsContractsFromGradleProperties() {
            // 操作と確認：契約と realm の両方
            assertThat(Contracts.openApi("admin.yaml")).containsKey("paths");
            assertThat(Contracts.realmsDir().resolve("ec-admin-realm.json")).isRegularFile();
        }
    }
}
