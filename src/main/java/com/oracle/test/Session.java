package com.oracle.test;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.oracle.test.exception.TestException;
import com.oracle.test.model.Action;
import com.oracle.test.model.Database;
import com.oracle.test.model.DatabaseType;
import com.oracle.test.model.GitHubCommittedFiles;
import com.oracle.test.model.GitHubFilename;

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
import static com.oracle.test.exception.TestException.*;

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

	private String prefixList;
	private String owner;
	private String repository;
	private String sha;

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
						throw new TestException(WRONG_DATABASE_TYPE_PARAMETER,
								new IllegalArgumentException("--db-type must be either atps, db19c, db21c, or db23ai"));
					}
					break;

				case "--skip-testing":
					action = Action.SKIP_TESTING;
					break;

				case "--prefix-list":
					prefixList = args[++i];
					break;

				case "--owner":
					owner = args[++i];
					break;

				case "--repository":
					repository = args[++i];
					break;

				case "--sha":
					sha = args[++i];
					break;

				default:
					displayUsage();
					throw new TestException(UNKNOWN_COMMAND_LINE_ARGUMENT);
			}
		}
	}

	private void displayUsage() {
		System.out.println("""
				Usage: test <service> <options...>
								
				Services:
				--create-schema    creates a schema for running the tests
				    Options:
				    --user <user>          user name to be used
				    --password <password>  password to be used
				    --db-type <type>       database type (atps, db19c, db21c, db23ai)
				--skip-testing
				    Options:
					--owner <owner>            GitHub project owner
					--repository <repository>  GitHub project repository
					--sha <sha>                GitHub commit sha to check
					--prefix-list <p1,p2,...>  Comma-separated list of prefixes that will NOT trigger tests
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
		if (action == null) return;

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
		if (user == null || user.isEmpty()) {
			throw new TestException(CREATE_SCHEMA_MISSING_USER_NAME);
		}
		if (password == null || password.isEmpty()) {
			throw new TestException(CREATE_SCHEMA_MISSING_PASSWORD);
		}
		if (databaseType == null) {
			throw new TestException(CREATE_SCHEMA_MISSING_DB_TYPE);
		}

		try {
			final String dbType = switch (databaseType) {
				case DatabaseType.atps -> "autonomous";
				case DatabaseType.db19c -> "db19c";
				case DatabaseType.db21c -> "db21c";
				case DatabaseType.db23ai -> "db23ai";
			};

			final String hostname = InetAddress.getLocalHost().getHostName();

			final String uri = String.format("https://api.atlas-controller.oraclecloud.com/ords/atlas/admin/database?type=%s&hostname=%s", dbType, hostname);

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
					throw new TestException(CREATE_SCHEMA_REST_ENDPOINT_ISSUE,
							new IllegalStateException("HTTP/S status code: " + response.statusCode()));
				}
			}
		}
		catch (UnknownHostException e) {
			throw new TestException(UNKNOWN_HOSTNAME, e);
		}
		catch (URISyntaxException e) {
			throw new TestException(WRONG_MAIN_CONTROLLER_URI, e);
		}
		catch (IOException | InterruptedException e) {
			throw new TestException(WRONG_MAIN_CONTROLLER_REST_CALL, e);
		}
	}

	private String basicAuth(final String user, final String password) {
		return String.format("Basic %s", Base64.getEncoder().encodeToString((String.format("%s:%s", user, password)).getBytes()));
	}

	private void createSchemaWithORDS(final String jsonInformation) {
		try {
			Database database = new ObjectMapper().readValue(jsonInformation, Database.class);
			database = new ObjectMapper().readValue(database.getDatabase(), Database.class);

			final String uri = String.format("https://%s.oraclecloudapps.com/ords/admin/_/sql", database.getHost());

			final HttpRequest request = HttpRequest.newBuilder()
					.uri(new URI(uri))
					.headers("Accept", "application/json",
							"Content-Type", "application/sql",
							"Authorization", basicAuth("admin", database.getPassword()),
							"Pragma", "no-cache",
							"Cache-Control", "no-store")
					.POST(HttpRequest.BodyPublishers.ofString(String.format("""
							create user %s_%s identified by "%s" DEFAULT TABLESPACE DATA TEMPORARY TABLESPACE TEMP;
							alter user %s_%s quota unlimited on data;
							grant CREATE SESSION, RESOURCE, CREATE VIEW, CREATE SYNONYM, CREATE ANY INDEX, EXECUTE ANY TYPE to %s_%s;
							""", user, runID, password, user, runID, user, runID)))
					.build();

			try (HttpClient client = HttpClient
					.newBuilder()
					.version(HttpClient.Version.HTTP_1_1)
					.proxy(ProxySelector.getDefault())
					.followRedirects(HttpClient.Redirect.NORMAL)
					.build()) {

				final HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

				if (response.statusCode() == 200) {
					System.out.println("Done.");
				}
				else {
					throw new TestException(ATPS_REST_ENDPOINT_ISSUE,
							new IllegalStateException("HTTP/S status code: " + response.statusCode()));
				}
			}
		}
		catch (JsonProcessingException e) {
			throw new TestException(BAD_CREATE_SCHEMA_RESPONSE, e);
		}
		catch (URISyntaxException | IOException | InterruptedException e) {
			throw new TestException(WRONG_ATPS_REST_CALL, e);
		}
	}

	private void skipTesting() {
		if (owner == null || owner.isEmpty()) {
			throw new TestException(SKIP_TESTING_MISSING_OWNER);
		}
		if (repository == null || repository.isEmpty()) {
			throw new TestException(SKIP_TESTING_MISSING_REPOSITORY);
		}
		if (sha == null || sha.isEmpty()) {
			throw new TestException(SKIP_TESTING_MISSING_SHA);
		}
		if (prefixList == null || prefixList.isEmpty()) {
			throw new TestException(SKIP_TESTING_MISSING_PREFIX_LIST);
		}

		try {
			final String uri = String.format("https://api.github.com/repos/%s/%s/commits/%s", owner, repository, sha);

			final HttpRequest request = HttpRequest.newBuilder()
					.uri(new URI(uri))
					.headers("Accept", "application/vnd.github+json",
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
					final GitHubCommittedFiles files = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false).readValue(response.body(), GitHubCommittedFiles.class);

					// Test now against prefixes
					final String[] prefixes = prefixList.split(",");

					final int filesNumber = files.getFiles().length;
					int filesMatchingAnyPrefix = 0;

					for (GitHubFilename filename : files.getFiles()) {
						final String filenameToTest =  filename.getFilename();
						//System.out.println("Testing ["+filenameToTest+"] ...");
						for (String prefix : prefixes) {
							if (filenameToTest.startsWith(prefix)) {
								filesMatchingAnyPrefix++;
								break;
							}
						}
					}

					if(filesNumber == filesMatchingAnyPrefix) {
						System.out.println("Skipping tests: YES");
						System.exit(-1);
					} else {
						System.out.println("Skipping tests: NO");
						System.exit(0);
					}
				}
				else {
					throw new TestException(SKIP_TESTING_REST_ENDPOINT_ISSUE,
							new IllegalStateException("HTTP/S status code: " + response.statusCode()));
				}
			}
		}
		catch (URISyntaxException e) {
			throw new TestException(SKIP_TESTING_WRONG_URI, e);
		}
		catch (IOException | InterruptedException e) {
			throw new TestException(SKIP_TESTING_WRONG_REST_CALL, e);
		}
	}
}
