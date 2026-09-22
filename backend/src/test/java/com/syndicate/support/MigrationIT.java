package com.syndicate.support;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

class MigrationIT extends IntegrationTestBase {

    @Autowired Flyway flyway;

    @Test
    void allMigrationsApplyCleanlyAndHibernateValidatesTheSchema() {
        // context startup already ran Flyway and ddl-auto=validate; assert nothing is pending
        assertThat(flyway.info().pending()).isEmpty();
        assertThat(flyway.info().current()).isNotNull();
    }
}
