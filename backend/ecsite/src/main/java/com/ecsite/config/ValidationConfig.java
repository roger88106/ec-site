package com.ecsite.config;

import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

/**
 * Bean Validation の設定
 */
@Configuration
class ValidationConfig {

    /**
     * バリデーターを作る。
     *
     * <p>制約アノテーションの {@code {key}} 形式のメッセージを messages.properties から解決する。
     *
     * @param messageSource メッセージソース
     * @return バリデーター
     */
    @Bean
    LocalValidatorFactoryBean validator(MessageSource messageSource) {
        var factoryBean = new LocalValidatorFactoryBean();
        factoryBean.setValidationMessageSource(messageSource);
        return factoryBean;
    }
}
