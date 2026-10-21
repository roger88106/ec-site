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
     * バリデーター。<br />
     * 制約アノテーションの {@code {key}} 形式のメッセージを messages.properties から解決する。
     * API の応答の検証のエラーの文は、この文ではなく {@code ValidationMessages} が制約の種類から作る。
     *
     * @param messageSource メッセージソース
     * @return バリデーター
     */
    @Bean
    public LocalValidatorFactoryBean validator(MessageSource messageSource) {
        var factoryBean = new LocalValidatorFactoryBean();
        factoryBean.setValidationMessageSource(messageSource);
        return factoryBean;
    }
}
