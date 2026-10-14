package com.ecsite.module.payment.domain.payment;

import com.ecsite.common.model.base.AggregateRoot;
import com.ecsite.common.model.value.Money;
import com.ecsite.module.payment.event.PaymentAuthorized;
import com.ecsite.module.payment.event.PaymentExpired;
import jakarta.annotation.Nullable;
import lombok.Getter;
import lombok.NonNull;
import lombok.experimental.Accessors;
import org.jmolecules.ddd.annotation.Identity;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * 支払の Aggregate Root（docs/backend/modules/payment.md「支払（集約）」）。<br />
 * どの操作も冪等で、遷移できないときは何もしない。決済サービスの通知は重ねて届き、順序が入れ替わるため。
 */
@Getter
@Accessors(fluent = true)
@org.jmolecules.ddd.annotation.AggregateRoot
public class Payment extends AggregateRoot {

// ============================== フィールド定義 ==============================

    /**
     * 支払 ID
     */
    @Identity
    private final UUID id;

    /**
     * 注文 ID（注文 1 つにつき支払は 1 つ）
     */
    private final UUID orderId;

    /**
     * 金額（注文の総額）
     */
    private final Money amount;

    /**
     * 支払の期限
     */
    private final Instant deadline;

    /**
     * 状態
     */
    private PaymentStatus status;

    /**
     * 開いている支払の画面。まだ開いていなければ null
     */
    @Nullable
    @Getter(lombok.AccessLevel.NONE)
    private PaymentPage page;

    /**
     * 与信の参照（決済サービスでの与信の ID）。与信がなければ null
     */
    @Nullable
    private String authorizationRef;

    /**
     * 与信の時刻（与信の成功を受けた時刻。与信期限の起点）。与信がなければ null
     */
    @Nullable
    @Getter(lombok.AccessLevel.NONE)
    private Instant authorizedAt;

    /**
     * バージョン（リポジトリが保存するときに上げる）
     */
    private final long version;

// ============================== コンストラクタ ==============================

    /**
     * 全属性コンストラクタ
     */
    private Payment(@NonNull UUID id, @NonNull UUID orderId, @NonNull Money amount, @NonNull Instant deadline,
                    @NonNull PaymentStatus status, PaymentPage page, String authorizationRef, Instant authorizedAt,
                    long version) {
        this.id = id;
        this.orderId = orderId;
        this.amount = amount;
        this.deadline = deadline;
        this.status = status;
        this.page = page;
        this.authorizationRef = authorizationRef;
        this.authorizedAt = authorizedAt;
        this.version = version;
    }

    /**
     * 未払いの支払を作る。
     *
     * @param id       支払 ID
     * @param orderId  注文 ID
     * @param amount   金額（注文の総額）
     * @param deadline 支払の期限
     * @return 支払
     */
    public static Payment create(UUID id, UUID orderId, Money amount, Instant deadline) {
        return new Payment(id, orderId, amount, deadline, PaymentStatus.UNPAID, null, null, null, 0);
    }

// ============================== ドメインの振る舞い ==============================

    /**
     * 開いている支払の画面を返す。
     *
     * @return 支払の画面。まだ開いていなければ空
     */
    public Optional<PaymentPage> page() {
        return Optional.ofNullable(page);
    }

    /**
     * 支払えるか（支払の画面に移れるか）を返す。未払いで、期限の前だけ。
     *
     * @param now 現在時刻
     * @return 支払えれば true
     */
    public boolean isPayable(@NonNull Instant now) {
        return status == PaymentStatus.UNPAID && now.isBefore(deadline);
    }

    /**
     * 開いた支払の画面を覚える。次からは同じ画面を使う。
     *
     * @param page 支払の画面
     */
    public void openPage(@NonNull PaymentPage page) {
        if (status != PaymentStatus.UNPAID) {
            throw new IllegalStateException("未払いでない支払の画面は開けません: " + status);
        }
        this.page = page;
    }

    /**
     * 与信の成功の通知を反映する。<br />
     * 未払いなら与信済みにして、与信の時刻を覚え、与信の成功のイベントを登録する。
     * 支払なし（期限切れかキャンセルの後）なら、取り消すために与信の取消待ちにする。
     * それ以外（すでに与信がある）なら何もしない。
     *
     * @param authorizationRef 与信の参照
     * @param now              現在時刻
     */
    public void authorize(@NonNull String authorizationRef, @NonNull Instant now) {
        switch (status) {
            case UNPAID -> {
                this.authorizationRef = authorizationRef;
                this.authorizedAt = now;
                status = PaymentStatus.AUTHORIZED;
                registerEvent(PaymentAuthorized.of(now, orderId));
            }
            case NOT_PAID -> {
                this.authorizationRef = authorizationRef;
                requestVoid(now);
            }
            case AUTHORIZED, VOID_PENDING, VOIDED, CAPTURED, AUTHORIZATION_EXPIRED, REFUND_PENDING, REFUNDED -> {
                // すでに与信がある（重ねて届いた通知、または順序が入れ替わった通知）
            }
        }
    }

