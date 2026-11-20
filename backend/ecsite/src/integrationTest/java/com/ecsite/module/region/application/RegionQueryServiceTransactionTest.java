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
import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 地域の照会のトランザクションの伝播の統合テスト
 *
 * <p>キャッシュが空の照会が、呼ぶ側のトランザクションに加わること（接続を 2 本取らないこと）を確認する。
 * 呼ぶ側のトランザクションの中では、読み込みの時点のトランザクションの資源（接続）が呼ぶ側と同じで、
 * 読み取り専用にならないことを確認する。呼ぶ側がなければ、読み込みが読み取り専用のトランザクションで行われることを確認する。
 * キャッシュを空にする操作は本番のコードにないので、空のキャッシュを新しく作る。照会と読み込みの担当には、
 * 本番と同じ設定のトランザクションのプロキシ（{@code TransactionInterceptor}）を付けて確認する。
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
     * データソース（トランザクションの資源の鍵）
     */
    @Autowired
    DataSource dataSource;

    /**
     * 地域のリポジトリ
     */
    @Autowired
    RegionRepository repository;

    // ============================== テスト ==============================

    @Test
    @DisplayName("呼ぶ側のトランザクションの中で、キャッシュが空の照会は、読み込みをそのトランザクションで行う")
    void loadJoinsCallerTransaction() {
        // 準備：読み込みの時点のトランザクションの状態を記録するサービス（キャッシュは空）
        var loads = new ArrayList<LoadState>();
        var service = newService(loads);
        var outer = new ArrayList<Object>();

        // 操作：読み書きの呼ぶ側のトランザクションの中で照会する
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            outer.add(TransactionSynchronizationManager.getResource(dataSource));
            service.prefectures();
        });

        // 確認：読み込みは、呼ぶ側と同じ資源で、読み取り専用にならずに行われた
        assertThat(outer).singleElement().isNotNull();
        assertThat(loads).hasSize(2).allSatisfy(load -> {
            assertThat(load.resource()).isSameAs(outer.get(0));
            assertThat(load.readOnly()).isFalse();
        });
    }

    @Test
    @DisplayName("呼ぶ側のトランザクションがないとき、読み込みは読み取り専用のトランザクションで行う")
    void loadsInReadOnlyTransactionWithoutCaller() {
        // 準備：読み込みの時点のトランザクションの状態を記録するサービス（キャッシュは空）
        var loads = new ArrayList<LoadState>();
        var service = newService(loads);

        // 操作：トランザクションの外で照会する
        service.prefectures();

        // 確認：読み込みは、トランザクションの中で、読み取り専用で行われた
        assertThat(loads).hasSize(2).allSatisfy(load -> {
            assertThat(load.resource()).isNotNull();
            assertThat(load.readOnly()).isTrue();
        });
    }

    // ============================== 準備の補助 ==============================

    /**
     * リポジトリを呼んだ時点のトランザクションの状態
     *
     * @param resource データソースのトランザクションの資源（なければ null）
     * @param readOnly 読み取り専用か
     */
    private record LoadState(Object resource, boolean readOnly) {
    }

    /**
     * 空のキャッシュと、{@code @Transactional} を有効にするプロキシを付けたサービスを作る。
     *
     * @param loads リポジトリを呼んだ時点のトランザクションの状態の記録
     */
    private RegionQueryService newService(List<LoadState> loads) {
        var recording = new RegionRepository() {
            @Override
            public List<Prefecture> findPrefectures() {
                loads.add(currentState());
                return repository.findPrefectures();
            }

            @Override
            public List<Municipality> findAllMunicipalities() {
                loads.add(currentState());
                return repository.findAllMunicipalities();
            }

            @Override
            public List<PostalAddress> findPostalAddresses(String postalCode) {
                return repository.findPostalAddresses(postalCode);
            }
        };
        var loader = transactional(new RegionMasterQueryService(recording), RegionMasterQueryService.class);
        var cache = new RegionMasterCache(loader, new RegionCacheProperties(Duration.ofHours(1)),
                Ticker.systemTicker());
        return transactional(new RegionQueryService(cache, recording), RegionQueryService.class);
    }

    /**
     * 現在のトランザクションの状態を返す。
     */
    private LoadState currentState() {
        return new LoadState(TransactionSynchronizationManager.getResource(dataSource),
                TransactionSynchronizationManager.isCurrentTransactionReadOnly());
    }

    /**
     * 本番と同じ設定のトランザクションのプロキシを付ける。
     */
    private <T> T transactional(T target, Class<T> type) {
        var factory = new ProxyFactory(target);
        factory.setProxyTargetClass(true);
        factory.addAdvice(new TransactionInterceptor(transactionManager, new AnnotationTransactionAttributeSource()));
        return type.cast(factory.getProxy());
    }
}
