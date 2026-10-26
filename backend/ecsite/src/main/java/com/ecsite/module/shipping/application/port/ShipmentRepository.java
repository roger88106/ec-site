package com.ecsite.module.shipping.application.port;

import com.ecsite.module.shipping.domain.Carrier;
import com.ecsite.module.shipping.domain.Shipment;
import com.ecsite.module.shipping.domain.TrackingNumber;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * 出荷のリポジトリ
 */
public interface ShipmentRepository {

    /**
     * 出荷を保存する。
     *
     * @param shipment 出荷
     * @throws com.ecsite.common.error.exception.BusinessException 同じ注文にすでに出荷があるとき
     */
    void insert(Shipment shipment);

    /**
     * 注文 ID で出荷をまとめて取得する。
     *
     * @param orderIds 注文 ID
     * @return 出荷。出荷のない注文は含めない
     */
    List<Shipment> findAllByOrderIds(Collection<UUID> orderIds);

    /**
     * 配送業者と送り状番号で出荷を取得し、行をロックする。同じ出荷への通知を直列にするため、トランザクションの中で呼ぶ。
     *
     * @param carrier        配送業者
     * @param trackingNumber 送り状番号
     * @return 出荷。合う出荷がなければ空
     */
    List<Shipment> findAllByTrackingForUpdate(Carrier carrier, TrackingNumber trackingNumber);

    /**
     * 通知で進めた配送状態と、通知を反映した日時を保存する。
     *
     * @param shipment 出荷
     */
    void updateProgress(Shipment shipment);

    /**
     * 配送状態が「返送」の出荷の注文 ID を返す。
     *
     * @return 注文 ID
     */
    List<UUID> findReturnedOrderIds();
}
