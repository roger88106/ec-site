package com.ecsite.common.presentation;

import com.ecsite.api.admin.model.CreatedId;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.UUID;

/**
 * 作成の応答（201、Location、作成した ID）を作る（バックエンドの書き方 第 2.2 節）。<br />
 * 作成した ID の型は、管理 API と店舗の API で別に生成されるので、API ごとに分ける。
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class CreatedResponses {

    /**
     * 管理 API の作成の応答を作る。Location は、今のリクエストの URI に ID を追加したもの。
     *
     * @param id 作成した ID
     * @return 作成の応答
     */
    public static ResponseEntity<CreatedId> admin(UUID id) {
        return ResponseEntity.created(belowCurrentRequest(id)).body(new CreatedId().id(id));
    }

    /**
     * 店舗の API の作成の応答を作る。Location は、今のリクエストの URI に ID を追加したもの。
     *
     * @param id 作成した ID
     * @return 作成の応答
     */
    public static ResponseEntity<com.ecsite.api.store.model.CreatedId> store(UUID id) {
        return ResponseEntity.created(belowCurrentRequest(id)).body(storeId(id));
    }

    /**
     * 店舗の API の作成の応答を、別のパスを指して作る。Location は、アプリの起点にパスを追加したもの。
     *
     * @param path パス（`{id}` に ID を入れる。例：`/api/store/orders/{id}`）
     * @param id   作成した ID
     * @return 作成の応答
     */
    public static ResponseEntity<com.ecsite.api.store.model.CreatedId> storeAt(String path, UUID id) {
        var location = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path(path)
                .buildAndExpand(id)
                .toUri();
        return ResponseEntity.created(location).body(storeId(id));
    }

    /**
     * 店舗の API の作成した ID を作る（作成済みを 200 で返すときの本文）。
     *
     * @param id 作成した ID
     * @return 作成した ID
     */
    public static com.ecsite.api.store.model.CreatedId storeId(UUID id) {
        return new com.ecsite.api.store.model.CreatedId().id(id);
    }

    /**
     * 今のリクエストの URI に ID を追加する。
     *
     * @param id 作成した ID
     * @return 作成したものの URI
     */
    private static URI belowCurrentRequest(UUID id) {
        return ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(id)
                .toUri();
    }
}
