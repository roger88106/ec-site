package com.ecsite.module.order.application.checkout;

import com.ecsite.common.error.CommonErrorCode;
import com.ecsite.common.error.exception.BusinessException;
import com.ecsite.common.error.exception.SystemException;
import com.ecsite.module.order.application.model.command.PlaceOrderCommand;
import com.ecsite.module.order.application.model.view.PlacementResult;
import com.ecsite.module.order.application.port.CartDirectory;
import com.ecsite.module.order.application.port.PaymentDirectory;
import com.ecsite.module.order.application.port.RegionDirectory;
import com.ecsite.module.order.application.port.StockDirectory;
import com.ecsite.module.order.domain.OrderErrorCode;
import com.ecsite.module.order.domain.checkout.port.CheckoutRepository;
import com.ecsite.module.order.domain.order.BuyerProfile;
import com.ecsite.module.order.domain.order.Order;
import com.ecsite.module.order.domain.order.port.OrderRepository;
import com.ecsite.module.order.domain.order.vo.AddressArea;
import com.ecsite.module.order.domain.order.vo.OrderNumber;
import com.ecsite.module.order.domain.order.vo.ShippingAddress;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import java.util.random.RandomGenerator;

/**
 * 注文の成立のサービス
 *
 * <p>在庫の減算、注文の保存（支払待ち）、支払を作ることを、1 つのトランザクションで行う。カートは変えない。
 */
@Service
@RequiredArgsConstructor
public class OrderPlacementService {

    /**
     * 空いている注文番号を探す回数の上限（ランダムな 6 桁が 1 日に重なる確率は小さいので、通常は 1 回で済む）
     */
    private static final int ORDER_NUMBER_ATTEMPTS = 10;

    /**
     * 注文番号の乱数
     */
    private final RandomGenerator random = new SecureRandom();

    /**
     * 注文手続きリポジトリ
     */
    private final CheckoutRepository checkoutRepository;

    /**
     * 注文リポジトリ
     */
    private final OrderRepository orderRepository;

    /**
     * 支払待ちの注文の判定
     */
    private final PendingOrderGuard pendingOrderGuard;

    /**
     * カートの腐敗防止層
     */
    private final CartDirectory cartDirectory;

    /**
     * 在庫の腐敗防止層
     */
    private final StockDirectory stockDirectory;

    /**
     * 決済の腐敗防止層
     */
    private final PaymentDirectory paymentDirectory;

    /**
     * 地域の腐敗防止層
     */
    private final RegionDirectory regionDirectory;

    /**
     * 見積もりのサービス
     */
    private final QuoteService quoteService;

    /**
     * 時計
     */
    private final Clock clock;

