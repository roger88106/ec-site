package com.ecsite.module.reporting.application.model.query;

import com.ecsite.module.reporting.domain.sales.Basis;
import com.ecsite.module.reporting.domain.sales.ReportPeriod;
import com.ecsite.module.reporting.domain.sales.SalesScope;
import lombok.NonNull;

import java.util.Objects;

/**
 * バリアントの売れ行きの条件
 *
 * <p>範囲は商品を選んだ範囲だけを受け付ける。商品を選んでいなければ、作る時点で {@link NullPointerException} を投げる。
 *
 * @param period 期間
 * @param basis  売上の数え方
 * @param scope  集計の範囲（商品を選んだ範囲）
 */
public record VariantSalesQuery(@NonNull ReportPeriod period, @NonNull Basis basis, @NonNull SalesScope scope) {

    /**
     * コンストラクタ。
     *
     * @param period 期間
     * @param basis  売上の数え方
     * @param scope  集計の範囲（商品を選んだ範囲）
     * @throws NullPointerException 範囲が商品を選んでいないとき
     */
    public VariantSalesQuery {
        Objects.requireNonNull(scope.productId(), "バリアントの売れ行きの範囲は商品を選んだ範囲である");
    }
}
