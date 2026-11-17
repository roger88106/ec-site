package com.ecsite.module.payment.application.model;

import java.time.Instant;

/**
 * 取得した与信
 *
 * @param authorizationRef 与信の参照
 * @param authorizedAt     与信の時刻（決済サービスが返す時刻。実際の与信より早いことがある）
 */
public record Authorization(String authorizationRef, Instant authorizedAt) {
}
