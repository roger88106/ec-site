package com.ecsite.module.cart.application;

import com.ecsite.common.error.exception.SystemException;
import com.ecsite.module.cart.application.port.CartRepository;
import com.ecsite.module.cart.application.port.CatalogDirectory;
import com.ecsite.module.cart.application.port.StockDirectory;
import com.ecsite.module.cart.domain.Cart;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Clock;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * カートを作れないときの例外の単体テスト。ゲストのカートの ID と会員 ID は利用者を指すので、メッセージに入れない。
 */
class CartServiceFailureMessageTest {

    /**
     * カートのリポジトリ（保存しない mock）
     */
    private final CartRepository cartRepository = mock(CartRepository.class);

    /**
     * カートのサービス
     */
    private CartService service;

    @BeforeEach
    void setUp() {
        service = new CartService(cartRepository, mock(CatalogDirectory.class), mock(StockDirectory.class),
                mock(ApplicationEventPublisher.class), Clock.systemUTC());
        when(cartRepository.create(any())).thenReturn(false);
    }

    @Test
    @DisplayName("ゲストのカートを保存できないとき、例外のメッセージにカートの ID が出ない")
    void hidesCartId() {
        var thrown = catchThrowable(() -> service.createCart());

        var cart = ArgumentCaptor.forClass(Cart.class);
        verify(cartRepository).create(cart.capture());
        assertThat(thrown).isInstanceOf(SystemException.class);
        assertThat(thrown.getMessage()).doesNotContain(cart.getValue().id().toString());
    }

    @Test
    @DisplayName("会員のカートを作れないとき、例外のメッセージに会員 ID が出ない")
    void hidesMemberId() {
        var memberId = UUID.randomUUID();
        when(cartRepository.findIdByMemberId(memberId)).thenReturn(Optional.empty());

        var thrown = catchThrowable(() -> service.memberCartId(memberId));

        assertThat(thrown).isInstanceOf(SystemException.class);
        assertThat(thrown.getMessage()).doesNotContain(memberId.toString());
    }
}
