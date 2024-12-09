package com.oracle.test;

import com.oracle.test.exception.TestException;

import java.net.InetAddress;
import java.net.UnknownHostException;

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

	private String user;
	private String password;
	private DatabaseType databaseType;

	public Session(final String[] args) {
		analyzeCommandLineParameters(args);
	}

	private void analyzeCommandLineParameters(final String[] args) {
		for (int i = 0; i < args.length; i++) {
			final String arg = args[i].toLowerCase();

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

				case "--user":
					user = args[++i];
					break;

				case "--password":
					password = args[++i];
					break;

				case "--db-type":
					try {
						databaseType = DatabaseType.valueOf( args[++i] );
					}
					catch(IllegalArgumentException iae) {
						throw new TestException(TestException.WRONG_DATABASE_TYPE_PARAMETER,
								new IllegalArgumentException("--db-type must be either atps, db19c, db21c, or db23ai"));
					}
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

		switch(action) {
			case CREATE_SCHEMA:
				createSchema();
				break;

			case SKIP_TESTING:
				skipTesting();
				break;
		}
	}

	private void createSchema() {
		try {
			final String hostname = InetAddress.getLocalHost().getHostName();

		}
		catch (UnknownHostException e) {
			throw new TestException(TestException.UNKNOWN_HOSTNAME, e);
		}
	}

	private void skipTesting() {
	}
}
