package com.ecsite.module.catalog.infrastructure;

import com.ecsite.module.catalog.domain.category.CategoryFactory;
import com.ecsite.module.catalog.domain.product.ProductFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 商品カタログモジュールの設定
 *
 * <p>domain のクラスを Spring の Bean にする。domain は Spring に依存しないので、クラスに注釈を付けずにここで登録する。
 */
@Configuration(proxyBeanMethods = false)
class CatalogConfig {

    /**
     * カテゴリの生成と復元のクラスを返す。
     *
     * @return カテゴリの Factory
     */
    @Bean
    CategoryFactory categoryFactory() {
        return new CategoryFactory();
    }

    /**
     * 商品の生成と復元のクラスを返す。
     *
     * @return 商品の Factory
     */
    @Bean
    ProductFactory productFactory() {
        return new ProductFactory();
    }
}
