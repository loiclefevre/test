package com.oracle.test;

import com.oracle.test.exception.TestException;

import static com.oracle.test.Main.VERSION;

/**
 * Test session.
 *
 * @author LLEFEVRE
 * @since 0.0.1
 */
public class Session {

	private boolean showVersion;

	public Action action;

	public Session(final String[] args) {
		analyzeCommandLineParameters(args);
	}

	private void analyzeCommandLineParameters(final String[] args) {
		for (String s : args) {
			final String arg = s.toLowerCase();

			switch (arg) {
				case "--help":
				case "-h":
				case "-?":
					displayUsage();
					System.exit(0);
					break;

				case "--version":
				case "-v":
					showVersion = true;
					break;

				case "--create-schema":
					action = Action.CREATE_SCHEMA;
					break;

				case "--skip-testing":
					action = Action.SKIP_TESTING;
					break;

				default:
					displayUsage();
					throw new TestException(TestException.UNKNOWN_COMMAND_LINE_ARGUMENT);
			}
		}
	}

	private void displayUsage() {
		System.out.println("""
				Usage: test <service> [options]
								
				Services:
				--create-schema    creates a schema for running the tests
				    Options:
				    --user              user name to be used
				    --password          password to be used
				    --db-type [type]    database type: atps, db19c, db21c, db23ai
				--skip-testing
				    Options:
					--prefix
				""");
	}

	public void banner() {
		if (showVersion) {
			System.out.printf("Test v%s%n", VERSION);
		}
		else {
			System.out.printf("Test%n");
		}
	}

	public void run() {
		System.out.printf("%s%n", action.getBanner());
	}
}
