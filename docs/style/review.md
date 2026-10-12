# 見直しで読む規則

自動で確かめられない規則（確かめ方が「人手」のもの）の一覧。各 spec の見直しの段階で、変えたコードをこの一覧と照らす。

## 使い方

1. 見直しの段階の tasks は、本書を参照に挙げる。
2. その spec で変えたファイルを、下の表の「対象」ごとに読み、規則に合わないところを直す。直さないものは、理由を見直しの報告に書く。
3. 一覧にない習慣に気づいたら、規則の文書に足すかを利用者に確かめる。規則を足したら、人手のものは本書にも足す。自動の検査ができた規則は、本書から消す。

## 一覧

| 対象 | 規則 |
|------|------|
| バックエンド | [層とパッケージ（レベル A）](backend.md#12-レベル-a-の層)、[レベル B の層](backend.md#13-レベル-b-の層) |
| バックエンド | [契約にない endpoint を足さない](backend.md#21-生成した-interface-を実装する)、[応答への変換は手で書く](backend.md#22-応答への変換は手で書く) |
| バックエンド | [形の検証と業務の規則の置き場所](backend.md#31-形の検証は-presentation業務の規則は-domain)、[エラーは XxxErrorCode と BusinessException](backend.md#32-エラーは-xxxerrorcode-と-businessexception) |
| バックエンド | [トランザクションの置き場所](backend.md#41-トランザクションは-applicationレベル-b-は-service)、[現在時刻は Clock から](backend.md#42-現在時刻は-clock-から) |
| バックエンド | [Lombok は main だけ](backend.md#51-lombok-は-main-だけ)、[名前の形](backend.md#52-名前の形) |
| フロント | [画面に依存しない処理は lib/ の関数](frontend.md#11-画面に依存しない処理は-lib-の関数にする)、[機能ごとに features/](frontend.md#12-機能ごとに-features機能) |
| フロント | [エラーの文は API の message](frontend.md#22-エラーの文は-api-の-message-を出す) |
| フロント | [catch では unstable_rethrow を先に](frontend.md#31-catch-では-unstable_rethrow-を先に呼ぶ)、[結果は ActionResult](frontend.md#32-結果は-actionresult-で返す) |
| テスト | [AC 番号を入れる](testing.md#11-ac-番号を入れる)、[名前は振る舞いを書く](testing.md#12-名前は振る舞いを書く) |
| テスト | [統合テストの土台](testing.md#21-統合テストの土台)、[テストデータは補助のクラス](testing.md#22-テストデータは補助のクラスで作る) |
| テスト | [画面に見えるもので探す](testing.md#31-画面に見えるもので探す)、[E2E のデータの印](testing.md#33-e2e-のデータには実行ごとの印)、[時間で待たない](testing.md#4-待ち方) |
| コメント | [何を書くか](comments.md#1-何を書くか)、[Javadoc](comments.md#2-javajavadoc)、[TSDoc](comments.md#3-typescripttsdoc)、[処理の中のコメント](comments.md#4-処理の中のコメント) |
| 文書 | 日本語の文書・コメント・コミットメッセージに、中国語の字や言い回しが混ざっていない（constitution 14） |
