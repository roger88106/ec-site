package com.ecsite.module.payment.application.model.view;

import com.ecsite.module.payment.domain.payment.PaymentId;

import java.time.Instant;

/**
 * 売上確定の結果が分からず、照合する支払
 *
 * @param id                 支払 ID
 * @param authorizationRef   決済サービスの与信の参照
 * @param captureRequestedAt 売上確定を求めた時刻（照合が読んだ時点の値）
 */
public record CaptureReconcileView(PaymentId id, String authorizationRef, Instant captureRequestedAt) {
}
