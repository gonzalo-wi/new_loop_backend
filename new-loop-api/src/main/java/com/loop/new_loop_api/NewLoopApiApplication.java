package com.loop.new_loop_api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.util.TimeZone;

@SpringBootApplication
public class NewLoopApiApplication {

	public static void main(String[] args) {
		// The JVM's auto-detected zone id can resolve to the deprecated alias "America/Buenos_Aires",
		// which some Postgres tzdata builds don't recognize. Pin the canonical id (same offset, no DST)
		// so the JDBC driver's startup TimeZone parameter is always accepted.
		TimeZone.setDefault(TimeZone.getTimeZone("America/Argentina/Buenos_Aires"));
		SpringApplication.run(NewLoopApiApplication.class, args);
	}

}
