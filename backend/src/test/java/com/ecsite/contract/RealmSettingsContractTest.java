package com.ecsite.contract;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * realm の設定の契約テスト（T3209、plan 003 第 2.3 節）。<br />
 * Keycloak は起動せず、realm の JSON（{@code keycloak/realms/}）の値が plan のとおりであることを確かめる。
 * 会員と運営者のアカウントが別の realm にあること（AC-133）は、KeycloakIntegrationTest と段階 1 の手動確認で確かめる。
 */
class RealmSettingsContractTest {

    /**
     * realm の設定の置き場所。Gradle の test task が設定する
     */
    private static final Path REALMS_DIR = Path.of(System.getProperty("keycloak.realms.dir", "../keycloak/realms"));

    private static final String STORE = "ec-store";
    private static final String ADMIN = "ec-admin";

    private static final long MINUTE = 60;
    private static final long HOUR = 60 * MINUTE;
    private static final long DAY = 24 * HOUR;

    private static Map<String, String> realms;

    @BeforeAll
    static void load() throws IOException {
        realms = Map.of(
                STORE, Files.readString(REALMS_DIR.resolve("ec-store-realm.json")),
                ADMIN, Files.readString(REALMS_DIR.resolve("ec-admin-realm.json")));
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {STORE, ADMIN})
    @DisplayName("AC-109 登録済みのメールアドレスは使えない。ログイン ID はメールアドレス")
    void emailIsUniqueLoginId(String realm) {
        assertThat((Boolean) read(realm, "$.duplicateEmailsAllowed")).isFalse();
        assertThat((Boolean) read(realm, "$.registrationEmailAsUsername")).isTrue();
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {STORE, ADMIN})
    @DisplayName("AC-110 パスワードは 8 文字以上、64 文字以下。メールアドレスと同じものは不可")
    void passwordPolicy(String realm) {
        String policy = read(realm, "$.passwordPolicy");

        assertThat(policy.split(" and ")).containsExactlyInAnyOrder("length(8)", "maxLength(64)", "notEmail");
    }

    @Test
    @DisplayName("AC-111、112 会員はメールアドレスの確認が必須。運営者は不要（管理の画面で作るため）")
    void emailVerification() {
        assertThat((Boolean) read(STORE, "$.verifyEmail")).isTrue();
        assertThat((Boolean) read(ADMIN, "$.verifyEmail")).isFalse();
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {STORE, ADMIN})
    @DisplayName("AC-116 5 回続けてログインに失敗すると 15 分止める（ずっと止めはしない）")
    void bruteForceProtection(String realm) {
        assertThat((Boolean) read(realm, "$.bruteForceProtected")).isTrue();
        assertThat((Boolean) read(realm, "$.permanentLockout")).isFalse();
        assertThat(number(realm, "$.failureFactor")).isEqualTo(5);
        assertThat(number(realm, "$.waitIncrementSeconds")).isEqualTo(15 * MINUTE);
        assertThat(number(realm, "$.maxFailureWaitSeconds")).isEqualTo(15 * MINUTE);
    }

    @Test
    @DisplayName("AC-117 ログインの状態は、会員は最後の操作から 7 日・最長 30 日、運営者は 30 分・最長 8 時間")
    void ssoSession() {
        assertThat(number(STORE, "$.ssoSessionIdleTimeout")).isEqualTo(7 * DAY);
        assertThat(number(STORE, "$.ssoSessionMaxLifespan")).isEqualTo(30 * DAY);
        assertThat(number(ADMIN, "$.ssoSessionIdleTimeout")).isEqualTo(30 * MINUTE);
        assertThat(number(ADMIN, "$.ssoSessionMaxLifespan")).isEqualTo(8 * HOUR);
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {STORE, ADMIN})
    @DisplayName("AC-118 パスワードを忘れたときの再設定が使える")
    void resetPassword(String realm) {
        assertThat((Boolean) read(realm, "$.resetPasswordAllowed")).isTrue();
    }

    @Test
    @DisplayName("AC-108、134 会員登録は消費者の realm だけ")
    void registration() {
        assertThat((Boolean) read(STORE, "$.registrationAllowed")).isTrue();
        assertThat((Boolean) read(ADMIN, "$.registrationAllowed")).isFalse();
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {STORE, ADMIN})
    @DisplayName("AC-140 access token の有効期間は 5 分")
    void accessTokenLifespan(String realm) {
        assertThat(number(realm, "$.accessTokenLifespan")).isEqualTo(5 * MINUTE);
    }

    private static <T> T read(String realm, String path) {
        return JsonPath.read(realms.get(realm), path);
    }

    private static long number(String realm, String path) {
        return JsonPath.<Number>read(realms.get(realm), path).longValue();
    }
}
