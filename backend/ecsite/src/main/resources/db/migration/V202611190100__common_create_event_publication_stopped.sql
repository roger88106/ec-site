-- 止めたイベントの表を作る（バックエンドのアーキテクチャの「イベントの再発行」）。
-- 再発行のジョブ（config）は、処理の回数が上限以上で、最後の処理に失敗した未完了のイベントを、
-- イベント発行記録の表からこの表に移して止める（この表にあることが、止めたことを表す）。
-- 列は、イベント発行記録の表のうち復旧に必要なものと同じ名前と型にし、止めた日時を加える。
-- 状態と完了日時は、止めたイベントでは常に「失敗」と「なし」なので置かない。
-- Spring Modulith を上げるときは、イベント発行記録の表の列の変化と、この表の列を照合する
-- （復旧の手順は戻す列を名前で並べるので、増えた列が空を許すかを確かめる）。
-- 処理の試行回数と状態の意味が変わっていないかも確かめる（止める条件と復旧の手順が、この 2 つの列の意味に依存する）。

CREATE TABLE common.event_publication_stopped
(
  id                     UUID                     NOT NULL,
  listener_id            TEXT                     NOT NULL,
  event_type             TEXT                     NOT NULL,
  serialized_event       TEXT                     NOT NULL,
  publication_date       TIMESTAMP WITH TIME ZONE NOT NULL,
  completion_attempts    INT,
  last_resubmission_date TIMESTAMP WITH TIME ZONE,
  stopped_date           TIMESTAMP WITH TIME ZONE NOT NULL,
  PRIMARY KEY (id)
);

COMMENT ON TABLE common.event_publication_stopped IS '止めたイベント';
COMMENT ON COLUMN common.event_publication_stopped.id IS 'イベント発行記録 ID';
COMMENT ON COLUMN common.event_publication_stopped.listener_id IS 'リスナー ID';
COMMENT ON COLUMN common.event_publication_stopped.event_type IS 'イベントの型';
COMMENT ON COLUMN common.event_publication_stopped.serialized_event IS '直列化したイベント';
COMMENT ON COLUMN common.event_publication_stopped.publication_date IS '発行日時';
COMMENT ON COLUMN common.event_publication_stopped.completion_attempts IS '処理の試行回数';
COMMENT ON COLUMN common.event_publication_stopped.last_resubmission_date IS '最終再発行日時';
COMMENT ON COLUMN common.event_publication_stopped.stopped_date IS '停止日時';

-- イベント発行記録の処理の試行回数のコメントは、論理名だけにする
COMMENT ON COLUMN common.event_publication.completion_attempts IS '処理の試行回数';
