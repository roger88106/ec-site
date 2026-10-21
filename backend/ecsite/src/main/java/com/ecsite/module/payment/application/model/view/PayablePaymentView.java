package com.ecsite.module.payment.application.model.view;

import com.ecsite.common.model.value.Money;
import com.ecsite.module.payment.domain.payment.PaymentPage;
import jakarta.annotation.Nullable;

import java.util.UUID;

/**
 * 支払える支払（未払いで期限の前）
 *
 * @param id     支払 ID
 * @param amount 金額
 * @param page   開いている支払画面。なければ null
 */
public record PayablePaymentView(UUID id, Money amount, @Nullable PaymentPage page) {
}
