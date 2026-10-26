package com.ecsite.module.shipping.infrastructure.mock;

import com.ecsite.module.shipping.application.port.ShipmentRepository;
import com.ecsite.module.shipping.domain.Shipment;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.ecsite.jooq.shipping.Tables.SHIPMENT;

/**
 * mock の物流サービスの画面が使う、最近の出荷の問い合わせ（docs/design/modules/shipping.md「mock」）。<br />
 * 本番の処理は使わないので、出荷のリポジトリの port に置かず、mock の側に置く。物流の mock フラグを立てたときだけ出す。
 * 出荷の再構築（列挙型への変換を含む）は出荷のリポジトリに任せ、ここは並びと件数だけを決める。
 */
@Component
@ConditionalOnBooleanProperty(name = "ecsite.shipping.mock.enabled")
@RequiredArgsConstructor
class MockShipmentQuery {

    /**
     * jOOQ DSL Context
     */
    private final DSLContext dsl;

    /**
     * 出荷のリポジトリ
     */
    private final ShipmentRepository shipmentRepository;

    /**
     * 新しい出荷から順に取得する。
     *
     * @param limit 件数の上限
     * @return 出荷（出荷日時の新しい順）
     */
    List<Shipment> findRecent(int limit) {
        // 注文 ID は出荷の表で一意（shipment_order_id_key）なので、注文 ID から出荷が 1 つに決まる
        var orderIds = dsl.select(SHIPMENT.ORDER_ID)
                .from(SHIPMENT)
                .orderBy(SHIPMENT.SHIPPED_AT.desc(), SHIPMENT.ID)
                .limit(limit)
                .fetch(SHIPMENT.ORDER_ID);
        var byOrderId = shipmentRepository.findAllByOrderIds(orderIds).stream()
                .collect(Collectors.toMap(Shipment::orderId, Function.identity()));
        return orderIds.stream().map(byOrderId::get).toList();
    }
}
