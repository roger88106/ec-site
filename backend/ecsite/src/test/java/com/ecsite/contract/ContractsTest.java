package com.ecsite.contract;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 契約の補助 {@link Contracts} の単体テスト。置き場所が渡されないとき、既定の場所で読まずに失敗することを確認する。
 */
class ContractsTest {

    /**
     * 名前で読む操作のテストが、値を入れたり消したりする system property の名前（Gradle が渡す名前とは別）
     */
    private static final String UNSET_PROPERTY = "ecsite.contracts-test.path";

    @Test
    @DisplayName("system property が渡されないとき、名前で読む操作は property の名前を示して失敗する")
    void pathFailsWithoutProperty() {
        System.clearProperty(UNSET_PROPERTY);

        assertThatThrownBy(() -> Contracts.path(UNSET_PROPERTY))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(UNSET_PROPERTY)
                .hasMessageContaining("build.gradle.kts");
    }

    @Test
    @DisplayName("system property が空のとき、名前で読む操作は失敗する")
    void pathFailsWithBlankProperty() {
        System.setProperty(UNSET_PROPERTY, " ");
        try {
            assertThatThrownBy(() -> Contracts.path(UNSET_PROPERTY))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining(UNSET_PROPERTY);
        } finally {
            System.clearProperty(UNSET_PROPERTY);
        }
    }

    @Test
    @DisplayName("system property が渡されたとき、名前で読む操作はその値を返す")
    void pathReturnsGivenValue() {
        System.setProperty(UNSET_PROPERTY, "/docs/overview.md");
        try {
            assertThat(Contracts.path(UNSET_PROPERTY)).isEqualTo(Path.of("/docs/overview.md"));
        } finally {
            System.clearProperty(UNSET_PROPERTY);
        }
    }

    @Test
    @DisplayName("置き場所の system property が渡されないとき、property の名前を示して失敗する")
    void failsWithoutProperty() {
        assertThatThrownBy(() -> Contracts.directory("openapi.dir", null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("openapi.dir");
    }

    @Test
    @DisplayName("置き場所の system property が空のとき、失敗する")
    void failsWithBlankProperty() {
        assertThatThrownBy(() -> Contracts.directory("keycloak.realms.dir", " "))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("keycloak.realms.dir");
    }

    @Test
    @DisplayName("置き場所の system property が渡されたとき、その値を返す")
    void returnsGivenPath() {
        assertThat(Contracts.directory("openapi.dir", "/contracts")).isEqualTo(Path.of("/contracts"));
    }

    @Test
    @DisplayName("Gradle が渡した置き場所で、契約と realm の設定が読める")
    void readsContractsFromGradleProperties() {
        assertThat(Contracts.openApi("admin.yaml")).containsKey("paths");
        assertThat(Contracts.realmsDir().resolve("ec-admin-realm.json")).isRegularFile();
    }
}
