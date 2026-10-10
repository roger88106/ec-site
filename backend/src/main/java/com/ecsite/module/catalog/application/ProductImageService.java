package com.ecsite.module.catalog.application;

import com.ecsite.common.error.CommonErrorCode;
import com.ecsite.common.error.exception.BusinessException;
import com.ecsite.module.catalog.application.model.view.ImageUploadView;
import com.ecsite.module.catalog.domain.product.Product;
import com.ecsite.module.catalog.domain.product.ProductErrorCode;
import com.ecsite.module.catalog.domain.product.port.ImageStorage;
import com.ecsite.module.catalog.domain.product.port.ProductRepository;
import com.ecsite.module.catalog.domain.product.vo.ImageFormat;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

/**
 * 商品画像のアプリケーションサービス（US-7。plan 001 第 7 節）。<br />
 * ファイルは署名付き URL でブラウザから受付のバケットへ直接送られ、登録のときに形式を確かめて配信のバケットへコピーする。
 */
@Service
@RequiredArgsConstructor
public class ProductImageService {

    /**
     * 商品リポジトリ
     */
    private final ProductRepository productRepository;

    /**
     * 画像の置き場所
     */
    private final ImageStorage imageStorage;

    /**
     * 時計
     */
    private final Clock clock;

    /**
     * 画像のアップロードを受け付ける（AC-39、40）。形式・大きさ・枚数を確かめ、署名付き URL を発行する。<br />
     * 受付は DB に記録しない（受付のキーに商品 ID を含める。登録されなかったファイルはライフサイクル規則で消える）。
     *
     * @param productId     商品 ID
     * @param contentType   ファイルの形式（申告）
     * @param contentLength ファイルの大きさ（申告）
     * @return 受付
     */
    public ImageUploadView createUpload(UUID productId, String contentType, long contentLength) {
        var product = findProduct(productId);
        product.verifyCanAddImage();
        var format = ImageFormat.ofContentType(contentType)
                .orElseThrow(() -> BusinessException.of(ProductErrorCode.UNSUPPORTED_IMAGE_FORMAT));
        verifySize(contentLength);

        var uploadId = UUID.randomUUID();
        var presigned = imageStorage.presignUpload(ProductImageKeys.upload(productId, uploadId), format.contentType(),
                contentLength);
        return new ImageUploadView(uploadId, presigned.url(), presigned.headers(), presigned.expiresAt());
    }

    /**
     * 受け付けた画像を登録する（AC-37、39、40）。<br />
     * 受付のファイルの大きさと、先頭のバイトで判定した形式を確かめ（申告は信用しない）、配信のバケットへコピーしてから、
     * 商品の画像の最後に加える。コピーの後に保存が失敗すると配信のファイルが残るが、だれからも参照されないだけで害はない。
     *
     * @param productId 商品 ID
     * @param uploadId  受付 ID
     * @param version   取得時の商品のバージョン
     * @return 画像 ID
     */
    @Transactional
    public UUID addImage(UUID productId, UUID uploadId, long version) {
        var product = getProduct(productId, version);
        product.verifyCanAddImage();

        var uploadKey = ProductImageKeys.upload(productId, uploadId);
        var size = imageStorage.findUploadSize(uploadKey)
                .orElseThrow(() -> BusinessException.of(ProductErrorCode.IMAGE_UPLOAD_NOT_FOUND));
        verifySize(size);
        var format = ImageFormat.detect(imageStorage.readUploadHead(uploadKey, ImageFormat.SIGNATURE_LENGTH))
                .orElseThrow(() -> BusinessException.of(ProductErrorCode.INVALID_IMAGE_FILE));

        var imageId = UUID.randomUUID();
        var publicKey = ProductImageKeys.published(productId, imageId, format);
        imageStorage.publish(uploadKey, publicKey, format.contentType());

        product.addImage(imageId, publicKey);
        productRepository.save(product);
        return imageId;
    }

    /**
     * 画像の並び順を変更する（AC-37）。
     *
     * @param productId 商品 ID
     * @param imageIds  並べたい順の画像 ID（すべて）
     * @param version   取得時の商品のバージョン
     */
    @Transactional
    public void reorderImages(UUID productId, List<UUID> imageIds, long version) {
        var product = getProduct(productId, version);

        product.reorderImages(imageIds);

        productRepository.save(product);
    }

    /**
     * 画像を削除する。配信のファイルは、商品画像削除のイベントで消す。
     *
     * @param productId 商品 ID
     * @param imageId   画像 ID
     * @param version   取得時の商品のバージョン
     */
    @Transactional
    public void deleteImage(UUID productId, UUID imageId, long version) {
        var product = getProduct(productId, version);

        product.deleteImage(imageId, clock.instant());

        productRepository.save(product);
    }

    /**
     * バリアントの画像を指定する（AC-38）。
     *
     * @param productId 商品 ID
     * @param variantId バリアント ID
     * @param imageId   画像 ID。null で指定を外す
     * @param version   取得時の商品のバージョン
     */
    @Transactional
    public void setVariantImage(UUID productId, UUID variantId, UUID imageId, long version) {
        var product = getProduct(productId, version);

        product.setVariantImage(variantId, imageId);

        productRepository.save(product);
    }

// ============================== プライベートメソッド ==============================

    /**
     * 大きさが 1 バイト以上、上限（10MB）以下であることを確かめる（AC-39）。
     */
    private static void verifySize(long size) {
        if (size <= 0 || size > ImageFormat.MAX_BYTES) {
            throw BusinessException.of(ProductErrorCode.IMAGE_TOO_LARGE);
        }
    }

    private Product findProduct(UUID productId) {
        return productRepository.findById(productId)
                .orElseThrow(() -> BusinessException.of(CommonErrorCode.NOT_FOUND));
    }

    /**
     * 商品を取得し、取得時のバージョンと一致することを確かめる。
     */
    private Product getProduct(UUID productId, long version) {
        var product = findProduct(productId);
        // 取得時のバージョンと一致しなければ、他の人が先に更新している
        if (product.version() != version) {
            throw BusinessException.of(CommonErrorCode.CONCURRENT_MODIFICATION);
        }
        return product;
    }
}
