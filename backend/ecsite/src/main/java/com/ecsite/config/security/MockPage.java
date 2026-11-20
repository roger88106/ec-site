package com.ecsite.config.security;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.core.env.Environment;

import java.util.Arrays;
import java.util.List;

/**
 * mock の画面
 *
 * <p>外部のサービス（決済、物流）の代わりの画面と、それを出すモジュールの mock のフラグの対応。
 * 画面は、そのモジュールの mock のフラグを立てたときだけ出す。
 */
@Getter(AccessLevel.PACKAGE)
@RequiredArgsConstructor
enum MockPage {

    /**
     * 決済の mock の支払画面
     */
    PAYMENT("決済", "ecsite.payment.mock.enabled", "/mock-payment/**"),

    /**
     * 物流の mock の配送業者の画面
     */
    SHIPPING("物流", "ecsite.shipping.mock.enabled", "/mock-carrier/**");

    /**
     * 画面を出すモジュールの名前（ログに出す）
     */
    private final String moduleName;

    /**
     * モジュールの mock のフラグの名前
     */
    private final String flag;

    /**
     * 画面のパス
     */
    private final String pathPattern;

    /**
     * mock のフラグを立てたモジュールの画面を返す。
     *
     * <p>フラグは、mock の実装を選ぶとき（{@code @ConditionalOnBooleanProperty}）と同じく、値が {@code true} と等しいとき
     * （大文字と小文字は区別しない）だけ立てたとみなす。{@code yes}・{@code on}・{@code 1} や真偽値でない値は、立てていないとみなす。
     *
     * @param environment 設定
     * @return フラグを立てた画面（宣言の順）
     */
    static List<MockPage> enabled(Environment environment) {
        return Arrays.stream(values())
                // 真偽値への変換は yes・on・1 も true にし、mock の実装を選ぶ判定とずれるので、文字列で比べる
                .filter(page -> "true".equalsIgnoreCase(environment.getProperty(page.flag)))
                .toList();
    }
}
