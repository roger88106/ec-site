package com.ecsite.architecture.sample.configprops;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 設定の値の型と設定のクラスの検査の例：record でないクラスで、秘密の値の項目を持ち、final の toString を書く（規則に合う）
 *
 * <p>構成の検査が、record でないクラスの final の toString を宣言と見なすことを確認するための例であり、本番のコードではない。
 */
@ConfigurationProperties("example-secret-class-final")
public class SecretClassWithFinalToStringProperties {

    private String apiKey;

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    @Override
    public final String toString() {
        return "SecretClassWithFinalToStringProperties[apiKey=***]";
    }
}
