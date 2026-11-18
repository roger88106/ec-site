package com.ecsite.architecture.sample.configprops;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 設定の値の型と設定のクラスの検査の例：秘密の値の項目を持ち、final の toString を書く（規則に合う）
 *
 * <p>構成の検査が、自分で書いた final の toString を宣言と見なすことを確認するための例であり、本番のコードではない。
 *
 * @param apiKey 例の秘密の値
 */
@ConfigurationProperties("example-secret-final")
public record SecretWithFinalToStringProperties(String apiKey) {

    @Override
    public final String toString() {
        return "SecretWithFinalToStringProperties[apiKey=***]";
    }
}
