package com.ecsite.module.order.domain.order;

import com.ecsite.common.error.exception.BusinessException;
import com.ecsite.common.model.base.AggregateRoot;
import com.ecsite.common.model.value.BoundedText;
import com.ecsite.common.model.value.Operator;
import com.ecsite.module.order.domain.OrderErrorCode;
import com.ecsite.module.order.domain.order.vo.BuyerProfile;
import com.ecsite.module.order.domain.order.vo.Cancellation;
import com.ecsite.module.order.domain.order.vo.CancellationRequest;
import com.ecsite.module.order.domain.order.vo.OrderLine;
import com.ecsite.module.order.domain.order.vo.OrderNumber;
import com.ecsite.module.order.domain.order.vo.ShippingAddress;
import com.ecsite.module.order.domain.quote.Amounts;
import com.ecsite.module.order.domain.quote.Quote;
import com.ecsite.module.order.domain.quote.QuoteItem;
import com.ecsite.module.order.event.OrderShipped;
import com.ecsite.module.order.event.OrderStatusChanged;
import lombok.Getter;
import lombok.NonNull;
import lombok.experimental.Accessors;
import org.jmolecules.ddd.annotation.Identity;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.IntStream;

/**
 * 注文の Aggregate Root（docs/design/modules/order.md「注文」）。<br />
 * 明細と金額は注文時点の写しで、成立の後は変わらない。変わるのは状態とキャンセルの記録だけ（docs/design/modules/order.md「状態遷移」）。<br />
 * 注文は支払待ちで作られ、決済のイベント（与信の成功、支払の期限切れ）で受付かキャンセルになる。受付から出荷すると出荷済みになる。
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
     * キャンセルの理由の最大の文字数
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
     * 注文手続き ID（注文手続き 1 つにつき注文は 1 つ）
     */
    private final UUID checkoutId;

    /**
     * 会員 ID（注文した会員）
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
     * 注文時の年齢と性別
     */
    private final BuyerProfile buyerProfile;

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
                  @NonNull BuyerProfile buyerProfile, @Nullable CancellationRequest cancellationRequest,
                  @Nullable Cancellation cancellation, long version) {
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
        this.buyerProfile = buyerProfile;
        this.cancellationRequest = cancellationRequest;
        this.cancellation = cancellation;
        this.version = version;
    }

    /**
     * 見積もりから注文を作る。状態は「支払待ち」。
     *
     * @param id              注文 ID
     * @param orderNumber     注文番号
     * @param checkoutId      注文手続き ID
     * @param memberId        会員 ID（注文した会員）
     * @param orderedAt       注文日時
     * @param quote           注文日時の見積もり（注文できるもの）
     * @param email           メールアドレス
     * @param shippingAddress お届け先
     * @param buyerProfile    注文時の年齢と性別
     * @return 注文
     */
    public static Order place(UUID id, OrderNumber orderNumber, UUID checkoutId, UUID memberId, Instant orderedAt,
                              @NonNull Quote quote,
                              @Nullable String email, ShippingAddress shippingAddress, BuyerProfile buyerProfile) {
        if (!quote.orderable()) {
            throw new IllegalArgumentException("注文できない見積もりからは注文を作れません");
        }
        // メールアドレスは、空でなく、長さの上限以内で、形式に合うこと
        if (email == null || email.length() > EMAIL_MAX_LENGTH || !EMAIL.matcher(email).matches()) {
            throw BusinessException.of(OrderErrorCode.INVALID_EMAIL);
        }
        var items = quote.items();
        var lines = IntStream.range(0, items.size()).mapToObj(i -> toLine(i + 1, items.get(i))).toList();
        var order = new Order(id, orderNumber, checkoutId, memberId, orderedAt, OrderStatus.PENDING_PAYMENT, lines, quote.amounts(),
                email, shippingAddress, buyerProfile, null, null, 0);
        order.registerEvent(OrderStatusChanged.of(orderedAt, id, null, OrderStatusChanged.Status.PENDING_PAYMENT, null, null));
        return order;
    }

