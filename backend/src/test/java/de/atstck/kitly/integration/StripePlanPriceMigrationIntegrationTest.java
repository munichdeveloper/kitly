package de.atstck.kitly.integration;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import java.sql.Connection;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies V30__copy_stripe_plan_price_ids_to_test_mode.sql (#49): legacy, mode-independent
 * stripe.plan.* price IDs are copied to stripe.test.plan.*, without deleting the legacy rows and
 * without overwriting already existing per-mode values.
 * <p>
 * Flyway already applied V30 to the (reused) container, so the test re-runs the script itself on
 * known rows, which also proves it is safe to run again.
 */
public class StripePlanPriceMigrationIntegrationTest extends BaseIntegrationTest {

    private static final String MIGRATION = "db/migration/V30__copy_stripe_plan_price_ids_to_test_mode.sql";
    private static final String KEY_PATTERN = "stripe.%plan.mig49_%";

    private JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        jdbc = new JdbcTemplate(dataSource);
        cleanUp();
    }

    @AfterEach
    void cleanUp() {
        new JdbcTemplate(dataSource).update("DELETE FROM platform_settings WHERE setting_key LIKE ?", KEY_PATTERN);
    }

    @Test
    void copiesLegacyPriceIdsToTestModeAndKeepsLegacyRows() throws Exception {
        insert("stripe.plan.mig49_solo", "price_test_solo");
        insert("stripe.plan.mig49_team", "price_test_team");
        insert("stripe.plan.mig49_team.active", "true");
        // Already migrated/edited per-mode value must win
        insert("stripe.test.plan.mig49_team", "price_test_team_new");

        runMigration();

        assertThat(value("stripe.test.plan.mig49_solo")).containsExactly("price_test_solo");
        assertThat(value("stripe.test.plan.mig49_team")).containsExactly("price_test_team_new");
        assertThat(value("stripe.test.plan.mig49_team.active")).isEmpty();
        // Nothing is invented for live mode
        assertThat(value("stripe.live.plan.mig49_solo")).isEmpty();
        // Legacy rows are kept
        assertThat(value("stripe.plan.mig49_solo")).containsExactly("price_test_solo");
        assertThat(value("stripe.plan.mig49_team")).containsExactly("price_test_team");

        // Running it again changes nothing
        runMigration();
        assertThat(value("stripe.test.plan.mig49_solo")).containsExactly("price_test_solo");
    }

    private void insert(String key, String value) {
        jdbc.update("INSERT INTO platform_settings (setting_key, setting_value, setting_type, description, is_encrypted) "
                + "VALUES (?, ?, 'STRING', 'test', FALSE)", key, value);
    }

    private List<String> value(String key) {
        return jdbc.queryForList("SELECT setting_value FROM platform_settings WHERE setting_key = ?", String.class, key);
    }

    private void runMigration() throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            ScriptUtils.executeSqlScript(connection, new EncodedResource(new ClassPathResource(MIGRATION)));
        }
    }
}
