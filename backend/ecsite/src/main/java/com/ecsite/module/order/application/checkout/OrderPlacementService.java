package com.ecsite.module.order.application.checkout;

import com.ecsite.common.error.CommonErrorCode;
import com.ecsite.common.error.exception.BusinessException;
import com.ecsite.common.error.exception.SystemException;
import com.ecsite.module.order.application.model.PlaceOrderCommand;
import com.ecsite.module.order.application.model.PlacementResult;
import com.ecsite.module.order.application.port.CartDirectory;
import com.ecsite.module.order.application.port.PaymentDirectory;
import com.ecsite.module.order.application.port.StockDirectory;
import com.ecsite.module.order.domain.checkout.port.CheckoutRepository;
import com.ecsite.module.order.domain.order.Order;
import com.ecsite.module.order.domain.order.OrderErrorCode;
import com.ecsite.module.order.domain.order.port.OrderRepository;
import com.ecsite.module.order.domain.order.vo.BuyerProfile;
import com.ecsite.module.order.domain.order.vo.OrderNumber;
import com.ecsite.module.order.domain.order.vo.ShippingAddress;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import java.util.random.RandomGenerator;

/**
 * 注文の成立のサービス（「注文の成立」）。<br />
 * 在庫の減算、注文の保存（支払待ち）、支払を作ることを、1 つのトランザクションで行う。カートは変えない（与信の成功で品目を除く）。
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
     * 見積もりのサービス
     */
    private final QuoteService quoteService;

    /**
     * 時計
     */
    private final Clock clock;

    /**
     * 注文する。<br />
     * 判定（カートの変更、購入できるか、総額、入力）をすべて済ませてから、在庫の減算 → 注文の保存（支払待ち） → 支払を作る、の順に書く。
     * どこかで失敗すれば、トランザクションごと取り消される。
     * 注文できるのは注文手続きを始めた会員だけで、メールアドレスは会員の token のもの（確認済み）を写す。
     *
     * @param command 注文のコマンド
     * @return 注文の結果
     */
    @Transactional
    public PlacementResult place(PlaceOrderCommand command) {
        var now = clock.instant();
        // 同じ注文手続きでの送信を 1 つずつ処理する。2 回目以降は作成済みの注文を返す
        var checkout = checkoutRepository.lockById(command.checkoutId())
                // 始めた会員だけが注文できる。ほかの会員には 404
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

        // 判定：カートの変更、購入できるか、確認した総額
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
        var address = ShippingAddress.of(command.name(), command.postalCode(), command.prefecture(), command.address(),
                command.phone());
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
            // 番号を確かめてから保存するまでの間に、同じ番号が使われた（ごくまれ）。やり直してもらう
            throw BusinessException.of(CommonErrorCode.CONCURRENT_MODIFICATION);
        }
        paymentDirectory.create(order.id(), order.amounts().total(), order.orderedAt());
        return new PlacementResult(order.id(), true);
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
