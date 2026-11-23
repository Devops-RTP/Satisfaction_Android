-- Daily summary per site (SQL Server syntax; adapt date functions for MySQL/PostgreSQL)
SELECT site_id,
       CAST(created_local AS DATE)                   AS day,
       COUNT(*)                                      AS responses,
       CAST(AVG(rating * 1.0) AS DECIMAL(3,2))       AS avg_rating,
       SUM(CASE WHEN rating >= 4 THEN 1 ELSE 0 END) * 100.0 / COUNT(*) AS pct_satisfied
FROM dbo.SurveyResponses
GROUP BY site_id, CAST(created_local AS DATE)
ORDER BY day DESC, site_id;
