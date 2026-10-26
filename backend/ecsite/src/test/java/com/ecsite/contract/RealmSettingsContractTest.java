package com.ecsite.contract;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * realm の設定の契約テスト。<br />
 * Keycloak は起動せず、realm の JSON（{@code docker/keycloak/realms/}）の値が、各テストの名前の受け入れ条件のとおりであることを確認する。
 * 会員と運営者のアカウントが別の realm にあることは、KeycloakIntegrationTest で確認する。
 */
class RealmSettingsContractTest {

    private static final String STORE = "ec-store";
    private static final String ADMIN = "ec-admin";

    private static final long MINUTE = 60;
    private static final long HOUR = 60 * MINUTE;
    private static final long DAY = 24 * HOUR;

    private static Map<String, String> realms;

    @BeforeAll
    static void load() {
        realms = Map.of(
                STORE, Contracts.realm("ec-store-realm.json"),
                ADMIN, Contracts.realm("ec-admin-realm.json"));
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

    @Test
    @DisplayName("AC-209、210、211、221 会員の属性に、任意の生年月日（日付の形）と性別（4 つの値）があり、会員が後から直せる")
    void memberAttributes() {
        // user profile の設定は、JSON を文字列にして持つ
        var profile = JsonPath.parse(JsonPath.<String>read(realms.get(STORE),
                "$.components['org.keycloak.userprofile.UserProfileProvider'][0].config['kc.user.profile.config'][0]"));

        assertThat(profile.<List<Object>>read("$.attributes[?(@.name == 'birthdate')].validations.iso-date"))
                .hasSize(1);
        assertThat(profile.<List<List<String>>>read(
                "$.attributes[?(@.name == 'gender')].validations.options.options").getFirst())
                .containsExactly("male", "female", "other", "undisclosed");
        assertThat(profile.<List<Object>>read("$.attributes[?(@.name in ['birthdate', 'gender'])].required"))
                .isEmpty();
        assertThat(profile.<List<List<String>>>read(
                "$.attributes[?(@.name in ['birthdate', 'gender'])].permissions.edit"))
                .allSatisfy(edit -> assertThat(edit).contains("user"));
    }

    @Test
    @DisplayName("AC-212、222 店舗画面の access token に、生年月日と性別を載せる")
    void memberAttributeClaims() {
        List<String> claims = read(STORE, "$.clients[?(@.clientId == 'ec-store-web')].protocolMappers"
                + "[?(@.protocolMapper == 'oidc-usermodel-attribute-mapper' && @.config['access.token.claim'] == 'true')]"
                + ".config['claim.name']");

        assertThat(claims).containsExactlyInAnyOrder("birthdate", "gender");
    }

    private static <T> T read(String realm, String path) {
        return JsonPath.read(realms.get(realm), path);
    }

    private static long number(String realm, String path) {
        return JsonPath.<Number>read(realms.get(realm), path).longValue();
    }
}
