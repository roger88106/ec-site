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
     * 標準の制約（{@code @NotBlank} など）のメッセージは、Hibernate Validator の日本語のものを使う
     * （ロケールは application.yml で日本語に固定している）。
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
