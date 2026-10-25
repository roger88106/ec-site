package com.ecsite.common.model.base;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 集約の基底クラスの単体テスト
 */
class AggregateRootTest {

    @Test
    @DisplayName("登録したイベントを、登録した順に取り出せる")
    void pullsRegisteredEventsInOrder() {
        var aggregate = new SampleAggregate();
        var first = new SampleEvent(UUID.randomUUID(), Instant.EPOCH);
        var second = new SampleEvent(UUID.randomUUID(), Instant.EPOCH);

        aggregate.doSomething(first);
        aggregate.doSomething(second);

        assertThat(aggregate.pullEvents()).containsExactly(first, second);
    }

    @Test
    @DisplayName("取り出したイベントは集約から消える（同じイベントを二度発行しない）")
    void clearsEventsAfterPull() {
        var aggregate = new SampleAggregate();
        aggregate.doSomething(new SampleEvent(UUID.randomUUID(), Instant.EPOCH));

        aggregate.pullEvents();

        assertThat(aggregate.pullEvents()).isEmpty();
    }

    // ============================== テスト用の集約とイベント ==============================

    /**
     * テスト用の集約
     */
    static class SampleAggregate extends AggregateRoot {
        void doSomething(SampleEvent event) {
            registerEvent(event);
        }
    }

    /**
     * テスト用のイベント
     *
     * @param eventId    イベント ID
     * @param occurredAt 発生時刻
     */
    record SampleEvent(UUID eventId, Instant occurredAt) implements DomainEvent {
    }
}
