package com.ecsite.module.media.application.port;

import com.ecsite.common.model.id.AssetId;
import com.ecsite.module.media.domain.PendingAsset;

import java.time.Instant;
import java.util.List;

/**
 * 仮のアセットのリポジトリ
 */
public interface PendingAssetRepository {

    /**
     * 仮のアセットを記録する。
     *
     * @param pendingAsset 仮のアセット
     */
    void insert(PendingAsset pendingAsset);

    /**
     * 仮のアセットを 1 件削除する。
     *
     * <p>同時に同じ仮のアセットを削除したときは、実際に削除した呼び出しだけが true を返す。
     *
     * @param id アセット ID
     * @return 削除したら true。なければ false
     */
    boolean deleteById(AssetId id);

    /**
     * 用意した時刻が指定した時刻より前の仮のアセットを、件数の上限まで削除する。
     *
     * <p>用意した時刻の古い順（同じ時刻はアセット ID の順）に選ぶ。他のトランザクションがロックしている行はスキップする。
     *
     * @param preparedBefore この時刻より前に用意したものを削除する
     * @param limit          削除する件数の上限
     * @return 削除した仮のアセット（順序は保証しない）
     */
    List<PendingAsset> deleteExpiredBefore(Instant preparedBefore, int limit);
}
