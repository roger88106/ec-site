package com.ecsite.common.model.base;

import com.ecsite.common.error.CommonErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static com.ecsite.support.BusinessErrorAssertions.assertBusinessError;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * 集約の基底クラスの単体テスト
 */
class AggregateRootTest {

    // ============================== テスト ==============================

    @Nested
    @DisplayName("イベント")
    class Events {

        @Test
        @DisplayName("登録したイベントを、登録した順に取り出せる")
        void pullsRegisteredEventsInOrder() {
            // 準備：イベントを 2 件登録する
            var aggregate = new SampleAggregate(0);
            var first = new SampleEvent(UUID.randomUUID(), Instant.EPOCH);
            var second = new SampleEvent(UUID.randomUUID(), Instant.EPOCH);
            aggregate.doSomething(first);
            aggregate.doSomething(second);

            // 操作と確認：登録した順に取り出せる
            assertThat(aggregate.pullEvents()).containsExactly(first, second);
        }

        @Test
        @DisplayName("取り出したイベントは集約から消える（同じイベントを二度発行しない）")
        void clearsEventsAfterPull() {
            // 準備：イベントを登録して取り出す
            var aggregate = new SampleAggregate(0);
            aggregate.doSomething(new SampleEvent(UUID.randomUUID(), Instant.EPOCH));
            aggregate.pullEvents();

            // 操作と確認：もう一度取り出すと空
            assertThat(aggregate.pullEvents()).isEmpty();
        }
    }

    @Nested
    @DisplayName("バージョンの確認")
    class Version {

        @Test
        @DisplayName("読んだ時点のバージョンと今のバージョンが同じなら、何もしない")
        void acceptsSameVersion() {
            // 準備：バージョン 3 の集約
            var aggregate = new SampleAggregate(3);

            // 操作と確認：同じバージョンでは失敗しない
            assertThatCode(() -> aggregate.requireVersion(3)).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("バージョンが違えば、更新の競合の業務のエラー")
        void rejectsDifferentVersion() {
            // 準備：バージョン 4 の集約
            var aggregate = new SampleAggregate(4);

            // 操作と確認：読んだ時点のバージョン 3 では、更新の競合
            assertBusinessError(() -> aggregate.requireVersion(3), CommonErrorCode.CONCURRENT_MODIFICATION);
        }
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
