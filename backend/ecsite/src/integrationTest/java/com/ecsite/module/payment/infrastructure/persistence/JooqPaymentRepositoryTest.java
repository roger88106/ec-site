package com.ecsite.module.payment.infrastructure.persistence;

import com.ecsite.common.model.value.Money;
import com.ecsite.module.payment.application.port.PaymentQueryRepository;
import com.ecsite.module.payment.domain.payment.Payment;
import com.ecsite.module.payment.domain.payment.PaymentPage;
import com.ecsite.module.payment.domain.payment.PaymentStatus;
import com.ecsite.module.payment.domain.payment.port.PaymentRepository;
import com.ecsite.module.payment.event.PaymentAuthorized;
import com.ecsite.module.payment.event.PaymentExpired;
import com.ecsite.support.async.AsyncEvents;
import com.ecsite.support.config.IntegrationTest;
import com.ecsite.support.persistence.QueryPlans;
import org.jooq.DSLContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.ecsite.jooq.payment.Tables.PAYMENT_;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 支払のリポジトリの統合テスト
 *
 * <p>行のロックを確認するテスト以外は、テストごとにロールバックする。
 */
@IntegrationTest
@RecordApplicationEvents
@Transactional
class JooqPaymentRepositoryTest {

    // ============================== 定数 ==============================

    /**
     * 支払の期限
     *
     * <p>日本時間の 2026-10-09 00:30。
     */
    private static final Instant DEADLINE = Instant.parse("2026-10-08T15:30:00Z");

    /**
     * 与信の時刻（期限の 10 分前）
     */
    private static final Instant AUTHORIZED_AT = Instant.parse("2026-10-08T15:20:00Z");

    /**
     * 支払の額
     */
    private static final Money AMOUNT = Money.of(5302);

    /**
     * 開いた支払画面
     */
    private static final PaymentPage PAGE = new PaymentPage("page-1", "https://pay.example.com/page-1");

    /**
     * 与信の参照
     */
    private static final String AUTHORIZATION_REF = "auth-1";

    /**
     * 売上確定を求めた時刻（与信の時刻の後）
     */
    private static final Instant CAPTURE_REQUESTED_AT = Instant.parse("2026-10-08T15:25:00Z");

    /**
     * 1 回更新した後のバージョン（作ったときは 0）
     */
    private static final long VERSION_AFTER_UPDATE = 1L;

    /**
     * 期限切れの検索の現在時刻
     *
     * <p>テーブル全体を検索するので、ロールバックしない他のテストの支払（2026 年）より前の時刻にする。
     */
    private static final Instant SEARCH_NOW = Instant.parse("1980-01-01T00:00:00Z");

    // ============================== Bean ==============================

    /**
     * 確認するリポジトリ
     */
    @Autowired
    PaymentRepository repository;

    /**
     * 確認する参照のリポジトリ
     */
    @Autowired
    PaymentQueryRepository queryRepository;

    /**
     * テストが直接 DB を読み書きする jOOQ の DSLContext
     */
    @Autowired
    DSLContext dsl;

    /**
     * 別のトランザクションで処理を実行する TransactionTemplate
     */
    @Autowired
    TransactionTemplate transactionTemplate;

    /**
     * 発行されたイベント
     */
    @Autowired
    ApplicationEvents events;

    // ============================== テスト ==============================

    @Nested
    @DisplayName("保存と再構築")
    class SaveAndRestore {

        @Test
        @DisplayName("新しい注文の支払を保存すると、true を返す")
        void insertReturnsTrue() {
            // 準備：新しい注文の未払いの支払
            var payment = payment(UUID.randomUUID(), DEADLINE);

            // 操作と確認：保存すると true
            assertThat(repository.insertIfAbsent(payment)).isTrue();
        }

        @Test
        @DisplayName("保存した支払を、ID から同じ内容で再構築できる")
        void restoresById() {
            // 準備：新しい注文の未払いの支払
            var payment = payment(UUID.randomUUID(), DEADLINE);
            repository.insertIfAbsent(payment);

            // 操作：ID から再構築する
            var restored = repository.findById(payment.id()).orElseThrow();

            // 確認：保存した未払いの支払の内容
            assertUnpaidSaved(restored, payment.id(), payment.orderId());
        }

        @Test
        @DisplayName("保存した支払を、注文 ID から同じ内容で再構築できる")
        void restoresByOrderId() {
            // 準備：新しい注文の未払いの支払
            var payment = payment(UUID.randomUUID(), DEADLINE);
            repository.insertIfAbsent(payment);

            // 操作：注文 ID から再構築する
            var restored = repository.findByOrderId(payment.orderId()).orElseThrow();

            // 確認：保存した未払いの支払の内容
            assertUnpaidSaved(restored, payment.id(), payment.orderId());
        }

