package com.ecsite.config.security;

import org.springframework.core.io.Resource;

import java.util.Optional;

/**
 * 管理 API の操作ごとに必要な権限。<br />
 * {@code admin.yaml} の各操作（メソッドとパス）と {@code x-required-permission} の対応を読み込む。
 * Controller には権限を書かないので、契約と食い違わず、書き漏れで公開されることもない。
 * 対応にない要求には権限を返さない（呼び出し側が拒否する）。
 */
class AdminOperationPermissions {

    /**
     * 権限の拡張項目の名前
     */
    private static final String PERMISSION_KEY = "x-required-permission";

    /**
     * 操作ごとの権限
     */
    private final OpenApiOperations<String> operations;

    /**
     * @param operations 操作ごとの権限
     */
    private AdminOperationPermissions(OpenApiOperations<String> operations) {
        this.operations = operations;
    }

    /**
     * {@code admin.yaml} を読み込む。権限のない操作があれば起動を止める。
     *
     * @param resource {@code admin.yaml}
     * @return 操作ごとに必要な権限
     */
    static AdminOperationPermissions load(Resource resource) {
        return new AdminOperationPermissions(OpenApiOperations.load(resource, (name, operation) -> {
            var permission = operation.get(PERMISSION_KEY);
            if (permission == null) {
                // 契約テスト（AdminApiPermissionContractTest）で防ぐが、起動でも止める
                throw new IllegalStateException("admin.yaml の " + name + " に " + PERMISSION_KEY + " がない");
            }
            return permission.toString();
        }));
    }

    /**
     * 要求に必要な権限を求める。
     *
     * @param method HTTP メソッド
     * @param path   アプリの中のパス（{@code /api/admin/...}）
     * @return 必要な権限。{@code admin.yaml} にない要求なら空
     */
    Optional<String> requiredPermission(String method, String path) {
        return operations.find(method, path);
    }
}
