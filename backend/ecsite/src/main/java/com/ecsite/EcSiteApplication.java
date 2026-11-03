package com.ecsite;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.FullyQualifiedAnnotationBeanNameGenerator;

/**
 * EC サイトのアプリケーション。<br />
 * 部品の名前は、クラスの完全修飾名にする。
 * 別のモジュールに、同じ単純名の部品（腐敗防止層の実装 {@code StockApiDirectory} など）があるためである。
 * モジュールの標準の構成は、バックエンドのアーキテクチャの「層とパッケージ」にある。
 */
@SpringBootApplication(nameGenerator = FullyQualifiedAnnotationBeanNameGenerator.class)
public class EcSiteApplication {

    /**
     * アプリを起動する。
     */
    public static void main(String[] args) {
        SpringApplication.run(EcSiteApplication.class, args);
    }

}
