package com.ecsite.module.reporting.domain.sales;

import com.ecsite.common.model.id.ProductId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 集計の範囲の単体テスト
 *
 * <p>商品を選んだら送料を除く規則は、範囲の型だけが持つ。
 */
class SalesScopeTest {

    @ParameterizedTest(name = "送料を除く指定が {0}")
    @ValueSource(booleans = {true, false})
    @DisplayName("商品を選ぶと、送料を除く指定によらず、送料を除く")
    void excludesShippingWhenProductSelected(boolean excludeShipping) {
        // 準備：商品を選んだ範囲
        var scope = new SalesScope(new ProductId(UUID.randomUUID()), false, excludeShipping);

        // 操作と確認：商品を選んだ範囲で、送料を除く
        assertThat(scope.hasProduct()).isTrue();
        assertThat(scope.shippingExcluded()).isTrue();
    }

    @ParameterizedTest(name = "送料を除く指定が {0}")
    @ValueSource(booleans = {true, false})
    @DisplayName("商品を選ばなければ、送料を除く指定のとおり")
    void followsSettingWhenNoProduct(boolean excludeShipping) {
        // 準備：商品を選ばない範囲
        var scope = new SalesScope(null, false, excludeShipping);

        // 操作と確認：商品を選ばない範囲で、指定のとおり
        assertThat(scope.hasProduct()).isFalse();
        assertThat(scope.shippingExcluded()).isEqualTo(excludeShipping);
    }

    @Test
    @DisplayName("バリアント別の範囲は、商品を選んだ範囲で、常に送料を除く")
    void variantScopeAlwaysExcludesShipping() {
        // 準備：バリアント別の範囲
        var productId = new ProductId(UUID.randomUUID());
        var scope = SalesScope.ofProduct(productId, false);

        // 操作と確認：商品を選んだ範囲で、送料を除く
        assertThat(scope.productId()).isEqualTo(productId);
        assertThat(scope.hasProduct()).isTrue();
        assertThat(scope.shippingExcluded()).isTrue();
    }

    @ParameterizedTest(name = "消費税を除く指定が {0}")
    @ValueSource(booleans = {true, false})
    @DisplayName("消費税を除く指定は、そのまま保たれる")
    void keepsTaxSetting(boolean excludeTax) {
        // 準備：商品を選ばない範囲と、バリアント別の範囲
        var all = new SalesScope(null, excludeTax, false);
        var variants = SalesScope.ofProduct(new ProductId(UUID.randomUUID()), excludeTax);

        // 操作と確認：消費税を除く指定のとおり
        assertThat(all.excludeTax()).isEqualTo(excludeTax);
        assertThat(variants.excludeTax()).isEqualTo(excludeTax);
    }
}