        @Test
        @DisplayName("ない ID・注文 ID には、空を返す")
        void returnsEmptyForUnknown() {
            // 操作と確認：ない ID とない注文 ID の、どちらも空
            assertThat(repository.findById(UUID.randomUUID())).isEmpty();
            assertThat(repository.findByOrderId(UUID.randomUUID())).isEmpty();
        }
    }

    @Nested
    @DisplayName("同じ注文の 2 つ目の支払")
    class SecondPayment {

        @Test
        @DisplayName("同じ注文の支払がすでにあれば、保存せずに false を返す")
        void returnsFalse() {
            // 準備：同じ注文の支払が保存されている
            var orderId = UUID.randomUUID();
            repository.insertIfAbsent(payment(orderId, DEADLINE));
            var second = payment(orderId, DEADLINE.plusSeconds(60));

            // 操作と確認：2 つ目の支払を保存すると false
            assertThat(repository.insertIfAbsent(second)).isFalse();
        }

        @Test
        @DisplayName("同じ注文の 2 つ目の支払は保存されず、先の支払が残る")
        void keepsFirst() {
            // 準備：同じ注文の支払が保存されている
            var orderId = UUID.randomUUID();
            var first = payment(orderId, DEADLINE);
            repository.insertIfAbsent(first);
            var second = payment(orderId, DEADLINE.plusSeconds(60));

            // 操作：2 つ目の支払を保存する
            repository.insertIfAbsent(second);

            // 確認：2 つ目の支払はなく、注文の支払は先の支払
            assertThat(repository.findById(second.id())).isEmpty();
            assertThat(repository.findByOrderId(orderId).orElseThrow().id()).isEqualTo(first.id());
        }
    }

    @Nested
    @DisplayName("更新")
    class Update {

        @Test
        @DisplayName("支払画面を開いて与信を受けた支払を更新すると、状態・画面・与信の参照・与信の時刻を保存する")
        void savesAuthorization() {
            // 準備：保存した未払いの支払
            var payment = savedPayment();

            // 操作：与信を受けた支払で更新する
            repository.update(authorized(payment));

            // 確認：再取得した支払に、与信を受けた内容が保存されている
            assertAuthorizationSaved(repository.findById(payment.id()).orElseThrow());
        }

        @Test
        @DisplayName("支払を更新すると、バージョンが 1 上がる")
        void incrementsVersion() {
            // 準備：保存した未払いの支払
            var payment = savedPayment();

            // 操作：与信を受けた支払で更新する
            repository.update(authorized(payment));

            // 確認：再取得した支払のバージョンが 1 上がっている
            assertThat(repository.findById(payment.id()).orElseThrow().toSnapshot().version()).isEqualTo(VERSION_AFTER_UPDATE);
        }

        @Test
        @DisplayName("売上確定を求めた時刻を保存し、消した後は空に戻る")
        void savesAndClearsCaptureRequest() {
            // 準備：与信済みで保存した支払
            var payment = savedPayment();
            repository.update(authorized(payment));

            // 操作：売上確定を求めて保存する
            var requested = repository.lockById(payment.id()).orElseThrow();
            requested.requestCapture(CAPTURE_REQUESTED_AT);
            repository.update(requested);

            // 確認：再取得した支払が、その時刻を持つ
            var restored = repository.findById(payment.id()).orElseThrow();
            assertThat(restored.captureRequestedAt()).contains(CAPTURE_REQUESTED_AT);

            // 操作：時刻を消して保存する
            var cleared = repository.lockById(payment.id()).orElseThrow();
            cleared.clearCaptureRequest(CAPTURE_REQUESTED_AT);
            repository.update(cleared);

            // 確認：再取得した支払に、時刻がない
            assertThat(repository.findById(payment.id()).orElseThrow().captureRequestedAt()).isEmpty();
        }

        @Test
        @DisplayName("売上確定を求めた時刻は、売上確定済みに進んでも保存されたまま")
        void keepsCaptureRequestAfterCapture() {
            // 準備：売上確定を求めて保存した与信済みの支払
            var payment = savedPayment();
            repository.update(authorized(payment));
            var requested = repository.lockById(payment.id()).orElseThrow();
            requested.requestCapture(CAPTURE_REQUESTED_AT);
            repository.update(requested);

            // 操作：売上確定を反映して保存する
            var captured = repository.lockById(payment.id()).orElseThrow();
            captured.capture(CAPTURE_REQUESTED_AT.plusSeconds(1));
            repository.update(captured);

            // 確認：売上確定済みで、時刻が残る
            var restored = repository.findById(payment.id()).orElseThrow();
            assertThat(restored.status()).isEqualTo(PaymentStatus.CAPTURED);
            assertThat(restored.captureRequestedAt()).contains(CAPTURE_REQUESTED_AT);
        }

