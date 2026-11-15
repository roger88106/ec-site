package com.ecsite.contract;

import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * realm の設定の契約テスト
 *
 * <p>Keycloak は起動せず、realm の JSON（{@code docker/keycloak/realms/}）の値が、各テストの名前の受け入れ条件のとおりであることを確認する。
 * 会員と運営者のアカウントが別の realm にあることは、{@code KeycloakIntegrationTest} で確認する。
 */
class RealmSettingsContractTest {

    // ============================== 定数 ==============================

    /**
     * 会員の realm
     */
    private static final String STORE = "ec-store";

    /**
     * 運営者の realm
     */
    private static final String ADMIN = "ec-admin";

    /**
     * 1 分（秒）
     */
    private static final long MINUTE = 60;

    /**
     * 1 時間（秒）
     */
    private static final long HOUR = 60 * MINUTE;

    /**
     * 1 日（秒）
     */
    private static final long DAY = 24 * HOUR;

    /**
     * realm の名前 → realm の定義の JSON
     */
    private static Map<String, String> realms;

    /**
     * 2 つの realm の定義を読む。
     */
    @BeforeAll
    static void load() {
        realms = Map.of(
                STORE, Contracts.realm("ec-store-realm.json"),
                ADMIN, Contracts.realm("ec-admin-realm.json"));
    }

    // ============================== テスト ==============================

    @Nested
    @DisplayName("登録とログイン ID")
    class Registration {

        @Test
        @DisplayName("AC-108 会員の realm は、会員登録ができる")
        void storeAllowsRegistration() {
            // 操作と確認：
            assertThat((Boolean) read(STORE, "$.registrationAllowed")).isTrue();
        }

        @Test
        @DisplayName("AC-134 運営者の realm は、自分で登録できない")
        void adminDisallowsRegistration() {
            // 操作と確認：
            assertThat((Boolean) read(ADMIN, "$.registrationAllowed")).isFalse();
        }

        @ParameterizedTest(name = "「{0}」")
        @ValueSource(strings = {STORE, ADMIN})
        @DisplayName("AC-109 登録済みのメールアドレスは使えない")
        void emailIsUnique(String realm) {
            // 操作と確認：
            assertThat((Boolean) read(realm, "$.duplicateEmailsAllowed")).isFalse();
        }

        @ParameterizedTest(name = "「{0}」")
        @ValueSource(strings = {STORE, ADMIN})
        @DisplayName("AC-113 ログイン ID はメールアドレス")
        void emailIsLoginId(String realm) {
            // 操作と確認：
            assertThat((Boolean) read(realm, "$.registrationEmailAsUsername")).isTrue();
        }

        @Test
        @DisplayName("AC-111、112 会員はメールアドレスの確認が必須")
        void storeRequiresEmailVerification() {
            // 操作と確認：
            assertThat((Boolean) read(STORE, "$.verifyEmail")).isTrue();
        }

        @Test
        @DisplayName("AC-134 運営者はメールアドレスの確認が不要（IdP の管理の画面で作るため）")
        void adminSkipsEmailVerification() {
            // 操作と確認：
            assertThat((Boolean) read(ADMIN, "$.verifyEmail")).isFalse();
        }
    }

    @Nested
    @DisplayName("パスワード")
    class Password {

        @ParameterizedTest(name = "「{0}」")
        @ValueSource(strings = {STORE, ADMIN})
        @DisplayName("AC-110 パスワードは 8 文字以上、64 文字以下で、メールアドレスと同じものは不可")
        void passwordPolicy(String realm) {
            // 操作：
            String policy = read(realm, "$.passwordPolicy");

            // 確認：
            assertThat(policy.split(" and ")).containsExactlyInAnyOrder("length(8)", "maxLength(64)", "notEmail");
        }

        @ParameterizedTest(name = "「{0}」")
        @ValueSource(strings = {STORE, ADMIN})
        @DisplayName("AC-116 5 回続けてログインに失敗すると 15 分止め、ずっと止めはしない")
        void bruteForceProtection(String realm) {
            // 操作と確認：
            assertLocksFor15MinutesAfter5Failures(realm);
        }

        @ParameterizedTest(name = "「{0}」")
        @ValueSource(strings = {STORE, ADMIN})
        @DisplayName("AC-118 パスワードを忘れたときの再設定が使える")
        void resetPassword(String realm) {
            // 操作と確認：
            assertThat((Boolean) read(realm, "$.resetPasswordAllowed")).isTrue();
        }
    }

    @Nested
    @DisplayName("ログインの状態と token")
    class Session {

        @Test
        @DisplayName("AC-117 会員のログインの状態は、最後の操作から 7 日、最長 30 日")
        void storeSsoSession() {
            // 操作と確認：
            assertThat(number(STORE, "$.ssoSessionIdleTimeout")).isEqualTo(7 * DAY);
            assertThat(number(STORE, "$.ssoSessionMaxLifespan")).isEqualTo(30 * DAY);
        }

