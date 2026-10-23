package com.ecsite.module.shipping.infrastructure.mock;

import com.ecsite.module.shipping.application.port.CarrierTracking;
import com.ecsite.module.shipping.domain.Carrier;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * テストで使う物流サービスの追跡。本番の mock（{@link MockCarrierTracking}）を包み、追跡を始めた送り状番号を記録する。<br />
 * 記録はメモリで、テストは 1 インスタンスで動かす前提のため。本番の jar に入れないため、テストで共有するソースに置く。
 */
public class TestCarrierTracking implements CarrierTracking {

    /**
     * 包む mock
     */
    private final MockCarrierTracking mock;

    /**
     * 追跡を始めた送り状番号（始めた順）
     */
    private final List<Started> started = new CopyOnWriteArrayList<>();

    /**
     * mock を包む。
     *
     * @param mock 本番の mock
     */
    public TestCarrierTracking(MockCarrierTracking mock) {
        this.mock = mock;
    }

    @Override
    public void startTracking(Carrier carrier, String trackingNumber) {
        mock.startTracking(carrier, trackingNumber);
        started.add(new Started(carrier, trackingNumber));
    }

    /**
     * 追跡を始めた送り状番号を返す。
     *
     * @return 追跡を始めた送り状番号（始めた順）
     */
    public List<Started> started() {
        return List.copyOf(started);
    }

    /**
     * 追跡を始めた送り状番号
     *
     * @param carrier        配送業者
     * @param trackingNumber 送り状番号（ハイフンを除いた数字）
     */
    public record Started(Carrier carrier, String trackingNumber) {
    }
}
