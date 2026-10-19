package com.ecsite.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

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
    void localEnablesBothMocks() throws IOException {
        assertThat(flag(PAYMENT, "local")).isTrue();
        assertThat(flag(SHIPPING, "local")).isTrue();
    }

    @Test
    @DisplayName("AC-231 Stripe の sandbox で動かす設定は、決済のフラグを下ろし、物流のフラグは立てたまま")
    void stripeDisablesPaymentMock() throws IOException {
        assertThat(flag(PAYMENT, "local", "stripe")).isFalse();
        assertThat(flag(SHIPPING, "local", "stripe")).isTrue();
    }

    @Test
    @DisplayName("AC-231 E2E の設定は、ローカルの設定のフラグを受け継ぐ")
    void e2eInheritsLocalFlags() throws IOException {
        assertThat(flag(PAYMENT, "local", "e2e")).isTrue();
        assertThat(flag(SHIPPING, "local", "e2e")).isTrue();
    }

    @Test
    @DisplayName("AC-231 共通の設定は、フラグを立てない")
    void defaultEnablesNoMock() throws IOException {
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
    private static boolean flag(String name, String... profiles) throws IOException {
        var files = new ArrayList<>(List.of("application.yml"));
        for (var profile : profiles) {
            files.add("application-" + profile + ".yml");
        }
        Optional<Object> value = Optional.empty();
        for (var file : files) {
            for (PropertySource<?> source : new YamlPropertySourceLoader().load(file, new ClassPathResource(file))) {
                var found = source.getProperty(name);
                if (found != null) {
                    value = Optional.of(found);
                }
            }
        }
        return value.map(found -> Boolean.parseBoolean(found.toString())).orElse(false);
    }
}
