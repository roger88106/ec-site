package com.ecsite.module.catalog.application;

import com.ecsite.common.error.CommonErrorCode;
import com.ecsite.common.error.exception.BusinessException;
import com.ecsite.module.catalog.application.model.view.ImageUploadView;
import com.ecsite.module.catalog.application.port.MediaDirectory;
import com.ecsite.module.catalog.domain.product.Product;
import com.ecsite.module.catalog.domain.product.port.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * 商品画像のアプリケーションサービス。<br />
 * ファイルの受け付け・検証・配信・削除はメディアモジュールが行い、ここでは商品の規則（枚数、並び順、バリアントの画像）を扱う。
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
     * 商品の取り出しと状態の切り替え
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
     * 画像のアップロードを受け付ける。枚数を確認し、形式と大きさはメディアモジュールが確認する。<br />
     * 受付は DB に記録しない（受付の範囲を商品 ID にする。登録されなかったファイルはライフサイクル規則で消える）。
     *
     * @param productId     商品 ID
     * @param contentType   ファイルの形式（申告）
     * @param contentLength ファイルの大きさ（申告）
     * @return 受付
     */
    @Transactional(readOnly = true)
    public ImageUploadView createUpload(UUID productId, String contentType, long contentLength) {
        var product = findProduct(productId);
        product.verifyCanAddImage();

        var upload = mediaDirectory.requestImageUpload(productId, contentType, contentLength);
        return new ImageUploadView(upload.uploadId(), upload.uploadUrl(), upload.headers(), upload.expiresAt());
    }

    /**
     * 受け付けた画像を登録する。<br />
     * 画像の変換と保存の間に DB の接続を持たないよう、トランザクションを分ける：
     * 加えられることを確認する → 画像を用意する（トランザクションの外）→ アセットの登録と商品への追加（1 つのトランザクション）。
     * 保存が失敗したら、用意した配信のファイルを消し、元の失敗を投げ直す。
     *
     * @param productId 商品 ID
     * @param uploadId  受付 ID
     * @param version   取得時の商品のバージョン
     * @return 画像 ID
     */
    public UUID addImage(UUID productId, UUID uploadId, long version) {
        queryService.verifyCanAddImage(productId, version);

        var prepared = mediaDirectory.prepareImage(productId, uploadId);
        try {
            return commandService.saveAddedImage(productId, version, prepared);
        } catch (RuntimeException ex) {
            mediaDirectory.discardImage(prepared);
            throw ex;
        }
    }

    /**
     * 画像の並び順を変更する。
     *
     * @param productId 商品 ID
     * @param imageIds  並べたい順の画像 ID（すべて）
     * @param version   取得時の商品のバージョン
     */
    @Transactional
    public void reorderImages(UUID productId, List<UUID> imageIds, long version) {
        var product = productLoader.get(productId, version);

        product.reorderImages(imageIds);

        productRepository.save(product);
    }

    /**
     * 画像を削除する。同じトランザクションの中でアセットを手放す（配信のファイルは、コミットの後にメディアモジュールが消す）。
     *
     * @param productId 商品 ID
     * @param imageId   画像 ID
     * @param version   取得時の商品のバージョン
     */
    @Transactional
    public void deleteImage(UUID productId, UUID imageId, long version) {
        var product = productLoader.get(productId, version);

        var assetId = product.deleteImage(imageId);

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
     */
    @Transactional
    public void setVariantImage(UUID productId, UUID variantId, @Nullable UUID imageId, long version) {
        var product = productLoader.get(productId, version);

        product.setVariantImage(variantId, imageId);

        productRepository.save(product);
    }

// ============================== プライベートメソッド ==============================

    /**
     * 商品を取得する。なければ 404 の業務のエラーにする。
     */
    private Product findProduct(UUID productId) {
        return productRepository.findById(productId)
                .orElseThrow(() -> BusinessException.of(CommonErrorCode.NOT_FOUND));
    }

}
