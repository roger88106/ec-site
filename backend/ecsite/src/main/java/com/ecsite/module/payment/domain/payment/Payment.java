package com.ecsite.module.payment.domain.payment;

import com.ecsite.common.model.base.AggregateRoot;
import com.ecsite.common.model.value.Money;
import com.ecsite.module.payment.event.PaymentAuthorized;
import com.ecsite.module.payment.event.PaymentExpired;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NonNull;
import lombok.experimental.Accessors;
import org.jmolecules.ddd.annotation.Identity;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * 支払
 *
 * <p>状態の遷移を持つ集約のルート。どの操作も冪等で、遷移できないときは何もしない（決済サービスの通知は重ねて届き、
 * 順序が入れ替わるため）。
 */
@Getter
@Accessors(fluent = true)
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@org.jmolecules.ddd.annotation.AggregateRoot
public class Payment extends AggregateRoot {

// ============================== フィールド定義 ==============================

    /**
     * 支払 ID
     */
    @Identity
    @EqualsAndHashCode.Include
    @NonNull
    private final UUID id;

    /**
     * 注文 ID（注文 1 つにつき支払は 1 つ）
     */
    @NonNull
    private final UUID orderId;

    /**
     * 金額（注文の総額）
     */
    @NonNull
    private final Money amount;

    /**
     * 支払の期限
     */
    @NonNull
    private final Instant deadline;

    /**
     * 状態
     */
    @NonNull
    private PaymentStatus status;

    /**
     * 開いている支払画面
     *
     * <p>まだ開いていなければ null。
     */
    @Nullable
    @Getter(AccessLevel.NONE)
    private PaymentPage page;

    /**
     * 与信の参照
     *
     * <p>決済サービスでの与信の ID。与信がなければ null。
     */
    @Nullable
    private String authorizationRef;

    /**
     * 与信の時刻
     *
     * <p>与信期限の起点。与信がなければ null。
     * 通知で与信済みにした支払は、通知を受けた時刻。決済サービスから取得した与信で与信済みにした支払は、決済サービスが返す与信の時刻。
     */
    @Nullable
    @Getter(AccessLevel.NONE)
    private Instant authorizedAt;

    /**
     * 与信の反映を待ち始めた時刻
     *
     * <p>期限切れにしようとして、決済サービスで支払済みと知った時刻。待っていなければ null。
     */
    @Nullable
    @Getter(AccessLevel.NONE)
    private Instant awaitingAuthorizationSince;

    /**
     * 売上確定を求めた時刻
     *
     * <p>売上確定を決済サービスに求めた時刻。求めていなければ null。
     * 与信済みのときに付き、遷移で削除されない。照合で、決済サービスが売上を確定していなかったときに {@link #clearCaptureRequest(Instant)} で削除する。
     */
    @Nullable
    @Getter(AccessLevel.NONE)
    private Instant captureRequestedAt;

    /**
     * バージョン
     *
     * <p>リポジトリが保存するときに上げる。
     */
    private final long version;

// ============================== コンストラクタ ==============================

    /**
     * 全属性コンストラクタ
     *
     * <p>状態と、与信の参照・与信の時刻の整合を検査する。支払画面と待ち始めた時刻は、未払いのときに付き、遷移で消えないので、
     * どの状態でも、あってもなくてもよい。売上確定を求めた時刻は、未払いと支払なしでは持たず、他の状態ではあってもなくてもよい。
     *
     * @param id                         支払 ID
     * @param orderId                    注文 ID
     * @param amount                     金額
     * @param deadline                   支払の期限
     * @param status                     状態
     * @param page                       支払画面
     * @param authorizationRef           与信の参照
     * @param authorizedAt               与信の時刻
     * @param awaitingAuthorizationSince 与信の反映を待ち始めた時刻
     * @param captureRequestedAt         売上確定を求めた時刻
     * @param version                    バージョン
     */
    private Payment(@NonNull UUID id, @NonNull UUID orderId, @NonNull Money amount, @NonNull Instant deadline,
                    @NonNull PaymentStatus status, @Nullable PaymentPage page, @Nullable String authorizationRef,
                    @Nullable Instant authorizedAt, @Nullable Instant awaitingAuthorizationSince,
                    @Nullable Instant captureRequestedAt, long version) {
        verifyAuthorization(status, authorizationRef, authorizedAt);
        verifyCaptureRequest(status, captureRequestedAt);
        this.id = id;
        this.orderId = orderId;
        this.amount = amount;
        this.deadline = deadline;
        this.status = status;
        this.page = page;
        this.authorizationRef = authorizationRef;
        this.authorizedAt = authorizedAt;
        this.awaitingAuthorizationSince = awaitingAuthorizationSince;
        this.captureRequestedAt = captureRequestedAt;
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
        return new Payment(id, orderId, amount, deadline, PaymentStatus.UNPAID, null, null, null, null, null, 0);
    }

// ============================== ドメインの振る舞い ==============================

