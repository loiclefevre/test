/*
 ** Oracle Test Pilot
 **
 ** Copyright (c) 2025 Oracle
 ** Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl/
 */
package com.oracle.testpilot;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.oracle.testpilot.exception.TestPilotException;
import com.oracle.testpilot.model.Action;
import com.oracle.testpilot.model.Database;
import com.oracle.testpilot.model.GitHubCommittedFiles;
import com.oracle.testpilot.model.GitHubFilename;
import com.oracle.testpilot.model.TechnologyType;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.ProxySelector;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

import static com.oracle.testpilot.Main.VERSION;
import static com.oracle.testpilot.exception.TestPilotException.*;
import static com.oracle.testpilot.model.Action.*;

/**
 * Oracle Test Pilot session.
 *
 * @author LLEFEVRE
 * @since 0.0.1
 */
public class Session {

	private boolean showVersion;

	public Action action;

	private final String runID;
	private String apiHOST;

	private String users;
	private String password;
	private TechnologyType technologyType;

	private String prefixList;
	private String owner;
	private String repository;
	private String sha;

	private static final String SQLCL_PATH = "/home/ubuntu/sqlcl/bin/sql";

	public Session(final String[] args) {
		runID = System.getenv("RUNID");
		apiHOST = System.getenv("API_HOST");
		if (apiHOST == null) {
			apiHOST = "api.testpilot-controller.oraclecloud.com";
		}
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

				case "--create-database":
					action = CREATE_DATABASE;
					break;

				case "--drop-database":
					action = DROP_DATABASE;
					break;

				case "--get-database-info":
					action = GET_DATABASE_INFO;
					break;

				case "--user":
					if (i + 1 < args.length) {
						users = args[++i];
					}
					else {
						throw new TestPilotException(USER_MISSING_PARAMETER, new IllegalArgumentException("Missing value for --user parameter"));
					}
					break;

				case "--password":
					if (i + 1 < args.length) {
						password = args[++i];
					}
					else {
						throw new TestPilotException(PASSWORD_MISSING_PARAMETER, new IllegalArgumentException("Missing value for --password parameter"));
					}
					break;

				case "--type":
					if (i + 1 < args.length) {
						try {
							technologyType = TechnologyType.valueOf(args[++i]);
						}
						catch (IllegalArgumentException iae) {
							throw new TestPilotException(WRONG_DATABASE_TYPE_PARAMETER,
									new IllegalArgumentException("--type must be either atps, db19c, db21c, or db23ai"));
						}
					}
					else {
						throw new TestPilotException(DBTYPE_MISSING_PARAMETER, new IllegalArgumentException("Missing value for --type parameter"));
					}
					break;

				case "--skip-testing":
					action = SKIP_TESTING;
					break;

				case "--prefix-list":
					if (i + 1 < args.length) {
						prefixList = args[++i];
					}
					else {
						throw new TestPilotException(PREFIX_LIST_MISSING_PARAMETER, new IllegalArgumentException("Missing value for --prefix-list parameter"));
					}
					break;

				case "--owner":
					if (i + 1 < args.length) {
						owner = args[++i];
					}
					else {
						throw new TestPilotException(OWNER_MISSING_PARAMETER, new IllegalArgumentException("Missing value for --owner parameter"));
					}
					break;

				case "--repository":
					if (i + 1 < args.length) {
						repository = args[++i];
					}
					else {
						throw new TestPilotException(REPOSITORY_MISSING_PARAMETER, new IllegalArgumentException("Missing value for --repository parameter"));
					}
					break;

				case "--sha":
					if (i + 1 < args.length) {
						sha = args[++i];
					}
					else {
						throw new TestPilotException(SHA_MISSING_PARAMETER, new IllegalArgumentException("Missing value for --sha parameter"));
					}
					break;

				default:
					displayUsage();
					//System.out.println("Wrong arg: "+arg);
					throw new TestPilotException(UNKNOWN_COMMAND_LINE_ARGUMENT);
			}
		}
	}

	private void displayUsage() {
		System.out.println("""
				Usage: test <service> <options...>
								
				Services:
				--get-database-info: get database information (host and service)
				    Options:
				    --type <type>              database type (atps, db19c, db21c, db23ai)
				--create-database: creates a database for running the tests
				    Options:
				    --user <user>              user name to be used
				    --password <password>      password to be used
				    --type <type>              database type (atps, db19c, db21c, db23ai)
				--drop-database: drops the database used for running the tests
				    Options:
				    --user <user>              user name to be used
				    --type <type>              database type (atps, db19c, db21c, db23ai)
				--skip-testing
				    Options:
					--owner <owner>            GitHub project owner
					--repository <repository>  GitHub project repository
					--sha <sha>                GitHub commit sha to check
					--prefix-list <p1,p2,...>  Comma-separated list of prefixes that will NOT trigger tests
				""");
	}

	public void banner() {
		switch (action) {
			case GET_DATABASE_INFO:
				return;
			default:
				if (showVersion) {
					System.out.printf("test_version=%s%n", VERSION);
				}
				break;
		}
	}

	public void run() {
		if (action == null) return;

		switch (action) {
			case GET_DATABASE_INFO:
				getDatabaseInfo();
				break;

			case CREATE_DATABASE:
				//System.out.printf("%s%n", action.getBanner());
				createDatabase();
				break;

			case DROP_DATABASE:
				//System.out.printf("%s%n", action.getBanner());
				dropDatabase();
				break;

			case SKIP_TESTING:
				//System.out.printf("%s%n", action.getBanner());
				skipTesting();
				break;
		}
	}

	private void getDatabaseInfo() {
		if (technologyType == null) {
			throw new TestPilotException(GET_DB_INFO_MISSING_DB_TYPE);
		}

		try {
			final String dbType = switch (technologyType) {
				case TechnologyType.atps -> "autonomous";
				case TechnologyType.db19c -> "db19c";
				case TechnologyType.db21c -> "db21c";
				case TechnologyType.db23ai -> "db23ai";
			};

			final String uri = String.format("https://%s/ords/testpilot/admin/database?type=%s", apiHOST, dbType);

			final HttpRequest request = HttpRequest.newBuilder()
					.uri(new URI(uri))
					.headers("Accept", "application/json",
							"Pragma", "no-cache",
							"Cache-Control", "no-store")
					.GET()
					.build();

			final SSLContext sslContext = createCustomSSLContext();

			try (HttpClient client = HttpClient
					.newBuilder()
					.sslContext(sslContext)
					.version(HttpClient.Version.HTTP_1_1)
					.proxy(ProxySelector.getDefault())
					.followRedirects(HttpClient.Redirect.NORMAL)
					.build()) {

				final HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

				if (response.statusCode() == 200) {
					Database database = new ObjectMapper().readValue(response.body(), Database.class);
					database = new ObjectMapper().readValue(database.getDatabase(), Database.class);

					final String connectionString = technologyType == TechnologyType.atps ?
							String.format("(description=(retry_count=5)(retry_delay=1)(address=(protocol=tcps)(port=1521)(host=%s.oraclecloud.com))(connect_data=(USE_TCP_FAST_OPEN=ON)(service_name=%s_tp.adb.oraclecloud.com))(security=(ssl_server_dn_match=no)))", database.getHost(), database.getService())
							:
							String.format("%s:1521/%s", database.getHost(), database.getService());

					System.out.printf("""
									host=%s
									service=%s
									version=%s
									connection_string="%s\"""",
							database.getHost(), database.getService(), database.getVersion(),
							connectionString);
				}
				else {
					throw new TestPilotException(GET_DB_INFO_REST_ENDPOINT_ISSUE,
							new IllegalStateException("HTTP/S status code: " + response.statusCode()));
				}
			}
		}
		catch (URISyntaxException e) {
			throw new TestPilotException(WRONG_MAIN_CONTROLLER_URI, e);
		}
		catch (IOException | InterruptedException e) {
			throw new TestPilotException(WRONG_MAIN_CONTROLLER_REST_CALL, e);
		}
	}

	private static SSLContext createCustomSSLContext() {
		final TrustManager[] trustAllCerts = new TrustManager[]{
				new X509TrustManager() {
					public java.security.cert.X509Certificate[] getAcceptedIssuers() {
						return new java.security.cert.X509Certificate[0];
					}

					public void checkClientTrusted(java.security.cert.X509Certificate[] certs, String authType) {
					}

					public void checkServerTrusted(java.security.cert.X509Certificate[] certs, String authType) {
					}
				}
		};

		try {
			final SSLContext sslContext = SSLContext.getInstance("TLS");
			sslContext.init(null, trustAllCerts, new java.security.SecureRandom());
			return sslContext;
		}
		catch (NoSuchAlgorithmException | KeyManagementException e) {
			throw new TestPilotException(WRONG_MAIN_CONTROLLER_REST_CALL, e);
		}
	}

	private void createDatabase() {
		if (users == null || users.isEmpty()) {
			throw new TestPilotException(CREATE_DATABASE_MISSING_USER_NAME);
		}
		if (password == null || password.isEmpty()) {
			throw new TestPilotException(CREATE_DATABASE_MISSING_PASSWORD);
		}
		if (technologyType == null) {
			throw new TestPilotException(CREATE_DATABASE_MISSING_DB_TYPE);
		}

		try {
			final String dbType = switch (technologyType) {
				case TechnologyType.atps -> "autonomous";
				case TechnologyType.db19c -> "db19c";
				case TechnologyType.db21c -> "db21c";
				case TechnologyType.db23ai -> "db23ai";
			};

			final String uri = String.format("https://%s/ords/testpilot/admin/database?type=%s", apiHOST, dbType);

			final HttpRequest request = HttpRequest.newBuilder()
					.uri(new URI(uri))
					.headers("Accept", "application/json",
							"Pragma", "no-cache",
							"Cache-Control", "no-store")
					.GET()
					.build();

			final SSLContext sslContext = createCustomSSLContext();

			try (HttpClient client = HttpClient
					.newBuilder()
					.sslContext(sslContext)
					.version(HttpClient.Version.HTTP_1_1)
					.proxy(ProxySelector.getDefault())
					.followRedirects(HttpClient.Redirect.NORMAL)
					.build()) {

				final HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

				if (response.statusCode() == 200) {
					if (technologyType == TechnologyType.atps) {
						createDatabaseWithORDS(response.body());
					}
					else {
						createDatabaseWithSQLcl(response.body());
					}
				}
				else {
					throw new TestPilotException(CREATE_DATABASE_REST_ENDPOINT_ISSUE,
							new IllegalStateException("HTTP/S status code: " + response.statusCode()));
				}
			}
		}
		catch (URISyntaxException e) {
			throw new TestPilotException(WRONG_MAIN_CONTROLLER_URI, e);
		}
		catch (IOException | InterruptedException e) {
			throw new TestPilotException(WRONG_MAIN_CONTROLLER_REST_CALL, e);
		}
	}

	private void dropDatabase() {
		if (users == null || users.isEmpty()) {
			throw new TestPilotException(DROP_DATABASE_MISSING_USER_NAME);
		}
		if (technologyType == null) {
			throw new TestPilotException(DROP_DATABASE_MISSING_DB_TYPE);
		}

		try {
			final String dbType = switch (technologyType) {
				case TechnologyType.atps -> "autonomous";
				case TechnologyType.db19c -> "db19c";
				case TechnologyType.db21c -> "db21c";
				case TechnologyType.db23ai -> "db23ai";
			};

			final String uri = String.format("https://%s/ords/testpilot/admin/database?type=%s", apiHOST, dbType);

			final HttpRequest request = HttpRequest.newBuilder()
					.uri(new URI(uri))
					.headers("Accept", "application/json",
							"Pragma", "no-cache",
							"Cache-Control", "no-store")
					.GET()
					.build();

			final SSLContext sslContext = createCustomSSLContext();

			try (HttpClient client = HttpClient
					.newBuilder()
					.sslContext(sslContext)
					.version(HttpClient.Version.HTTP_1_1)
					.proxy(ProxySelector.getDefault())
					.followRedirects(HttpClient.Redirect.NORMAL)
					.build()) {

				final HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

				if (response.statusCode() == 200) {
					if (technologyType == TechnologyType.atps) {
						dropDatabaseWithORDS(response.body());
					}
					else {
						dropDatabaseWithSQLcl(response.body());
					}
				}
				else {
					throw new TestPilotException(DROP_DATABASE_REST_ENDPOINT_ISSUE,
							new IllegalStateException("HTTP/S status code: " + response.statusCode()));
				}
			}
		}
		catch (URISyntaxException e) {
			throw new TestPilotException(WRONG_MAIN_CONTROLLER_URI, e);
		}
		catch (IOException | InterruptedException e) {
			throw new TestPilotException(WRONG_MAIN_CONTROLLER_REST_CALL, e);
		}
	}

	private String basicAuth(final String user, final String password) {
		return String.format("Basic %s", Base64.getEncoder().encodeToString((String.format("%s:%s", user, password)).getBytes()));
	}

	// For Base DB Systems
	private void createDatabaseWithSQLcl(final String jsonInformation) {
		try {
			Database database = new ObjectMapper().readValue(jsonInformation, Database.class);
			database = new ObjectMapper().readValue(database.getDatabase(), Database.class);

			final String connectionString = technologyType == TechnologyType.atps ?
					String.format("(description=(retry_count=5)(retry_delay=1)(address=(protocol=tcps)(port=1521)(host=%s.oraclecloud.com))(connect_data=(USE_TCP_FAST_OPEN=ON)(service_name=%s_tp.adb.oraclecloud.com))(security=(ssl_server_dn_match=no)))", database.getHost(), database.getService())
					:
					String.format("%s:1521/%s", database.getHost(), database.getService());

			System.out.printf("""
							host=%s
							service=%s
							version=%s
							connection_string="%s\"""",
					database.getHost(), database.getService(), database.getVersion(),
					connectionString);

			// Create temporary SQL script
			final File tempSQLScript = File.createTempFile("test", ".sql");

			try (PrintWriter p = new PrintWriter(tempSQLScript)) {
				for (String user : users.split(",")) {
					if (technologyType == TechnologyType.db23ai) {
						p.println(String.format("""
										create user %s_%s identified by "%s" DEFAULT TABLESPACE USERS TEMPORARY TABLESPACE TEMP;
										alter user %s_%s quota unlimited on users;
										grant CREATE SESSION, RESOURCE, CREATE VIEW, CREATE SYNONYM, CREATE ANY INDEX, EXECUTE ANY TYPE, CREATE DOMAIN to %s_%s;
										""",
								user, runID, password, user, runID, user, runID));
					}
					else {
						p.println(String.format("""
										create user %s_%s identified by "%s" DEFAULT TABLESPACE USERS TEMPORARY TABLESPACE TEMP;
										alter user %s_%s quota unlimited on users;
										grant CREATE SESSION, RESOURCE, CREATE VIEW, CREATE SYNONYM, CREATE ANY INDEX, EXECUTE ANY TYPE to %s_%s;
										""",
								user, runID, password, user, runID, user, runID));
					}
				}

				p.println("\nexit;");
			}

			final ProcessBuilder pb = new ProcessBuilder(SQLCL_PATH, "-s",
					String.format("system/%s@%s:1521/%s",
							database.getPassword(),
							database.getHost(),
							database.getService()),
					"@" + tempSQLScript.getCanonicalPath())
					.inheritIO();

			pb.environment().put("JAVA_HOME", "/home/ubuntu/graalvm-jdk-24.0.1+9.1");

			final Process p = pb.start();

			final int returnCode = p.waitFor();

			if (returnCode != 0) {
				throw new TestPilotException(SQLCL_ERROR, new RuntimeException("SQLcl exited with error code " + returnCode));
			}
			else {
				System.out.println("create_database=ok");
			}
		}
		catch (JsonProcessingException e) {
			throw new TestPilotException(BAD_CREATE_DATABASE_RESPONSE, e);
		}
		catch (IOException e) {
			throw new TestPilotException(WRONG_SQLCL_USAGE, e);
		}
		catch (InterruptedException e) {
			throw new TestPilotException(SQLCL_INTERRUPTED);
		}
	}

	// For Base DB Systems
	private void dropDatabaseWithSQLcl(final String jsonInformation) {
		try {
			Database database = new ObjectMapper().readValue(jsonInformation, Database.class);
			database = new ObjectMapper().readValue(database.getDatabase(), Database.class);

			// Create temporary SQL script
			final File tempSQLScript = File.createTempFile("test", ".sql");

			try (PrintWriter p = new PrintWriter(tempSQLScript)) {
				for (String user : users.split(",")) {
					p.println(String.format("drop user %s_%s cascade;",
							user, runID));
				}
				p.print("\nexit;");
			}

			final ProcessBuilder pb = new ProcessBuilder(SQLCL_PATH, "-s",
					String.format("system/%s@%s:1521/%s",
							database.getPassword(),
							database.getHost(),
							database.getService()),
					"@" + tempSQLScript.getCanonicalPath())
					.inheritIO();

			pb.environment().put("JAVA_HOME", "/home/ubuntu/graalvm-jdk-24.0.1+9.1");

			final Process p = pb.start();

			final int returnCode = p.waitFor();

			if (returnCode != 0) {
				throw new TestPilotException(SQLCL_ERROR, new RuntimeException("SQLcl exited with error code " + returnCode));
			}
			else {
				System.out.println("drop_database=ok");
			}
		}
		catch (JsonProcessingException e) {
			throw new TestPilotException(BAD_DROP_DATABASE_RESPONSE, e);
		}
		catch (IOException e) {
			throw new TestPilotException(WRONG_SQLCL_USAGE, e);
		}
		catch (InterruptedException e) {
			throw new TestPilotException(SQLCL_INTERRUPTED);
		}
	}

	private void createDatabaseWithORDS(final String jsonInformation) {
		try {
			Database database = new ObjectMapper().readValue(jsonInformation, Database.class);
			database = new ObjectMapper().readValue(database.getDatabase(), Database.class);

			final String connectionString = technologyType == TechnologyType.atps ?
					String.format("(description=(retry_count=5)(retry_delay=1)(address=(protocol=tcps)(port=1521)(host=%s.oraclecloud.com))(connect_data=(USE_TCP_FAST_OPEN=ON)(service_name=%s_tp.adb.oraclecloud.com))(security=(ssl_server_dn_match=no)))", database.getHost(), database.getService())
					:
					String.format("%s:1521/%s", database.getHost(), database.getService());

			System.out.printf("""
							host=%s
							service=%s
							version=%s
							connection_string="%s\"""",
					database.getHost(), database.getService(), database.getVersion(),
					connectionString);

			final String uri = String.format("https://%s.oraclecloudapps.com/ords/admin/_/sql", database.getHost());

			final StringBuilder sql = new StringBuilder();

			for (String user : users.split(",")) {
				sql.append(String.format("""
						create user %s_%s identified by "%s" DEFAULT TABLESPACE DATA TEMPORARY TABLESPACE TEMP;
						alter user %s_%s quota unlimited on data;
						grant CREATE SESSION, RESOURCE, CREATE VIEW, CREATE SYNONYM, CREATE ANY INDEX, EXECUTE ANY TYPE, CREATE DOMAIN to %s_%s;
						\n""", user, runID, password, user, runID, user, runID));
			}

			final HttpRequest request = HttpRequest.newBuilder()
					.uri(new URI(uri))
					.headers("Accept", "application/json",
							"Content-Type", "application/sql",
							"Authorization", basicAuth("admin", database.getPassword()),
							"Pragma", "no-cache",
							"Cache-Control", "no-store")
					// WE EXPECT ATP-S 23ai
					.POST(HttpRequest.BodyPublishers.ofString(sql.toString()))
					.build();

			try (HttpClient client = HttpClient
					.newBuilder()
					.version(HttpClient.Version.HTTP_1_1)
					.proxy(ProxySelector.getDefault())
					.followRedirects(HttpClient.Redirect.NORMAL)
					.build()) {

				final HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

				if (response.statusCode() == 200) {
					System.out.println("create_database=ok");
				}
				else {
					throw new TestPilotException(ATPS_REST_ENDPOINT_ISSUE,
							new IllegalStateException("HTTP/S status code: " + response.statusCode()));
				}
			}
		}
		catch (JsonProcessingException e) {
			throw new TestPilotException(BAD_CREATE_DATABASE_RESPONSE, e);
		}
		catch (URISyntaxException | IOException | InterruptedException e) {
			throw new TestPilotException(WRONG_ATPS_REST_CALL, e);
		}
	}

	private void dropDatabaseWithORDS(final String jsonInformation) {
		try {
			Database database = new ObjectMapper().readValue(jsonInformation, Database.class);
			database = new ObjectMapper().readValue(database.getDatabase(), Database.class);

			final String uri = String.format("https://%s.oraclecloudapps.com/ords/admin/_/sql", database.getHost());

			final StringBuilder sql = new StringBuilder();

			for (String user : users.split(",")) {
				sql.append(String.format("drop user %s_%s cascade;%n", user, runID));
			}

			final HttpRequest request = HttpRequest.newBuilder()
					.uri(new URI(uri))
					.headers("Accept", "application/json",
							"Content-Type", "application/sql",
							"Authorization", basicAuth("admin", database.getPassword()),
							"Pragma", "no-cache",
							"Cache-Control", "no-store")
					// WE EXPECT ATP-S 23ai
					.POST(HttpRequest.BodyPublishers.ofString(sql.toString()))
					.build();

			try (HttpClient client = HttpClient
					.newBuilder()
					.version(HttpClient.Version.HTTP_1_1)
					.proxy(ProxySelector.getDefault())
					.followRedirects(HttpClient.Redirect.NORMAL)
					.build()) {

				final HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

				if (response.statusCode() == 200) {
					System.out.println("drop_database=ok");
				}
				else {
					throw new TestPilotException(ATPS_REST_ENDPOINT_ISSUE,
							new IllegalStateException("HTTP/S status code: " + response.statusCode()));
				}
			}
		}
		catch (JsonProcessingException e) {
			throw new TestPilotException(BAD_DROP_DATABASE_RESPONSE, e);
		}
		catch (URISyntaxException | IOException | InterruptedException e) {
			throw new TestPilotException(WRONG_ATPS_REST_CALL, e);
		}
	}

	private void skipTesting() {
		if (owner == null || owner.isEmpty()) {
			throw new TestPilotException(SKIP_TESTING_MISSING_OWNER);
		}
		if (repository == null || repository.isEmpty()) {
			throw new TestPilotException(SKIP_TESTING_MISSING_REPOSITORY);
		}
		if (sha == null || sha.isEmpty()) {
			throw new TestPilotException(SKIP_TESTING_MISSING_SHA);
		}
		if (prefixList == null || prefixList.isEmpty()) {
			throw new TestPilotException(SKIP_TESTING_MISSING_PREFIX_LIST);
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
						final String filenameToTest = filename.getFilename();
						//System.out.println("Testing ["+filenameToTest+"] ...");
						for (String prefix : prefixes) {
							if (filenameToTest.startsWith(prefix)) {
								filesMatchingAnyPrefix++;
								break;
							}
						}
					}

					if (filesNumber == filesMatchingAnyPrefix) {
						System.out.println("skip_tests=yes");
						System.exit(0);
					}
					else {
						System.out.println("skip_tests=no");
						System.exit(0);
					}
				}
				else {
					throw new TestPilotException(SKIP_TESTING_REST_ENDPOINT_ISSUE,
							new IllegalStateException("HTTP/S status code: " + response.statusCode()));
				}
			}
		}
		catch (URISyntaxException e) {
			throw new TestPilotException(SKIP_TESTING_WRONG_URI, e);
		}
		catch (IOException | InterruptedException e) {
			throw new TestPilotException(SKIP_TESTING_WRONG_REST_CALL, e);
		}
	}
}
