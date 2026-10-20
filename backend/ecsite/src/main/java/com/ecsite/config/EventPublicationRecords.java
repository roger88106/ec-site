package com.ecsite.config;

import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * Spring Modulith のイベントの記録を、再発行のジョブのために直接読み書きする（バックエンドのアーキテクチャ 第 5 節）。<br />
 * 止めた印は、処理の回数を上限より 1 つ多くした値で表す（Spring Modulith の再発行は、処理の回数を見ずに読むため、
 * 絞り込みは再発行のジョブが行う）。
 */
@Component
@RequiredArgsConstructor
class EventPublicationRecords {

    /**
     * jOOQ DSL Context
     */
    private final DSLContext dsl;

    /**
     * 処理の回数が上限に達し、最後の処理に失敗したイベントに、止めた印を付ける。
     *
     * @param maxAttempts 処理の回数の上限
     * @return 印を付けたイベント
     */
    List<StoppedPublication> markStopped(int maxAttempts) {
        return List.of();
    }

    /**
     * 止めたイベント（ログに出す項目だけ。イベントの中身は持たない）
     *
     * @param id                 イベントの記録の ID
     * @param eventType          イベントの種類（クラスの完全修飾名）
     * @param listenerId         リスナーの ID
     * @param completionAttempts 処理の回数
     */
    record StoppedPublication(UUID id, String eventType, String listenerId, int completionAttempts) {
    }
}