    /**
     * 注文する。どこかで失敗すれば、トランザクションごと取り消される。同じ注文手続きでの再送には、作成済みの注文を返す。
     *
     * @param command 注文のコマンド
     * @return 注文の結果
     * @throws BusinessException 注文手続きがない、または始めた会員でない（{@code NOT_FOUND}）。メールアドレスが確認済みでない（{@code EMAIL_NOT_VERIFIED}）。
     *                           確認していない支払待ちの注文がある（{@code PENDING_ORDER_CONFIRMATION_REQUIRED}）。
     *                           支払待ちの注文が上限に達している（{@code PENDING_ORDER_LIMIT_EXCEEDED}）。
     *                           カートがない、または注文できない（{@code CART_NOT_ORDERABLE}）。カートが変わった（{@code CART_CHANGED}）。
     *                           在庫が足りない（{@code OUT_OF_STOCK}）。総額が変わった（{@code TOTAL_AMOUNT_CHANGED}）。
     *                           入力が正しくない（{@code INVALID_EMAIL}、{@code INVALID_RECIPIENT_NAME}、{@code INVALID_POSTAL_CODE}、
     *                           {@code INVALID_PREFECTURE}、{@code INVALID_MUNICIPALITY}、{@code INVALID_ADDRESS}、{@code INVALID_PHONE_NUMBER}）。
     *                           注文番号が同時に使われた（{@code CONCURRENT_MODIFICATION}）
     */
    @Transactional
    public PlacementResult place(@NonNull PlaceOrderCommand command) {
        // 会員のロック ※同じ会員の注文を 1 つずつ処理する。支払待ちの注文を読む前に取るので、ロックを待った後の読み取りに先の注文が見える
        orderRepository.lockForMember(command.memberId());
        // 時刻はロックを待った後に取る
        var now = clock.instant();

        // 注文手続き取得 ※同じ注文手続きでの送信を 1 つずつ処理する。2 回目以降は作成済みの注文を返す
        var checkout = checkoutRepository.lockById(command.checkoutId())
                // 始めた会員だけが注文できる。ほかの会員には、ないときと同じにする
                .filter(found -> found.isOwnedBy(command.memberId()))
                .orElseThrow(() -> BusinessException.of(CommonErrorCode.NOT_FOUND));
        // 連絡先は確認済みのメールアドレスだけ（IdP の設定で確認前はログインできないが、念のため）
        if (!command.emailVerified()) {
            throw BusinessException.of(OrderErrorCode.EMAIL_NOT_VERIFIED);
        }
        var existing = orderRepository.findIdByCheckoutId(checkout.id());
        if (existing.isPresent()) {
            return new PlacementResult(existing.get(), false);
        }

        // 支払待ちの注文の確認 ※再送は注文の状態と上限によらず作成済みの注文を返すので、作成済みの注文があるかの確認の後に行う（確認の判定、上限の判定の順）。
        // 会員のロックの中で判定するので、同時の注文でも支払待ちの注文は上限を超えない
        pendingOrderGuard.verifyAcknowledged(checkout);

        // 判定：カートの変更、購入できるか、確認した総額 ※書き込みの前に全ての判定を済ませる
        var cart = cartDirectory.find(checkout.cartId())
                .orElseThrow(() -> BusinessException.of(OrderErrorCode.CART_NOT_ORDERABLE));
        if (checkout.cartChangedSince(cart.version())) {
            throw BusinessException.of(OrderErrorCode.CART_CHANGED);
        }
        var quote = quoteService.quote(cart.lines(), now, checkout.id());
        CheckoutService.verifyOrderable(quote);
        if (!quote.amounts().total().equals(command.expectedTotal())) {
            throw BusinessException.of(OrderErrorCode.TOTAL_AMOUNT_CHANGED);
        }

        // 注文作成 ※メールアドレスは、会員の token のもの（確認済み）を写す
        var address = ShippingAddress.of(command.name(), command.postalCode(),
                addressArea(command.prefectureCode(), command.municipalityCode()), command.address(), command.phone());
        var order = Order.place(UUID.randomUUID(), unusedOrderNumber(now), checkout.id(), checkout.memberId(), now, quote,
                command.email(),
                address,
                BuyerProfile.of(command.birthdate(), command.gender(), now));

        // 書き込み：在庫の減算（期限切れなら引き当て直し）→ 注文（支払待ち）→ 支払
        var shortages = stockDirectory.commit(checkout.id(), CheckoutService.quantities(cart.lines()), order.id(),
                order.orderNumber().value());
        if (!shortages.isEmpty()) {
            throw BusinessException.of(OrderErrorCode.OUT_OF_STOCK);
        }
        if (!orderRepository.insert(order)) {
            // 番号を確認してから保存するまでの間に、同じ番号が使われた（ごくまれ）。やり直してもらう
            throw BusinessException.of(CommonErrorCode.CONCURRENT_MODIFICATION);
        }
        paymentDirectory.create(order.id(), order.amounts().total(), order.orderedAt());
        return new PlacementResult(order.id(), true);
    }

    /**
     * お届け先の都道府県と市区町村を、地域のマスターデータで確認し、名前を写す。
     *
     * @param prefectureCode   都道府県のコード
     * @param municipalityCode 市区町村のコード
     * @return 都道府県と市区町村
     * @throws BusinessException 都道府県がない（{@code INVALID_PREFECTURE}）。市区町村がない、または都道府県に属さない（{@code INVALID_MUNICIPALITY}）
     */
    private AddressArea addressArea(String prefectureCode, String municipalityCode) {
        var prefectureName = regionDirectory.findPrefectureName(prefectureCode)
                .orElseThrow(() -> BusinessException.of(OrderErrorCode.INVALID_PREFECTURE));
        var municipality = regionDirectory.findMunicipality(municipalityCode)
                .filter(found -> found.prefectureCode().equals(prefectureCode))
                .orElseThrow(() -> BusinessException.of(OrderErrorCode.INVALID_MUNICIPALITY));
        return new AddressArea(prefectureCode, prefectureName, municipality.code(), municipality.name());
    }

    /**
     * まだ使われていない注文番号を作る。
     *
     * @param orderedAt 注文日時
     * @return 注文番号
     */
    private OrderNumber unusedOrderNumber(Instant orderedAt) {
        for (int i = 0; i < ORDER_NUMBER_ATTEMPTS; i++) {
            var number = OrderNumber.generate(orderedAt, random);
            if (!orderRepository.existsByOrderNumber(number)) {
                return number;
            }
        }
        throw SystemException.of("空いている注文番号が見つかりません: " + orderedAt, null);
    }
}
