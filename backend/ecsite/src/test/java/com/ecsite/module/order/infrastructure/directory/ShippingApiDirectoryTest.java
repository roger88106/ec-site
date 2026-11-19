package com.ecsite.module.order.infrastructure.directory;

import com.ecsite.module.order.application.model.view.OrderShipment;
import com.ecsite.module.order.domain.order.Carrier;
import com.ecsite.module.shipping.api.model.ShipmentSummary;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 出荷モジュールの型と注文の型を変換するときの、列挙の変換の単体テスト
 *
 * <p>列挙の値の対応だけを確認する。
 */
class ShippingApiDirectoryTest {

    // ============================== テスト ==============================

    @Nested
    @DisplayName("注文の配送業者から出荷モジュールの配送業者")
    class ToShippingCarrier {

        @ParameterizedTest(name = "「{0}」")
        @EnumSource(Carrier.class)
        @DisplayName("AC-277 注文の配送業者の全ての値を、出荷モジュールの同じ名前の値に写す")
        void mapsEveryCarrier(Carrier carrier) {
            // 操作と確認：同じ名前の値になる
            assertThat(ShippingApiDirectory.toShippingCarrier(carrier)).isEqualTo(ShipmentSummary.Carrier.valueOf(carrier.name()));
        }
    }

    @Nested
    @DisplayName("出荷モジュールの配送業者から注文の配送業者")
    class ToCarrier {

        @ParameterizedTest(name = "「{0}」")
        @EnumSource(ShipmentSummary.Carrier.class)
        @DisplayName("AC-277 出荷モジュールの配送業者の全ての値を、注文の同じ名前の値に写す")
        void mapsEveryCarrier(ShipmentSummary.Carrier carrier) {
            // 操作と確認：同じ名前の値になる
            assertThat(ShippingApiDirectory.toCarrier(carrier)).isEqualTo(Carrier.valueOf(carrier.name()));
        }
    }

    @Nested
    @DisplayName("配送状態")
    class Status {

        @ParameterizedTest(name = "「{0}」")
        @EnumSource(ShipmentSummary.Status.class)
        @DisplayName("AC-277 出荷モジュールの配送状態の全ての値を、注文の配送状態の同じ名前の値に写す")
        void mapsEveryStatus(ShipmentSummary.Status status) {
            // 操作と確認：同じ名前の値になる
            assertThat(ShippingApiDirectory.toStatus(status)).isEqualTo(OrderShipment.Status.valueOf(status.name()));
        }
    }
}
