package com.ecsite.module.payment.application.model.view;

import com.ecsite.common.model.value.Money;
import com.ecsite.module.payment.domain.payment.PaymentPage;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

/**
 * 支払える支払
 *
 * <p>未払いで、支払の期限の前の支払。
 *
 * @param id     支払 ID
 * @param amount 金額
 * @param page   開いている支払画面。なければ null
 */
public record PayablePaymentView(UUID id, Money amount, @Nullable PaymentPage page) {
}
