package com.ecsite.module.notification.infrastructure.directory;

import com.ecsite.module.notification.application.port.ShippingDirectory;
import com.ecsite.module.order.event.OrderShipped;
import com.ecsite.module.shipping.api.ShipmentSummary;
import com.ecsite.module.shipping.api.ShippingApi;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 出荷の腐敗防止層（バックエンドのアーキテクチャの「モジュラーモノリス」、docs/03-機能仕様/notification.md）。<br />
 * 出荷モジュールの API を呼び、配送業者の表示名のキーを得る。
 */
@Component
@RequiredArgsConstructor
class ShippingApiDirectory implements ShippingDirectory {

    /**
     * 出荷の API
     */
    private final ShippingApi shippingApi;

    /**
     * 配送業者の表示名の文言のキーを返す。イベントの配送業者を、出荷モジュールの配送業者に変換して問い合わせる。
     *
     * @param carrier 注文の出荷イベントの配送業者
     * @return 文言のキー
     */
    @Override
    public String nameKey(OrderShipped.Carrier carrier) {
        return shippingApi.carrierNameKey(toShippingCarrier(carrier));
    }

    /**
     * イベントの配送業者を、出荷モジュールの配送業者にする。
     */
    private static ShipmentSummary.Carrier toShippingCarrier(OrderShipped.Carrier carrier) {
        return switch (carrier) {
            case YAMATO -> ShipmentSummary.Carrier.YAMATO;
            case SAGAWA -> ShipmentSummary.Carrier.SAGAWA;
            case JAPAN_POST -> ShipmentSummary.Carrier.JAPAN_POST;
        };
    }
}
