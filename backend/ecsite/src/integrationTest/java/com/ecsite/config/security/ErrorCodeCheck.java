package com.ecsite.config.security;

import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.PathNotFoundException;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.test.web.servlet.MvcResult;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

/**
 * 統合テストのエラーの応答の確かめ（docs/api/conventions.md 第 4 節）。<br />
 * MockMvc のすべての結果で、エラーの本文の {@code errorCode} が、その操作の一覧（{@code x-error-codes}）か
 * 共通のエラー（{@code x-common-error-codes}）にあることを確かめる。操作は、認可と同じく契約から照らす。
 */
public final class ErrorCodeCheck {

    /**
     * 店舗 API と管理 API の、操作ごとの返しうるエラーのコード
     */
    private final List<OpenApiOperations<Set<String>>> apis;

    /**
     * 共通のエラー（2 つの API の和）
     */
    private final Set<String> common;

    /**
     * @param apis   操作ごとの返しうるエラーのコード
     * @param common 共通のエラー
     */
    private ErrorCodeCheck(List<OpenApiOperations<Set<String>>> apis, Set<String> common) {
        this.apis = apis;
        this.common = common;
    }

    /**
     * クラスパスの契約（{@code openapi/admin.yaml}、{@code openapi/store.yaml}）を読む。
     *
     * @return 確かめ
     */
    public static ErrorCodeCheck load() {
        var resources = List.<Resource>of(new ClassPathResource("openapi/admin.yaml"), new ClassPathResource("openapi/store.yaml"));
        var common = new TreeSet<String>();
        for (Resource resource : resources) {
            common.addAll(commonCodes(resource));
        }
        var apis = resources.stream()
                .map(resource -> OpenApiOperations.load(resource, (name, operation) -> codes(operation.get("x-error-codes"))))
                .toList();
        return new ErrorCodeCheck(apis, Set.copyOf(common));
    }

    /**
     * MockMvc の結果を確かめる。エラーの本文でなければ何もしない。
     *
     * @param result MockMvc の結果
     */
    public void verify(MvcResult result) throws IOException {
        var response = result.getResponse();
        var contentType = response.getContentType();
        if (response.getStatus() < 400 || contentType == null || !contentType.contains("json")) {
            return;
        }
        verify(result.getRequest().getMethod(), result.getRequest().getRequestURI(),
                response.getContentAsString(StandardCharsets.UTF_8));
    }

    /**
     * エラーの本文の {@code errorCode} が、操作の一覧か共通のエラーにあることを確かめる。
     *
     * @param method HTTP メソッド
     * @param path   アプリの中のパス（{@code /api/admin/...}）
     * @param body   エラーの本文
     * @throws AssertionError 一覧にも共通のエラーにもないコードのとき
     */
    void verify(String method, String path, String body) {
        String errorCode;
        try {
            errorCode = JsonPath.read(body, "$.errorCode");
        } catch (PathNotFoundException | IllegalArgumentException e) {
            return;
        }
        if (errorCode == null || common.contains(errorCode)) {
            return;
        }
        Optional<Set<String>> listed = apis.stream()
                .map(api -> api.find(method, path))
                .flatMap(Optional::stream)
                .findFirst();
        if (listed.isEmpty() || !listed.get().contains(errorCode)) {
            throw new AssertionError("%s %s が返した %s が、契約のその操作の x-error-codes にも共通のエラーにもない（一覧：%s）"
                    .formatted(method, path, errorCode, listed.map(Object::toString).orElse("契約にない操作")));
        }
    }

    /**
     * 契約の共通のエラーを読む。
     */
    @SuppressWarnings("unchecked")
    private static Set<String> commonCodes(Resource resource) {
        try (InputStream in = resource.getInputStream()) {
            Map<String, Object> root = new Yaml(new SafeConstructor(new LoaderOptions())).load(in);
            var components = (Map<String, Object>) root.getOrDefault("components", Map.of());
            var schemas = (Map<String, Object>) components.getOrDefault("schemas", Map.of());
            var errorCode = (Map<String, Object>) schemas.getOrDefault("ErrorCode", Map.of());
            return codes(errorCode.get("x-common-error-codes"));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * YAML の値をコードの集合にする。
     */
    private static Set<String> codes(Object value) {
        return value == null ? Set.of() : Set.copyOf(((List<?>) value).stream().map(String::valueOf).toList());
    }

    /**
     * MockMvc のすべての結果を確かめる設定
     */
    @TestConfiguration(proxyBeanMethods = false)
    public static class Config {

        /**
         * MockMvc に、すべての結果の確かめを足す
         *
         * @return MockMvc の設定
         */
        @Bean
        MockMvcBuilderCustomizer errorCodeCheckCustomizer() {
            var check = ErrorCodeCheck.load();
            return builder -> builder.alwaysDo(check::verify);
        }
    }
}
