package com.ecsite.module.catalog.application.product;

import com.ecsite.common.error.CommonErrorCode;
import com.ecsite.common.error.exception.BusinessException;
import com.ecsite.module.catalog.application.model.PreparedImage;
import com.ecsite.module.catalog.application.port.MediaDirectory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 商品画像のゲートウェイサービスの単体テスト
 */
class ProductImageGatewayServiceTest {

    // ============================== Bean ==============================

    /**
     * メディアの腐敗防止層（mock）
     */
    private final MediaDirectory mediaDirectory = mock(MediaDirectory.class);

    /**
     * 商品画像のコマンドサービス（mock）
     */
    private final ProductImageCommandService commandService = mock(ProductImageCommandService.class);

    /**
     * 商品画像のゲートウェイサービス
     */
    private final ProductImageGatewayService service =
            new ProductImageGatewayService(mediaDirectory, mock(ProductQueryService.class), commandService);

    // ============================== テスト ==============================

    @Test
    @DisplayName("DC-489-6 保存が競合で失敗し、破棄も失敗したとき、競合の例外を投げ、破棄の例外は抑制された例外にある")
    void keepsOriginalExceptionWhenDiscardFails() {
        // 準備：保存が競合の例外を投げ、破棄も例外を投げる
        var productId = UUID.randomUUID();
        var prepared = new PreparedImage(UUID.randomUUID());
        var conflict = BusinessException.of(CommonErrorCode.CONCURRENT_MODIFICATION);
        var discardFailure = new IllegalStateException("discard failed");
        when(mediaDirectory.prepareImage(any(), any())).thenReturn(prepared);
        when(commandService.saveAddedImage(productId, 1L, prepared)).thenThrow(conflict);
        doThrow(discardFailure).when(mediaDirectory).discardImage(prepared);

        // 操作：画像を追加する
        var thrown = catchThrowable(() -> service.addImage(productId, UUID.randomUUID(), 1L));

        // 確認：競合の例外が投げられ、破棄の例外は抑制された例外にある
        assertThat(thrown).isSameAs(conflict);
        assertThat(thrown.getSuppressed()).containsExactly(discardFailure);
    }
}
