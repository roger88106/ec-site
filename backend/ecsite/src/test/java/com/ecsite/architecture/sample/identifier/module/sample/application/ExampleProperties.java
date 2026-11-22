package com.ecsite.architecture.sample.identifier.module.sample.application;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.UUID;

/**
 * 識別子の型の検査の例：application の設定の型に、UUID の項目と、UUID を受け取るメソッドがある
 *
 * <p>設定の型は環境変数の文字列から作るので、項目とその アクセサー とコンストラクターは UUID を使ってよい（規則に合う）。
 * 他のメソッドの引数の UUID は規則に合わない。
 * 構成の検査が例外だけを除くことを確認するための例であり、本番のコードではない。
 *
 * @param categoryId 区分の ID
 */
@ConfigurationProperties("example")
public record ExampleProperties(UUID categoryId) {

    /**
     * 区分のパスを返す（UUID を受け取るので規則に合わない）。
     *
     * @param id 区分の ID
     * @return パス
     */
    public String pathOf(UUID id) {
        return "/" + id;
    }
}
