package com.ecsite.module.order.domain.order;

import com.ecsite.common.error.exception.BusinessException;
import com.ecsite.common.model.AggregateRoot;
import com.ecsite.common.model.Operator;
import com.ecsite.common.model.value.BoundedText;
import com.ecsite.module.order.domain.quote.Amounts;
import com.ecsite.module.order.domain.quote.Quote;
import com.ecsite.module.order.domain.quote.QuoteItem;
import com.ecsite.module.order.event.OrderCancelled;
import com.ecsite.module.order.event.OrderPlaced;
import jakarta.annotation.Nullable;
import lombok.Getter;
import lombok.NonNull;
import lombok.experimental.Accessors;
import org.jmolecules.ddd.annotation.Identity;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.IntStream;

/**
 * 注文の Aggregate Root（docs/modules/order.md「注文」）。<br />
 * 明細と金額は注文時点の写しで、成立の後は変わらない（AC-92）。変わるのは状態とキャンセルの記録だけ（BR-9、docs/modules/order.md「状態遷移」）。
 */
@Getter
@Accessors(fluent = true)
@org.jmolecules.ddd.annotation.AggregateRoot
public class Order extends AggregateRoot {

    /**
     * メールアドレスの最大の文字数
     */
    static final int EMAIL_MAX_LENGTH = 254;

    /**
     * キャンセルの理由の最大の文字数（AC-95、103）
     */
    static final int REASON_MAX_LENGTH = 500;

    /**
     * メールアドレスの形（ローカル部 @ ドメイン。細かな規則は問わない）
     */
    private static final Pattern EMAIL = Pattern.compile("[^@\\s]+@[^@\\s]+");

// ============================== フィールド定義 ==============================

    /**
     * 注文 ID
     */
    @Identity
    private final UUID id;

    /**
     * 注文番号
     */
    private final OrderNumber orderNumber;

    /**
     * 注文手続き ID（注文手続き 1 つにつき注文は 1 つ。AC-91）
     */
    private final UUID checkoutId;

    /**
     * 会員 ID（注文した会員。AC-128）
     */
    private final UUID memberId;

    /**
     * 注文日時（価格と税率を判定した時点）
     */
    private final Instant orderedAt;

    /**
     * 状態
     */
    private OrderStatus status;

    /**
     * 明細（カートの並び）
     */
    private final List<OrderLine> lines;

    /**
     * 金額の内訳
     */
    private final Amounts amounts;

    /**
     * メールアドレス
     */
    private final String email;

    /**
     * お届け先
     */
    private final ShippingAddress shippingAddress;

    /**
     * キャンセルの申請。なければ null
     */
    @Nullable
    private CancellationRequest cancellationRequest;

    /**
     * キャンセル。キャンセルでなければ null
     */
    @Nullable
    private Cancellation cancellation;

    /**
     * バージョン（リポジトリが保存するときに上げる）
     */
    private final long version;

// ============================== コンストラクタ ==============================

    /**
     * 全属性コンストラクタ
     */
    private Order(@NonNull UUID id, @NonNull OrderNumber orderNumber, @NonNull UUID checkoutId, @NonNull UUID memberId,
                  @NonNull Instant orderedAt, @NonNull OrderStatus status, @NonNull List<OrderLine> lines,
                  @NonNull Amounts amounts, @NonNull String email, @NonNull ShippingAddress shippingAddress,
                  CancellationRequest cancellationRequest, Cancellation cancellation, long version) {
        this.id = id;
        this.orderNumber = orderNumber;
        this.checkoutId = checkoutId;
        this.memberId = memberId;
        this.orderedAt = orderedAt;
        this.status = status;
        this.lines = List.copyOf(lines);
        this.amounts = amounts;
        this.email = email;
        this.shippingAddress = shippingAddress;
        this.cancellationRequest = cancellationRequest;
        this.cancellation = cancellation;
        this.version = version;
    }

