package com.ecsite.module.cart.event;

import com.ecsite.common.model.id.CartId;
import com.ecsite.support.config.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.modulith.events.core.EventSerializer;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * カート変更のイベントの直列化のテスト
 *
 * <p>イベントの記録に、カートのバージョンを加える前に書かれたイベントが残っていても、読み込めることを確認する。
 */
@IntegrationTest
class CartChangedSerializationTest {

    @Autowired
    EventSerializer serializer;

    @Test
    @DisplayName("カートのバージョンのない記録は、バージョンが null のイベントとして読み込める")
    void readsEventWithoutCartVersionAsNull() {
        // 準備：カートのバージョンを加える前の記録の形
        var eventId = UUID.randomUUID();
        var cartId = new CartId(UUID.randomUUID());
        var occurredAt = Instant.parse("2026-11-01T00:00:00Z");
        var json = """
                {"eventId":"%s","occurredAt":"%s","cartId":"%s"}""".formatted(eventId, occurredAt, cartId);

        // 操作
        var event = serializer.deserialize(json, CartChanged.class);

        // 確認
        assertThat(event).isEqualTo(new CartChanged(eventId, occurredAt, cartId, null));
    }

    @Test
    @DisplayName("カートのバージョンのあるイベントは、直列化して読み込むと同じになる")
    void roundTripsEventWithCartVersion() {
        // 準備
        var event = CartChanged.of(Instant.parse("2026-11-01T00:00:00Z"), new CartId(UUID.randomUUID()), 7);

        // 操作
        var restored = serializer.deserialize(serializer.serialize(event), CartChanged.class);

        // 確認
        assertThat(restored).isEqualTo(event);
    }
}
