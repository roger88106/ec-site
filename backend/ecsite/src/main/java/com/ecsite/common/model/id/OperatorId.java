package com.ecsite.common.model.id;

import com.ecsite.common.model.base.EntityId;
import lombok.NonNull;

import java.util.UUID;

/**
 * 運営者の識別子
 *
 * @param value 識別子の値
 */
public record OperatorId(@NonNull UUID value) implements EntityId {
    /**
     * 識別子の値を UUID の文字列で返す。
     *
     * <p>record の既定の形（{@code OperatorId[value=...]}）にしない。文字列に連結する所（メッセージ、ログ、ファイルのキー）で、UUID の文字列のままにするため。
     *
     * @return UUID の文字列
     */
    @Override
    public String toString() {
        return value.toString();
    }
}