        @Test
        @DisplayName("AC-117 運営者のログインの状態は、最後の操作から 30 分、最長 8 時間")
        void adminSsoSession() {
            // 操作と確認：
            assertThat(number(ADMIN, "$.ssoSessionIdleTimeout")).isEqualTo(30 * MINUTE);
            assertThat(number(ADMIN, "$.ssoSessionMaxLifespan")).isEqualTo(8 * HOUR);
        }

        @ParameterizedTest(name = "「{0}」")
        @ValueSource(strings = {STORE, ADMIN})
        @DisplayName("AC-140 access token の有効期間は 5 分")
        void accessTokenLifespan(String realm) {
            // 操作と確認：
            assertThat(number(realm, "$.accessTokenLifespan")).isEqualTo(5 * MINUTE);
        }
    }

    @Nested
    @DisplayName("会員の属性")
    class MemberAttributes {

        @Test
        @DisplayName("AC-209 生年月日は日付の形で入れる")
        void birthdateIsDate() {
            // 操作：
            List<Object> validations = memberProfile().read("$.attributes[?(@.name == 'birthdate')].validations.iso-date");

            // 確認：
            assertThat(validations).hasSize(1);
        }

        @Test
        @DisplayName("AC-221 性別は 4 つの値から選ぶ")
        void genderHasFourOptions() {
            // 操作：
            List<List<String>> options = memberProfile().read("$.attributes[?(@.name == 'gender')].validations.options.options");

            // 確認：
            assertThat(options.getFirst()).containsExactly("male", "female", "other", "undisclosed");
        }

        @Test
        @DisplayName("AC-210、221 生年月日と性別は任意")
        void attributesAreOptional() {
            // 操作：
            List<Object> required = memberProfile().read("$.attributes[?(@.name in ['birthdate', 'gender'])].required");

            // 確認：
            assertThat(required).isEmpty();
        }

        @Test
        @DisplayName("AC-211、221 生年月日と性別は、会員が後から直せる")
        void attributesAreEditableByMember() {
            // 操作：
            List<List<String>> edit = memberProfile().read("$.attributes[?(@.name in ['birthdate', 'gender'])].permissions.edit");

            // 確認：
            assertThat(edit).allSatisfy(roles -> assertThat(roles).contains("user"));
        }

        @Test
        @DisplayName("AC-212、222 店舗画面の access token に、生年月日と性別を載せる")
        void memberAttributeClaims() {
            // 操作：
            List<String> claims = read(STORE, "$.clients[?(@.clientId == 'ec-store-web')].protocolMappers"
                    + "[?(@.protocolMapper == 'oidc-usermodel-attribute-mapper' && @.config['access.token.claim'] == 'true')]"
                    + ".config['claim.name']");

            // 確認：
            assertThat(claims).containsExactlyInAnyOrder("birthdate", "gender");
        }
    }

    // ============================== 確認の補助 ==============================

    /**
     * ログインの失敗が続いたときの止め方を確認する。
     *
     * <p>確認すること：
     * <ul>
     *   <li>続けて失敗したときに止める</li>
     *   <li>ずっと止めはしない</li>
     *   <li>止めるのは 5 回の失敗から</li>
     *   <li>1 回に止める時間は 15 分</li>
     *   <li>止める時間の最長も 15 分</li>
     * </ul>
     *
     * @param realm realm の名前
     */
    private static void assertLocksFor15MinutesAfter5Failures(String realm) {
        assertThat((Boolean) read(realm, "$.bruteForceProtected")).isTrue();
        assertThat((Boolean) read(realm, "$.permanentLockout")).isFalse();
        assertThat(number(realm, "$.failureFactor")).isEqualTo(5);
        assertThat(number(realm, "$.waitIncrementSeconds")).isEqualTo(15 * MINUTE);
        assertThat(number(realm, "$.maxFailureWaitSeconds")).isEqualTo(15 * MINUTE);
    }

    // ============================== 補助 ==============================

    /**
     * 会員の realm の user profile の設定を返す。
     *
     * <p>user profile の設定は、JSON を文字列にして持つ。
     *
     * @return user profile の設定
     */
    private static DocumentContext memberProfile() {
        return JsonPath.parse(JsonPath.<String>read(realms.get(STORE),
                "$.components['org.keycloak.userprofile.UserProfileProvider'][0].config['kc.user.profile.config'][0]"));
    }

    /**
     * realm の定義を JSONPath で読む。
     *
     * @param realm realm の名前
     * @param path  JSONPath
     * @param <T>   読む値の型
     * @return 読んだ値
     */
    private static <T> T read(String realm, String path) {
        return JsonPath.read(realms.get(realm), path);
    }

    /**
     * realm の定義の数値を JSONPath で読む。
     *
     * @param realm realm の名前
     * @param path  JSONPath
     * @return 読んだ値
     */
    private static long number(String realm, String path) {
        return JsonPath.<Number>read(realms.get(realm), path).longValue();
    }
}
