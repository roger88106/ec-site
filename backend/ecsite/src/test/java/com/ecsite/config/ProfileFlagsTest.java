package com.ecsite.config;

import com.ecsite.TestSettings;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * profile ごとの設定ファイルが立てる mock フラグの検査。<br />
 * 起動のときと同じ順（共通の設定、指定した profile の順）に設定ファイルを重ね、後のものが先のものを上書きするとして読む。
 */
class ProfileFlagsTest {

    /**
     * 決済の mock フラグ
     */
    private static final String PAYMENT = "ecsite.payment.mock.enabled";

    /**
     * 物流の mock フラグ
     */
    private static final String SHIPPING = "ecsite.shipping.mock.enabled";

    @Test
    @DisplayName("AC-231 ローカルの設定は、決済と物流のフラグを立てる")
    void localEnablesBothMocks() {
        assertThat(flag(PAYMENT, "local")).isTrue();
        assertThat(flag(SHIPPING, "local")).isTrue();
    }

    @Test
    @DisplayName("AC-231 Stripe の sandbox で動かす設定は、決済のフラグを下ろし、物流のフラグは立てたまま")
    void stripeDisablesPaymentMock() {
        assertThat(flag(PAYMENT, "local", "stripe")).isFalse();
        assertThat(flag(SHIPPING, "local", "stripe")).isTrue();
    }

    @Test
    @DisplayName("AC-231 E2E の設定は、ローカルの設定のフラグを受け継ぐ")
    void e2eInheritsLocalFlags() {
        assertThat(flag(PAYMENT, "local", "e2e")).isTrue();
        assertThat(flag(SHIPPING, "local", "e2e")).isTrue();
    }

    @Test
    @DisplayName("AC-231 共通の設定は、フラグを立てない")
    void defaultEnablesNoMock() {
        assertThat(flag(PAYMENT)).isFalse();
        assertThat(flag(SHIPPING)).isFalse();
    }

    /**
     * 設定ファイルを重ねたときのフラグの値。どれにも書かれていなければ false。
     *
     * @param name     フラグの名前
     * @param profiles 指定する profile（順に重ねる）
     * @return フラグの値
     */
    private static boolean flag(String name, String... profiles) {
        return Boolean.parseBoolean(TestSettings.load(profiles).get(name));
    }
}
