package com.ecsite.config.security;

import com.ecsite.IntegrationTest;
import com.ecsite.TestTokens;
import com.ecsite.contract.Contracts;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 店舗 API の token の要否が、契約（{@code store.yaml} の {@code security}）と合うことの検査。<br />
 * 操作の一覧はテストに書かず {@code store.yaml} から読むので、操作を追加したときに認可の設定が食い違えば、このテストが失敗する。
 * パスの変数には、どの資源にも当たらない ID を入れる（認可の後の結果は見ない）。
 */
@IntegrationTest
@AutoConfigureMockMvc
@Transactional
class StoreApiTokenContractTest {

    /**
     * OpenAPI で操作を表す HTTP メソッド
     */
    private static final Set<String> HTTP_METHODS = Set.of("get", "put", "post", "delete", "patch");

    @Autowired
    MockMvc mockMvc;

    @Test
    @DisplayName("AC-232、AC-234 契約で会員の token だけを受け付ける操作は、どれも token なしで 401")
    void memberOperationsRejectMissingToken() throws Exception {
        var operations = operations().stream().filter(Operation::requiresToken).toList();
        assertThat(operations).isNotEmpty();

        assertThat(statusesWithoutToken(operations)).allSatisfy((name, code) ->
                assertThat(code).as(name).isEqualTo(401));
    }

    @Test
    @DisplayName("AC-233、AC-234 契約で token が必要ない操作は、どれも token なしで 401 にならない")
    void publicOperationsAcceptMissingToken() throws Exception {
        var operations = operations().stream().filter(operation -> !operation.requiresToken()).toList();
        assertThat(operations).isNotEmpty();

        assertThat(statusesWithoutToken(operations)).allSatisfy((name, code) ->
                assertThat(code).as(name).isNotEqualTo(401));
    }

    @Test
    @DisplayName("契約にない店舗 API の操作は、token なしで 401")
    void unknownOperationRejected() throws Exception {
        mockMvc.perform(request(HttpMethod.DELETE, "/api/store/products").with(TestTokens.none()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("契約にない店舗 API の操作は、会員の token があっても 403")
    void unknownOperationForbiddenWithToken() throws Exception {
        mockMvc.perform(request(HttpMethod.DELETE, "/api/store/products").with(TestTokens.member().bearer()))
                .andExpect(status().isForbidden());
    }

    // ============================== 部品 ==============================

    /**
     * 店舗 API の操作
     *
     * @param method        HTTP メソッド
     * @param path          パス（{@code servers.url} を含む）
     * @param requiresToken 会員の token が必要か（{@code security} が {@code memberToken} だけ）
     */
    private record Operation(String method, String path, boolean requiresToken) {
    }

    /**
     * 操作ごとに token なしで呼び、ステータスコードを集める。
     *
     * @param operations 操作
     * @return 「メソッド パス」→ ステータスコード
     */
    private Map<String, Integer> statusesWithoutToken(List<Operation> operations) throws Exception {
        var statuses = new LinkedHashMap<String, Integer>();
        for (var operation : operations) {
            var path = operation.path().replaceAll("\\{[^}]+}", UUID.randomUUID().toString());
            var result = mockMvc.perform(request(HttpMethod.valueOf(operation.method()), path).with(TestTokens.none()))
                    .andReturn();
            statuses.put(operation.method() + " " + operation.path(), result.getResponse().getStatus());
        }
        return statuses;
    }

    /**
     * {@code store.yaml} の操作を読む。
     *
     * @return 操作
     */
    // YAML を読んだ汎用の Map を、契約（OpenAPI）の形が決まっているので、型を当てはめて読む
    @SuppressWarnings("unchecked")
    private static List<Operation> operations() throws IOException {
        Map<String, Object> root = new Yaml(new SafeConstructor(new LoaderOptions())).load(Files.readString(Contracts.openApiDir().resolve("store.yaml")));
        var basePath = (String) ((List<Map<String, Object>>) root.get("servers")).getFirst().get("url");
        var operations = new ArrayList<Operation>();
        ((Map<String, Map<String, Object>>) root.get("paths")).forEach((path, item) -> item.forEach((key, value) -> {
            if (HTTP_METHODS.contains(key)) {
                var security = (List<Map<String, Object>>) ((Map<String, Object>) value).get("security");
                if (security == null) {
                    // 起動の検証（StoreOperationSecurity）と同じく、security のない操作は契約の誤りとして失敗させる
                    throw new IllegalStateException("store.yaml の " + key.toUpperCase(Locale.ROOT) + " " + path + " に security がない");
                }
                var requiresToken = !security.isEmpty() && security.stream().noneMatch(Map::isEmpty);
                operations.add(new Operation(key.toUpperCase(Locale.ROOT), basePath + path, requiresToken));
            }
        }));
        return operations;
    }
}