        @Test
        @DisplayName("与信を受けた支払を更新すると、この注文の与信の成功のイベントが発行される")
        void publishesAuthorizedEvent() {
            // 準備：保存した未払いの支払
            var payment = savedPayment();

            // 操作：与信を受けた支払で更新する
            repository.update(authorized(payment));

            // 確認：与信の成功のイベントは、この注文のものだけ
            assertThat(events.stream(PaymentAuthorized.class)).extracting(PaymentAuthorized::orderId)
                    .containsExactly(payment.orderId());
        }

        @Test
        @DisplayName("期限切れにした支払を更新すると、支払われなかった状態になる")
        void savesExpiry() {
            // 準備：保存した未払いの支払
            var payment = savedPayment();

            // 操作：期限切れにした支払で更新する
            repository.update(expired(payment));

            // 確認：再取得した支払が支払なし
            assertThat(repository.findById(payment.id()).orElseThrow().status()).isEqualTo(PaymentStatus.NOT_PAID);
        }

        @Test
        @DisplayName("期限切れにした支払を更新すると、この注文の期限切れのイベントが発行される")
        void publishesExpiredEvent() {
            // 準備：保存した未払いの支払
            var payment = savedPayment();

            // 操作：期限切れにした支払で更新する
            repository.update(expired(payment));

            // 確認：この注文の期限切れのイベントだけ
            assertThat(events.stream(PaymentExpired.class)).extracting(PaymentExpired::orderId)
                    .containsExactly(payment.orderId());
        }
    }

    @Nested
    @DisplayName("行ロックの取得")
    class Lock {

        @Test
        @DisplayName("支払を ID で行ロックして取得すると、保存した支払を返す")
        void locksById() {
            // 準備：支払画面を開いて保存した支払
            var payment = savedPaymentWithPage();

            // 操作と確認：ID で行ロックすると、保存した支払
            assertThat(repository.lockById(payment.id()).orElseThrow().id()).isEqualTo(payment.id());
        }

        @Test
        @DisplayName("支払を注文 ID で行ロックして取得すると、保存した支払を返す")
        void locksByOrderId() {
            // 準備：支払画面を開いて保存した支払
            var payment = savedPaymentWithPage();

            // 操作と確認：注文 ID で行ロックすると、保存した支払
            assertThat(repository.lockByOrderId(payment.orderId()).orElseThrow().id()).isEqualTo(payment.id());
        }

        @Test
        @DisplayName("支払を支払画面の ID で行ロックして取得すると、保存した支払を返す")
        void locksByPageId() {
            // 準備：支払画面を開いて保存した支払
            var payment = savedPaymentWithPage();

            // 操作と確認：支払画面の ID で行ロックすると、保存した支払
            assertThat(repository.lockByPageId(PAGE.id()).orElseThrow().id()).isEqualTo(payment.id());
        }

        @Test
        @DisplayName("ない ID・注文 ID・支払画面の ID で行ロックすると、空を返す")
        void returnsEmptyForUnknown() {
            // 操作と確認：ない ID、ない注文 ID、ない支払画面の ID の、どれも空
            assertThat(repository.lockById(UUID.randomUUID())).isEmpty();
            assertThat(repository.lockByOrderId(UUID.randomUUID())).isEmpty();
            assertThat(repository.lockByPageId("page-none")).isEmpty();
        }

        @Test
        @Transactional(propagation = Propagation.NOT_SUPPORTED)
        @DisplayName("支払を行ロックして取得している間は、別のトランザクションが同じ支払の行をロックできない")
        void blocksOtherTransactionWhileLocked() {
            // 準備：ロールバックしないので、このテストで作った支払の行だけを使う
            var payment = payment(UUID.randomUUID(), DEADLINE);
            transactionTemplate.executeWithoutResult(status -> repository.insertIfAbsent(payment));
            var executor = Executors.newSingleThreadExecutor();
            try {
                transactionTemplate.executeWithoutResult(status -> {
                    // 操作：行ロックを取得し、その間に別のトランザクションで、待たずにロックを求める
                    assertThat(repository.lockById(payment.id())).isPresent();
                    var other = executor.submit(() -> transactionTemplate.executeWithoutResult(inner ->
                            dsl.selectFrom(PAYMENT_).where(PAYMENT_.ID.eq(payment.id())).forUpdate().noWait().fetch()));

                    // 確認：別のトランザクションはロックを取れない
                    assertThatThrownBy(() -> other.get(AsyncEvents.TIMEOUT.toSeconds(), TimeUnit.SECONDS))
                            .isInstanceOf(ExecutionException.class)
                            .hasMessageContaining("could not obtain lock");
                });
            } finally {
                executor.shutdownNow();
            }
        }
    }

