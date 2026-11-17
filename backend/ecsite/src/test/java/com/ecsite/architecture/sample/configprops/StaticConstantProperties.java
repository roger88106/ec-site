package com.ecsite.architecture.sample.configprops;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 設定の値の型と設定のクラスの検査の例：static の定数の名前が秘密の値の名前だが、設定の値の項目ではない（規則に合う）
 *
 * <p>構成の検査が規則に合う例を違反にしないことを確認するための例であり、本番のコードではない。
 *
 * @param value 例の値
 */
@ConfigurationProperties("example-static-constant")
public record StaticConstantProperties(String value) {

    static final String DEFAULT_KEY = "default";
}
