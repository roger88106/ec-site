package com.ecsite.module.region.api;

import com.ecsite.module.region.api.model.RegionMunicipality;
import com.ecsite.module.region.api.model.RegionPrefecture;
import com.ecsite.support.config.IntegrationTest;
import org.jooq.DSLContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.nio.file.Files;
import java.nio.file.Path;

import static com.ecsite.jooq.region.Tables.MUNICIPALITY;
import static com.ecsite.jooq.region.Tables.POSTAL_CODE;
import static com.ecsite.jooq.region.Tables.PREFECTURE;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * 地域の api の受け入れテスト
 *
 * <p>最初のデータ（郵便番号のデータから作ったマイグレーション）を読む。マスターデータはテストで変えないので、表の全体の数を確認する。
 */
@IntegrationTest
class RegionApiTest {

    // ============================== 定数 ==============================

    /**
     * 都道府県の数
     */
    private static final int PREFECTURES = 47;

    /**
     * 市区町村の数
     *
     * <p>2026-09-30 の郵便番号のデータ。政令指定都市は区で数える。
     */
    private static final int MUNICIPALITIES = 1892;

    // ============================== Bean ==============================

    /**
     * 地域の api
     */
    @Autowired
    RegionLookupApi regionApi;

    /**
     * 件数を数える
     */
    @Autowired
    DSLContext dsl;

    // ============================== テスト ==============================

    @Nested
    @DisplayName("最初のデータ")
    class InitialData {

        @Test
        @DisplayName("AC-259 都道府県は、北海道から沖縄県までの 47 がある")
        void allPrefectures() {
            // 操作：都道府県の一覧を読む
            var prefectures = regionApi.prefectures();

            // 確認：47 件で、最初が北海道、最後が沖縄県。表にも 47 行
            assertThat(prefectures).hasSize(PREFECTURES)
                    .first().isEqualTo(new RegionPrefecture("01", "北海道"));
            assertThat(prefectures).last().isEqualTo(new RegionPrefecture("47", "沖縄県"));
            assertThat(dsl.fetchCount(PREFECTURE)).isEqualTo(PREFECTURES);
        }

        @Test
        @DisplayName("AC-259 市区町村は、全国の数だけある")
        void allMunicipalities() {
            // 操作と確認：郵便番号のデータの市区町村の数と同じ
            assertThat(dsl.fetchCount(MUNICIPALITY)).isEqualTo(MUNICIPALITIES);
        }

        @Test
        @DisplayName("AC-260 郵便番号は、最初のデータの一覧の郵便番号だけがある")
        void somePostalCodes() throws Exception {
            // 準備：最初のデータに入れる郵便番号の一覧（空行と注釈の行を除き、重複を除く）の数
            var listed = Files.readAllLines(Path.of(System.getProperty("region.postal-codes"))).stream()
                    .map(String::strip)
                    .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                    .distinct()
                    .count();

            // 操作と確認：表の郵便番号の種類の数が、一覧の数と同じ
            assertThat(dsl.fetchCount(dsl.selectDistinct(POSTAL_CODE.POSTAL_CODE_).from(POSTAL_CODE))).isEqualTo(listed);
        }
    }

    @Nested
    @DisplayName("市区町村の検索")
    class FindMunicipality {

        @Test
        @DisplayName("市区町村をコードで引くと、都道府県の名前も返す")
        void findsWithPrefecture() {
            // 操作と確認：札幌市中央区と、北海道
            assertThat(regionApi.findMunicipality("01101")).contains(new RegionMunicipality("01101", "札幌市中央区", "01", "北海道"));
        }

        @Test
        @DisplayName("ないコードは空")
        void emptyForUnknownCode() {
            // 操作と確認：空
            assertThat(regionApi.findMunicipality("99999")).isEmpty();
        }
    }
}
