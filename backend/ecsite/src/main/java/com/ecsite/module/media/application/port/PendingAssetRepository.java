package com.ecsite.module.media.application.port;

import com.ecsite.module.media.domain.PendingAsset;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

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
     * 仮のアセットを 1 件消す。
     *
     * <p>同時に同じ仮のアセットを消したときは、実際に消した呼び出しだけが true を返す。
     *
     * @param id アセット ID
     * @return 消したら true。なければ false
     */
    boolean deleteById(UUID id);

    /**
     * 用意した時刻が指定した時刻より前の仮のアセットを、件数の上限まで消す。
     *
     * <p>用意した時刻の古い順（同じ時刻はアセット ID の順）に選ぶ。他のトランザクションがロックしている行は飛ばす。
     *
     * @param preparedBefore この時刻より前に用意したものを消す
     * @param limit          消す件数の上限
     * @return 消した仮のアセット（順は決めない）
     */
    List<PendingAsset> deleteExpiredBefore(Instant preparedBefore, int limit);
}
