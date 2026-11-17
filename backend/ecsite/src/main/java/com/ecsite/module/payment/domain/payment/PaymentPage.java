package com.ecsite.module.payment.domain.payment;

import lombok.NonNull;
import org.jmolecules.ddd.annotation.ValueObject;

/**
 * 支払画面
 *
 * <p>消費者がカード番号を入力する、決済サービスの画面。
 *
 * @param id  決済サービスでの画面の ID
 * @param url 画面の URL
 */
@ValueObject
public record PaymentPage(@NonNull String id, @NonNull String url) {
}