    /**
     * 見積もりから注文を作る（AC-90、92）。状態は「受付」。
     *
     * @param id              注文 ID
     * @param orderNumber     注文番号
     * @param checkoutId      注文手続き ID
     * @param memberId        会員 ID（注文した会員）
     * @param orderedAt       注文日時
     * @param quote           注文日時の見積もり（注文できるもの）
     * @param email           メールアドレス
     * @param shippingAddress お届け先
     * @return 注文
     */
    public static Order place(UUID id, OrderNumber orderNumber, UUID checkoutId, UUID memberId, Instant orderedAt,
                              @NonNull Quote quote,
                              String email, ShippingAddress shippingAddress) {
        if (!quote.orderable()) {
            throw new IllegalArgumentException("注文できない見積もりからは注文を作れません");
        }
        if (email == null || email.length() > EMAIL_MAX_LENGTH || !EMAIL.matcher(email).matches()) {
            throw BusinessException.of(OrderErrorCode.INVALID_EMAIL);
        }
        var items = quote.items();
        var lines = IntStream.range(0, items.size()).mapToObj(i -> toLine(i + 1, items.get(i))).toList();
        var order = new Order(id, orderNumber, checkoutId, memberId, orderedAt, OrderStatus.ACCEPTED, lines, quote.amounts(),
                email, shippingAddress, null, null, 0);
        order.registerEvent(OrderPlaced.of(orderedAt, id));
        return order;
    }

// ============================== ドメインの振る舞い ==============================

    /**
     * 消費者がキャンセルを申請する（AC-95、96）。「受付」から「キャンセル申請中」にする。<br />
     * 申請中なら何もしない（冪等）。申請は 1 回だけで、却下済み・キャンセル済みの注文は申請できない。
     *
     * @param reason 理由（任意、500 文字まで）。空白だけなら理由なし
     * @param now    現在時刻
     */
    public void requestCancellation(String reason, @NonNull Instant now) {
        if (status == OrderStatus.CANCELLATION_REQUESTED) {
            return;
        }
        if (status != OrderStatus.ACCEPTED || cancellationRequest != null) {
            throw BusinessException.of(OrderErrorCode.CANCELLATION_NOT_REQUESTABLE);
        }
        cancellationRequest = new CancellationRequest(now, optionalReason(reason), CancellationRequest.Result.PENDING,
                null, null);
        status = OrderStatus.CANCELLATION_REQUESTED;
    }

    /**
     * キャンセルの申請を承認し、キャンセルにする（AC-101）。キャンセル済みなら何もしない（AC-105）。
     *
     * @param operator 承認した運営者（AC-142）
     * @param now      現在時刻
     * @return キャンセルにしたら true（呼ぶ側が在庫を戻す）。すでにキャンセルなら false
     */
    public boolean approveCancellation(@NonNull Operator operator, @NonNull Instant now) {
        if (status == OrderStatus.CANCELLED) {
            return false;
        }
        if (status != OrderStatus.CANCELLATION_REQUESTED) {
            throw BusinessException.of(OrderErrorCode.CANCELLATION_NOT_PENDING);
        }
        decideRequest(CancellationRequest.Result.APPROVED, operator, now);
        cancel(new Cancellation(now, false, null, operator));
        return true;
    }

    /**
     * キャンセルの申請を却下し、「受付」に戻す（AC-102）。申請中でない注文（キャンセル済みを含む）は却下できない。
     *
     * @param operator 却下した運営者（AC-142）
     * @param now      現在時刻
     */
    public void rejectCancellation(@NonNull Operator operator, @NonNull Instant now) {
        if (status != OrderStatus.CANCELLATION_REQUESTED) {
            throw BusinessException.of(OrderErrorCode.CANCELLATION_NOT_PENDING);
        }
        decideRequest(CancellationRequest.Result.REJECTED, operator, now);
        status = OrderStatus.ACCEPTED;
    }

