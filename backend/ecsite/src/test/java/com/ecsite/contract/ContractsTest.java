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