// ============================== ドメインの振る舞い ==============================

    /**
     * 支払待ちかを返す。
     *
     * @return 「支払待ち」なら true
     */
    public boolean isAwaitingPayment() {
        return status == OrderStatus.PENDING_PAYMENT;
    }

    /**
     * 与信の成功を受けて、「支払待ち」から「受付」にする。支払待ちでなければ何もしない。
     *
     * @param now 現在時刻
     * @return 受付にしたら true（呼ぶ側がカートから品目を除く）。支払待ちでなければ false
     */
    public boolean acceptPayment(@NonNull Instant now) {
        if (status != OrderStatus.PENDING_PAYMENT) {
            return false;
        }
        changeStatus(OrderStatus.ACCEPTED, now);
        return true;
    }

    /**
     * 支払の期限切れを受けて、「支払待ち」からキャンセル（支払期限切れ）にする。支払待ちでなければ何もしない。
     *
     * @param now 現在時刻
     * @return キャンセルにしたら true（呼ぶ側が在庫を戻す）。支払待ちでなければ false
     */
    public boolean expirePayment(@NonNull Instant now) {
        if (status != OrderStatus.PENDING_PAYMENT) {
            return false;
        }
        cancel(new Cancellation(now, CancellationKind.PAYMENT_EXPIRED, null, null));
        return true;
    }

    /**
     * 運営者がキャンセルできるかを返す。「受付」と「キャンセル申請中」、出荷済みなら配送状態が返送のときだけ。
     * 支払待ちは支払の期限で自動でキャンセルになり、キャンセル済みは何もしない（できる操作のフラグに出さない）。
     *
     * @param returned 配送状態が返送か（出荷済みのときだけ使う）
     * @return キャンセルできれば true
     */
    public boolean isCancellableByOperator(boolean returned) {
        return status == OrderStatus.ACCEPTED || status == OrderStatus.CANCELLATION_REQUESTED
                || (status == OrderStatus.SHIPPED && returned);
    }

    /**
     * 運営者のキャンセルで、在庫に戻すかを聞くかを返す。出荷済みで返送の注文だけ（商品が戻るので、戻すかを選ぶ）。
     * 出荷の前の注文は、在庫に必ず戻す。
     *
     * @param returned 配送状態が返送か（出荷済みのときだけ使う）
     * @return 在庫に戻すかを聞くなら true
     */
    public boolean asksRestockOnCancel(boolean returned) {
        return status == OrderStatus.SHIPPED && returned;
    }

    /**
     * 出荷できるかを返す。「受付」だけ（キャンセル申請中は、判断の後に出荷する）。
     *
     * @return 出荷できれば true
     */
    public boolean isShippable() {
        return status == OrderStatus.ACCEPTED;
    }

    /**
     * 出荷済みかを返す。
     *
     * @return 「出荷済み」なら true
     */
    public boolean isShipped() {
        return status == OrderStatus.SHIPPED;
    }

    /**
     * キャンセルを申請できるかを返す。「受付」で、まだ申請していない注文だけ（申請は 1 回だけ）。
     *
     * @return 申請できれば true
     */
    public boolean isCancellationRequestable() {
        return status == OrderStatus.ACCEPTED && cancellationRequest == null;
    }

    /**
     * キャンセルの申請が判断を待っているか（承認・却下できるか）を返す。
     *
     * @return 「キャンセル申請中」なら true
     */
    public boolean isCancellationPending() {
        return status == OrderStatus.CANCELLATION_REQUESTED;
    }

    /**
     * キャンセル済みかを返す。
     *
     * @return 「キャンセル」なら true
     */
    public boolean isCancelled() {
        return status == OrderStatus.CANCELLED;
    }

    /**
     * 消費者がキャンセルを申請する。「受付」から「キャンセル申請中」にする。<br />
     * 申請中なら何もしない（冪等）。申請は 1 回だけで、却下済み・キャンセル済みの注文は申請できない。
     *
     * @param reason 理由（任意、500 文字まで）。空白だけなら理由なし
     * @param now    現在時刻
     */
    public void requestCancellation(@Nullable String reason, @NonNull Instant now) {
        if (status == OrderStatus.CANCELLATION_REQUESTED) {
            return;
        }
        if (!isCancellationRequestable()) {
            throw BusinessException.of(OrderErrorCode.CANCELLATION_NOT_REQUESTABLE);
        }
        cancellationRequest = new CancellationRequest(now, optionalReason(reason), CancellationRequest.Result.PENDING,
                null, null);
        changeStatus(OrderStatus.CANCELLATION_REQUESTED, now);
    }

    /**
     * キャンセルの申請を承認し、キャンセルにする。キャンセル済みなら何もしない。
     *
     * @param operator 承認した運営者
     * @param now      現在時刻
     * @return キャンセルにしたら true（呼ぶ側が在庫を戻す）。すでにキャンセルなら false
     */
    public boolean approveCancellation(@NonNull Operator operator, @NonNull Instant now) {
        if (status == OrderStatus.CANCELLED) {
            return false;
        }
        if (!isCancellationPending()) {
            throw BusinessException.of(OrderErrorCode.CANCELLATION_NOT_PENDING);
        }
        decideRequest(CancellationRequest.Result.APPROVED, operator, now);
        cancel(new Cancellation(now, CancellationKind.REQUEST_APPROVED, null, operator));
        return true;
    }

    /**
     * キャンセルの申請を却下し、「受付」に戻す。申請中でない注文（キャンセル済みを含む）は却下できない。
     *
     * @param operator 却下した運営者
     * @param now      現在時刻
     */
    public void rejectCancellation(@NonNull Operator operator, @NonNull Instant now) {
        if (!isCancellationPending()) {
            throw BusinessException.of(OrderErrorCode.CANCELLATION_NOT_PENDING);
        }
        decideRequest(CancellationRequest.Result.REJECTED, operator, now);
        changeStatus(OrderStatus.ACCEPTED, now);
    }

    /**
     * 出荷する。「受付」から「出荷済み」にし、注文の出荷のイベントを登録する。売上の確定は呼ぶ側が先に済ませる。
     *
     * @param carrier        配送業者
     * @param trackingNumber 送り状番号（ハイフンを除いた数字）
     * @param now            現在時刻（出荷日時）
     */
    public void ship(@NonNull Carrier carrier, @NonNull String trackingNumber, @NonNull Instant now) {
        if (!isShippable()) {
            throw BusinessException.of(OrderErrorCode.ORDER_NOT_SHIPPABLE);
        }
        changeStatus(OrderStatus.SHIPPED, now);
        registerEvent(OrderShipped.of(now, id, orderNumber.value(), email, toEventCarrier(carrier),
                trackingNumber));
    }

    /**
     * 運営者の判断でキャンセルする。申請中なら、申請は承認されたものとする。<br />
     * キャンセル済みなら何もせず、理由も変えない。支払待ちはキャンセルできない（支払の期限で自動でキャンセルになる）。
     * 出荷済みは、配送状態が返送のときだけキャンセルでき、在庫に戻すかの指定が必要だ。出荷済みでなければ、指定によらず在庫に戻す。
     *
     * @param reason   理由（必須、500 文字まで）
     * @param restock  在庫に戻すか（出荷済みのときは必須。それ以外は使わない）
     * @param returned 配送状態が返送か（出荷済みのときだけ使う）
     * @param operator キャンセルした運営者。申請中なら、判断した運営者にもなる
     * @param now      現在時刻
     * @return キャンセルにしたら true（呼ぶ側は、キャンセルの記録に従って在庫を戻す）。すでにキャンセルなら false
     */
    public boolean cancelByOperator(@Nullable String reason, @Nullable Boolean restock, boolean returned, @NonNull Operator operator,
                                    @NonNull Instant now) {
        var required = optionalReason(reason);
        if (required == null) {
            throw BusinessException.of(OrderErrorCode.CANCELLATION_REASON_REQUIRED);
        }
        if (status == OrderStatus.CANCELLED) {
            return false;
        }
        if (status == OrderStatus.PENDING_PAYMENT) {
            throw BusinessException.of(OrderErrorCode.AWAITING_PAYMENT);
        }
        var restocked = true;
        if (status == OrderStatus.SHIPPED) {
            if (!returned) {
                throw BusinessException.of(OrderErrorCode.SHIPPED_ORDER_NOT_RETURNED);
            }
            if (restock == null) {
                throw BusinessException.of(OrderErrorCode.RESTOCK_CHOICE_REQUIRED);
            }
            restocked = restock;
        }
        if (status == OrderStatus.CANCELLATION_REQUESTED) {
            decideRequest(CancellationRequest.Result.APPROVED, operator, now);
        }
        cancel(new Cancellation(now, CancellationKind.OPERATOR, required, operator, restocked));
        return true;
    }

