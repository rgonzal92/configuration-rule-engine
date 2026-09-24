-- Each live assistant request reserves one attempt before the model is called, so a timeout still
-- counts. Attempts outlive their workspace so the deployment-wide daily count stays exact after a
-- guest workspace is purged.
CREATE TABLE suggestion_attempt (
  id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  workspace_id uuid REFERENCES workspace (id) ON DELETE SET NULL,
  attempted_at timestamptz NOT NULL
);

CREATE INDEX suggestion_attempt_workspace ON suggestion_attempt (workspace_id);
CREATE INDEX suggestion_attempt_time ON suggestion_attempt (attempted_at);
