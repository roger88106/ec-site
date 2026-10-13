package com.ecsite.module.payment.domain.payment;

import lombok.NonNull;

/**
 * 支払の画面。消費者がカード番号を入力する、決済サービスの画面。
 *
 * @param id  決済サービスでの画面の ID
 * @param url 画面の URL
 */
@org.jmolecules.ddd.annotation.ValueObject
public record PaymentPage(@NonNull String id, @NonNull String url) {
}
