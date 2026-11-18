package com.ecsite;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.FullyQualifiedAnnotationBeanNameGenerator;

/**
 * EC サイトのアプリケーション
 *
 * <p>部品の名前は、クラスの完全修飾名にする。
 * 別のモジュールに、同じ単純名の部品（腐敗防止層の実装 {@code StockApiDirectory} など）があるためである。
 *
 * <p>設定の型（{@code @ConfigurationProperties} を付けた型）の登録は、{@code @ConfigurationPropertiesScan} にまとめる。
 * スキャンするパッケージは {@code com.ecsite.config} と {@code com.ecsite.module} の 2 つに限る。
 * 統合テストのクラスパスには単体テストのクラスも入り、構成の検査の見本の設定の型までスキャンされるためである。
 */
@SpringBootApplication(nameGenerator = FullyQualifiedAnnotationBeanNameGenerator.class)
@ConfigurationPropertiesScan({"com.ecsite.config", "com.ecsite.module"})
public class EcSiteApplication {

    /**
     * アプリを起動する。
     *
     * @param args 起動の引数
     */
    public static void main(String[] args) {
        SpringApplication.run(EcSiteApplication.class, args);
    }

}
