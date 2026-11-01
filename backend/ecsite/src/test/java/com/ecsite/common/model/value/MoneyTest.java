package com.ecsite.common.model.value;

import com.ecsite.common.error.CommonErrorCode;
import com.ecsite.common.error.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 金額の単体テスト（バックエンドのアーキテクチャの「永続化」）
 */
class MoneyTest {

    @Test
    @DisplayName("円単位の整数で表す")
    void holdsYenAsInteger() {
        var money = Money.of(1_980);

        assertThat(money.yen()).isEqualTo(1_980L);
    }

    @Test
    @DisplayName("0 円を表せる")
    void allowsZero() {
        assertThat(Money.of(0)).isEqualTo(Money.ZERO);
    }

    @Test
    @DisplayName("負の金額は拒否される")
    void rejectsNegative() {
        assertThatThrownBy(() -> Money.of(-1))
                .isInstanceOfSatisfying(BusinessException.class, ex ->
                        assertThat(ex.getErrorDetail().errorCode()).isEqualTo(CommonErrorCode.INVALID_MONEY.getCode()));
    }

    @Test
    @DisplayName("加算できる。元の金額は変わらない")
    void adds() {
        var a = Money.of(1_800);
        var b = Money.of(180);

        assertThat(a.plus(b)).isEqualTo(Money.of(1_980));
        assertThat(a).isEqualTo(Money.of(1_800));
    }

    @Test
    @DisplayName("同じ金額は等しい")
    void equalsByValue() {
        assertThat(Money.of(100)).isEqualTo(Money.of(100)).hasSameHashCodeAs(Money.of(100));
        assertThat(Money.of(100)).isNotEqualTo(Money.of(101));
    }
}
