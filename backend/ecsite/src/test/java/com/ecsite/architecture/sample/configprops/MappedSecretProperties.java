package com.ecsite.architecture.sample.configprops;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Map;

/**
 * 設定の値の型と設定のクラスの検査の例：Map の値の型（注釈がない）が秘密の値の項目を持つが、toString を宣言しない（規則に合わない）
 *
 * <p>構成の検査が違反を見つけられることを確認するための例であり、本番のコードではない。
 *
 * @param endpoints 例の接続先（名前ごと）
 */
@ConfigurationProperties("example-mapped-secret")
public record MappedSecretProperties(Map<String, Endpoint> endpoints) {

    /**
     * 例の接続先
     *
     * @param url    例の URL
     * @param secret 例の秘密の値
     */
    public record Endpoint(String url, String secret) {
    }
}
