package com.ecsite.module.shipping.infrastructure.carrier;

import com.ecsite.module.shipping.domain.Carrier;
import com.ecsite.module.shipping.domain.DeliveryStatus;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;

/**
 * 物流サービスの状態の名前から、配送状態への対応の表（docs/backend/modules/shipping.md「状態の名前の対応」）。<br />
 * 配送業者ごとの表示の名前を、途中のものはすべて「配送中」にまとめる。表にない名前と、表のない配送業者は、対応なし。
 * 表は用語集の「配送業者の表示と配送状態の対応」と合わせる。
 */
@Component
public class CarrierStatusMapping {

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
     * @return 配送状態。表にない名前なら空
     */
    public Optional<DeliveryStatus> resolve(Carrier carrier, String name) {
        if (name == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(TABLE.getOrDefault(carrier, Map.of()).get(name.strip()));
    }
}
