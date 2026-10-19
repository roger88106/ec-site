package com.ecsite.config.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * OpenAPI の操作の一覧の読み方の検査。<br />
 * 操作ごとの値は、管理 API なら権限、店舗 API なら token の要否で、読む側が決める（ここでは operationId を値にする）。
 */
class OpenApiOperationsTest {

    /**
     * 操作の一覧。具体的なパスと、変数を含むパスが重なる
     */
    private static final String SPEC = """
            servers:
              - url: /api/admin
            paths:
              /categories/{categoryId}:
                parameters:
                  - name: categoryId
                    in: path
                get:
                  operationId: getCategory
              /categories/order:
                get:
                  operationId: getCategoryOrder
                put:
                  operationId: putCategoryOrder
            """;

    /**
     * 操作の一覧（値は operationId）
     */
    private final OpenApiOperations<String> operations = OpenApiOperations.load(
            new ByteArrayResource(SPEC.getBytes(StandardCharsets.UTF_8)), (name, operation) -> (String) operation.get("operationId"));

    @Test
    @DisplayName("パスは servers の URL を前に付けて照らす")
    void prefixesServerUrl() {
        assertThat(operations.find("GET", "/api/admin/categories/order")).hasValue("getCategoryOrder");
        assertThat(operations.find("GET", "/categories/order")).isEmpty();
    }

    @Test
    @DisplayName("具体的なパスを、変数を含むパスより先に照らす")
    void matchesSpecificPathFirst() {
        assertThat(operations.find("GET", "/api/admin/categories/order")).hasValue("getCategoryOrder");
        assertThat(operations.find("GET", "/api/admin/categories/c1")).hasValue("getCategory");
    }

    @Test
    @DisplayName("メソッドも照らし、パス共通の parameters は操作として読まない")
    void matchesMethod() {
        assertThat(operations.find("PUT", "/api/admin/categories/order")).hasValue("putCategoryOrder");
        assertThat(operations.find("DELETE", "/api/admin/categories/order")).isEmpty();
    }

    @Test
    @DisplayName("契約にない操作は空")
    void unknownOperationIsEmpty() {
        assertThat(operations.find("GET", "/api/admin/unknown")).isEmpty();
    }
}
