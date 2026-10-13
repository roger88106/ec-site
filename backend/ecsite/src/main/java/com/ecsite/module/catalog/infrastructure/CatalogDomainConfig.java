package com.ecsite.module.catalog.infrastructure;

import com.ecsite.module.catalog.domain.category.CategoryFactory;
import com.ecsite.module.catalog.domain.product.ProductFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 商品カタログモジュールの domain の部品を、Spring の部品にする。<br />
 * domain は Spring に依存しないので、注釈を付けずにここで登録する（バックエンドのアーキテクチャ 第 2 節）。
 */
@Configuration(proxyBeanMethods = false)
class CatalogDomainConfig {

    /**
     * カテゴリの生成と復元の部品を返す。
     *
     * @return カテゴリの Factory
     */
    @Bean
    CategoryFactory categoryFactory() {
        return new CategoryFactory();
    }

    /**
     * 商品の生成と復元の部品を返す。
     *
     * @return 商品の Factory
     */
    @Bean
    ProductFactory productFactory() {
        return new ProductFactory();
    }
}