// ============================== プライベートメソッド ==============================

    /**
     * 申請に結果を記録する。
     */
    private void decideRequest(CancellationRequest.Result result, Operator operator, Instant now) {
        var request = Objects.requireNonNull(cancellationRequest, "申請中の注文には申請がある");
        cancellationRequest = new CancellationRequest(request.requestedAt(), request.reason(), result, now, operator);
    }

    /**
     * キャンセルにし、状態の変化のイベントにキャンセルの種類と在庫に戻したかを載せる。
     */
    private void cancel(Cancellation cancellation) {
        this.cancellation = cancellation;
        var from = status;
        status = OrderStatus.CANCELLED;
        registerEvent(OrderStatusChanged.of(cancellation.cancelledAt(), id, toEventStatus(from),
                OrderStatusChanged.Status.CANCELLED,
                toEventKind(cancellation.kind()), cancellation.restocked()));
    }

    /**
     * 状態を変え、状態の変化のイベントを登録する（キャンセルは {@link #cancel} で行う）。
     */
    private void changeStatus(OrderStatus to, Instant at) {
        var from = status;
        status = to;
        registerEvent(OrderStatusChanged.of(at, id, toEventStatus(from), toEventStatus(to), null, null));
    }

    /**
     * 注文の状態を、イベントの状態にする。
     */
    private static OrderStatusChanged.Status toEventStatus(OrderStatus status) {
        return switch (status) {
            case PENDING_PAYMENT -> OrderStatusChanged.Status.PENDING_PAYMENT;
            case ACCEPTED -> OrderStatusChanged.Status.ACCEPTED;
            case CANCELLATION_REQUESTED -> OrderStatusChanged.Status.CANCELLATION_REQUESTED;
            case CANCELLED -> OrderStatusChanged.Status.CANCELLED;
            case SHIPPED -> OrderStatusChanged.Status.SHIPPED;
        };
    }

    /**
     * キャンセルの種類を、イベントの種類にする。
     */
    private static OrderStatusChanged.CancellationKind toEventKind(CancellationKind kind) {
        return switch (kind) {
            case REQUEST_APPROVED -> OrderStatusChanged.CancellationKind.REQUEST_APPROVED;
            case OPERATOR -> OrderStatusChanged.CancellationKind.OPERATOR;
            case PAYMENT_EXPIRED -> OrderStatusChanged.CancellationKind.PAYMENT_EXPIRED;
        };
    }

    /**
     * 配送業者を、イベントの配送業者にする。
     */
    private static OrderShipped.Carrier toEventCarrier(Carrier carrier) {
        return switch (carrier) {
            case YAMATO -> OrderShipped.Carrier.YAMATO;
            case SAGAWA -> OrderShipped.Carrier.SAGAWA;
            case JAPAN_POST -> OrderShipped.Carrier.JAPAN_POST;
        };
    }

    /**
     * 理由を検証する。空白だけなら理由なし（null）とし、500 文字を超えれば拒否する。
     */
    private static @Nullable String optionalReason(@Nullable String reason) {
        if (reason == null || reason.isBlank()) {
            return null;
        }
        if (!BoundedText.fitsWithin(reason, REASON_MAX_LENGTH)) {
            throw BusinessException.of(OrderErrorCode.INVALID_CANCELLATION_REASON, List.of(REASON_MAX_LENGTH));
        }
        return reason;
    }

    /**
     * 見積もりの品目を、注文時点の写しの明細にする。
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
                Objects.requireNonNull(item.ratePercent(), "注文できる見積もりの品目には率がある"),
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
     * @param buyerProfile        注文時の年齢と性別
     * @param cancellationRequest キャンセルの申請
     * @param cancellation        キャンセル
     * @param version             バージョン
     */
    public record Snapshot(UUID id, OrderNumber orderNumber, UUID checkoutId, UUID memberId, Instant orderedAt, OrderStatus status,
                           List<OrderLine> lines, Amounts amounts, String email, ShippingAddress shippingAddress,
                           BuyerProfile buyerProfile, @Nullable CancellationRequest cancellationRequest, @Nullable Cancellation cancellation,
                           long version) {
    }

    /**
     * スナップショットを取得する
     *
     * @return 注文のスナップショット
     */
    public Snapshot toSnapshot() {
        return new Snapshot(id, orderNumber, checkoutId, memberId, orderedAt, status, lines, amounts, email, shippingAddress,
                buyerProfile, cancellationRequest, cancellation, version);
    }

    /**
     * スナップショットから復元する
     *
     * @param snapshot 注文のスナップショット
     * @return 注文
     */
    public static Order restore(Snapshot snapshot) {
        return new Order(snapshot.id(), snapshot.orderNumber(), snapshot.checkoutId(), snapshot.memberId(), snapshot.orderedAt(),
                snapshot.status(), snapshot.lines(), snapshot.amounts(), snapshot.email(), snapshot.shippingAddress(), snapshot.buyerProfile(),
                snapshot.cancellationRequest(), snapshot.cancellation(), snapshot.version());
    }
}
