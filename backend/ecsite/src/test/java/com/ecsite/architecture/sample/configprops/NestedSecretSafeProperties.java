package com.ecsite.architecture.sample.configprops;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 設定の値の型と設定のクラスの検査の例：入れ子の型が秘密の値の項目を持ち、toString を宣言する（規則に合う）
 *
 * <p>構成の検査が規則に合う例を違反にしないことを確認するための例であり、本番のコードではない。
 *
 * @param account 例の資格情報
 */
@ConfigurationProperties("example-nested-secret-safe")
public record NestedSecretSafeProperties(MaskedCredentials account) {

    /**
     * 例の資格情報
     *
     * @param apiKey 例の秘密の値
     */
    public record MaskedCredentials(String apiKey) {

        @Override
        public String toString() {
            return "MaskedCredentials[apiKey=***]";
        }
    }
}
