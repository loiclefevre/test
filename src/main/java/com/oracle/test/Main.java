package com.oracle.test;

import com.oracle.test.exception.TestException;

import java.util.Locale;

/**
 * Test services main entry point.
 *
 * @author LLEFEVRE
 * @since 0.0.1
 */
public class Main {

	static {
		Locale.setDefault(Locale.US);
		System.setProperty("java.net.useSystemProxies", "true");
	}

	public static final String VERSION = "0.0.2";

	public static void main(final String[] args) {

		final long startTime = System.currentTimeMillis();
		int exitStatus = 0;

		Session session = null;

		try {
			session = new Session(args);
			session.banner();
			session.run();
		}
		catch (TestException te) {
			exitStatus = te.getErrorCode();
			if (session != null) {
				switch (session.action) {
					case CREATE_SCHEMA:
						System.out.printf("Schema creation failed (%d)%n", exitStatus);
						break;

					case SKIP_TESTING:
						System.out.printf("Skip testing check failed (%d)%n", exitStatus);
						break;
				}
			}

			System.out.println("Error: " + te.getMessage());
			te.printStackTrace();
		}

		System.exit(exitStatus);
	}
}
