package com.ecsite.common.event;

import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.AliasFor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 外部サービスを呼ぶイベントのリスナー
 *
 * <p>メールの送信、ファイルの削除、決済の取消、物流の追跡の依頼のように、外部サービスを呼ぶリスナーに付ける。
 * 外部サービスを呼ばないリスナーには、Spring Modulith の {@code @ApplicationModuleListener} を付ける。
 *
 * <p>{@code @ApplicationModuleListener} と同じ組み合わせ（非同期、コミットの後、新しいトランザクション）で、
 * 外部サービスを呼ぶリスナー専用のスレッドプール（{@link #EXECUTOR}）で動く。
 * 理由：外部サービスが遅いとき、リスナーがスレッドを長く使う。スレッドプールを分けておくと、
 * 外部サービスを呼ばないリスナーが、そのスレッドの空きを待たない。
 * イベント発行記録の完了、失敗、再発行は、{@code @ApplicationModuleListener} のリスナーと同じように動く。
 */
@Async(ExternalServiceListener.EXECUTOR)
@Transactional(propagation = Propagation.REQUIRES_NEW)
@TransactionalEventListener
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface ExternalServiceListener {

    /**
     * 外部サービスを呼ぶリスナーのスレッドプールの Bean の名前
     */
    String EXECUTOR = "externalServiceExecutor";

    /**
     * トランザクションを読み取り専用にするか
     *
     * @return 読み取り専用なら true
     */
    @AliasFor(annotation = Transactional.class, attribute = "readOnly")
    boolean readOnlyTransaction() default false;

    /**
     * リスナーの ID
     *
     * @return リスナーの ID
     */
    @AliasFor(annotation = EventListener.class, attribute = "id")
    String id() default "";

    /**
     * リスナーを呼ぶ条件（SpEL）
     *
     * @return 条件
     */
    @AliasFor(annotation = EventListener.class, attribute = "condition")
    String condition() default "";

    /**
     * トランザクションの伝播
     *
     * @return 伝播。既定は新しいトランザクション
     */
    @AliasFor(annotation = Transactional.class, attribute = "propagation")
    Propagation propagation() default Propagation.REQUIRES_NEW;
}