    /**
     * 期限切れにする。未払いなら支払なしにして、支払の期限切れのイベントを登録する。
     *
     * @param now 現在時刻
     * @return 支払なしにしたら true。未払いでなければ false
     */
    public boolean expire(@NonNull Instant now) {
        if (status != PaymentStatus.UNPAID) {
            return false;
        }
        status = PaymentStatus.NOT_PAID;
        registerEvent(PaymentExpired.of(now, orderId));
        return true;
    }

    /**
     * 注文のキャンセルに合わせて取り消す。<br />
     * 与信済みなら与信の取消待ちにし、売上確定済みなら返金待ちにする。未払いなら、取り消す与信がないので支払なしにする。
     * それ以外（与信期限切れを含む）なら何もしない。与信期限切れは、決済サービスで与信が切れていて、取り消すものがない。
     *
     * @param now 現在時刻
     */
    public void cancel(@NonNull Instant now) {
        switch (status) {
            case AUTHORIZED -> requestVoid(now);
            case UNPAID -> status = PaymentStatus.NOT_PAID;
            case CAPTURED -> requestRefund(now);
            case VOID_PENDING, VOIDED, NOT_PAID, AUTHORIZATION_EXPIRED, REFUND_PENDING, REFUNDED -> {
                // 取り消すものがない、または取消か返金を始めている
            }
        }
    }

    /**
     * 売上を確定したことを反映する。与信済みでなければ何もしない。
     */
    public void capture() {
        if (status == PaymentStatus.AUTHORIZED) {
            status = PaymentStatus.CAPTURED;
        }
    }

    /**
     * 売上確定が与信の期限切れで断られたことを反映する。与信済みでなければ何もしない。
     */
    public void expireAuthorization() {
        if (status == PaymentStatus.AUTHORIZED) {
            status = PaymentStatus.AUTHORIZATION_EXPIRED;
        }
    }

    /**
     * 決済サービスで返金したことを反映する。返金待ちでなければ何もしない。
     */
    public void markRefunded() {
        if (status == PaymentStatus.REFUND_PENDING) {
            status = PaymentStatus.REFUNDED;
        }
    }

    /**
     * 与信の時刻を返す。
     *
     * @return 与信の時刻。与信がなければ空
     */
    public Optional<Instant> authorizedAt() {
        return Optional.ofNullable(authorizedAt);
    }

    /**
     * 与信期限を返す。
     *
     * @param term 与信の有効期間
     * @return 与信期限。与信の時刻がなければ空
     */
    public Optional<Instant> authorizationDeadline(@NonNull AuthorizationTerm term) {
        return authorizedAt().map(term::deadlineOf);
    }

    /**
     * 与信期限が近いかを返す。与信済みと与信期限切れの支払だけが対象で、期限を過ぎていても近いとする。
     *
     * @param term 与信の有効期間と、近いとする長さ
     * @param now  現在時刻
     * @return 近ければ true
     */
    public boolean isAuthorizationExpiring(@NonNull AuthorizationTerm term, @NonNull Instant now) {
        var target = status == PaymentStatus.AUTHORIZED || status == PaymentStatus.AUTHORIZATION_EXPIRED;
        return target && authorizedAt().map(at -> term.isExpiring(at, now)).orElse(false);
    }

    /**
     * 決済サービスで与信を取り消したことを反映する。与信の取消待ちでなければ何もしない。
     */
    public void markVoided() {
        if (status == PaymentStatus.VOID_PENDING) {
            status = PaymentStatus.VOIDED;
        }
    }

// ============================== プライベートメソッド ==============================

    /**
     * 与信の取消待ちにし、取消の依頼を登録する。
     */
    private void requestVoid(Instant now) {
        status = PaymentStatus.VOID_PENDING;
        registerEvent(VoidRequested.of(now, id));
    }

    /**
     * 返金待ちにし、返金の依頼を登録する。
     */
    private void requestRefund(Instant now) {
        status = PaymentStatus.REFUND_PENDING;
        registerEvent(RefundRequested.of(now, id));
    }

// ============================== スナップショット関連処理 ==============================

    /**
     * 支払のスナップショット
     *
     * @param id               支払 ID
     * @param orderId          注文 ID
     * @param amount           金額
     * @param deadline         支払の期限
     * @param status           状態
     * @param page             支払の画面
     * @param authorizationRef 与信の参照
     * @param authorizedAt     与信の時刻
     * @param version          バージョン
     */
    public record Snapshot(UUID id, UUID orderId, Money amount, Instant deadline, PaymentStatus status,
                           @Nullable PaymentPage page, @Nullable String authorizationRef,
                           @Nullable Instant authorizedAt, long version) {
    }

    /**
     * スナップショットを取得する
     *
     * @return 支払のスナップショット
     */
    public Snapshot toSnapshot() {
        return new Snapshot(id, orderId, amount, deadline, status, page, authorizationRef, authorizedAt, version);
    }

    /**
     * スナップショットから復元する
     *
     * @param snapshot 支払のスナップショット
     * @return 支払
     */
    public static Payment restore(Snapshot snapshot) {
        return new Payment(snapshot.id(), snapshot.orderId(), snapshot.amount(), snapshot.deadline(), snapshot.status(),
                snapshot.page(), snapshot.authorizationRef(), snapshot.authorizedAt(), snapshot.version());
    }
}
