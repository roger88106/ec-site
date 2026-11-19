package com.ecsite.module.reporting.presentation;

import com.ecsite.api.admin.model.AgeGroupSales;
import com.ecsite.api.admin.model.GenderSales;
import com.ecsite.api.admin.model.Granularity;
import com.ecsite.api.admin.model.ReportBasis;
import com.ecsite.module.reporting.domain.sales.AgeGroup;
import com.ecsite.module.reporting.domain.sales.Basis;
import com.ecsite.module.reporting.domain.sales.ReportGender;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * レポートの型と、生成したリクエスト・レスポンスのモデルの、列挙の変換の単体テスト
 *
 * <p>列挙の値の対応だけを確認する。項目の変換と応答の形は、レポート API の統合テストと契約のテストが確認する。
 */
class ReportMapperTest {

    // ============================== テスト ==============================

    @ParameterizedTest(name = "「{0}」")
    @EnumSource(ReportBasis.class)
    @DisplayName("AC-277 リクエストの売上の数え方の全ての値を、ドメインの同じ名前の値に写す")
    void mapsEveryBasis(ReportBasis basis) {
        // 操作と確認：同じ名前の値になる
        assertThat(new ReportMapper().toBasis(basis)).isEqualTo(Basis.valueOf(basis.name()));
    }

    @Test
    @DisplayName("AC-277 売上の数え方を省くと、注文基準にする")
    void defaultsBasisToOrder() {
        // 操作と確認：省いた値は注文基準
        assertThat(new ReportMapper().toBasis(null)).isEqualTo(Basis.ORDER);
    }

    @ParameterizedTest(name = "「{0}」")
    @EnumSource(Granularity.class)
    @DisplayName("AC-277 リクエストの集計の粒度の全ての値を、ドメインの同じ名前の値に写す")
    void mapsEveryGranularity(Granularity granularity) {
        // 操作と確認：同じ名前の値になる
        assertThat(new ReportMapper().toGranularity(granularity))
                .isEqualTo(com.ecsite.module.reporting.domain.sales.Granularity.valueOf(granularity.name()));
    }

    @ParameterizedTest(name = "「{0}」")
    @EnumSource(AgeGroup.class)
    @DisplayName("AC-277 年齢層の全ての値を、レスポンスの同じ名前の値に写す")
    void mapsEveryAgeGroup(AgeGroup ageGroup) {
        // 操作と確認：同じ名前の値になる
        assertThat(ReportMapper.toAgeGroup(ageGroup)).isEqualTo(AgeGroupSales.AgeGroupEnum.valueOf(ageGroup.name()));
    }

    @ParameterizedTest(name = "「{0}」")
    @EnumSource(ReportGender.class)
    @DisplayName("AC-277 性別の区分の全ての値を、レスポンスの同じ名前の値に写す")
    void mapsEveryGender(ReportGender gender) {
        // 操作と確認：同じ名前の値になる
        assertThat(ReportMapper.toGender(gender)).isEqualTo(GenderSales.GenderEnum.valueOf(gender.name()));
    }
}
