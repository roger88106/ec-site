package com.ecsite.module.media.application.port;

import com.ecsite.common.model.id.AssetId;
import com.ecsite.module.media.domain.Asset;

import java.util.Collection;
import java.util.List;

/**
 * アセットのリポジトリ
 */
public interface AssetRepository {

    /**
     * アセットを記録する。
     *
     * @param asset アセット
     */
    void insert(Asset asset);

    /**
     * アセットをまとめて取得する。
     *
     * @param ids アセット ID
     * @return アセット。ないものは含まない
     */
    List<Asset> findAllById(Collection<AssetId> ids);

    /**
     * アセットの記録をまとめて消す。
     *
     * <p>ないものは無視する。同時に同じアセットを削除したときは、実際に削除した呼び出しの戻り値にだけ、そのアセットが含まれる。
     *
     * @param ids アセット ID
     * @return 実際に削除したアセット。ないものは含まない
     */
    List<Asset> deleteAllById(Collection<AssetId> ids);
}
