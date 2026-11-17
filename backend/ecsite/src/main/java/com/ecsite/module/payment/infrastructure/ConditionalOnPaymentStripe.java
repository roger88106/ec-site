package com.ecsite.module.payment.infrastructure;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Stripe の決済サービスの条件
 *
 * <p>決済の mock フラグ（{@code ecsite.payment.mock.enabled}）を立てないときに有効にする。
 * 立てるときの条件は {@link ConditionalOnPaymentMock}。
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@ConditionalOnBooleanProperty(name = "ecsite.payment.mock.enabled", havingValue = false, matchIfMissing = true)
public @interface ConditionalOnPaymentStripe {
}
