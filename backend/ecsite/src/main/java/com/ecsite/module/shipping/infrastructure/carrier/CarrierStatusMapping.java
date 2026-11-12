package com.ecsite.module.shipping.infrastructure.carrier;

import com.ecsite.module.shipping.domain.Carrier;
import com.ecsite.module.shipping.domain.DeliveryStatus;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;

/**
 * 状態の名前の対応の表
 *
 * <p>物流サービスの状態の名前を、配送状態に対応させる。配送業者ごとの途中の状態の名前は、全て「配送中」にまとめる。
 */
@Component
class CarrierStatusMapping {

    /**
     * 配送業者 → （状態の名前 → 配送状態）。佐川急便と日本郵便は、表示を確認していないので表がない
     */
    private static final Map<Carrier, Map<String, DeliveryStatus>> TABLE = Map.of(
            Carrier.YAMATO, Map.ofEntries(
                    Map.entry("荷物受付", DeliveryStatus.IN_TRANSIT),
                    Map.entry("発送", DeliveryStatus.IN_TRANSIT),
                    Map.entry("作業店通過", DeliveryStatus.IN_TRANSIT),
                    Map.entry("配達中", DeliveryStatus.IN_TRANSIT),
                    Map.entry("持戻", DeliveryStatus.IN_TRANSIT),
                    Map.entry("保管中", DeliveryStatus.IN_TRANSIT),
                    Map.entry("配達完了", DeliveryStatus.DELIVERED),
                    Map.entry("返品", DeliveryStatus.RETURNED)));

    /**
     * 状態の名前を配送状態にする。
     *
     * @param carrier 配送業者
     * @param name    物流サービスの状態の名前
     * @return 配送状態。表にない名前と、表のない配送業者なら空
     */
    Optional<DeliveryStatus> resolve(Carrier carrier, String name) {
        return Optional.ofNullable(TABLE.getOrDefault(carrier, Map.of()).get(name.strip()));
    }
}
