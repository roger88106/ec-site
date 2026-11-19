package com.ecsite.module.shipping.api;

import com.ecsite.module.shipping.api.model.ShipmentSummary;
import com.ecsite.module.shipping.domain.Carrier;
import com.ecsite.module.shipping.domain.DeliveryStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 出荷の公開 API の、列挙の変換の単体テスト
 *
 * <p>列挙の値の対応だけを確認する。出荷を登録して返す流れは、出荷の API の統合テストが確認する。
 */
class ShippingApiConversionTest {

    // ============================== テスト ==============================

    @Nested
    @DisplayName("公開の型の配送業者からドメインの配送業者")
    class ToCarrier {

        @ParameterizedTest(name = "「{0}」")
        @EnumSource(ShipmentSummary.Carrier.class)
        @DisplayName("AC-277 公開の型の配送業者の全ての値を、ドメインの同じ名前の値に写す")
        void mapsEveryCarrier(ShipmentSummary.Carrier carrier) {
            // 操作と確認：同じ名前の値になる
            assertThat(ShippingApi.toCarrier(carrier)).isEqualTo(Carrier.valueOf(carrier.name()));
        }
    }

    @Nested
    @DisplayName("ドメインの配送業者から公開の型の配送業者")
    class ToSummaryCarrier {

        @ParameterizedTest(name = "「{0}」")
        @EnumSource(Carrier.class)
        @DisplayName("AC-277 ドメインの配送業者の全ての値を、公開の型の同じ名前の値に写す")
        void mapsEveryCarrier(Carrier carrier) {
            // 操作と確認：同じ名前の値になる
            assertThat(ShippingApi.toSummaryCarrier(carrier)).isEqualTo(ShipmentSummary.Carrier.valueOf(carrier.name()));
        }
    }

    @Nested
    @DisplayName("配送状態")
    class ToSummaryStatus {

        @ParameterizedTest(name = "「{0}」")
        @EnumSource(DeliveryStatus.class)
        @DisplayName("AC-277 配送状態の全ての値を、公開の型の同じ名前の値に写す")
        void mapsEveryStatus(DeliveryStatus status) {
            // 操作と確認：同じ名前の値になる
            assertThat(ShippingApi.toSummaryStatus(status)).isEqualTo(ShipmentSummary.Status.valueOf(status.name()));
        }
    }
}
