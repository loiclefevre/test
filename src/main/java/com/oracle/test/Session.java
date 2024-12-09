package com.oracle.test;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.Base64;

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

	private final String runID;

	private String user;
	private String password;
	private DatabaseType databaseType;

	public Session(final String[] args) {
		runID = System.getenv("RUNID");
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
						databaseType = DatabaseType.valueOf(args[++i]);
					}
					catch (IllegalArgumentException iae) {
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

		switch (action) {
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
			final String dbType = switch(databaseType) {
				case DatabaseType.atps -> "autonomous";
				case DatabaseType.db19c -> "db19c";
				case DatabaseType.db21c -> "db21c";
				case DatabaseType.db23ai -> "db23ai";
			};

			final String hostname = InetAddress.getLocalHost().getHostName();

			final String uri = String.format("https://api.atlas-controller.oraclecloud.com/ords/atlas/admin/database?type=%s&hostname=%s",dbType, hostname);

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

				final HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

				if (response.statusCode() == 200) {
					createSchemaWithORDS(response.body());
				}
				else {
					throw new TestException(TestException.CREATE_SCHEMA_REST_ENDPOINT_ISSUE,
							new IllegalStateException("HTTP/S status code: "+response.statusCode()));
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
			throw new TestException(TestException.WRONG_MAIN_CONTROLLER_REST_CALL, e);
		}
		catch (InterruptedException e) {
			throw new TestException(TestException.WRONG_MAIN_CONTROLLER_REST_CALL, e);
		}
	}

	private String basicAuth(final String user, final String password) {
		return String.format("Basic %s", Base64.getEncoder().encodeToString((String.format("%s:%s", user, password)).getBytes()));
	}

	private void createSchemaWithORDS(final String jsonInformation) {
		try {
			Database database = new ObjectMapper().readValue(jsonInformation, Database.class);
			database = new ObjectMapper().readValue(database.getDatabase(), Database.class);

			System.out.println(database);

			final String uri = String.format("https://%s.oraclecloudapps.com/ords/admin/_/sql", database.getHost());

			final HttpRequest request = HttpRequest.newBuilder()
					.uri(new URI(uri))
					.headers("Accept", "application/json",
							"Content-Type", "application/sql",
							"Authorization", basicAuth("admin", database.getPassword()),
							"Pragma", "no-cache",
							"Cache-Control", "no-store")
					.POST(HttpRequest.BodyPublishers.ofString(String.format("""
							create user hibernate_orm_test_%s identified by "Oracle_19_Password" DEFAULT TABLESPACE DATA TEMPORARY TABLESPACE TEMP;
							alter user hibernate_orm_test_%s quota unlimited on data;
							grant CREATE SESSION, RESOURCE, CREATE VIEW, CREATE SYNONYM, CREATE ANY INDEX, EXECUTE ANY TYPE to hibernate_orm_test_%s;
							""",runID, runID, runID)))
					.build();

			try (HttpClient client = HttpClient
					.newBuilder()
					.version(HttpClient.Version.HTTP_1_1)
					.proxy(ProxySelector.getDefault())
					.followRedirects(HttpClient.Redirect.NORMAL)
					.build()) {

				final HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

				if (response.statusCode() == 200) {
					System.out.println(response.body());
				}
				else {
					throw new TestException(TestException.ATPS_REST_ENDPOINT_ISSUE,
							new IllegalStateException("HTTP/S status code: "+response.statusCode()));
				}
			}
		}
		catch (JsonMappingException e) {
			throw new TestException(TestException.BAD_CREATE_SCHEMA_RESPONSE,e);
		}
		catch (JsonProcessingException e) {
			throw new TestException(TestException.BAD_CREATE_SCHEMA_RESPONSE,e);
		}
		catch (URISyntaxException e) {
			throw new TestException(TestException.WRONG_ATPS_REST_CALL,e);
		}
		catch (IOException e) {
			throw new TestException(TestException.WRONG_ATPS_REST_CALL,e);
		}
		catch (InterruptedException e) {
			throw new TestException(TestException.WRONG_ATPS_REST_CALL,e);
		}
	}

	private void skipTesting() {
	}
}
