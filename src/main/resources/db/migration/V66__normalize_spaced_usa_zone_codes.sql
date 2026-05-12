UPDATE shipping.shipping_zones
SET country_codes = (
    SELECT array_agg(
        CASE
            WHEN upper(trim(code)) = 'USA' THEN 'US'
            ELSE code
        END
        ORDER BY ordinal
    )
    FROM unnest(country_codes) WITH ORDINALITY AS country_code(code, ordinal)
)
WHERE country_codes IS NOT NULL
  AND EXISTS (
      SELECT 1
      FROM unnest(country_codes) AS country_code(code)
      WHERE upper(trim(code)) = 'USA'
  );
