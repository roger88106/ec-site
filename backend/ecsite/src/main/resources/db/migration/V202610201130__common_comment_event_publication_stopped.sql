-- イベント発行記録の処理の試行回数に、止めた印の意味を書く（バックエンドのアーキテクチャ 第 5 節）。
-- 再発行のジョブ（config）は、Spring Modulith の表を直接読み書きする：
--   処理の試行回数は、発行の時点で 1、再発行のたびに 1 つ増える（Spring Modulith 2.1）。
--   試行回数が上限に達し、状態が FAILED のイベントは、試行回数を上限より 1 つ多くして止める（再発行しない）。
-- Spring Modulith を上げるときは、公式 DDL の差分に加えて、処理の試行回数と状態の意味が変わっていないかを確かめる。
COMMENT ON COLUMN common.event_publication.completion_attempts IS '処理の試行回数（上限を超えた値は、再発行を止めた印）';
