package com.ecsite.support.config;

import com.ecsite.contract.Contracts;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.util.Map;

/**
 * 統合テストのコンテナのイメージ
 *
 * <p>docker/compose.yaml の {@code image} を読む。手元（compose）と統合テスト（Testcontainers）で、同じバージョンのイメージを動かすため。
 * Dependabot が compose を更新すれば、統合テストも同じバージョンになる。
 */
public final class ComposeImages {

    /**
     * compose のファイルの、リポジトリのルートからの相対パス
     */
    private static final String COMPOSE_FILE = "docker/compose.yaml";

    /**
     * インスタンスを作らない。
     */
    private ComposeImages() {
    }

    /**
     * compose で使うイメージを、名前で探して返す。
     *
     * @param name イメージの名前（バージョンを除く。例：{@code axllent/mailpit}）
     * @return バージョンを付けたイメージ（例：{@code axllent/mailpit:v1.33.0}）
     * @throws IllegalStateException compose にその名前のイメージがないとき
     */
    public static String of(String name) {
        return find(read(), name);
    }

    /**
     * compose の内容から、名前が一致するイメージを探す。
     *
     * @param compose 読んだ compose の内容
     * @param name    イメージの名前（バージョンを除く）
     * @return バージョンを付けたイメージ
     * @throws IllegalStateException その名前のイメージがないとき
     */
    private static String find(Map<String, Object> compose, String name) {
        if (!(compose.get("services") instanceof Map<?, ?> services)) {
            throw new IllegalStateException(COMPOSE_FILE + " に services がない");
        }
        for (Object service : services.values()) {
            if (service instanceof Map<?, ?> map && map.get("image") instanceof String image
                    && image.lastIndexOf(':') > 0 && image.substring(0, image.lastIndexOf(':')).equals(name)) {
                return image;
            }
        }
        throw new IllegalStateException(COMPOSE_FILE + " に " + name + " のイメージがない");
    }

    /**
     * compose を読む。
     *
     * @return 読んだ compose の内容
     * @throws UncheckedIOException ファイルを読めないとき
     */
    private static Map<String, Object> read() {
        try {
            return new Yaml(new SafeConstructor(new LoaderOptions())).load(Files.readString(Contracts.path(COMPOSE_FILE)));
        } catch (IOException e) {
            throw new UncheckedIOException(COMPOSE_FILE + " を読めない", e);
        }
    }
}
