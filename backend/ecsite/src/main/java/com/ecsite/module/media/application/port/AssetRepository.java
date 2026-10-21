package com.ecsite.module.media.application.port;

import com.ecsite.module.media.domain.Asset;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

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
    List<Asset> findAllById(Collection<UUID> ids);

    /**
     * アセットの記録をまとめて消す。ないものは無視する。
     *
     * @param ids アセット ID
     */
    void deleteAllById(Collection<UUID> ids);
}
