-- Microsoft SQL Server
CREATE TABLE dbo.SurveyResponses (
    response_id    VARCHAR(36)   NOT NULL PRIMARY KEY,
    rating         TINYINT       NOT NULL CHECK (rating BETWEEN 1 AND 5),
    created_utc    DATETIME2(0)  NOT NULL,
    created_local  DATETIME2(0)  NOT NULL,
    site_id        NVARCHAR(100) NOT NULL,
    device_id      NVARCHAR(100) NOT NULL,
    received_utc   DATETIME2(0)  NOT NULL DEFAULT SYSUTCDATETIME()
);
CREATE INDEX IX_SurveyResponses_site_time ON dbo.SurveyResponses (site_id, created_utc);

-- Least-privilege login for the tablets (insert + the duplicate check only)
-- CREATE LOGIN kiosk_writer WITH PASSWORD = '...';
-- CREATE USER kiosk_writer FOR LOGIN kiosk_writer;
-- GRANT SELECT (response_id), INSERT ON dbo.SurveyResponses TO kiosk_writer;
