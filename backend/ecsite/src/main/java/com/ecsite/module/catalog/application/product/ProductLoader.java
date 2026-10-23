package com.ecsite.module.catalog.application.product;

import com.ecsite.common.error.CommonErrorCode;
import com.ecsite.common.error.exception.BusinessException;
import com.ecsite.common.model.base.AggregateGuards;
import com.ecsite.module.catalog.domain.product.Product;
import com.ecsite.module.catalog.domain.product.port.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import java.util.function.BiPredicate;

/**
 * 商品の更新の処理（と、更新の前の確認）で共通の、商品の取り出しと状態の切り替え。<br />
 * 取引の境界は、呼ぶ側の service が持つ。
 */
@Component
@RequiredArgsConstructor
class ProductLoader {

    /**
     * 商品リポジトリ
     */
    private final ProductRepository productRepository;

    /**
     * 時計
     */
    private final Clock clock;

    /**
     * 商品を取得し、取得時のバージョンと一致することを確認する。
     *
     * @param productId 商品 ID
     * @param version   取得時のバージョン
     * @return 商品
     */
    Product get(UUID productId, long version) {
        var product = get(productId);

        // 取得時のバージョンと一致しなければ、他の人が先に更新している（保存のときにもリポジトリが比べる）
        AggregateGuards.requireVersion(product.version(), version);
        return product;
    }

    /**
     * 商品を、バージョンを確認せずに取得する。なければ 404 の業務のエラーにする。
     *
     * @param productId 商品 ID
     * @return 商品
     */
    Product get(UUID productId) {
        return productRepository.findById(productId)
                .orElseThrow(() -> BusinessException.of(CommonErrorCode.NOT_FOUND));
    }

    /**
     * 状態（公開状態、バリアントの販売停止）を切り替える。<br />
     * 状態の切り替えはバージョンを受け取らない（API の規約）。すでにその状態なら保存しないので、バージョンも上がらない。
     * 取得から保存までの間に他で更新されていれば、保存時に競合になる。
     *
     * @param productId 商品 ID
     * @param operation 切り替えの操作。状態が変わったら true を返す
     */
    void changeStatus(UUID productId, BiPredicate<Product, Instant> operation) {
        var product = productRepository.findById(productId)
                .orElseThrow(() -> BusinessException.of(CommonErrorCode.NOT_FOUND));

        if (operation.test(product, clock.instant())) {
            productRepository.save(product);
        }
    }
}
