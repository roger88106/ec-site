package com.ecsite.architecture.sample.configprops;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 設定の値の型と設定のクラスの検査の例：秘密の値の項目を持ち、toString を宣言する（規則に合う）
 *
 * <p>構成の検査が規則に合う例を違反にしないことを確認するための例であり、本番のコードではない。
 *
 * @param apiKey 例の秘密の値
 */
@ConfigurationProperties("example-secret-safe")
public record SecretWithToStringProperties(String apiKey) {

    @Override
    public String toString() {
        return "SecretWithToStringProperties[apiKey=***]";
    }
}
