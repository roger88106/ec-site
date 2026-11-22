package com.ecsite.module.payment.domain.payment;

import com.ecsite.common.model.base.EntityId;
import lombok.NonNull;

import java.util.UUID;

/**
 * 支払の識別子
 *
 * @param value 識別子の値
 */
public record PaymentId(@NonNull UUID value) implements EntityId {
    /**
     * 識別子の値を UUID の文字列で返す。
     *
     * <p>record の既定の形（{@code PaymentId[value=...]}）にしない。文字列に連結する所（メッセージ、ログ、ファイルのキー）で、UUID の文字列のままにするため。
     *
     * @return UUID の文字列
     */
    @Override
    public String toString() {
        return value.toString();
    }
}
