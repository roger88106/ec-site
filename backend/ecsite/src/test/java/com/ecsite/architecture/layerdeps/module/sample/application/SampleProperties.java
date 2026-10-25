package com.ecsite.architecture.layerdeps.module.sample.application;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 層の依存の検査の例：application の設定の値の型（infrastructure が使ってよい）。<br />
 * 構成の検査が違反を見つけられることを確認するための例であり、本番のコードではない。
 *
 * @param name 設定の値
 */
@ConfigurationProperties("sample")
public record SampleProperties(String name) {
}
