package com.ecsite.module.payment.application.model.view;

import com.ecsite.module.payment.domain.payment.PaymentPage;
import jakarta.annotation.Nullable;

import java.util.UUID;

/**
 * 期限を過ぎた未払いの支払
 *
 * @param id   支払 ID
 * @param page 開いている支払の画面。なければ null
 */
public record PastDeadlinePaymentView(UUID id, @Nullable PaymentPage page) {
}
