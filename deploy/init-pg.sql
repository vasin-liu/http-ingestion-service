DROP TABLE IF EXISTS dahua_vehicle_pass;
DROP TABLE IF EXISTS dahua_vehicle_count;
DROP TABLE IF EXISTS dahua_motor_illegal;
DROP TABLE IF EXISTS dahua_motor_illegal_count;
DROP TABLE IF EXISTS meiya_traffic_police_alert;
DROP TABLE IF EXISTS meiya_dispatch110_flow;
DROP TABLE IF EXISTS users;
DROP TABLE IF EXISTS items;
DROP TABLE IF EXISTS webhook_events;
DROP TABLE IF EXISTS jiadu_event_info;

CREATE TABLE dahua_vehicle_pass (
    record_id VARCHAR(64) PRIMARY KEY,
    plate_num VARCHAR(32),
    cap_time VARCHAR(32),
    channel_name VARCHAR(255),
    plate_type VARCHAR(16)
);

CREATE TABLE dahua_vehicle_count (
    stat_key VARCHAR(32) PRIMARY KEY,
    total_count BIGINT
);

CREATE TABLE dahua_motor_illegal (
    record_id VARCHAR(64) PRIMARY KEY,
    plate_num VARCHAR(32),
    cap_time BIGINT,
    rec_type BIGINT,
    channel_name VARCHAR(255),
    plate_type VARCHAR(16)
);

CREATE TABLE dahua_motor_illegal_count (
    stat_key VARCHAR(32) PRIMARY KEY,
    total_count BIGINT
);

CREATE TABLE meiya_traffic_police_alert (
    jqbh VARCHAR(64) PRIMARY KEY,
    jqfssj VARCHAR(32),
    evcc VARCHAR(32),
    desct VARCHAR(512)
);

CREATE TABLE meiya_dispatch110_flow (
    bh VARCHAR(64) PRIMARY KEY,
    jjdbh VARCHAR(64),
    gxsj VARCHAR(32),
    xxlxms VARCHAR(512)
);

CREATE TABLE users (
    id BIGINT PRIMARY KEY,
    name VARCHAR(255)
);

-- Example templates (rest-pagination / rest-offset-limit / webhook-json-array)
CREATE TABLE items (
    id BIGINT PRIMARY KEY,
    name VARCHAR(255)
);

CREATE TABLE webhook_events (
    id BIGINT PRIMARY KEY
);

CREATE TABLE jiadu_event_info (
    event_id VARCHAR(64) PRIMARY KEY,
    event_type BIGINT,
    event_name VARCHAR(255),
    send_time VARCHAR(32),
    camera_id VARCHAR(64),
    img_url TEXT,
    video_url TEXT,
    event_time VARCHAR(32),
    confidence DOUBLE PRECISION,
    task_id VARCHAR(64),
    event_group INT,
    census INT,
    inter_day INT,
    enter_number INT,
    out_number INT,
    raw_json JSONB
);
