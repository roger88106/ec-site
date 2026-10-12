package com.ecsite.config.security;

import org.springframework.core.io.Resource;
import org.springframework.http.server.PathContainer;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 管理 API の操作ごとに必要な権限（plan 003 第 3.2 節）。<br />
 * {@code admin.yaml} の各操作（メソッドとパス）と {@code x-required-permission} の対応を読み込む。
 * Controller には権限を書かないので、契約と食い違わず、書き漏れで公開されることもない。
 * 対応にない要求には権限を返さない（呼び出し側が拒否する。NFR-3）。
 */
class AdminOperationPermissions {

    /**
     * 権限の拡張項目の名前
     */
    private static final String PERMISSION_KEY = "x-required-permission";

    /**
     * OpenAPI で操作を表す HTTP メソッド
     */
    private static final Set<String> HTTP_METHODS = Set.of("get", "put", "post", "delete", "patch", "head", "options", "trace");

    /**
     * 操作。パスは {@code servers.url} を含む
     *
     * @param method     HTTP メソッド（大文字）
     * @param pattern    パス
     * @param permission 必要な権限
     */
    private record Operation(String method, PathPattern pattern, String permission) {
    }

    /**
     * 操作の一覧。パスの具体的なものが先（{@code /categories/order} を {@code /categories/{categoryId}} より先に照らす）
     */
    private final List<Operation> operations;

    private AdminOperationPermissions(List<Operation> operations) {
        this.operations = operations;
    }

    /**
     * {@code admin.yaml} を読み込む。
     *
     * @param resource {@code admin.yaml}
     * @return 操作ごとに必要な権限
     */
    static AdminOperationPermissions load(Resource resource) {
        try (InputStream in = resource.getInputStream()) {
            return parse(in);
        } catch (IOException e) {
            throw new UncheckedIOException("admin.yaml を読めない：" + resource, e);
        }
    }

    @SuppressWarnings("unchecked")
    private static AdminOperationPermissions parse(InputStream in) {
        Map<String, Object> root = new Yaml(new SafeConstructor(new LoaderOptions())).load(in);
        var servers = (List<Map<String, Object>>) root.get("servers");
        var basePath = (String) servers.getFirst().get("url");
        var paths = (Map<String, Map<String, Object>>) root.get("paths");

        var parser = PathPatternParser.defaultInstance;
        var operations = new ArrayList<Operation>();
        paths.forEach((path, item) -> item.forEach((key, value) -> {
            // パス共通の parameters などは操作ではない
            if (!HTTP_METHODS.contains(key)) {
                return;
            }
            var permission = ((Map<String, Object>) value).get(PERMISSION_KEY);
            if (permission == null) {
                // 契約テスト（AdminApiPermissionContractTest）で防ぐが、起動でも止める
                throw new IllegalStateException("admin.yaml の " + key.toUpperCase(Locale.ROOT) + " " + path + " に " + PERMISSION_KEY + " がない");
            }
            operations.add(new Operation(key.toUpperCase(Locale.ROOT), parser.parse(basePath + path), permission.toString()));
        }));
        operations.sort(Comparator.comparing(Operation::pattern, PathPattern.SPECIFICITY_COMPARATOR));
        return new AdminOperationPermissions(List.copyOf(operations));
    }

    /**
     * 要求に必要な権限を求める。
     *
     * @param method HTTP メソッド
     * @param path   アプリの中のパス（{@code /api/admin/...}）
     * @return 必要な権限。{@code admin.yaml} にない要求なら空
     */
    Optional<String> requiredPermission(String method, String path) {
        var container = PathContainer.parsePath(path);
        return operations.stream()
                .filter(op -> op.method().equals(method) && op.pattern().matches(container))
                .map(Operation::permission)
                .findFirst();
    }
}
