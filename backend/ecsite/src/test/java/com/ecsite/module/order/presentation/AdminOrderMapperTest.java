package com.ecsite.module.order.presentation;

import com.ecsite.api.admin.model.CancellationKind;
import com.ecsite.api.admin.model.CancellationRequest;
import com.ecsite.api.admin.model.Carrier;
import com.ecsite.api.admin.model.OrderStatus;
import com.ecsite.api.admin.model.PaymentStatus;
import com.ecsite.api.admin.model.ShipmentStatus;
import com.ecsite.module.order.application.model.view.OrderPayment;
import com.ecsite.module.order.application.model.view.OrderShipment;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 管理の注文の型と、生成したリクエスト・レスポンスのモデルの、列挙の変換の単体テスト
 *
 * <p>列挙の値の対応だけを確認する。項目の変換と応答の形は、管理の注文 API の統合テストと契約のテストが確認する。
 */
class AdminOrderMapperTest {

    // ============================== テスト ==============================

    @ParameterizedTest(name = "「{0}」")
    @EnumSource(OrderStatus.class)
    @DisplayName("AC-277 リクエストの注文の状態の全ての値を、ドメインの同じ名前の値に写す")
    void mapsEveryRequestOrderStatus(OrderStatus status) {
        // 操作と確認：同じ名前の値になる
        assertThat(new AdminOrderMapper().toDomainStatus(status))
                .isEqualTo(com.ecsite.module.order.domain.order.OrderStatus.valueOf(status.name()));
    }

    @ParameterizedTest(name = "「{0}」")
    @EnumSource(Carrier.class)
    @DisplayName("AC-277 リクエストの配送業者の全ての値を、ドメインの同じ名前の値に写す")
    void mapsEveryRequestCarrier(Carrier carrier) {
        // 操作と確認：同じ名前の値になる
        assertThat(new AdminOrderMapper().toDomainCarrier(carrier))
                .isEqualTo(com.ecsite.module.order.domain.order.Carrier.valueOf(carrier.name()));
    }

    @ParameterizedTest(name = "「{0}」")
    @EnumSource(com.ecsite.module.order.domain.order.OrderStatus.class)
    @DisplayName("AC-277 ドメインの注文の状態の全ての値を、レスポンスの同じ名前の値に写す")
    void mapsEveryDomainOrderStatus(com.ecsite.module.order.domain.order.OrderStatus status) {
        // 操作と確認：同じ名前の値になる
        assertThat(AdminOrderMapper.toStatus(status)).isEqualTo(OrderStatus.valueOf(status.name()));
    }

    @ParameterizedTest(name = "「{0}」")
    @EnumSource(OrderPayment.Status.class)
    @DisplayName("AC-277 支払の状態の全ての値を、レスポンスの同じ名前の値に写す")
    void mapsEveryPaymentStatus(OrderPayment.Status status) {
        // 操作と確認：同じ名前の値になる
        assertThat(AdminOrderMapper.toPaymentStatus(status)).isEqualTo(PaymentStatus.valueOf(status.name()));
    }

    @ParameterizedTest(name = "「{0}」")
    @EnumSource(com.ecsite.module.order.domain.order.Carrier.class)
    @DisplayName("AC-277 ドメインの配送業者の全ての値を、レスポンスの同じ名前の値に写す")
    void mapsEveryDomainCarrier(com.ecsite.module.order.domain.order.Carrier carrier) {
        // 操作と確認：同じ名前の値になる
        assertThat(AdminOrderMapper.toCarrier(carrier)).isEqualTo(Carrier.valueOf(carrier.name()));
    }

    @ParameterizedTest(name = "「{0}」")
    @EnumSource(OrderShipment.Status.class)
    @DisplayName("AC-277 配送状態の全ての値を、レスポンスの同じ名前の値に写す")
    void mapsEveryShipmentStatus(OrderShipment.Status status) {
        // 操作と確認：同じ名前の値になる
        assertThat(AdminOrderMapper.toShipmentStatus(status)).isEqualTo(ShipmentStatus.valueOf(status.name()));
    }

    @ParameterizedTest(name = "「{0}」")
    @EnumSource(com.ecsite.module.order.domain.order.vo.CancellationRequest.Result.class)
    @DisplayName("AC-277 キャンセルの申請の結果の全ての値を、レスポンスの同じ名前の値に写す")
    void mapsEveryCancellationResult(com.ecsite.module.order.domain.order.vo.CancellationRequest.Result result) {
        // 操作と確認：同じ名前の値になる
        assertThat(AdminOrderMapper.toResult(result)).isEqualTo(CancellationRequest.ResultEnum.valueOf(result.name()));
    }

    @ParameterizedTest(name = "「{0}」")
    @EnumSource(com.ecsite.module.order.domain.order.vo.CancellationKind.class)
    @DisplayName("AC-277 キャンセルの種類の全ての値を、レスポンスの同じ名前の値に写す")
    void mapsEveryCancellationKind(com.ecsite.module.order.domain.order.vo.CancellationKind kind) {
        // 操作と確認：同じ名前の値になる
        assertThat(AdminOrderMapper.toKind(kind)).isEqualTo(CancellationKind.valueOf(kind.name()));
    }
}
