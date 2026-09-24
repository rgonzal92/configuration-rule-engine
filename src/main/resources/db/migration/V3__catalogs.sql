-- Catalogs hold features and relationship groups. Composite keys make every source and target
-- belong to the group's own catalog. Feature IDs repeat across copies of the same seed.
-- Features are only ever removed with their catalog, so every reference to them cascades.
CREATE TABLE catalog (
  id uuid PRIMARY KEY,
  workspace_id uuid NOT NULL REFERENCES workspace (id) ON DELETE CASCADE,
  name text NOT NULL,
  sort_order int NOT NULL,
  revision bigint NOT NULL CHECK (revision > 0),
  UNIQUE (workspace_id, id)
);

CREATE TABLE feature (
  catalog_id uuid NOT NULL REFERENCES catalog (id) ON DELETE CASCADE,
  id uuid NOT NULL,
  code text NOT NULL,
  name text NOT NULL,
  position int NOT NULL,
  PRIMARY KEY (catalog_id, id),
  UNIQUE (catalog_id, code)
);

CREATE TABLE relationship_group (
  catalog_id uuid NOT NULL REFERENCES catalog (id) ON DELETE CASCADE,
  id uuid NOT NULL,
  seq bigint GENERATED ALWAYS AS IDENTITY,
  source_feature_id uuid NOT NULL,
  kind text NOT NULL CHECK (kind IN ('REQUIRES', 'REQUIRED_WITH', 'NOT_ALLOWED_WITH')),
  PRIMARY KEY (catalog_id, id),
  FOREIGN KEY (catalog_id, source_feature_id) REFERENCES feature (catalog_id, id)
    ON DELETE CASCADE
);

-- An exclusion keeps the single authored source-target entry; it is never mirrored.
CREATE TABLE relationship_target (
  catalog_id uuid NOT NULL,
  group_id uuid NOT NULL,
  target_feature_id uuid NOT NULL,
  position int NOT NULL,
  PRIMARY KEY (catalog_id, group_id, target_feature_id),
  FOREIGN KEY (catalog_id, group_id) REFERENCES relationship_group (catalog_id, id)
    ON DELETE CASCADE,
  FOREIGN KEY (catalog_id, target_feature_id) REFERENCES feature (catalog_id, id)
    ON DELETE CASCADE
);

CREATE INDEX relationship_target_feature ON relationship_target (catalog_id, target_feature_id);

-- One pending batch per guest catalog. Its version only ever increases, and a check is current
-- only while both checked versions still match.
CREATE TABLE pending_batch (
  catalog_id uuid PRIMARY KEY REFERENCES catalog (id) ON DELETE CASCADE,
  operations jsonb NOT NULL,
  draft_version bigint NOT NULL,
  base_revision bigint NOT NULL,
  checked_draft_version bigint,
  checked_revision bigint
);

-- Lets a retried apply return its first result after the draft has been cleared.
CREATE TABLE apply_receipt (
  workspace_id uuid NOT NULL REFERENCES workspace (id) ON DELETE CASCADE,
  command_id uuid NOT NULL,
  catalog_id uuid NOT NULL,
  draft_version bigint NOT NULL,
  result jsonb NOT NULL,
  PRIMARY KEY (workspace_id, command_id),
  FOREIGN KEY (workspace_id, catalog_id) REFERENCES catalog (workspace_id, id) ON DELETE CASCADE
);

CREATE INDEX apply_receipt_catalog ON apply_receipt (catalog_id);

-- Read-only showcase catalogs. The laptop catalog is also the seed copied for each guest.
INSERT INTO catalog (id, workspace_id, name, sort_order, revision) VALUES
  ('00000000-0000-0000-0000-00000000c001', '00000000-0000-0000-0000-000000000001', 'Laptop', 1, 1),
  ('00000000-0000-0000-0000-00000000c002', '00000000-0000-0000-0000-000000000001',
   'Automotive (fictional)', 2, 1);