    @Nested
    @DisplayName("注文 ID でまとめた取得")
    class FindByOrderIds {

        @Test
        @DisplayName("注文の支払をまとめて取得でき、支払のない注文は含まない")
        void findsPaymentsOfOrders() {
            // 準備：取得する 2 件と、取得しない 1 件
            var first = payment(UUID.randomUUID(), DEADLINE);
            var second = payment(UUID.randomUUID(), DEADLINE);
            repository.insertIfAbsent(first);
            repository.insertIfAbsent(second);
            repository.insertIfAbsent(payment(UUID.randomUUID(), DEADLINE));

            // 操作：支払のない注文を加えて取得する
            var found = repository.findByOrderIds(List.of(first.orderId(), second.orderId(), UUID.randomUUID()));

            // 確認：取得する 2 件だけ
            assertThat(found).extracting(Payment::id).containsExactlyInAnyOrder(first.id(), second.id());
        }

        @Test
        @DisplayName("注文 ID がなければ、空を返す")
        void returnsEmptyForNoOrders() {
            // 操作と確認：注文 ID の空の一覧で取得すると、空
            assertThat(repository.findByOrderIds(List.of())).isEmpty();
        }
    }

    @Nested
    @DisplayName("期限を過ぎた未払いの支払の検索")
    class PastDeadline {

        @Test
        @DisplayName("期限を過ぎた未払いの支払を、期限の古い順に取得する。期限ちょうどの支払も含む")
        void findsInDeadlineOrder() {
            // 準備：期限が古い順に 3 件を、順を入れ替えて保存する
            var oldest = payment(UUID.randomUUID(), SEARCH_NOW.minusSeconds(300));
            var older = payment(UUID.randomUUID(), SEARCH_NOW.minusSeconds(200));
            var onDeadline = payment(UUID.randomUUID(), SEARCH_NOW);
            insertAll(older, onDeadline, oldest);

            // 操作と確認：3 件が期限の古い順に並ぶ
            assertThat(repository.findUnpaidPastDeadline(SEARCH_NOW, 10)).extracting(Payment::id)
                    .containsExactly(oldest.id(), older.id(), onDeadline.id());
        }

        @Test
        @DisplayName("期限を過ぎた未払いの支払を、上限の件数まで取得する")
        void findsUpToLimit() {
            // 準備：期限を過ぎた 3 件
            var oldest = payment(UUID.randomUUID(), SEARCH_NOW.minusSeconds(300));
            var older = payment(UUID.randomUUID(), SEARCH_NOW.minusSeconds(200));
            insertAll(older, payment(UUID.randomUUID(), SEARCH_NOW), oldest);

            // 操作と確認：古い 2 件
            assertThat(repository.findUnpaidPastDeadline(SEARCH_NOW, 2)).extracting(Payment::id)
                    .containsExactly(oldest.id(), older.id());
        }

        @Test
        @DisplayName("期限内の支払は取得しない")
        void excludesBeforeDeadline() {
            // 準備：期限を過ぎた 1 件と、期限が 1 秒後の 1 件
            var past = payment(UUID.randomUUID(), SEARCH_NOW.minusSeconds(100));
            insertAll(past, payment(UUID.randomUUID(), SEARCH_NOW.plusSeconds(1)));

            // 操作と確認：期限を過ぎた 1 件だけ
            assertThat(repository.findUnpaidPastDeadline(SEARCH_NOW, 10)).extracting(Payment::id)
                    .containsExactly(past.id());
        }

        @Test
        @DisplayName("未払いでない（期限切れにした）支払は取得しない")
        void excludesNotUnpaid() {
            // 準備：期限を過ぎた 1 件と、期限切れにした 1 件
            var past = payment(UUID.randomUUID(), SEARCH_NOW.minusSeconds(200));
            var expired = payment(UUID.randomUUID(), SEARCH_NOW.minusSeconds(100));
            insertAll(past, expired);
            repository.update(expired(expired, SEARCH_NOW));

            // 操作と確認：期限切れにしていない 1 件だけ
            assertThat(repository.findUnpaidPastDeadline(SEARCH_NOW, 10)).extracting(Payment::id)
                    .containsExactly(past.id());
        }

