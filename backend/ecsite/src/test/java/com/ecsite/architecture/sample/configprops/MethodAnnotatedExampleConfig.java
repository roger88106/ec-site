package com.ecsite.architecture.sample.configprops;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * 設定の値の型と設定のクラスを検査するための例：メソッドに {@code @ConfigurationProperties} を付けている（規則に合わない）
 *
 * <p>構成の検査が違反を見つけられることを確認するための例であり、本番のコードではない。
 */
public class MethodAnnotatedExampleConfig {

    /**
     * 設定の値の型を、メソッドへの注釈で登録する例
     *
     * @return 設定の値の型
     */
    @Bean
    @ConfigurationProperties("example.method")
    public ExampleProperties exampleProperties() {
        return null;
    }
}