INSERT INTO feature (catalog_id, id, code, name, position)
SELECT '00000000-0000-0000-0000-00000000c001'::uuid, id::uuid, code, name, position
FROM (VALUES
  ('00000000-0000-0000-0000-00000000f001', 'DEDICATED_GPU', 'Dedicated GPU', 1),
  ('00000000-0000-0000-0000-00000000f002', 'HIGH_WATTAGE_CHARGER', 'High-wattage charger', 2),
  ('00000000-0000-0000-0000-00000000f003', 'FANLESS_CHASSIS', 'Fanless chassis', 3),
  ('00000000-0000-0000-0000-00000000f004', 'TOUCHSCREEN', 'Touchscreen', 4),
  ('00000000-0000-0000-0000-00000000f005', 'STYLUS_SUPPORT', 'Stylus support', 5),
  ('00000000-0000-0000-0000-00000000f006', 'HIGH_RESOLUTION_DISPLAY', 'High-resolution display', 6),
  ('00000000-0000-0000-0000-00000000f007', 'BACKLIT_KEYBOARD', 'Backlit keyboard', 7),
  ('00000000-0000-0000-0000-00000000f008', 'FINGERPRINT_READER', 'Fingerprint reader', 8),
  ('00000000-0000-0000-0000-00000000f009', 'LTE_MODEM', 'LTE modem', 9),
  ('00000000-0000-0000-0000-00000000f010', 'EXTRA_BATTERY', 'Extra battery', 10),
  ('00000000-0000-0000-0000-00000000f011', 'PRIVACY_SCREEN', 'Privacy screen', 11)
) AS seed (id, code, name, position);

INSERT INTO feature (catalog_id, id, code, name, position)
SELECT '00000000-0000-0000-0000-00000000c002'::uuid, id::uuid, code, name, position
FROM (VALUES
  ('00000000-0000-0000-0000-00000000f101', 'TOW_PACKAGE', 'Tow package', 1),
  ('00000000-0000-0000-0000-00000000f102', 'HEAVY_DUTY_COOLING', 'Heavy-duty cooling', 2),
  ('00000000-0000-0000-0000-00000000f103', 'TRAILER_BRAKE_CONTROLLER', 'Trailer brake controller', 3),
  ('00000000-0000-0000-0000-00000000f104', 'SUNROOF', 'Sunroof', 4),
  ('00000000-0000-0000-0000-00000000f105', 'ROOF_RACK', 'Roof rack', 5),
  ('00000000-0000-0000-0000-00000000f106', 'HEATED_SEATS', 'Heated seats', 6),
  ('00000000-0000-0000-0000-00000000f107', 'REMOTE_START', 'Remote start', 7),
  ('00000000-0000-0000-0000-00000000f108', 'ALL_WEATHER_MATS', 'All-weather floor mats', 8)
) AS seed (id, code, name, position);

INSERT INTO relationship_group (catalog_id, id, source_feature_id, kind) VALUES
  ('00000000-0000-0000-0000-00000000c001', '00000000-0000-0000-0000-00000000a001',
   '00000000-0000-0000-0000-00000000f001', 'REQUIRES'),
  ('00000000-0000-0000-0000-00000000c002', '00000000-0000-0000-0000-00000000a101',
   '00000000-0000-0000-0000-00000000f101', 'REQUIRES'),
  ('00000000-0000-0000-0000-00000000c002', '00000000-0000-0000-0000-00000000a102',
   '00000000-0000-0000-0000-00000000f103', 'REQUIRED_WITH'),
  ('00000000-0000-0000-0000-00000000c002', '00000000-0000-0000-0000-00000000a103',
   '00000000-0000-0000-0000-00000000f104', 'NOT_ALLOWED_WITH');

INSERT INTO relationship_target (catalog_id, group_id, target_feature_id, position) VALUES
  ('00000000-0000-0000-0000-00000000c001', '00000000-0000-0000-0000-00000000a001',
   '00000000-0000-0000-0000-00000000f002', 1),
  ('00000000-0000-0000-0000-00000000c002', '00000000-0000-0000-0000-00000000a101',
   '00000000-0000-0000-0000-00000000f102', 1),
  ('00000000-0000-0000-0000-00000000c002', '00000000-0000-0000-0000-00000000a102',
   '00000000-0000-0000-0000-00000000f101', 1),
  ('00000000-0000-0000-0000-00000000c002', '00000000-0000-0000-0000-00000000a103',
   '00000000-0000-0000-0000-00000000f105', 1);