        @Test
        @DisplayName("AC-311 与信の反映を待つ支払が上限の件数より多く、期限が古くても、それより新しい期限切れの支払を取得する")
        void excludesAwaitingAuthorization() {
            // 準備：上限（2 件）より多い 3 件の待つ支払と、それより期限が新しい 1 件
            var awaiting = new ArrayList<Payment>();
            for (int i = 0; i < 3; i++) {
                awaiting.add(payment(UUID.randomUUID(), SEARCH_NOW.minusSeconds(300 - i)));
            }
            var newer = payment(UUID.randomUUID(), SEARCH_NOW.minusSeconds(100));
            awaiting.forEach(repository::insertIfAbsent);
            repository.insertIfAbsent(newer);
            awaiting.forEach(payment -> awaitAuthorization(payment, SEARCH_NOW.minusSeconds(10)));

            // 操作と確認：待つ支払を除いて、新しい支払を取得する
            assertThat(repository.findUnpaidPastDeadline(SEARCH_NOW, 2)).extracting(Payment::id)
                    .containsExactly(newer.id());
        }
    }

    @Nested
    @DisplayName("与信の反映を待つ支払の検索")
    class AwaitingAuthorization {

        @Test
        @DisplayName("AC-409-3 与信の反映を待つ未払いの支払を、待ち始めた時刻の古い順に取得し、待っていない・未払いでない支払は除く")
        void findsInWaitingOrder() {
            // 準備：待つ 2 件、待っていない 1 件、待った後に与信済みになった 1 件
            var waiting = awaitingPayments();

            // 操作：待つ支払を、上限なしで取得する
            var found = repository.findUnpaidAwaitingAuthorization(Integer.MAX_VALUE);

            // 確認：このテストの支払のうち、待つ 2 件だけが古い順に
            assertThat(found).extracting(Payment::id).filteredOn(waiting.mine()::contains)
                    .containsExactly(waiting.oldest().id(), waiting.older().id());
        }

        @Test
        @DisplayName("AC-409-3 与信の反映を待つ未払いの支払を、上限の件数まで取得する")
        void findsUpToLimit() {
            // 準備：待つ 2 件（最も古いのは、他のテストの支払より古い 1980 年から待つ）
            var waiting = awaitingPayments();

            // 操作と確認：上限 1 件で取得すると、最も古くから待つ 1 件
            assertThat(repository.findUnpaidAwaitingAuthorization(1)).extracting(Payment::id)
                    .containsExactly(waiting.oldest().id());
        }
    }

    @Nested
    @DisplayName("長く与信の反映を待つ支払の件数")
    class CountAwaitingAuthorization {

        /**
         * 数える基準の時刻（この時刻までに待ち始めた支払を数える）
         */
        private static final Instant LATEST_AWAITING_SINCE = SEARCH_NOW.minusSeconds(3600);

        @Test
        @DisplayName("AC-312 基準の時刻ちょうどから待つ支払は数え、その 1 マイクロ秒後から待つ支払は数えない")
        void countsUntilLatestInclusive() {
            // 準備：他のテストの支払を含む今の件数と、基準の時刻ちょうどから待つ支払、その 1 マイクロ秒後から待つ支払
            var before = queryRepository.countUnpaidAwaitingAuthorizationUntil(LATEST_AWAITING_SINCE);
            var atLatest = payment(UUID.randomUUID(), DEADLINE);
            var afterLatest = payment(UUID.randomUUID(), DEADLINE);
            insertAll(atLatest, afterLatest);
            awaitAuthorization(atLatest, LATEST_AWAITING_SINCE);
            awaitAuthorization(afterLatest, LATEST_AWAITING_SINCE.plus(Duration.ofNanos(1_000)));

            // 操作：基準の時刻までに待ち始めた支払を数える
            var count = queryRepository.countUnpaidAwaitingAuthorizationUntil(LATEST_AWAITING_SINCE);

            // 確認：増えたのは、ちょうどの 1 件だけ
            assertThat(count - before).isEqualTo(1);
        }

        @Test
        @DisplayName("AC-312 待った後に与信済みになった支払は数えない")
        void excludesAuthorized() {
            // 準備：他のテストの支払を含む今の件数と、基準の時刻から待ち、その後に与信済みになった支払
            var before = queryRepository.countUnpaidAwaitingAuthorizationUntil(LATEST_AWAITING_SINCE);
            var authorized = payment(UUID.randomUUID(), DEADLINE);
            insertAll(authorized);
            awaitAuthorization(authorized, LATEST_AWAITING_SINCE);
            var loaded = repository.findById(authorized.id()).orElseThrow();
            loaded.authorize(AUTHORIZATION_REF, LATEST_AWAITING_SINCE.plusSeconds(1));
            repository.update(loaded);

            // 操作：基準の時刻までに待ち始めた支払を数える
            var count = queryRepository.countUnpaidAwaitingAuthorizationUntil(LATEST_AWAITING_SINCE);

            // 確認：件数は増えない
            assertThat(count).isEqualTo(before);
        }
    }

