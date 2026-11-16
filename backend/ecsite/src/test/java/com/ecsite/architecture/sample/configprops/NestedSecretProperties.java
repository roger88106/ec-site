package com.ecsite.architecture.sample.configprops;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 設定の値の型と設定のクラスの検査の例：入れ子の型（注釈がない）が秘密の値の項目を持つが、toString を宣言しない（規則に合わない）
 *
 * <p>構成の検査が違反を見つけられることを確認するための例であり、本番のコードではない。
 *
 * @param account 例の資格情報
 */
@ConfigurationProperties("example-nested-secret")
public record NestedSecretProperties(Credentials account) {

    /**
     * 例の資格情報
     *
     * @param apiKey 例の秘密の値
     */
    public record Credentials(String apiKey) {
    }
}
