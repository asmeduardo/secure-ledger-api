package io.github.asmeduardo.secureledger.demo;

import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionOperations;

/**
 * Loads the fictitious accounts of the evaluation script when the {@code demo} profile is active.
 * The script runs once, in one transaction; the deferred triggers of the ledger check it at commit
 * like any other write.
 */
@Component
@Profile("demo")
class DemoData implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoData.class);
    private static final String FIRST_DEMO_ACCOUNT = "a1000000-0000-4000-8000-000000000001";

    private final DataSource dataSource;
    private final JdbcTemplate jdbc;
    private final TransactionOperations transactions;

    DemoData(DataSource dataSource, JdbcTemplate jdbc, TransactionOperations transactions) {
        this.dataSource = dataSource;
        this.jdbc = jdbc;
        this.transactions = transactions;
    }

    @Override
    public void run(ApplicationArguments args) {
        transactions.executeWithoutResult(status -> {
            var seeded = jdbc.queryForObject(
                    "select exists (select 1 from accounts.customer_account where id = ?::uuid)",
                    Boolean.class,
                    FIRST_DEMO_ACCOUNT);
            if (Boolean.TRUE.equals(seeded)) {
                return;
            }
            ScriptUtils.executeSqlScript(
                    DataSourceUtils.getConnection(dataSource), new ClassPathResource("demo/demo-data.sql"));
            log.info("Loaded the fictitious demo accounts");
        });
    }
}