    @Nested
    @DisplayName("同じ期限・同じ待ち始めた時刻の支払の並び順")
    class TieOrder {

        @Test
        @DisplayName("DC-549-3 同じ期限の支払は、支払 ID の順に取得する（挿入の順とは逆の ID の順）")
        void pastDeadlineTiesInIdOrder() {
            // 準備：同じ期限で、挿入の順とは逆の ID の順（大きい ID から保存する）の 3 件
            var tied = SEARCH_NOW.minusSeconds(500);
            var ids = tiedIds();
            insertAll(paymentWithId(ids.get(2), tied), paymentWithId(ids.get(1), tied), paymentWithId(ids.get(0), tied));

            // 操作と確認：支払 ID の小さい順に並ぶ
            assertThat(repository.findUnpaidPastDeadline(SEARCH_NOW, 10)).extracting(Payment::id)
                    .containsExactlyElementsOf(ids);
        }

        @Test
        @DisplayName("DC-549-3 同じ時刻から待つ支払は、支払 ID の順に取得する（挿入の順とは逆の ID の順）")
        void awaitingTiesInIdOrder() {
            // 準備：同じ時刻から待つ、挿入の順とは逆の ID の順（大きい ID から保存する）の 3 件
            var since = SEARCH_NOW.minusSeconds(500);
            var ids = tiedIds();
            var payments = List.of(paymentWithId(ids.get(2), DEADLINE), paymentWithId(ids.get(1), DEADLINE),
                    paymentWithId(ids.get(0), DEADLINE));
            payments.forEach(repository::insertIfAbsent);
            payments.forEach(payment -> awaitAuthorization(payment, since));

            // 操作と確認：待ち始めた時刻が最も古い 3 件が、支払 ID の小さい順に並ぶ
            assertThat(repository.findUnpaidAwaitingAuthorization(3)).extracting(Payment::id)
                    .containsExactlyElementsOf(ids);
        }
    }

    @Nested
    @DisplayName("検索が使う索引")
    class IndexUsage {

        /**
         * 期限切れの支払の検索用の部分索引の名前
         */
        private static final String DEADLINE_INDEX = "payment_unpaid_deadline_id_idx";

        /**
         * 与信の反映を待つ支払の検索と件数の集計用の部分索引の名前
         */
        private static final String AWAITING_INDEX = "payment_unpaid_awaiting_authorization_since_id_idx";

        @Autowired
        ApplicationEventPublisher eventPublisher;

        @Autowired
        Clock clock;

        @Test
        @DisplayName("DC-549-1 期限切れの支払の検索は、期限の部分索引を、範囲の条件で使う")
        void pastDeadlineUsesDeadlineIndex() {
            // 操作：期限切れの支払を検索し、実行計画を取る
            var plans = QueryPlans.capture(dsl, recording ->
                    new JooqPaymentRepository(recording, eventPublisher, clock).findUnpaidPastDeadline(SEARCH_NOW, 10));

            // 確認：期限の部分索引の範囲の条件に、期限の列が出る
            assertThat(plans).as("実行されたクエリの実行計画").hasSize(1);
            assertThat(plans.get(0))
                    .as("期限切れの支払の検索の実行計画（索引 %s の範囲の条件を使うはず）%n%s", DEADLINE_INDEX, plans.get(0))
                    .containsPattern(QueryPlans.indexRangeCondition(DEADLINE_INDEX, "deadline"));
        }

        @Test
        @DisplayName("DC-549-2 長く与信の反映を待つ支払の件数の集計は、待ち始めた時刻の部分索引を、範囲の条件で使う")
        void countUsesAwaitingIndex() {
            // 操作：件数を集計し、実行計画を取る
            var plans = QueryPlans.capture(dsl, recording ->
                    new JooqPaymentQueryRepository(recording).countUnpaidAwaitingAuthorizationUntil(SEARCH_NOW));

            // 確認：待ち始めた時刻の部分索引の範囲の条件に、待ち始めた時刻の列が出る
            assertThat(plans).as("実行されたクエリの実行計画").hasSize(1);
            assertThat(plans.get(0))
                    .as("件数の集計の実行計画（索引 %s の範囲の条件を使うはず）%n%s", AWAITING_INDEX, plans.get(0))
                    .containsPattern(QueryPlans.indexRangeCondition(AWAITING_INDEX, "awaiting_authorization_since"));
        }

