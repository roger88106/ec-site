package com.ecsite.module.catalog.application.product;

import com.ecsite.common.error.exception.BusinessException;
import com.ecsite.module.catalog.application.model.PreparedImage;
import com.ecsite.module.catalog.application.model.view.ImageUploadView;
import com.ecsite.module.catalog.application.port.MediaDirectory;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * 商品画像のゲートウェイサービス
 *
 * <p>ファイルの受け付け・検証・配信・削除はメディアモジュールが行い、ここでは商品の規則を扱う。
 * メディアの呼び出しをトランザクションの外で行う受付と追加を受け持ち、保存は {@link ProductImageCommandService} に任せる。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProductImageGatewayService {

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
     * @throws BusinessException 形式が対象外（{@code UNSUPPORTED_IMAGE_FORMAT}）
     * @throws BusinessException 大きさが範囲の外（{@code IMAGE_TOO_LARGE}）
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
     * @throws BusinessException 受付がない（{@code IMAGE_UPLOAD_NOT_FOUND}）
     * @throws BusinessException 大きさが範囲の外（{@code IMAGE_TOO_LARGE}）
     * @throws BusinessException 中身が対象の画像でない（{@code INVALID_IMAGE_FILE}）
     * @throws BusinessException 変換できない（{@code IMAGE_CONVERSION_FAILED}）
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
            discardAfterFailure(prepared, e);
            throw e;
        }
    }

// ============================== プライベートメソッド ==============================

    /**
     * 用意した画像を破棄する。破棄に失敗しても例外を投げない。
     *
     * <p>破棄の失敗で元の失敗を隠さない。破棄の失敗は、元の例外に抑制された例外として付け、警告のログにも出力する。
     *
     * @param prepared 用意した画像
     * @param failure  保存の失敗（元の例外）
     */
    private void discardAfterFailure(PreparedImage prepared, RuntimeException failure) {
        try {
            mediaDirectory.discardImage(prepared);
        } catch (RuntimeException e) {
            failure.addSuppressed(e);
            log.warn("用意した画像を破棄できない：assetId={}", prepared.assetId(), e);
        }
    }
}
