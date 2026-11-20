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
     * <p>フラグは、mock の実装を選ぶときと同じく、{@code true} のときだけ立てたとみなす。
     *
     * @param environment 設定
     * @return フラグを立てた画面（宣言の順）
     */
    static List<MockPage> enabled(Environment environment) {
        return Arrays.stream(values())
                .filter(page -> environment.getProperty(page.flag, Boolean.class, false))
                .toList();
    }
}
