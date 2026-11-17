package com.ecsite.architecture.sample.configprops;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 設定の値の型と設定のクラスの検査の例：設定の値の型（秘密の値の項目を持たないので、toString の規則に合う）
 *
 * <p>構成の検査が規則に合う例を違反にしないことを確認するための例であり、本番のコードではない。
 *
 * @param value 例の値
 */
@ConfigurationProperties("example")
public record ExampleProperties(String value) {
}
