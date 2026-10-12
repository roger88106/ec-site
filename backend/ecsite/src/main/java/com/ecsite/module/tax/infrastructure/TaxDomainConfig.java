package com.ecsite.module.tax.infrastructure;

import com.ecsite.module.tax.domain.taxcategory.TaxCategoryFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 税モジュールの domain の部品を、Spring の部品にする。<br />
 * domain は Spring に依存しないので、注釈を付けずにここで登録する（constitution 4.2）。
 */
@Configuration(proxyBeanMethods = false)
class TaxDomainConfig {

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
