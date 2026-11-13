package com.ecsite.module.catalog.application.product;

import com.ecsite.common.error.exception.BusinessException;
import com.ecsite.module.catalog.application.model.view.ImageUploadView;
import com.ecsite.module.catalog.application.port.MediaDirectory;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * 商品画像のサービス
 *
 * <p>ファイルの受け付け・検証・配信・削除はメディアモジュールが行い、ここでは商品の規則を扱う。
 * メディアの呼び出しをトランザクションの外で行う受付と追加を受け持ち、保存は {@link ProductImageCommandService} に任せる。
 */
@Service
@RequiredArgsConstructor
public class ProductImageService {

    /**
     * メディアの腐敗防止層
     */
    private final MediaDirectory mediaDirectory;

    /**
     * 商品の参照のサービス（画像を加えられることの確認）
     */
    private final ProductQueryService queryService;

    /**
     * 商品画像のコマンドサービス（追加の、トランザクションの中の処理）
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
     * @throws BusinessException 形式が対象外
     * @throws BusinessException 大きさが範囲の外
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
     * @throws BusinessException 受付がない
     * @throws BusinessException 大きさが範囲の外
     * @throws BusinessException 中身が対象の画像でない
     * @throws BusinessException 変換できない
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
}
