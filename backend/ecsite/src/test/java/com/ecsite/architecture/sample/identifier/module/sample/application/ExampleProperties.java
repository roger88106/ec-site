package com.ecsite.architecture.sample.identifier.module.sample.application;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.UUID;

/**
 * 識別子の型の検査の例：application の設定の型に、UUID の項目がある
 *
 * <p>設定の型は環境変数の文字列から作るので、UUID を使ってよい（規則に合う）。
 * 構成の検査が例外を正しく除くことを確認するための例であり、本番のコードではない。
 *
 * @param categoryId 区分の ID
 */
@ConfigurationProperties("example")
public record ExampleProperties(UUID categoryId) {
}
