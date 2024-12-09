package com.oracle.test;

import com.oracle.test.exception.TestException;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ProxySelector;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

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

		switch(action) {
			case CREATE_SCHEMA:
				System.out.printf("%s%n", action.getBanner());
				createSchema();
				break;

			case SKIP_TESTING:
				System.out.printf("%s%n", action.getBanner());
				skipTesting();
				break;
		}
	}

	private void createSchema() {
		try {
			final String hostname = InetAddress.getLocalHost().getHostName();

			// https://api.atlas-controller.oraclecloud.com/ords/atlas/admin/database?type=autonomous&hostname=`hostname`

			final String uri = String.format("https://api.atlas-controller.oraclecloud.com/ords/atlas/admin/database?type=autonomous&hostname=%s",hostname);

			System.out.println(uri);

			final HttpRequest request = HttpRequest.newBuilder()
					.uri(new URI(uri))
					.headers("Accept", "application/json",
							"Pragma", "no-cache",
							"Cache-Control", "no-store")
					.GET()
					.build();

			try (HttpClient client = HttpClient
					.newBuilder()
					.version(HttpClient.Version.HTTP_1_1)
					.proxy(ProxySelector.getDefault())
					.followRedirects(HttpClient.Redirect.NORMAL)
					.build()) {

				final HttpResponse<String>  response = client.send(request, HttpResponse.BodyHandlers.ofString());

				if (response.statusCode() == 200) {

				}
			}
		}
		catch (UnknownHostException e) {
			throw new TestException(TestException.UNKNOWN_HOSTNAME, e);
		}
		catch (URISyntaxException e) {
			throw new TestException(TestException.WRONG_MAIN_CONTROLLER_URI, e);
		}
		catch (IOException e) {
			throw new RuntimeException(e);
		}
		catch (InterruptedException e) {
			throw new RuntimeException(e);
		}
	}

	private void skipTesting() {
	}
}