    /**
     * 開いている支払画面を返す。
     *
     * @return 支払画面。まだ開いていなければ空
     */
    public Optional<PaymentPage> page() {
        return Optional.ofNullable(page);
    }

    /**
     * 支払えるか（支払画面に移れるか）を返す。
     *
     * <p>未払いで、期限の前だけ。
     *
     * @param now 現在時刻
     * @return 支払えれば true
     */
    public boolean isPayable(@NonNull Instant now) {
        return status == PaymentStatus.UNPAID && now.isBefore(deadline);
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
     * 与信期限が近いかを返す。
     *
     * <p>与信済みと与信期限切れの支払だけが対象で、期限を過ぎていても近いとする。
     *
     * @param term 与信の有効期間と、近いとする長さ
     * @param now  現在時刻
     * @return 近ければ true
     */
    public boolean isAuthorizationExpiring(@NonNull AuthorizationTerm term, @NonNull Instant now) {
        return status.watchesAuthorizationDeadline()
                && authorizedAt().map(at -> term.isExpiring(at, now)).orElse(false);
    }

    /**
     * 与信の反映を待ち始めた時刻を返す。
     *
     * @return 待ち始めた時刻。待っていなければ空
     */
    public Optional<Instant> awaitingAuthorizationSince() {
        return Optional.ofNullable(awaitingAuthorizationSince);
    }

    /**
     * 開いた支払画面を記録する。
     *
     * <p>次からは同じ画面を使う。
     *
     * @param page 支払画面
     */
    public void openPage(@NonNull PaymentPage page) {
        if (status != PaymentStatus.UNPAID) {
            throw new IllegalStateException("未払いでない支払画面は開けません: " + status);
        }
        this.page = page;
    }

    /**
     * 与信を反映する。
     *
     * <p>与信の時刻は現在時刻にする。反映の仕方は {@link #authorize(String, Instant, Instant)} と同じ。
     *
     * @param authorizationRef 与信の参照
     * @param now              現在時刻
     * @return 状態を変えたら true。すでに与信がある状態なら false
     */
    public boolean authorize(@NonNull String authorizationRef, @NonNull Instant now) {
        return authorize(authorizationRef, now, now);
    }

    /**
     * 与信を反映する。
     *
     * <p>未払いなら与信済みにして、与信の時刻を記録し、与信の成功のイベントを現在時刻で登録する。
     * 支払なしなら与信の取消待ちにする。それ以外なら何もしない。
     *
     * @param authorizationRef 与信の参照
     * @param authorizedAt     与信の時刻（与信期限の起点）
     * @param now              現在時刻（イベントの発生時刻）
     * @return 状態を変えたら true（未払いから与信済みに、支払なしから与信の取消待ちに）。すでに与信がある状態なら false
     */
    public boolean authorize(@NonNull String authorizationRef, @NonNull Instant authorizedAt, @NonNull Instant now) {
        return switch (status) {
            case UNPAID -> {
                this.authorizationRef = authorizationRef;
                this.authorizedAt = authorizedAt;
                status = PaymentStatus.AUTHORIZED;
                registerEvent(PaymentAuthorized.of(now, orderId));
                yield true;
            }
            // 期限切れかキャンセルの後に届いた与信は、取り消す
            case NOT_PAID -> {
                this.authorizationRef = authorizationRef;
                requestVoid(now);
                yield true;
            }
            // すでに与信がある（重ねて届いた通知、または順序が入れ替わった通知）
            case AUTHORIZED, VOID_PENDING, VOIDED, CAPTURED, AUTHORIZATION_EXPIRED, REFUND_PENDING, REFUNDED -> false;
        };
    }

    /**
     * 期限切れにする。
     *
     * <p>未払いなら支払なしにして、支払の期限切れのイベントを登録する。
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
     * 与信の反映を待つ支払にする。
     *
     * <p>状態は未払いのまま、待ち始めた時刻を記録する。未払いでないか、すでに待っていれば何もしない。
     *
     * @param now 現在時刻
     * @return 待ち始めたら true
     */
    public boolean awaitAuthorization(@NonNull Instant now) {
        // 未払いでない支払（通知が先に届いて与信済みになったなど）と、すでに待っている支払は変えない
        if (status != PaymentStatus.UNPAID || awaitingAuthorizationSince != null) {
            return false;
        }
        awaitingAuthorizationSince = now;
        return true;
    }

    /**
     * 売上を確定したことを反映する。
     *
     * <p>与信済みなら売上確定済みにする。与信の取消待ちなら、返金待ちにして返金の依頼を登録する。
     * それ以外なら何もしない。
     *
     * @param now 現在時刻
     * @return 状態を変えたら true。売上確定を反映する状態でなければ false
     */
    public boolean capture(@NonNull Instant now) {
        return switch (status) {
            case AUTHORIZED -> {
                status = PaymentStatus.CAPTURED;
                yield true;
            }
            // 決済サービスが確定した後に注文がキャンセルされた。確定した与信は取り消せないので、返金で戻す
            case VOID_PENDING -> {
                requestRefund(now);
                yield true;
            }
            // 売上確定を反映する状態ではない
            case UNPAID, NOT_PAID, VOIDED, CAPTURED, AUTHORIZATION_EXPIRED, REFUND_PENDING, REFUNDED -> false;
        };
    }

    /**
     * 売上確定が与信の期限切れで断られたことを反映する。
     *
     * <p>与信済みでなければ何もしない。
     *
     * @return 与信期限切れにしたら true。与信済みでなければ false
     */
    public boolean expireAuthorization() {
        if (status != PaymentStatus.AUTHORIZED) {
            return false;
        }
        status = PaymentStatus.AUTHORIZATION_EXPIRED;
        return true;
    }

    /**
     * 売上確定を求めた時刻を記録する。
     *
     * <p>与信済みなら、時刻を現在時刻にする。すでに時刻があれば上書きする（運営者の再実行で、新しい時刻にする）。
     * 与信済みでなければ何もしない。
     *
     * @param now 現在時刻
     * @return 時刻を記録したら true。与信済みでなければ false
     */
    public boolean requestCapture(@NonNull Instant now) {
        if (status != PaymentStatus.AUTHORIZED) {
            return false;
        }
        captureRequestedAt = now;
        return true;
    }

    /**
     * 売上確定を求めた時刻を削除する。
     *
     * <p>与信済みで、記録している時刻が読んだ時刻と等しいときだけ削除する。読んだ後に再実行されて時刻が変わっていれば、削除しない。
     *
     * @param readRequestedAt 読んだ売上確定を求めた時刻
     * @return 削除したら true。与信済みでないか、時刻が違うか、時刻がなければ false
     */
    public boolean clearCaptureRequest(@NonNull Instant readRequestedAt) {
        if (status != PaymentStatus.AUTHORIZED || !readRequestedAt.equals(captureRequestedAt)) {
            return false;
        }
        captureRequestedAt = null;
        return true;
    }

    /**
     * 売上確定を求めた時刻を返す。
     *
     * @return 売上確定を求めた時刻。求めていなければ空
     */
    public Optional<Instant> captureRequestedAt() {
        return Optional.ofNullable(captureRequestedAt);
    }

    /**
     * 注文のキャンセルに合わせて取り消す。
     *
     * <p>与信済みなら与信の取消待ちに、売上確定済みなら返金待ちに、未払いなら支払なしにする。
     * それ以外なら何もしない。
     *
     * @param now 現在時刻
     * @return 状態を変えたら true。取り消すものがなければ false
     */
    public boolean cancel(@NonNull Instant now) {
        return switch (status) {
            case AUTHORIZED -> {
                requestVoid(now);
                yield true;
            }
            // 未払いは、取り消す与信がない
            case UNPAID -> {
                status = PaymentStatus.NOT_PAID;
                yield true;
            }
            case CAPTURED -> {
                requestRefund(now);
                yield true;
            }
            // 取り消すものがない（与信期限切れは、決済サービスで与信が切れている）か、取消か返金を始めている
            case VOID_PENDING, VOIDED, NOT_PAID, AUTHORIZATION_EXPIRED, REFUND_PENDING, REFUNDED -> false;
        };
    }

    /**
     * 決済サービスで与信を取り消したことを反映する。
     *
     * <p>与信の取消待ちでなければ何もしない。
     *
     * @return 取消済みにしたら true。与信の取消待ちでなければ false
     */
    public boolean markVoided() {
        if (status != PaymentStatus.VOID_PENDING) {
            return false;
        }
        status = PaymentStatus.VOIDED;
        return true;
    }

    /**
     * 決済サービスで返金したことを反映する。
     *
     * <p>返金待ちでなければ何もしない。
     *
     * @return 返金済みにしたら true。返金待ちでなければ false
     */
    public boolean markRefunded() {
        if (status != PaymentStatus.REFUND_PENDING) {
            return false;
        }
        status = PaymentStatus.REFUNDED;
        return true;
    }

// ============================== プライベートメソッド ==============================

    /**
     * 状態と、与信の参照・与信の時刻が整合するかを検査し、整合しなければ拒否する。
     *
     * <p>メッセージには状態の名前だけを書き、与信の参照と時刻を書かない（DB から戻した値が入るため）。
     *
     * @param status           状態
     * @param authorizationRef 与信の参照
     * @param authorizedAt     与信の時刻
     */
    private static void verifyAuthorization(PaymentStatus status, @Nullable String authorizationRef,
                                            @Nullable Instant authorizedAt) {
        switch (status) {
            // 与信が成功していない。支払なしの後に届いた与信は、与信の取消待ちにする
            case UNPAID, NOT_PAID -> {
                if (authorizationRef != null || authorizedAt != null) {
                    throw new IllegalArgumentException("与信のない状態は、与信の参照と与信の時刻を持たない");
                }
            }
            // 未払いから与信済みにした支払と、そこから進んだ支払
            case AUTHORIZED, CAPTURED, AUTHORIZATION_EXPIRED -> {
                if (authorizationRef == null || authorizedAt == null) {
                    throw new IllegalArgumentException("与信済みから進んだ状態は、与信の参照と与信の時刻を持つ");
                }
            }
            // 支払なしの後に届いた与信から進んだ支払は、与信の時刻を持たない
            case VOID_PENDING, VOIDED, REFUND_PENDING, REFUNDED -> {
                if (authorizationRef == null) {
                    throw new IllegalArgumentException("与信を取り消す・返金する状態は、与信の参照を持つ");
                }
            }
        }
    }

    /**
     * 状態と、売上確定を求めた時刻が整合するかを検査し、整合しなければ拒否する。
     *
     * <p>メッセージには値を書かない（DB から戻した値が入るため）。
     *
     * @param status             状態
     * @param captureRequestedAt 売上確定を求めた時刻
     */
    private static void verifyCaptureRequest(PaymentStatus status, @Nullable Instant captureRequestedAt) {
        if (captureRequestedAt != null && (status == PaymentStatus.UNPAID || status == PaymentStatus.NOT_PAID)) {
            throw new IllegalArgumentException("与信のない状態は、売上確定を求めた時刻を持たない");
        }
    }

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
     * @param id                         支払 ID
     * @param orderId                    注文 ID
     * @param amount                     金額
     * @param deadline                   支払の期限
     * @param status                     状態
     * @param page                       支払画面
     * @param authorizationRef           与信の参照
     * @param authorizedAt               与信の時刻
     * @param awaitingAuthorizationSince 与信の反映を待ち始めた時刻
     * @param captureRequestedAt         売上確定を求めた時刻
     * @param version                    バージョン
     */
    public record Snapshot(UUID id, UUID orderId, Money amount, Instant deadline, PaymentStatus status,
                           @Nullable PaymentPage page, @Nullable String authorizationRef,
                           @Nullable Instant authorizedAt, @Nullable Instant awaitingAuthorizationSince,
                           @Nullable Instant captureRequestedAt, long version) {
    }

    /**
     * スナップショットを取得する。
     *
     * @return 支払のスナップショット
     */
    public Snapshot toSnapshot() {
        return new Snapshot(id, orderId, amount, deadline, status, page, authorizationRef, authorizedAt,
                awaitingAuthorizationSince, captureRequestedAt, version);
    }

    /**
     * スナップショットから復元する。
     *
     * @param snapshot 支払のスナップショット
     * @return 支払
     */
    public static Payment restore(Snapshot snapshot) {
        return new Payment(snapshot.id(), snapshot.orderId(), snapshot.amount(), snapshot.deadline(), snapshot.status(),
                snapshot.page(), snapshot.authorizationRef(), snapshot.authorizedAt(), snapshot.awaitingAuthorizationSince(),
                snapshot.captureRequestedAt(), snapshot.version());
    }
}
