package com.ecsite.module.payment.application.model.view;

import com.ecsite.common.model.value.Money;
import com.ecsite.module.payment.domain.payment.PaymentStatus;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

/**
 * 売上を確定する前の支払
 *
 * @param id               支払 ID
 * @param status           支払の状態
 * @param authorizationRef 決済サービスの与信の参照。与信の前は null
 * @param amount           金額
 */
public record PaymentCaptureView(UUID id, PaymentStatus status, @Nullable String authorizationRef, Money amount) {
}
