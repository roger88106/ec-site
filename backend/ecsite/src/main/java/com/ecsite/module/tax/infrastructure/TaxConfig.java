package com.ecsite.module.tax.infrastructure;

import com.ecsite.module.tax.domain.taxcategory.TaxCategoryFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 税モジュールの設定
 *
 * <p>domain の部品を Spring の Bean にする。domain は Spring に依存しないので、部品に注釈を付けずにここで登録する。
 */
@Configuration(proxyBeanMethods = false)
class TaxConfig {

    /**
     * 税区分の生成と復元の部品を返す。
     *
     * @return 税区分の Factory
     */
    @Bean
    TaxCategoryFactory taxCategoryFactory() {
        return new TaxCategoryFactory();
    }
}
