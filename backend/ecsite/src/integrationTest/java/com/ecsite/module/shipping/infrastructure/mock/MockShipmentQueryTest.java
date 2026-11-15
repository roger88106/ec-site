package com.ecsite.module.shipping.infrastructure.mock;

import com.ecsite.IntegrationTest;
import com.ecsite.module.shipping.application.port.ShipmentRepository;
import com.ecsite.module.shipping.domain.Carrier;
import com.ecsite.module.shipping.domain.Shipment;
import com.ecsite.module.shipping.domain.TrackingNumber;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * mock の最近の出荷の問い合わせの統合テスト
 *
 * <p>mock の画面に表示する、最近の出荷の読み出しを確認する。画面そのものは {@code MockCarrierTest} が確認する。
 *
 * <p>問い合わせは表の全体から新しい順に読むので、出荷日時は、表にありうるどの出荷（{@code MockCarrierTest} の 2095 年を含む）より後にする。
 * そうすると、件数の上限の中には自分の出荷だけが入る。各テストは巻き戻すので、データは残らない。
 */
@IntegrationTest
@Transactional
class MockShipmentQueryTest {

    // ============================== 定数 ==============================

    /**
     * 古い方の出荷の出荷日時
     */
    private static final Instant OLDER_SHIPPED_AT = Instant.parse("3000-01-01T00:00:00Z");

    /**
     * 新しい方の出荷の出荷日時
     */
    private static final Instant NEWER_SHIPPED_AT = Instant.parse("3000-01-02T00:00:00Z");

    // ============================== Bean ==============================

    @Autowired
    MockShipmentQuery query;

    @Autowired
    ShipmentRepository repository;

    // ============================== テスト ==============================

    @Test
    @DisplayName("出荷を新しい順に読める")
    void findsRecentFirst() {
        // 準備：出荷日時の違う 2 つの出荷
        var older = insert("111", OLDER_SHIPPED_AT);
        var newer = insert("222", NEWER_SHIPPED_AT);

        // 操作と確認：新しい方が先
        assertThat(query.findRecent(2)).extracting(Shipment::id).containsExactly(newer.id(), older.id());
    }

    @Test
    @DisplayName("件数の上限までしか読まない")
    void findsUpToLimit() {
        // 準備：出荷日時の違う 2 つの出荷
        insert("111", OLDER_SHIPPED_AT);
        var newer = insert("222", NEWER_SHIPPED_AT);

        // 操作と確認：上限が 1 件なら、新しい方だけ
        assertThat(query.findRecent(1)).extracting(Shipment::id).containsExactly(newer.id());
    }

    // ============================== 準備の補助 ==============================

    /**
     * ヤマト運輸の出荷を保存する。
     *
     * @param trackingNumber 送り状番号
     * @param shippedAt      出荷日時
     * @return 保存した出荷
     */
    private Shipment insert(String trackingNumber, Instant shippedAt) {
        var shipment = Shipment.create(UUID.randomUUID(), UUID.randomUUID(), Carrier.YAMATO, TrackingNumber.of(trackingNumber),
                shippedAt);
        repository.insert(shipment);
        return shipment;
    }
}
