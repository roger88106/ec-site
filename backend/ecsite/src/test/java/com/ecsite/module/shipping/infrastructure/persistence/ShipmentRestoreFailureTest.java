package com.ecsite.module.shipping.infrastructure.persistence;

import com.ecsite.RestoreFailures;
import com.ecsite.jooq.shipping.tables.records.ShipmentRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import static com.ecsite.RestoreFailures.RECORDS;
import static com.ecsite.RestoreFailures.assertValueIsHidden;
import static com.ecsite.jooq.shipping.Tables.SHIPMENT;

/**
 * 出荷を DB の値から戻すときの失敗の単体テスト。例外の出力に DB の値が出ないことを確認する。
 */
class ShipmentRestoreFailureTest {

    /**
     * 例外のメッセージに入ると分かる値
     */
    private final String value = RestoreFailures.value();

    @Test
    @DisplayName("配送業者が列挙にないとき、例外の出力に値が出ない")
    void hidesCarrier() {
        assertFindHides(shipment -> shipment.setCarrier(value));
    }

    @Test
    @DisplayName("配送状態が列挙にないとき、例外の出力に値が出ない")
    void hidesStatus() {
        assertFindHides(shipment -> shipment.setStatus(value));
    }

    /**
     * 行の一部を壊して出荷を読み、例外の出力に値が出ないことを確認する。
     *
     * @param breaking 行を壊す処理
     */
    private void assertFindHides(Consumer<ShipmentRecord> breaking) {
        var shipment = RECORDS.newRecord(SHIPMENT);
        shipment.setCarrier("YAMATO");
        shipment.setTrackingNumber("123");
        shipment.setStatus("SHIPPED");
        breaking.accept(shipment);
        var dsl = new RestoreFailures().with("shipment", shipment).dsl();
        var repository = new JooqShipmentRepository(dsl, Clock.systemUTC());

        assertValueIsHidden(() -> repository.findAllByOrderIds(List.of(UUID.randomUUID())), value);
    }
}
