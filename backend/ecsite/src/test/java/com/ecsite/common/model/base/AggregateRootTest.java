package com.ecsite.common.model.base;

import com.ecsite.common.error.CommonErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static com.ecsite.BusinessErrorAssertions.assertBusinessError;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * 集約の基底クラスの単体テスト
 */
class AggregateRootTest {

    @Test
    @DisplayName("登録したイベントを、登録した順に取り出せる")
    void pullsRegisteredEventsInOrder() {
        var aggregate = new SampleAggregate(0);
        var first = new SampleEvent(UUID.randomUUID(), Instant.EPOCH);
        var second = new SampleEvent(UUID.randomUUID(), Instant.EPOCH);

        aggregate.doSomething(first);
        aggregate.doSomething(second);

        assertThat(aggregate.pullEvents()).containsExactly(first, second);
    }

    @Test
    @DisplayName("取り出したイベントは集約から消える（同じイベントを二度発行しない）")
    void clearsEventsAfterPull() {
        var aggregate = new SampleAggregate(0);
        aggregate.doSomething(new SampleEvent(UUID.randomUUID(), Instant.EPOCH));

        aggregate.pullEvents();

        assertThat(aggregate.pullEvents()).isEmpty();
    }

    @Test
    @DisplayName("読んだ時点のバージョンと今のバージョンが同じなら、何もしない")
    void acceptsSameVersion() {
        var aggregate = new SampleAggregate(3);

        assertThatCode(() -> aggregate.requireVersion(3)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("バージョンが違えば、更新の競合の業務のエラー")
    void rejectsDifferentVersion() {
        var aggregate = new SampleAggregate(4);

        assertBusinessError(() -> aggregate.requireVersion(3), CommonErrorCode.CONCURRENT_MODIFICATION);
    }

    // ============================== テスト用の集約とイベント ==============================

    /**
     * テスト用の集約
     */
    static class SampleAggregate extends AggregateRoot {

        /**
         * バージョン
         */
        private final long version;

        SampleAggregate(long version) {
            this.version = version;
        }

        @Override
        public long version() {
            return version;
        }

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
