package com.ecsite.module.reporting.application.model.query;

import com.ecsite.module.reporting.domain.sales.Basis;
import com.ecsite.module.reporting.domain.sales.ReportPeriod;
import com.ecsite.module.reporting.domain.sales.SalesScope;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * バリアントの売れ行きの条件の単体テスト
 */
class VariantSalesQueryTest {

    /**
     * 集計の期間
     */
    private static final ReportPeriod PERIOD = new ReportPeriod(LocalDate.parse("2031-04-01"), LocalDate.parse("2031-04-30"));

    @Test
    @DisplayName("商品を選んだ範囲なら、条件を作れる")
    void acceptsScopeWithProduct() {
        // 準備：商品を選んだ範囲
        var scope = SalesScope.ofProduct(UUID.randomUUID(), false);

        // 操作：条件を作る
        var query = new VariantSalesQuery(PERIOD, Basis.ORDER, scope);

        // 確認：範囲をそのまま持つ
        assertThat(query.scope()).isEqualTo(scope);
    }

    @Test
    @DisplayName("商品を選んでいない範囲では、条件を作る時点で NullPointerException")
    void rejectsScopeWithoutProduct() {
        // 準備：商品を選んでいない範囲
        var scope = new SalesScope(null, false, false);

        // 操作と確認：作る時点で止まり、メッセージで理由が分かる
        assertThatThrownBy(() -> new VariantSalesQuery(PERIOD, Basis.ORDER, scope))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("バリアントの売れ行きの範囲は商品を選んだ範囲である");
    }
}
