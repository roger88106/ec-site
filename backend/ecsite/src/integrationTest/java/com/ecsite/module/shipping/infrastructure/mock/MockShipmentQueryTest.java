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
 * mock の最近の出荷の問い合わせの統合テスト。<br />
 * 各テストは巻き戻すので、データは残らない。
 */
@IntegrationTest
@Transactional
class MockShipmentQueryTest {

    @Autowired
    MockShipmentQuery query;

    @Autowired
    ShipmentRepository repository;

    @Test
    @DisplayName("出荷を新しい順に、件数の上限まで読める")
    void findsRecentFirst() {
        // 時刻は、表にありうるどの出荷（MockCarrierTest の 2095 年を含む）より後にして、件数の上限で自分の 2 件だけが返るようにする
        var older = Shipment.create(UUID.randomUUID(), UUID.randomUUID(), Carrier.YAMATO, TrackingNumber.of("111"),
                Instant.parse("3000-01-01T00:00:00Z"));
        var newer = Shipment.create(UUID.randomUUID(), UUID.randomUUID(), Carrier.YAMATO, TrackingNumber.of("222"),
                Instant.parse("3000-01-02T00:00:00Z"));
        repository.insert(older);
        repository.insert(newer);

        assertThat(query.findRecent(2)).extracting(Shipment::id).containsExactly(newer.id(), older.id());
        assertThat(query.findRecent(1)).extracting(Shipment::id).containsExactly(newer.id());
    }
}
