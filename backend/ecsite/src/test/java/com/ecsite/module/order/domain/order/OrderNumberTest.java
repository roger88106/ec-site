package com.ecsite.module.order.domain.order;

import com.ecsite.module.order.domain.order.vo.OrderNumber;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;
import java.util.random.RandomGenerator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 注文番号の単体テスト（T2307、BR-10）
 */
class OrderNumberTest {

    @Test
    @DisplayName("BR-10 注文番号は注文日（日本時間）の 8 桁、ハイフン、ランダムな数字 6 桁")
    void generatesFromJapaneseDateAndRandomDigits() {
        // 2026-10-07 15:30 UTC は、日本時間では 2026-10-08 00:30
        var orderedAt = Instant.parse("2026-10-07T15:30:00Z");

        assertThat(OrderNumber.generate(orderedAt, fixed(482915)).value()).isEqualTo("20261008-482915");
        assertThat(OrderNumber.generate(orderedAt, fixed(7)).value()).isEqualTo("20261008-000007");
    }

    @ParameterizedTest
    @ValueSource(strings = {"2026100-482915", "20261008482915", "20261008-48291", "20261008-48291a"})
    @DisplayName("BR-10 形の違う注文番号は作れない")
    void rejectsInvalidFormat(String value) {
        assertThatThrownBy(() -> OrderNumber.of(value)).isInstanceOf(IllegalArgumentException.class);
    }

    /**
     * 決まった値を返す乱数
     */
    private static RandomGenerator fixed(int value) {
        return new RandomGenerator() {
            @Override
            public long nextLong() {
                return value;
            }

            @Override
            public int nextInt(int bound) {
                return value;
            }
        };
    }
}
