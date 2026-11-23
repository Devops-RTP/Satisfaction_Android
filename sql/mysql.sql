-- MySQL / MariaDB
CREATE TABLE SurveyResponses (
    response_id    VARCHAR(36)  NOT NULL PRIMARY KEY,
    rating         TINYINT      NOT NULL,
    created_utc    DATETIME     NOT NULL,
    created_local  DATETIME     NOT NULL,
    site_id        VARCHAR(100) NOT NULL,
    device_id      VARCHAR(100) NOT NULL,
    received_utc   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX ix_site_time (site_id, created_utc)
);
-- MySQL 8: the bundled driver needs mysql_native_password for this user:
-- CREATE USER 'kiosk_writer'@'%' IDENTIFIED WITH mysql_native_password BY '...';
-- GRANT INSERT ON yourdb.SurveyResponses TO 'kiosk_writer'@'%';
