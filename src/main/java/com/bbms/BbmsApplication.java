package com.bbms;

import com.bbms.domain.User;
import com.bbms.repo.UserRepository;
import com.bbms.service.WorkflowHistoryBackfill;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import java.util.Locale;

@SpringBootApplication
public class BbmsApplication {
    public static void main(String[] args) { SpringApplication.run(BbmsApplication.class, args); }
    @Bean @Profile("!presentation & !training") @ConditionalOnProperty(name="bbms.startup.allow-depleted-inventory-migration", havingValue="true", matchIfMissing=true) @Order(Ordered.HIGHEST_PRECEDENCE) CommandLineRunner allowDepletedInventory(JdbcTemplate jdbc) {
        return args -> {
            var checks = jdbc.query("select tc.constraint_name, cc.check_clause from information_schema.table_constraints tc join information_schema.check_constraints cc on cc.constraint_schema=tc.constraint_schema and cc.constraint_name=tc.constraint_name where tc.constraint_schema=database() and tc.table_name='blood_units' and tc.constraint_type='CHECK'",
                    (rs, row) -> new String[]{rs.getString(1), rs.getString(2)});
            boolean nonnegativeCheck = false;
            for (String[] check : checks) {
                String expression = check[1].toLowerCase(Locale.ROOT).replace("`", "").replaceAll("[\\s()]", "");
                if (expression.equals("quantity>=1")) {
                    String constraint = check[0].replace("`", "``");
                    jdbc.execute("alter table blood_units drop check `" + constraint + "`");
                } else if (expression.equals("quantity>=0")) {
                    nonnegativeCheck = true;
                }
            }
            if (!nonnegativeCheck) jdbc.execute("alter table blood_units add constraint chk_blood_units_nonnegative check (quantity >= 0)");
        };
    }
    @Bean @Profile("!presentation & !training") @ConditionalOnProperty(name="bbms.startup.admin-bootstrap", havingValue="true", matchIfMissing=true) CommandLineRunner createAdmin(UserRepository users, PasswordEncoder encoder) {
        return args -> {
            String email = System.getenv("BBMS_ADMIN_EMAIL");
            String password = System.getenv("BBMS_ADMIN_PASSWORD");
            if (email != null && !email.isBlank() && password != null && password.length() >= 12 && users.findByEmailIgnoreCase(email).isEmpty()) {
                users.save(new User("BBMS Administrator", email, encoder.encode(password), "ADMIN", "Blood bank administration", ""));
        }
        };
    }
    @Bean @Profile("!presentation & !training") @ConditionalOnProperty(name="bbms.startup.workflow-history-backfill", havingValue="true", matchIfMissing=true) @Order(Ordered.LOWEST_PRECEDENCE - 1) CommandLineRunner backfillWorkflowHistory(WorkflowHistoryBackfill backfill) {
        return args -> backfill.backfill();
    }
}
