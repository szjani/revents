CREATE TABLE IF NOT EXISTS aggregate (
    aggregate_type VARCHAR(255) NOT NULL,
    aggregate_id VARCHAR(255) NOT NULL,
    revision BIGINT NOT NULL,
    PRIMARY KEY (aggregate_type, aggregate_id)
);

CREATE TABLE IF NOT EXISTS event (
    sequence_number BIGINT NOT NULL AUTO_INCREMENT,
    event_id VARCHAR(255) NOT NULL UNIQUE,
    aggregate_type VARCHAR(255) NOT NULL,
    aggregate_id VARCHAR(255) NOT NULL,
    created TIMESTAMP WITH TIME ZONE NOT NULL,
    revision BIGINT NOT NULL,
    event_type VARCHAR(255) NOT NULL,
    data CLOB NOT NULL,
    PRIMARY KEY (sequence_number)
);

CREATE TABLE IF NOT EXISTS stream_link (
    event_id VARCHAR(255) NOT NULL,
    stream_name VARCHAR(255) NOT NULL,
    PRIMARY KEY (event_id, stream_name),
    FOREIGN KEY (event_id) REFERENCES event(event_id)
);

CREATE TABLE IF NOT EXISTS snapshot (
    aggregate_type VARCHAR(255) NOT NULL,
    aggregate_id VARCHAR(255) NOT NULL,
    aggregate CLOB NOT NULL,
    based_on_event VARCHAR(255) NOT NULL,
    revision BIGINT NOT NULL,
    created TIMESTAMP WITH TIME ZONE NOT NULL,
    PRIMARY KEY (aggregate_type, aggregate_id)
);

CREATE INDEX IF NOT EXISTS idx_aggregate_events ON event (aggregate_type, aggregate_id, revision);
CREATE INDEX IF NOT EXISTS idx_stream_link_stream_name ON stream_link (stream_name);
