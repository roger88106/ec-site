package com.ecsite.module.catalog.application.product;

import com.ecsite.module.catalog.application.port.MediaDirectory;
import com.ecsite.module.catalog.application.port.MediaDirectory.PreparedImage;
import com.ecsite.module.catalog.domain.product.port.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * 商品画像の追加の、画像を用意した後の保存。<br />
 * 画像の用意（変換と保存）はトランザクションの外で行うので、その前の確認（{@link ProductQueryService#verifyCanAddImage}）と、
 * その後の保存を、それぞれ 1 つのトランザクションにする。流れは {@link ProductImageService#addImage} が持つ。
 */
@Service
@RequiredArgsConstructor
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
     * 商品の取り出しと状態の切り替え
     */
    private final ProductLoader productLoader;

    /**
     * 用意した画像をアセットとして登録し、商品の画像の最後に加える。<br />
     * 用意の間にほかの操作が商品を変えていれば、バージョンの不一致で失敗する（アセットの記録も取り消される）。
     *
     * @param productId 商品 ID
     * @param version   取得時の商品のバージョン
     * @param prepared  用意した画像
     * @return 画像 ID
     */
    @Transactional
    public UUID saveAddedImage(UUID productId, long version, PreparedImage prepared) {
        var product = productLoader.get(productId, version);
        product.verifyCanAddImage();

        var assetId = mediaDirectory.registerImage(prepared);

        var imageId = UUID.randomUUID();
        product.addImage(imageId, assetId);
        productRepository.save(product);
        return imageId;
    }
}
