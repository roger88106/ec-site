package com.ecsite;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * 同時実行
 *
 * <p>同じ処理を複数のスレッドで同時に始め、全ての結果を待つ。
 *
 * <p>待つ時間の上限は {@link AsyncEvents#TIMEOUT}。デッドロックなどで終わらないときは、テストを失敗させて止める。
 */
public final class Concurrently {

    private Concurrently() {
    }

    /**
     * 同時に動かす処理
     *
     * @param <T> 処理の結果の型
     */
    @FunctionalInterface
    public interface Task<T> {

        /**
         * 処理を行う。
         *
         * @param index スレッドの番号（0 から）
         * @return 処理の結果
         * @throws Exception 処理が失敗したとき
         */
        T call(int index) throws Exception;
    }

    /**
     * 同じ処理を、指定の数のスレッドで同時に始め、全ての結果を待つ。
     *
     * @param threads スレッドの数
     * @param task    各スレッドで行う処理
     * @param <T>     処理の結果の型
     * @return スレッドの番号の順の結果
     * @throws Exception 処理が失敗したとき、または上限の時間までに終わらないとき
     */
    public static <T> List<T> run(int threads, Task<T> task) throws Exception {
        var ready = new CountDownLatch(threads);
        var start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(threads);
        try {
            List<Future<T>> futures = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                var index = i;
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    return task.call(index);
                }));
            }
            if (!ready.await(AsyncEvents.TIMEOUT.toSeconds(), TimeUnit.SECONDS)) {
                throw new TimeoutException("スレッドの準備が上限の時間までに終わらない");
            }
            start.countDown();
            var results = new ArrayList<T>();
            for (var future : futures) {
                results.add(future.get(AsyncEvents.TIMEOUT.toSeconds(), TimeUnit.SECONDS));
            }
            return results;
        } finally {
            // close は全部の処理の終わりを待つので、途中で失敗すると start を待つスレッドが残る。割り込んで止める
            executor.shutdownNow();
        }
    }
}
