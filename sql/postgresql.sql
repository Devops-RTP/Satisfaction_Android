-- PostgreSQL
CREATE TABLE survey_responses (
    response_id    VARCHAR(36)  PRIMARY KEY,
    rating         SMALLINT     NOT NULL CHECK (rating BETWEEN 1 AND 5),
    created_utc    TIMESTAMP    NOT NULL,
    created_local  TIMESTAMP    NOT NULL,
    site_id        VARCHAR(100) NOT NULL,
    device_id      VARCHAR(100) NOT NULL,
    received_utc   TIMESTAMP    NOT NULL DEFAULT (now() AT TIME ZONE 'utc')
);
CREATE INDEX ix_survey_site_time ON survey_responses (site_id, created_utc);
-- CREATE ROLE kiosk_writer LOGIN PASSWORD '...';
-- GRANT INSERT ON survey_responses TO kiosk_writer;
-- In kiosk.ini use: table = survey_responses
