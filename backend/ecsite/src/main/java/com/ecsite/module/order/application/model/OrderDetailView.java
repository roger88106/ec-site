package com.ecsite.module.order.application.model;

import com.ecsite.module.order.domain.order.OrderStatus;
import com.ecsite.module.order.domain.order.vo.Cancellation;
import com.ecsite.module.order.domain.order.vo.CancellationRequest;
import com.ecsite.module.order.domain.order.vo.OrderLine;
import com.ecsite.module.order.domain.order.vo.OrderNumber;
import com.ecsite.module.order.domain.order.vo.ShippingAddress;
import com.ecsite.module.order.domain.quote.Amounts;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * 注文の詳細の表示
 *
 * @param id                  注文 ID
 * @param orderNumber         注文番号
 * @param orderedAt           注文日時
 * @param status              状態
 * @param lines               明細
 * @param amounts             金額の内訳
 * @param email               連絡先のメールアドレス
 * @param shippingAddress     お届け先
 * @param cancellationRequest キャンセルの申請。なければ null
 * @param cancellation        キャンセル。なければ null
 * @param payment             注文の支払。決済を入れる前の注文は null
 * @param shipment            注文の出荷。出荷していなければ null
 * @param awaitingPayment     支払待ちで、支払がある
 * @param actions             画面の操作の可否
 */
public record OrderDetailView(UUID id, OrderNumber orderNumber, Instant orderedAt, OrderStatus status,
                              List<OrderLine> lines, Amounts amounts, String email, ShippingAddress shippingAddress,
                              @Nullable CancellationRequest cancellationRequest, @Nullable Cancellation cancellation,
                              @Nullable OrderPayment payment, @Nullable OrderShipment shipment,
                              boolean awaitingPayment, Actions actions) {

    /**
     * 画面の操作の可否
     *
     * @param cancellationRequestable 会員がキャンセルを申請できる
     * @param payable                 会員が支払える（支払待ちで、支払の期限の前）
     * @param cancellationPending     キャンセルの申請を承認・却下できる
     * @param cancellable             運営者がキャンセルできる
     * @param shippable               出荷を登録できる
     * @param cancelAsksRestock       運営者のキャンセルで、在庫に戻すかを聞く
     */
    public record Actions(boolean cancellationRequestable, boolean payable, boolean cancellationPending,
                          boolean cancellable, boolean shippable, boolean cancelAsksRestock) {
    }
}
