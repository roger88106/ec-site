package com.ecsite.module.catalog.application.product;

import com.ecsite.common.error.exception.BusinessException;
import com.ecsite.module.catalog.application.model.PreparedImage;
import com.ecsite.module.catalog.application.port.MediaDirectory;
import com.ecsite.module.catalog.domain.product.port.ProductRepository;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * 商品画像の追加の保存
 *
 * <p>画像を用意した後の保存を、1 つのトランザクションで行う。
 */
@Service
@RequiredArgsConstructor
class ProductImageCommandService {

    /**
     * 商品リポジトリ
     */
    private final ProductRepository productRepository;

    /**
     * メディアの腐敗防止層
     */
    private final MediaDirectory mediaDirectory;

    /**
     * 商品の取得と状態の切り替え
     */
    private final ProductLoader productLoader;

    /**
     * 用意した画像をアセットとして登録し、商品の画像の最後に加える。
     *
     * @param productId 商品 ID
     * @param version   取得時の商品のバージョン
     * @param prepared  用意した画像
     * @return 画像 ID
     * @throws BusinessException 商品がない（{@code NOT_FOUND}）。取得した後に他で更新された（{@code CONCURRENT_MODIFICATION}。
     *                           アセットの記録も取り消される）。画像が上限に達している（{@code TOO_MANY_PRODUCT_IMAGES}）
     */
    @Transactional
    public UUID saveAddedImage(@NonNull UUID productId, long version, @NonNull PreparedImage prepared) {
        // 商品取得と枚数の確認
        var product = productLoader.get(productId, version);
        product.verifyCanAddImage();

        // アセットの登録
        var assetId = mediaDirectory.registerImage(prepared);

        // 画像の追加と商品保存
        var imageId = UUID.randomUUID();
        product.addImage(imageId, assetId);
        productRepository.save(product);
        return imageId;
    }
}
