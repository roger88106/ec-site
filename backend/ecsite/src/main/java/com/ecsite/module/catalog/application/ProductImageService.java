package com.ecsite.module.catalog.application;

import com.ecsite.common.error.CommonErrorCode;
import com.ecsite.common.error.exception.BusinessException;
import com.ecsite.module.catalog.application.model.view.ImageUploadView;
import com.ecsite.module.catalog.application.port.MediaDirectory;
import com.ecsite.module.catalog.domain.product.Product;
import com.ecsite.module.catalog.domain.product.port.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * 商品画像のアプリケーションサービス（US-7。plan 001 第 7 節）。<br />
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
     * 画像のアップロードを受け付ける（AC-39、40）。枚数を確かめ、形式と大きさはメディアモジュールが確かめる。<br />
     * 受付は DB に記録しない（受付の範囲を商品 ID にする。登録されなかったファイルはライフサイクル規則で消える）。
     *
     * @param productId     商品 ID
     * @param contentType   ファイルの形式（申告）
     * @param contentLength ファイルの大きさ（申告）
     * @return 受付
     */
    public ImageUploadView createUpload(UUID productId, String contentType, long contentLength) {
        var product = findProduct(productId);
        product.verifyCanAddImage();

        var upload = mediaDirectory.requestImageUpload(productId, contentType, contentLength);
        return new ImageUploadView(upload.uploadId(), upload.uploadUrl(), upload.headers(), upload.expiresAt());
    }

    /**
     * 受け付けた画像を登録する（AC-37、39、40）。<br />
     * 同じトランザクションの中で、メディアモジュールにアセットを確定させ（ファイルの中身を確かめる）、商品の画像の最後に加える。
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

        var assetId = mediaDirectory.confirmImage(productId, uploadId);

        var imageId = UUID.randomUUID();
        product.addImage(imageId, assetId);
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
     * 画像を削除する。同じトランザクションの中でアセットを手放す（配信のファイルは、コミットの後にメディアモジュールが消す）。
     *
     * @param productId 商品 ID
     * @param imageId   画像 ID
     * @param version   取得時の商品のバージョン
     */
    @Transactional
    public void deleteImage(UUID productId, UUID imageId, long version) {
        var product = getProduct(productId, version);

        var assetId = product.deleteImage(imageId);

        productRepository.save(product);
        mediaDirectory.release(List.of(assetId));
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
     * 商品を取得する。なければ 404 の業務のエラーにする。
     */
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
