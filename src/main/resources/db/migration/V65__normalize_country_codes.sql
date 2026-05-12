UPDATE auth.addresses
SET country = 'US'
WHERE upper(trim(country)) = 'USA';

UPDATE auth.addresses
SET country = upper(trim(country))
WHERE country ~* '^[[:space:]]*[a-z]{2}[[:space:]]*$';

UPDATE b2b.company_shipping_addresses
SET country = 'US'
WHERE upper(trim(country)) = 'USA';

UPDATE b2b.company_shipping_addresses
SET country = upper(trim(country))
WHERE country ~* '^[[:space:]]*[a-z]{2}[[:space:]]*$';

UPDATE shipping.shipping_zones
SET country_codes = array_replace(country_codes, 'USA', 'US')
WHERE country_codes IS NOT NULL;

UPDATE shipping.shipping_zones
SET country_codes = (
    SELECT array_agg(
        CASE
            WHEN upper(trim(code)) = 'USA' THEN 'US'
            WHEN code ~* '^[[:space:]]*[a-z]{2}[[:space:]]*$' THEN upper(trim(code))
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
         OR code ~* '^[[:space:]]*[a-z]{2}[[:space:]]*$'
  );

UPDATE checkout.orders
SET shipping_address = jsonb_set(shipping_address, '{country}', '"US"', false)
WHERE shipping_address IS NOT NULL
  AND upper(trim(shipping_address ->> 'country')) = 'USA';

UPDATE checkout.orders
SET shipping_address = jsonb_set(
    shipping_address,
    '{country}',
    to_jsonb(upper(trim(shipping_address ->> 'country'))),
    false
)
WHERE shipping_address IS NOT NULL
  AND (shipping_address ->> 'country') ~* '^[[:space:]]*[a-z]{2}[[:space:]]*$';
