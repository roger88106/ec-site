package com.ecsite.module.order.application.model;

import com.ecsite.module.order.application.port.PaymentDirectory;
import org.jspecify.annotations.Nullable;

import java.time.Instant;

/**
 * 注文の支払の表示
 *
 * @param status                支払の状態
 * @param deadline              支払の期限
 * @param payable               支払画面に移れるか（未払いで、支払の期限の前）
 * @param authorizationDeadline 与信期限。与信を受けていなければ null
 * @param authorizationExpiring 与信期限が近いか（期限を過ぎたものを含む。売上確定の前だけ）
 */
public record OrderPayment(PaymentDirectory.Status status, Instant deadline, boolean payable,
                           @Nullable Instant authorizationDeadline, boolean authorizationExpiring) {
}
