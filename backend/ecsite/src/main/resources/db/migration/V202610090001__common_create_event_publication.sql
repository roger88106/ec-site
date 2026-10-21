-- Spring Modulith のイベント発行記録（plan 001 第 4 節）
-- spring-modulith-events-jdbc 2.1.1 の公式 DDL（schemas/v2/schema-postgresql.sql）を common schema に作る。
-- 自動作成は使わない（spring.modulith.events.jdbc.schema-initialization.enabled=false）。
-- Spring Modulith を上げるときは、公式 DDL の差分を新しいマイグレーションで適用する。

CREATE TABLE common.event_publication
(
  id                     UUID NOT NULL,
  listener_id            TEXT NOT NULL,
  event_type             TEXT NOT NULL,
  serialized_event       TEXT NOT NULL,
  publication_date       TIMESTAMP WITH TIME ZONE NOT NULL,
  completion_date        TIMESTAMP WITH TIME ZONE,
  status                 TEXT,
  completion_attempts    INT,
  last_resubmission_date TIMESTAMP WITH TIME ZONE,
  PRIMARY KEY (id)
);
CREATE INDEX event_publication_serialized_event_hash_idx ON common.event_publication USING hash (serialized_event);
CREATE INDEX event_publication_by_completion_date_idx ON common.event_publication (completion_date);
