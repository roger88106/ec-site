package com.ecsite.config.security;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcBuilderCustomizer;
import org.springframework.context.annotation.Bean;

import java.nio.charset.StandardCharsets;

/**
 * MockMvc のすべての結果で、エラーの応答の確認（{@link ErrorCodeCheck}）を行う設定
 */
@TestConfiguration(proxyBeanMethods = false)
public class ErrorCodeCheckConfig {

    /**
     * MockMvc に、すべての結果の確認を追加する
     *
     * @return MockMvc の設定
     */
    @Bean
    MockMvcBuilderCustomizer errorCodeCheckCustomizer() {
        var check = ErrorCodeCheck.load();
        return builder -> builder.alwaysDo(result -> {
            var response = result.getResponse();
            check.verify(result.getRequest().getMethod(), result.getRequest().getRequestURI(), response.getStatus(),
                    response.getContentType(), response.getContentAsString(StandardCharsets.UTF_8));
        });
    }
}
