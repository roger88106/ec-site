package com.ecsite.module.region.application;

import com.ecsite.module.region.application.model.RegionMaster;
import com.ecsite.module.region.application.port.RegionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 地域のマスターデータの読み込み
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class RegionMasterQueryService {

    /**
     * 地域のマスターデータのリポジトリ
     */
    private final RegionRepository repository;

    /**
     * 都道府県の全件と市区町村の全件を、1 つのトランザクションで読む。
     *
     * @return 地域のマスターデータ
     */
    public RegionMaster load() {
        return new RegionMaster(repository.findPrefectures(), repository.findAllMunicipalities());
    }
}
