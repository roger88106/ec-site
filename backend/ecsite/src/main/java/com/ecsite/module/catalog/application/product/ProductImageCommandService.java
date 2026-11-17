package com.ecsite.module.catalog.application.product;

import com.ecsite.common.error.exception.BusinessException;
import com.ecsite.module.catalog.application.model.PreparedImage;
import com.ecsite.module.catalog.application.port.MediaDirectory;
import com.ecsite.module.catalog.domain.product.port.ProductRepository;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * 商品画像のコマンドサービス
 *
 * <p>画像を用意した後の保存と、画像の並び順・削除・バリアントの画像の指定を、それぞれ 1 つのトランザクションで行う。
 */
@Service
@RequiredArgsConstructor
@Transactional
public class ProductImageCommandService {

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
     * @throws BusinessException 商品がない（{@code NOT_FOUND}）
     * @throws BusinessException 取得した後に他で更新された（{@code CONCURRENT_MODIFICATION}。アセットの記録も取り消される）
     * @throws BusinessException 画像が上限に達している（{@code TOO_MANY_PRODUCT_IMAGES}）
     */
    public UUID saveAddedImage(@NonNull UUID productId, long version, @NonNull PreparedImage prepared) {
        // 商品取得と枚数の確認
        var product = productLoader.get(productId, version);
        product.verifyCanAddImage();

        // アセットの登録
        mediaDirectory.registerImage(prepared);

        // 画像の追加と商品保存
        var imageId = UUID.randomUUID();
        product.addImage(imageId, prepared.assetId());
        productRepository.save(product);
        return imageId;
    }

    /**
     * 画像の並び順を変更する。
     *
     * @param productId 商品 ID
     * @param imageIds  並べたい順の画像 ID（全て）
     * @param version   取得時の商品のバージョン
     * @throws BusinessException 商品がない（{@code NOT_FOUND}）
     * @throws BusinessException 取得した後に他で更新された（{@code CONCURRENT_MODIFICATION}）
     * @throws BusinessException 全ての画像と過不足なく一致しない（{@code INVALID_IMAGE_ORDER}）
     */
    public void reorderImages(@NonNull UUID productId, @NonNull List<UUID> imageIds, long version) {
        // 商品取得
        var product = productLoader.get(productId, version);

        // 並び順変更
        product.reorderImages(imageIds);

        // 商品保存
        productRepository.save(product);
    }

    /**
     * 画像を削除する。
     *
     * <p>同じトランザクションの中でアセットを手放す。
     *
     * @param productId 商品 ID
     * @param imageId   画像 ID
     * @param version   取得時の商品のバージョン
     * @throws BusinessException 商品か画像がない（{@code NOT_FOUND}）
     * @throws BusinessException 取得した後に他で更新された（{@code CONCURRENT_MODIFICATION}）
     */
    public void deleteImage(@NonNull UUID productId, @NonNull UUID imageId, long version) {
        // 商品取得
        var product = productLoader.get(productId, version);

        // 画像削除
        var assetId = product.deleteImage(imageId);

        // 商品保存と、画像のアセットの解放
        productRepository.save(product);
        mediaDirectory.release(List.of(assetId));
    }

    /**
     * バリアントの画像を指定する。
     *
     * @param productId 商品 ID
     * @param variantId バリアント ID
     * @param imageId   画像 ID。null で指定を外す
     * @param version   取得時の商品のバージョン
     * @throws BusinessException 商品かバリアントがない（{@code NOT_FOUND}）
     * @throws BusinessException 取得した後に他で更新された（{@code CONCURRENT_MODIFICATION}）
     * @throws BusinessException 商品にない画像（{@code INVALID_VARIANT_IMAGE}）
     */
    public void setVariantImage(@NonNull UUID productId, @NonNull UUID variantId, @Nullable UUID imageId, long version) {
        // 商品取得
        var product = productLoader.get(productId, version);

        // バリアントの画像の指定
        product.setVariantImage(variantId, imageId);

        // 商品保存
        productRepository.save(product);
    }
}
