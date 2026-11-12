package com.ecsite.module.catalog.application.product;

import com.ecsite.common.error.exception.BusinessException;
import com.ecsite.module.catalog.application.model.view.ImageUploadView;
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
 * 商品画像のサービス
 *
 * <p>ファイルの受け付け・検証・配信・削除はメディアモジュールが行い、ここでは商品の規則を扱う。
 */
@Service
@RequiredArgsConstructor
public class ProductImageService {

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
     * 商品の参照のサービス（画像を加えられることの確認）
     */
    private final ProductQueryService queryService;

    /**
     * 商品画像の追加の、トランザクションの中の処理
     */
    private final ProductImageCommandService commandService;

    /**
     * 画像のアップロードを受け付ける。
     *
     * <p>受付は DB に記録しない（登録されなかったファイルはライフサイクル規則で消える）。
     *
     * @param productId     商品 ID
     * @param contentType   ファイルの形式（申告）
     * @param contentLength ファイルの大きさ（申告）
     * @return 受付
     * @throws BusinessException 商品がない（{@code NOT_FOUND}）
     * @throws BusinessException 画像が上限に達している（{@code TOO_MANY_PRODUCT_IMAGES}）
     * @throws BusinessException 形式が対象外、または大きさが範囲の外
     */
    public ImageUploadView createUpload(@NonNull UUID productId, @NonNull String contentType, long contentLength) {
        // 枚数の確認
        queryService.verifyCanAddImage(productId);

        // 受付
        return mediaDirectory.requestImageUpload(productId, contentType, contentLength);
    }

    /**
     * 受け付けた画像を登録する。
     *
     * @param productId 商品 ID
     * @param uploadId  受付 ID
     * @param version   取得時の商品のバージョン
     * @return 画像 ID
     * @throws BusinessException 商品がない（{@code NOT_FOUND}）
     * @throws BusinessException 取得した後に他で更新された（{@code CONCURRENT_MODIFICATION}）
     * @throws BusinessException 画像が上限に達している（{@code TOO_MANY_PRODUCT_IMAGES}）
     * @throws BusinessException 受付がない、大きさが範囲の外、中身が対象の画像でない、または変換できない
     */
    public UUID addImage(@NonNull UUID productId, @NonNull UUID uploadId, long version) {
        // 加えられることの確認 ※画像の変換と保存の間に DB の接続を持たないよう、確認・用意・保存でトランザクションを分ける
        queryService.verifyCanAddImage(productId, version);

        // 画像の用意（トランザクションの外）
        var prepared = mediaDirectory.prepareImage(productId, uploadId);

        // アセットの登録と商品への追加（1 つのトランザクション）
        try {
            return commandService.saveAddedImage(productId, version, prepared);
        } catch (RuntimeException e) {
            // 保存が失敗したら、用意した配信のファイルを消し、元の失敗を投げ直す
            mediaDirectory.discardImage(prepared);
            throw e;
        }
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
    @Transactional
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
    @Transactional
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
    @Transactional
    public void setVariantImage(@NonNull UUID productId, @NonNull UUID variantId, @Nullable UUID imageId, long version) {
        // 商品取得
        var product = productLoader.get(productId, version);

        // バリアントの画像の指定
        product.setVariantImage(variantId, imageId);

        // 商品保存
        productRepository.save(product);
    }

}
