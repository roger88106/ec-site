package com.ecsite.module.reporting.application.port;

import java.util.Map;

/**
 * 地域の腐敗防止層（docs/03-機能仕様/reporting.md「都道府県のコード」）
 */
public interface RegionDirectory {

    /**
     * 都道府県の名前を引く。
     *
     * @return 都道府県のコード → 都道府県名
     */
    Map<String, String> prefectureNames();
}
