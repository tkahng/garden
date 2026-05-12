package io.k2dv.garden.shared;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CountryCodeMigrationIT extends AbstractIntegrationTest {

    @Autowired JdbcTemplate jdbc;
    @Autowired DataSource dataSource;

    @Test
    void migrationNormalizesKnownUsaCountryCodes() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID addressId = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();
        UUID companyAddressId = UUID.randomUUID();
        UUID zoneId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();

        jdbc.update("""
            INSERT INTO auth.users (id, email, first_name, last_name, status)
            VALUES (?, ?, 'Test', 'User', 'ACTIVE')
            """, userId, "migration-" + userId + "@example.com");
        jdbc.update("""
            INSERT INTO auth.addresses
              (id, user_id, first_name, last_name, address1, city, zip, country)
            VALUES (?, ?, 'Test', 'User', '1 Main St', 'Portland', '97201', 'USA')
            """, addressId, userId);
        jdbc.update("""
            INSERT INTO b2b.companies (id, name)
            VALUES (?, 'Migration Co')
            """, companyId);
        jdbc.update("""
            INSERT INTO b2b.company_shipping_addresses
              (id, company_id, first_name, last_name, address1, city, zip, country)
            VALUES (?, ?, 'Test', 'User', '1 Main St', 'Portland', '97201', 'USA')
            """, companyAddressId, companyId);
        jdbc.update("""
            INSERT INTO shipping.shipping_zones (id, name, country_codes)
            VALUES (?, 'Migration Zone', ARRAY['USA', ' ca '])
            """, zoneId);
        jdbc.update("""
            INSERT INTO checkout.orders
              (id, user_id, status, total_amount, currency, shipping_address)
            VALUES (?, ?, 'PENDING_PAYMENT', 10.00, 'usd',
              '{"firstName":"Test","country":"USA"}'::jsonb)
            """, orderId, userId);

        Connection connection = DataSourceUtils.getConnection(dataSource);
        try {
            ScriptUtils.executeSqlScript(connection,
                new ClassPathResource("db/migration/V65__normalize_country_codes.sql"));
        } finally {
            DataSourceUtils.releaseConnection(connection, dataSource);
        }

        assertThat(jdbc.queryForObject(
            "SELECT country FROM auth.addresses WHERE id = ?", String.class, addressId))
            .isEqualTo("US");
        assertThat(jdbc.queryForObject(
            "SELECT country FROM b2b.company_shipping_addresses WHERE id = ?", String.class, companyAddressId))
            .isEqualTo("US");
        assertThat(jdbc.queryForObject(
            "SELECT country_codes::text FROM shipping.shipping_zones WHERE id = ?", String.class, zoneId))
            .isEqualTo("{US,CA}");
        assertThat(jdbc.queryForObject(
            "SELECT shipping_address ->> 'country' FROM checkout.orders WHERE id = ?", String.class, orderId))
            .isEqualTo("US");
    }
}
