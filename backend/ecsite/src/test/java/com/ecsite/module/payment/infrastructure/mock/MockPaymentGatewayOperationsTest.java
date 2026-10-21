package com.ecsite.module.payment.infrastructure.mock;

import com.ecsite.module.payment.application.port.PaymentGateway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 本番の mock の決済サービスが、テストだけで使う操作を持たないことの検証。
 * テストだけで使う操作は、テストのソースの、mock を包む実装（{@code TestPaymentGateway}）が持つ。
 */
@DisplayName("本番の mock の決済サービスの公開の操作")
class MockPaymentGatewayOperationsTest {

    /**
     * 支払画面の操作（画面の Controller と、画面の操作を真似るテストから呼ぶ）
     */
    private static final Set<String> PAGE_OPERATIONS = Set.of("pay", "markPaid", "notifyAuthorized", "page", "authorizationRefOf");

    @Test
    @DisplayName("AC-246 本番の mock の決済サービスは、port と支払画面の操作のほかに公開の操作を持たない")
    void hasNoTestOnlyOperations() {
        var allowed = Stream.concat(Arrays.stream(PaymentGateway.class.getMethods()).map(Method::getName), PAGE_OPERATIONS.stream())
                .collect(Collectors.toSet());

        var publicOperations = Arrays.stream(MockPaymentGateway.class.getDeclaredMethods())
                .filter(method -> Modifier.isPublic(method.getModifiers()) && !method.isSynthetic())
                .map(Method::getName)
                .toList();

        assertThat(publicOperations).isSubsetOf(allowed);
    }
}
