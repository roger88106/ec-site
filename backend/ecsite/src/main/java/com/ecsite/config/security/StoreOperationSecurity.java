package com.ecsite.config.security;

import org.springframework.core.io.Resource;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 店舗 API の操作ごとの、会員の token の要否。<br />
 * {@code store.yaml} の各操作（メソッドとパス）の {@code security} を読み込む。{@code memberToken} だけの操作は token が要り、
 * 空の要件（{@code {}}）を含む操作と {@code security} のない操作は token なしで使える。
 * 認可の設定にパスを並べないので、操作を追加しても契約と食い違わない。対応にない要求には要否を返さない（呼び出し側が拒否する）。
 */
class StoreOperationSecurity {

    /**
     * 操作ごとの token の要否
     */
    private final OpenApiOperations<Boolean> operations;

    /**
     * @param operations 操作ごとの token の要否
     */
    private StoreOperationSecurity(OpenApiOperations<Boolean> operations) {
        this.operations = operations;
    }

    /**
     * {@code store.yaml} を読み込む。
     *
     * @param resource {@code store.yaml}
     * @return 操作ごとの token の要否
     */
    @SuppressWarnings("unchecked")
    static StoreOperationSecurity load(Resource resource) {
        return new StoreOperationSecurity(OpenApiOperations.load(resource, (name, operation) -> {
            var security = (List<Map<String, Object>>) operation.get("security");
            return security != null && !security.isEmpty() && security.stream().noneMatch(Map::isEmpty);
        }));
    }

    /**
     * 要求に会員の token が必要かを求める。
     *
     * @param method HTTP メソッド
     * @param path   アプリの中のパス（{@code /api/store/...}）
     * @return token が要れば true。{@code store.yaml} にない要求なら空
     */
    Optional<Boolean> requiresToken(String method, String path) {
        return operations.find(method, path);
    }
}
