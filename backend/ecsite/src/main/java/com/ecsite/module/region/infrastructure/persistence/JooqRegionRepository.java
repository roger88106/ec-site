package com.ecsite.module.region.infrastructure.persistence;

import com.ecsite.module.region.application.port.RegionRepository;
import com.ecsite.module.region.domain.vo.Municipality;
import com.ecsite.module.region.domain.vo.PostalAddress;
import com.ecsite.module.region.domain.vo.Prefecture;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.jooq.Record1;
import org.jooq.Records;
import org.jooq.SelectField;
import org.springframework.stereotype.Repository;

import java.util.List;

import static com.ecsite.jooq.region.Tables.MUNICIPALITY;
import static com.ecsite.jooq.region.Tables.POSTAL_CODE;
import static com.ecsite.jooq.region.Tables.PREFECTURE;
import static org.jooq.impl.DSL.row;

/**
 * 地域のマスターデータのリポジトリの jOOQ の実装
 */
@Repository
@RequiredArgsConstructor
class JooqRegionRepository implements RegionRepository {

    /**
     * jOOQ DSL Context
     */
    private final DSLContext dsl;

// ============================== 取得列と変換 ==============================

    /**
     * 都道府県の列と、都道府県への変換
     *
     * <p>列の並びは {@link Prefecture} の項目の順にする。
     */
    private static final SelectField<Prefecture> PREFECTURE_ROW =
            row(PREFECTURE.CODE, PREFECTURE.NAME).mapping(Prefecture::new);

    /**
     * 市区町村の列と、市区町村への変換
     *
     * <p>列の並びは {@link Municipality} の項目の順にする。
     */
    private static final SelectField<Municipality> MUNICIPALITY_ROW =
            row(MUNICIPALITY.CODE, MUNICIPALITY.PREFECTURE_CODE, MUNICIPALITY.NAME).mapping(Municipality::new);

// ============================== 検索処理 ==============================

    /**
     * {@inheritDoc}
     */
    @Override
    public List<Prefecture> findPrefectures() {
        return dsl.select(PREFECTURE_ROW).from(PREFECTURE).orderBy(PREFECTURE.CODE)
                .fetch(Record1::value1);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<Municipality> findAllMunicipalities() {
        return dsl.select(MUNICIPALITY_ROW).from(MUNICIPALITY).orderBy(MUNICIPALITY.CODE)
                .fetch(Record1::value1);
    }

    /**
     * {@inheritDoc}
     *
     * <p>市区町村と都道府県を結合して、並び順に返す。
     */
    @Override
    public List<PostalAddress> findPostalAddresses(String postalCode) {
        // 列の並びは PostalAddress の項目の順にする
        return dsl.select(POSTAL_CODE.POSTAL_CODE_, PREFECTURE_ROW, MUNICIPALITY_ROW, POSTAL_CODE.TOWN_NAME)
                .from(POSTAL_CODE)
                .join(MUNICIPALITY).on(MUNICIPALITY.CODE.eq(POSTAL_CODE.MUNICIPALITY_CODE))
                .join(PREFECTURE).on(PREFECTURE.CODE.eq(MUNICIPALITY.PREFECTURE_CODE))
                .where(POSTAL_CODE.POSTAL_CODE_.eq(postalCode))
                .orderBy(POSTAL_CODE.DISPLAY_ORDER)
                .fetch(Records.mapping(PostalAddress::new));
    }
}
