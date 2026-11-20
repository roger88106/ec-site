package com.ecsite.config.security;

import com.ecsite.support.LogCapture;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.event.Level;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * mock のフラグの起動時の警告の単体テスト
 *
 * <p>警告の Bean だけを読み込み、mock のフラグの設定ごとに、起動のときに出る警告のログを確認する。
 */
class MockFlagWarningTest {

    // ============================== 定数 ==============================

    /**
     * 決済の mock のフラグの名前
     */
    private static final String PAYMENT_FLAG = "ecsite.payment.mock.enabled";

    /**
     * 物流の mock のフラグの名前
     */
    private static final String SHIPPING_FLAG = "ecsite.shipping.mock.enabled";

    /**
     * 警告の文に入る、本番では立てないことを伝える語
     */
    private static final String PRODUCTION = "本番";

    // ============================== テスト ==============================

    /**
     * 1 つのモジュールだけフラグを立てる場合の、ケース名と、立てるフラグと、立てないフラグを返す。
     *
     * @return テストの入力
     */
    static Stream<Arguments> singleFlagCases() {
        return Stream.of(
                Arguments.of("決済だけ", PAYMENT_FLAG, SHIPPING_FLAG),
                Arguments.of("物流だけ", SHIPPING_FLAG, PAYMENT_FLAG));
    }

    @ParameterizedTest(name = "「{0}」")
    @MethodSource("singleFlagCases")
    @DisplayName("DC-477-4 1 つのモジュールの mock のフラグを立てて起動すると、そのモジュールのフラグを本番で立てないことを、警告のログに 1 つだけ出力する")
    void warnsForFlaggedModule(String name, String flagged, String unflagged) {
        // 操作：1 つのモジュールのフラグだけを立て、もう 1 つは立てずに起動する
        runner.withPropertyValues(flagged + "=true", unflagged + "=false")
                .run(context -> {
                    assertThat(context).hasNotFailed();

                    // 確認：警告は 1 つで、立てたフラグの名前と、本番で立てないことが分かる
                    assertThat(logs.messages(Level.WARN)).singleElement().asString()
                            .contains(flagged)
                            .contains(PRODUCTION)
                            .doesNotContain(unflagged);
                });
    }

    @Test
    @DisplayName("DC-477-4 両方のモジュールの mock のフラグを立てて起動すると、モジュールごとに警告のログを出力する")
    void warnsForEachFlaggedModule() {
        // 操作：両方のフラグを立てて起動する
        runner.withPropertyValues(PAYMENT_FLAG + "=true", SHIPPING_FLAG + "=true")
                .run(context -> {
                    assertThat(context).hasNotFailed();

                    // 確認：警告はモジュールごとに 1 つずつで、それぞれ、そのモジュールのフラグの名前と、本番で立てないことが分かる
                    assertThat(logs.messages(Level.WARN)).satisfiesExactlyInAnyOrder(
                            message -> assertThat(message).contains(PAYMENT_FLAG).contains(PRODUCTION).doesNotContain(SHIPPING_FLAG),
                            message -> assertThat(message).contains(SHIPPING_FLAG).contains(PRODUCTION).doesNotContain(PAYMENT_FLAG));
                });
    }

    /**
     * フラグを立てない場合の、ケース名と設定の値を返す。
     *
     * @return テストの入力
     */
    static Stream<Arguments> noFlagCases() {
        return Stream.of(
                Arguments.of("フラグを設定しない", new String[]{}),
                Arguments.of("両方のフラグを false にする", new String[]{PAYMENT_FLAG + "=false", SHIPPING_FLAG + "=false"}));
    }

    @ParameterizedTest(name = "「{0}」")
    @MethodSource("noFlagCases")
    @DisplayName("DC-477-4 mock のフラグを立てずに起動すると、警告のログを出力しない")
    void doesNotWarnWithoutFlags(String name, String[] flags) {
        // 操作と確認：起動でき、警告がない
        runner.withPropertyValues(flags)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(logs.messages(Level.WARN)).isEmpty();
                });
    }

    // ============================== 準備の補助 ==============================

    /**
     * 警告を出すクラスのログ
     */
    @RegisterExtension
    final LogCapture logs = LogCapture.of(MockFlagWarning.class);

    /**
     * mock のフラグの警告だけを読む Spring のコンテキスト
     */
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withBean(MockFlagWarning.class);
}
