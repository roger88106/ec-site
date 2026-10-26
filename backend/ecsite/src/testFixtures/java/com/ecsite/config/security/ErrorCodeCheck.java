package com.ecsite.config.security;

import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.PathNotFoundException;
import org.springframework.core.io.ClassPathResource;
import org.jspecify.annotations.Nullable;
import org.springframework.core.io.Resource;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

/**
 * エラーの応答の確認（docs/rules/api-conventions.md 第 5 節）。<br />
 * 応答（MockMvc のすべての結果）で、エラーの本文の {@code errorCode} が、その操作の一覧（{@code x-error-codes}）か
 * 共通のエラー（{@code x-common-error-codes}）にあることを確認する。操作は、認可と同じく契約から照合する。<br />
 * 統合テストでは {@code ErrorCodeCheckConfig} が MockMvc に接続して全ての応答を確認し、検査そのものは単体テストでも確かめる。
 */
public final class ErrorCodeCheck {

    /**
     * 契約のある API のパスの始まり
     */
    private static final List<String> API_PREFIXES = List.of("/api/admin/", "/api/store/");

    /**
     * 店舗 API と管理 API の、操作ごとの返しうるエラーのコード
     */
    private final List<OpenApiOperations<Set<String>>> apis;

    /**
     * 共通のエラー（2 つの API の和）
     */
    private final Set<String> common;

    /**
     * 契約から読んだエラーのコードで確認を作る。
     *
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
     * @return 確認
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
     * 応答を確認する。エラーの本文（状態コードが 400 以上の JSON）でなければ何もしない。
     *
     * @param method      HTTP メソッド
     * @param path        アプリの中のパス（{@code /api/admin/...}）
     * @param status      応答の状態コード
     * @param contentType 応答の Content-Type（ないときは null）
     * @param body        応答の本文
     */
    public void verify(String method, String path, int status, @Nullable String contentType, String body) {
        if (status < 400 || contentType == null || !contentType.contains("json")) {
            return;
        }
        verify(method, path, body);
    }

    /**
     * エラーの本文の {@code errorCode} が、操作の一覧か共通のエラーにあることを確認する。
     *
     * @param method HTTP メソッド
     * @param path   アプリの中のパス（{@code /api/admin/...}）
     * @param body   エラーの本文
     * @throws AssertionError 一覧にも共通のエラーにもないコードのとき
     */
    public void verify(String method, String path, String body) {
        String errorCode;
        try {
            errorCode = JsonPath.read(body, "$.errorCode");
        } catch (PathNotFoundException | IllegalArgumentException e) {
            return;
        }
        // 契約の API の外（テストだけの endpoint など）は確認しない
        if (errorCode == null || common.contains(errorCode) || API_PREFIXES.stream().noneMatch(path::startsWith)) {
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
    // YAML を読んだ汎用の Map を、契約（OpenAPI）の形が決まっているので、型を当てはめて読む
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
}
