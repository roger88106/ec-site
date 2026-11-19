package com.ecsite.module.order.presentation;

import com.ecsite.api.store.model.CancellationKind;
import com.ecsite.api.store.model.CancellationRequest;
import com.ecsite.api.store.model.Carrier;
import com.ecsite.api.store.model.OrderStatus;
import com.ecsite.api.store.model.ShipmentStatus;
import com.ecsite.api.store.model.UnpurchasableReason;
import com.ecsite.module.order.application.model.view.OrderShipment;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 店舗の注文の型を、生成したレスポンスのモデルに変換するときの、列挙の変換の単体テスト
 *
 * <p>列挙の値の対応だけを確認する。項目の変換と応答の形は、店舗の注文 API の統合テストと契約のテストが確認する。
 */
class StoreOrderMapperTest {

    // ============================== テスト ==============================

    @ParameterizedTest(name = "「{0}」")
    @EnumSource(com.ecsite.module.order.domain.order.OrderStatus.class)
    @DisplayName("AC-277 ドメインの注文の状態の全ての値を、レスポンスの同じ名前の値に写す")
    void mapsEveryOrderStatus(com.ecsite.module.order.domain.order.OrderStatus status) {
        // 操作と確認：同じ名前の値になる
        assertThat(StoreOrderMapper.toStatus(status)).isEqualTo(OrderStatus.valueOf(status.name()));
    }

    @ParameterizedTest(name = "「{0}」")
    @EnumSource(com.ecsite.module.order.domain.order.vo.CancellationKind.class)
    @DisplayName("AC-277 キャンセルの種類の全ての値を、レスポンスの同じ名前の値に写す")
    void mapsEveryCancellationKind(com.ecsite.module.order.domain.order.vo.CancellationKind kind) {
        // 操作と確認：同じ名前の値になる
        assertThat(StoreOrderMapper.toKind(kind)).isEqualTo(CancellationKind.valueOf(kind.name()));
    }

    @ParameterizedTest(name = "「{0}」")
    @EnumSource(com.ecsite.module.order.domain.order.Carrier.class)
    @DisplayName("AC-277 ドメインの配送業者の全ての値を、レスポンスの同じ名前の値に写す")
    void mapsEveryCarrier(com.ecsite.module.order.domain.order.Carrier carrier) {
        // 操作と確認：同じ名前の値になる
        assertThat(StoreOrderMapper.toCarrier(carrier)).isEqualTo(Carrier.valueOf(carrier.name()));
    }

    @ParameterizedTest(name = "「{0}」")
    @EnumSource(OrderShipment.Status.class)
    @DisplayName("AC-277 配送状態の全ての値を、レスポンスの同じ名前の値に写す")
    void mapsEveryShipmentStatus(OrderShipment.Status status) {
        // 操作と確認：同じ名前の値になる
        assertThat(StoreOrderMapper.toShipmentStatus(status)).isEqualTo(ShipmentStatus.valueOf(status.name()));
    }

    @ParameterizedTest(name = "「{0}」")
    @EnumSource(com.ecsite.module.order.domain.quote.UnpurchasableReason.class)
    @DisplayName("AC-277 買えない理由の全ての値を、レスポンスの同じ名前の値に写す")
    void mapsEveryUnpurchasableReason(com.ecsite.module.order.domain.quote.UnpurchasableReason reason) {
        // 操作と確認：同じ名前の値になる
        assertThat(StoreOrderMapper.toReason(reason)).isEqualTo(UnpurchasableReason.valueOf(reason.name()));
    }

    @ParameterizedTest(name = "「{0}」")
    @EnumSource(com.ecsite.module.order.domain.order.vo.CancellationRequest.Result.class)
    @DisplayName("AC-277 キャンセルの申請の結果の全ての値を、レスポンスの同じ名前の値に写す")
    void mapsEveryCancellationResult(com.ecsite.module.order.domain.order.vo.CancellationRequest.Result result) {
        // 操作と確認：同じ名前の値になる
        assertThat(StoreOrderMapper.toResult(result)).isEqualTo(CancellationRequest.ResultEnum.valueOf(result.name()));
    }
}
