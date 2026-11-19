package com.ecsite.module.order.api;

import com.ecsite.module.order.api.model.OrderSnapshot;
import com.ecsite.module.order.domain.order.vo.Gender;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 注文の性別を、公開の性別に変換するときの単体テスト
 */
class OrderApiGenderTest {

    // ============================== テスト ==============================

    @ParameterizedTest(name = "「{0}」")
    @EnumSource(Gender.class)
    @DisplayName("AC-277 注文の性別の全ての値を、公開の性別の同じ名前の値に変換する")
    void mapsEveryGender(Gender gender) {
        // 操作と確認：同じ名前の値になる
        assertThat(OrderApi.toGender(gender)).isEqualTo(OrderSnapshot.Gender.valueOf(gender.name()));
    }
}
