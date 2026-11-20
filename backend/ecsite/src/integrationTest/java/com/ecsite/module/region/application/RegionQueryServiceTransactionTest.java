package com.ecsite.module.region.application;

import com.ecsite.module.region.application.port.RegionRepository;
import com.ecsite.module.region.domain.vo.Municipality;
import com.ecsite.module.region.domain.vo.PostalAddress;
import com.ecsite.module.region.domain.vo.Prefecture;
import com.ecsite.support.config.IntegrationTest;
import com.github.benmanes.caffeine.cache.Ticker;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 地域の照会のトランザクションの伝播の統合テスト
 *
 * <p>キャッシュが空の照会が、呼ぶ側のトランザクションに加わること（接続を 2 本取らないこと）を確認する。
 * キャッシュを空にする操作は本番のコードにないので、空のキャッシュを新しく作り、同じ設定のトランザクションの
 * 仕組みを付けたサービスで確認する。
 */
@IntegrationTest
class RegionQueryServiceTransactionTest {

    // ============================== Bean ==============================

    /**
     * トランザクションの管理
     */
    @Autowired
    PlatformTransactionManager transactionManager;

    /**
     * 地域のリポジトリ
     */
    @Autowired
    RegionRepository repository;

    // ============================== テスト ==============================

    @Test
    @DisplayName("呼ぶ側のトランザクションの中で、キャッシュが空の照会は、読み込みをそのトランザクションの中で行う")
    void loadJoinsCallerTransaction() {
        // 準備：読み込みの時点のトランザクションの状態を記録するサービス（キャッシュは空）
        var activeDuringLoad = new ArrayList<Boolean>();
        var service = newService(activeDuringLoad);

        // 操作：呼ぶ側のトランザクションの中で照会する
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> service.prefectures());

        // 確認：読み込みは、トランザクションの中で行われた
        assertThat(activeDuringLoad).containsExactly(true, true);
    }

    @Test
    @DisplayName("呼ぶ側のトランザクションがないとき、照会はトランザクションを始めない")
    void startsNoTransactionWithoutCaller() {
        // 準備：読み込みの時点のトランザクションの状態を記録するサービス（キャッシュは空）
        var activeDuringLoad = new ArrayList<Boolean>();
        var service = newService(activeDuringLoad);

        // 操作：トランザクションの外で照会する
        service.prefectures();

        // 確認：読み込みは、トランザクションなしで行われた
        assertThat(activeDuringLoad).containsExactly(false, false);
    }

    // ============================== 準備の補助 ==============================

    /**
     * 空のキャッシュと、{@code @Transactional} を有効にする仕組みを付けたサービスを作る。
     *
     * @param activeDuringLoad リポジトリを呼んだ時点のトランザクションの有無の記録
     */
    private RegionQueryService newService(List<Boolean> activeDuringLoad) {
        var recording = new RegionRepository() {
            @Override
            public List<Prefecture> findPrefectures() {
                activeDuringLoad.add(TransactionSynchronizationManager.isActualTransactionActive());
                return repository.findPrefectures();
            }

            @Override
            public List<Municipality> findAllMunicipalities() {
                activeDuringLoad.add(TransactionSynchronizationManager.isActualTransactionActive());
                return repository.findAllMunicipalities();
            }

            @Override
            public List<PostalAddress> findPostalAddresses(String postalCode) {
                return repository.findPostalAddresses(postalCode);
            }
        };
        var loader = new RegionMasterQueryService(recording);
        var cache = new RegionMasterCache(loader, new RegionCacheProperties(Duration.ofHours(1)),
                Ticker.systemTicker());
        var factory = new ProxyFactory(new RegionQueryService(cache, recording));
        factory.setProxyTargetClass(true);
        factory.addAdvice(new TransactionInterceptor(transactionManager, new AnnotationTransactionAttributeSource()));
        return (RegionQueryService) factory.getProxy();
    }
}
