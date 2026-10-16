package com.loop.new_loop_api;

import com.loop.new_loop_api.support.RequiresLocalPostgres;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

// The full context runs Flyway against a real database, so like the other
// Postgres-backed tests it only runs when LOOP_TEST_DB_URL points to one.
@RequiresLocalPostgres
@SpringBootTest
class NewLoopApiApplicationTests {

	@Test
	void contextLoads() {
	}

}
