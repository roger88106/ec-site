package com.ecsite.module.catalog.presentation;

import com.ecsite.api.admin.model.PublicationStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 商品の型と、生成したリクエスト・レスポンスのモデルの、列挙の変換の単体テスト
 */
class ProductMapperTest {

    @ParameterizedTest(name = "{0}")
    @EnumSource(PublicationStatus.class)
    @DisplayName("AC-277 リクエストの公開状態の全ての値を、ドメインの同じ名前の値に写す")
    void mapsEveryRequestStatus(PublicationStatus status) {
        assertThat(ProductMapper.toDomainStatus(status))
                .isEqualTo(com.ecsite.module.catalog.domain.product.PublicationStatus.valueOf(status.name()));
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(com.ecsite.module.catalog.domain.product.PublicationStatus.class)
    @DisplayName("AC-277 ドメインの公開状態の全ての値を、レスポンスの同じ名前の値に写す")
    void mapsEveryDomainStatus(com.ecsite.module.catalog.domain.product.PublicationStatus status) {
        assertThat(ProductMapper.toResponseStatus(status)).isEqualTo(PublicationStatus.valueOf(status.name()));
    }
}
