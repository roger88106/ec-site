package com.ecsite.architecture.sample.configprops;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 設定の値の型と設定のクラスの検査の例：秘密の値の項目を持ち、引数のある toString だけを書く（規則に合わない）
 *
 * <p>構成の検査が、引数のある toString を宣言と見なさないことを確認するための例であり、本番のコードではない。
 *
 * @param apiKey 例の秘密の値
 */
@ConfigurationProperties("example-secret-parameterized")
public record SecretWithParameterizedToStringProperties(String apiKey) {

    public String toString(boolean masked) {
        return masked ? "SecretWithParameterizedToStringProperties[apiKey=***]" : apiKey;
    }
}
