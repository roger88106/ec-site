-- 定期ジョブの分散ロック（バックエンドのアーキテクチャ 第 5 節）
-- ShedLock の JDBC の provider の公式 DDL（PostgreSQL）を common schema に作る。
-- 時刻は DB の時計（UTC）で書かれる（usingDbTime）。

CREATE TABLE common.shedlock
(
  name       VARCHAR(64)  NOT NULL,
  lock_until TIMESTAMP    NOT NULL,
  locked_at  TIMESTAMP    NOT NULL,
  locked_by  VARCHAR(255) NOT NULL,
  PRIMARY KEY (name)
);

COMMENT ON TABLE common.shedlock IS '定期ジョブのロック';
COMMENT ON COLUMN common.shedlock.name IS 'ジョブ名';
COMMENT ON COLUMN common.shedlock.lock_until IS 'ロックの期限';
COMMENT ON COLUMN common.shedlock.locked_at IS 'ロックした日時';
COMMENT ON COLUMN common.shedlock.locked_by IS 'ロックしたインスタンス';
