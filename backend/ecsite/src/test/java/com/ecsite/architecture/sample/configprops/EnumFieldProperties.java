package com.ecsite.architecture.sample.configprops;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 設定の値の型と設定のクラスの検査の例：項目の型が enum で、enum の定数の名前が秘密の値の名前だが、設定の値の項目ではない（規則に合う）
 *
 * <p>構成の検査が規則に合う例を違反にしないことを確認するための例であり、本番のコードではない。
 *
 * @param kind 例の種類
 */
@ConfigurationProperties("example-enum-field")
public record EnumFieldProperties(Kind kind) {

    /**
     * 例の種類
     */
    public enum Kind {
        TOKEN,
        PLAIN
    }
}