        @Test
        @DisplayName("DC-549-2 与信の反映を待つ支払の検索は、待ち始めた時刻の部分索引を使い、シーケンシャルスキャンにならない")
        void awaitingSearchUsesAwaitingIndex() {
            // 操作：与信の反映を待つ支払を検索し、実行計画を取る（範囲の条件はないので、索引の名前だけを見る）
            var plans = QueryPlans.capture(dsl, recording ->
                    new JooqPaymentRepository(recording, eventPublisher, clock).findUnpaidAwaitingAuthorization(10));

            // 確認：待ち始めた時刻の部分索引を使い、シーケンシャルスキャンがない
            assertThat(plans).as("実行されたクエリの実行計画").hasSize(1);
            assertThat(plans.get(0))
                    .as("与信の反映を待つ支払の検索の実行計画（索引 %s を使うはず）%n%s", AWAITING_INDEX, plans.get(0))
                    .contains(AWAITING_INDEX)
                    .doesNotContain("Seq Scan");
        }
    }

    // ============================== 確認の補助 ==============================

    /**
     * 作ったまま保存した未払いの支払（{@link #payment(UUID, Instant)}）を、そのまま再構築したことを確認する。
     *
     * <p>確認すること（ID のほかの期待値は定数）：
     * <ul>
     *   <li>支払 ID と注文 ID が保存したもの</li>
     *   <li>金額が {@link #AMOUNT}、支払の期限が {@link #DEADLINE}</li>
     *   <li>状態が未払い</li>
     *   <li>支払画面・与信の参照・与信の時刻・与信の反映を待ち始めた時刻・売上確定を求めた時刻がない</li>
     *   <li>バージョンが 0</li>
     * </ul>
     *
     * @param restored  再構築した支払
     * @param paymentId 保存した支払 ID（実行ごとに作るので、定数にできない）
     * @param orderId   保存した注文 ID（実行ごとに作るので、定数にできない）
     */
    private static void assertUnpaidSaved(Payment restored, UUID paymentId, UUID orderId) {
        var snapshot = restored.toSnapshot();
        assertThat(snapshot.id()).isEqualTo(paymentId);
        assertThat(snapshot.orderId()).isEqualTo(orderId);
        assertThat(snapshot.amount()).isEqualTo(AMOUNT);
        assertThat(snapshot.deadline()).isEqualTo(DEADLINE);
        assertThat(snapshot.status()).isEqualTo(PaymentStatus.UNPAID);
        assertThat(snapshot.page()).isNull();
        assertThat(snapshot.authorizationRef()).isNull();
        assertThat(snapshot.authorizedAt()).isNull();
        assertThat(snapshot.awaitingAuthorizationSince()).isNull();
        assertThat(snapshot.captureRequestedAt()).isNull();
        assertThat(snapshot.version()).isZero();
    }

    /**
     * 与信を受けた支払（{@link #authorized(Payment)}）の更新が保存されたことを確認する。
     *
     * <p>確認すること（期待値は定数）：
     * <ul>
     *   <li>状態が与信済み</li>
     *   <li>支払画面</li>
     *   <li>与信の参照</li>
     *   <li>与信の時刻</li>
     * </ul>
     *
     * @param restored 再取得した支払
     */
    private static void assertAuthorizationSaved(Payment restored) {
        var snapshot = restored.toSnapshot();
        assertThat(snapshot.status()).isEqualTo(PaymentStatus.AUTHORIZED);
        assertThat(snapshot.page()).isEqualTo(PAGE);
        assertThat(snapshot.authorizationRef()).isEqualTo(AUTHORIZATION_REF);
        assertThat(snapshot.authorizedAt()).isEqualTo(AUTHORIZED_AT);
    }

    // ============================== 準備の補助 ==============================

    /**
     * 与信の反映を待つ支払の検索のテストの支払
     *
     * @param oldest 最も古くから待つ支払
     * @param older  次に古くから待つ支払
     * @param mine   このテストで保存した全ての支払の ID
     */
    private record AwaitingPayments(Payment oldest, Payment older, List<UUID> mine) {
    }

    /**
     * 指定した注文と期限の未払いの支払を作る。
     *
     * @param orderId  注文 ID
     * @param deadline 期限
     * @return 支払
     */
    private static Payment payment(UUID orderId, Instant deadline) {
        return Payment.create(UUID.randomUUID(), orderId, AMOUNT, deadline);
    }