    /**
     * 運営者の判断でキャンセルする（AC-103）。申請中なら、申請は承認されたものとする。<br />
     * キャンセル済みなら何もせず、理由も変えない（AC-105）。
     *
     * @param reason   理由（必須、500 文字まで）
     * @param operator キャンセルした運営者（AC-142）。申請中なら、判断した運営者にもなる
     * @param now      現在時刻
     * @return キャンセルにしたら true（呼ぶ側が在庫を戻す）。すでにキャンセルなら false
     */
    public boolean cancelByOperator(String reason, @NonNull Operator operator, @NonNull Instant now) {
        var required = optionalReason(reason);
        if (required == null) {
            throw BusinessException.of(OrderErrorCode.CANCELLATION_REASON_REQUIRED);
        }
        if (status == OrderStatus.CANCELLED) {
            return false;
        }
        if (status == OrderStatus.CANCELLATION_REQUESTED) {
            decideRequest(CancellationRequest.Result.APPROVED, operator, now);
        }
        cancel(new Cancellation(now, true, required, operator));
        return true;
    }

// ============================== プライベートメソッド ==============================

    /**
     * 申請に結果を記録する。
     */
    private void decideRequest(CancellationRequest.Result result, Operator operator, Instant now) {
        cancellationRequest = new CancellationRequest(cancellationRequest.requestedAt(), cancellationRequest.reason(),
                result, now, operator);
    }

    /**
     * キャンセルにし、注文キャンセルのイベントを登録する。
     */
    private void cancel(Cancellation cancellation) {
        this.cancellation = cancellation;
        status = OrderStatus.CANCELLED;
        registerEvent(OrderCancelled.of(cancellation.cancelledAt(), id));
    }

    /**
     * 理由を検証する。空白だけなら理由なし（null）とし、500 文字を超えれば拒否する。
     */
    private static String optionalReason(String reason) {
        if (reason == null || reason.isBlank()) {
            return null;
        }
        if (!BoundedText.fitsWithin(reason, REASON_MAX_LENGTH)) {
            throw BusinessException.of(OrderErrorCode.INVALID_CANCELLATION_REASON);
        }
        return reason;
    }

    /**
     * 見積もりの品目を、注文時点の写しの明細にする（AC-92）。
     */
    private static OrderLine toLine(int lineNo, QuoteItem item) {
        var variant = item.variant();
        return new OrderLine(
                lineNo,
                variant.variantId(),
                variant.productCode(),
                variant.variantCode(),
                variant.productName(),
                variant.axisValues(),
                variant.priceExclTax(),
                item.ratePercent(),
                item.unitPriceInclTax(),
                item.quantity(),
                item.subtotal());
    }

// ============================== スナップショット関連処理 ==============================

    /**
     * 注文のスナップショット
     *
     * @param id                  注文 ID
     * @param orderNumber         注文番号
     * @param checkoutId          注文手続き ID
     * @param memberId            会員 ID
     * @param orderedAt           注文日時
     * @param status              状態
     * @param lines               明細
     * @param amounts             金額の内訳
     * @param email               メールアドレス
     * @param shippingAddress     お届け先
     * @param cancellationRequest キャンセルの申請
     * @param cancellation        キャンセル
     * @param version             バージョン
     */
    public record Snapshot(UUID id, OrderNumber orderNumber, UUID checkoutId, UUID memberId, Instant orderedAt, OrderStatus status,
                           List<OrderLine> lines, Amounts amounts, String email, ShippingAddress shippingAddress,
                           @Nullable CancellationRequest cancellationRequest, @Nullable Cancellation cancellation,
                           long version) {
    }

    /**
     * スナップショットを取得する
     *
     * @return 注文のスナップショット
     */
    public Snapshot toSnapshot() {
        return new Snapshot(id, orderNumber, checkoutId, memberId, orderedAt, status, lines, amounts, email, shippingAddress,
                cancellationRequest, cancellation, version);
    }

    /**
     * スナップショットから復元する
     *
     * @param snapshot 注文のスナップショット
     * @return 注文
     */
    public static Order restore(Snapshot snapshot) {
        return new Order(snapshot.id(), snapshot.orderNumber(), snapshot.checkoutId(), snapshot.memberId(), snapshot.orderedAt(),
                snapshot.status(), snapshot.lines(), snapshot.amounts(), snapshot.email(), snapshot.shippingAddress(),
                snapshot.cancellationRequest(), snapshot.cancellation(), snapshot.version());
    }
}