    /**
     * 指定した支払 ID と期限の未払いの支払を作る。
     *
     * @param id       支払 ID
     * @param deadline 期限
     * @return 支払
     */
    private static Payment paymentWithId(UUID id, Instant deadline) {
        return Payment.create(id, UUID.randomUUID(), AMOUNT, deadline);
    }

    /**
     * 同じ期限（同じ待ち始めた時刻）の支払の並び順を確認するための、小さい順の支払 ID 3 件を返す。
     *
     * @return 支払 ID（小さい順）
     */
    private static List<UUID> tiedIds() {
        return List.of(
                UUID.fromString("00000000-0000-0000-0000-000000000001"),
                UUID.fromString("00000000-0000-0000-0000-000000000002"),
                UUID.fromString("00000000-0000-0000-0000-000000000003"));
    }

    /**
     * 未払いの支払を保存する。
     *
     * @return 保存した支払
     */
    private Payment savedPayment() {
        var payment = payment(UUID.randomUUID(), DEADLINE);
        repository.insertIfAbsent(payment);
        return payment;
    }

    /**
     * 支払画面を開いた未払いの支払を保存する。
     *
     * @return 保存した支払
     */
    private Payment savedPaymentWithPage() {
        var payment = savedPayment();
        var loaded = repository.findById(payment.id()).orElseThrow();
        loaded.openPage(PAGE);
        repository.update(loaded);
        return payment;
    }

    /**
     * 保存した支払を行ロックして読み、支払画面を開いて与信を受けた形にする（保存はしない）。
     *
     * @param payment 保存した支払
     * @return 与信を受けた支払
     */
    private Payment authorized(Payment payment) {
        var loaded = repository.lockById(payment.id()).orElseThrow();
        loaded.openPage(PAGE);
        loaded.authorize(AUTHORIZATION_REF, AUTHORIZED_AT);
        return loaded;
    }

    /**
     * 保存した支払を読み、期限の 1 秒後に期限切れにした形にする（保存はしない）。
     *
     * @param payment 保存した支払
     * @return 期限切れにした支払
     */
    private Payment expired(Payment payment) {
        return expired(payment, DEADLINE.plusSeconds(1));
    }

    /**
     * 保存した支払を読み、指定の時刻に期限切れにした形にする（保存はしない）。
     *
     * @param payment 保存した支払
     * @param now     期限切れにする時刻
     * @return 期限切れにした支払
     */
    private Payment expired(Payment payment, Instant now) {
        var loaded = repository.findById(payment.id()).orElseThrow();
        assertThat(loaded.expire(now)).isTrue();
        return loaded;
    }

    /**
     * 支払をまとめて保存する。
     *
     * @param payments 支払
     */
    private void insertAll(Payment... payments) {
        for (var payment : payments) {
            repository.insertIfAbsent(payment);
        }
    }

    /**
     * 保存した支払を、与信の反映を待つ支払にする。
     *
     * @param payment 保存した支払
     * @param since   待ち始めた時刻
     */
    private void awaitAuthorization(Payment payment, Instant since) {
        var loaded = repository.findById(payment.id()).orElseThrow();
        assertThat(loaded.awaitAuthorization(since)).isTrue();
        repository.update(loaded);
    }

    /**
     * 与信の反映を待つ支払の検索のための、4 件の支払を保存する。
     *
     * <p>待つ 2 件（{@link #SEARCH_NOW} から待つ支払と、その 100 秒後から待つ支払）、待っていない 1 件、
     * 待った後に与信済みになった 1 件。テーブル全体を検索するので、待ち始めた時刻は他のテストより前にする。
     *
     * @return 保存した支払
     */
    private AwaitingPayments awaitingPayments() {
        var oldest = payment(UUID.randomUUID(), DEADLINE);
        var older = payment(UUID.randomUUID(), DEADLINE);
        var notAwaiting = payment(UUID.randomUUID(), DEADLINE);
        var authorized = payment(UUID.randomUUID(), DEADLINE);
        insertAll(older, notAwaiting, oldest, authorized);
        awaitAuthorization(older, SEARCH_NOW.plusSeconds(100));
        awaitAuthorization(oldest, SEARCH_NOW);
        awaitAuthorization(authorized, SEARCH_NOW.plusSeconds(50));
        var authorizedLoaded = repository.findById(authorized.id()).orElseThrow();
        authorizedLoaded.authorize(AUTHORIZATION_REF, SEARCH_NOW.plusSeconds(60));
        repository.update(authorizedLoaded);
        return new AwaitingPayments(oldest, older, List.of(oldest.id(), older.id(), notAwaiting.id(), authorized.id()));
    }
}
