/*
 ** Oracle Test Pilot
 **
 ** Copyright (c) 2025 Oracle
 ** Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl/
 */
package com.oracle.testpilot;

import com.oracle.testpilot.exception.TestPilotException;
import com.oracle.testpilot.json.JSON;
import com.oracle.testpilot.model.Action;
import com.oracle.testpilot.model.Database;
import com.oracle.testpilot.model.GitHubCommittedFiles;
import com.oracle.testpilot.model.GitHubFilename;
import com.oracle.testpilot.model.TechnologyType;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.IOException;
import java.net.ProxySelector;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Base64;

import static com.oracle.testpilot.exception.TestPilotException.*;
import static com.oracle.testpilot.model.Action.*;

/**
 * Oracle Test Pilot session.
 *
 * @author LLEFEVRE
 * @since 0.0.1
 */
public class Session {

	public Action action;

	private final String runID;
	private String apiHOST;

	private String users;
	private String password;
	private String technologyType;

	private String prefixList;
	private String owner;
	private String repository;
	private String sha;

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

				case "--create":
					action = CREATE;
					break;

				case "--drop":
					action = DROP;
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

				case "--oci-service":
					if (i + 1 < args.length) {
						try {
							technologyType = args[++i];

							switch (technologyType) {
								case "autonomous-transaction-processing-serverless":
								case "base-database-service-19c":
								case "base-database-service-21c":
								case "base-database-service-23ai":
									break;

								default:
									throw new IllegalArgumentException(technologyType);
							}
						}
						catch (IllegalArgumentException iae) {
							throw new TestPilotException(WRONG_OCI_SERVICE_PARAMETER,
									new IllegalArgumentException("--oci-service must be either autonomous-transaction-processing-serverless, base-database-service-19c, base-database-service-21c, or base-database-service-23ai"));
						}
					}
					else {
						throw new TestPilotException(OCI_SERVICE_MISSING_PARAMETER, new IllegalArgumentException("Missing value for --oci-service parameter"));
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
				Usage: test <action> <options...>
								
				Action:
				--create: to provision the requested Oracle Cloud Infrastructure service to test
				    Options:
				    --oci-service <value>      OCI service type (autonomous-transaction-processing-serverless, base-database-service-19c, base-database-service-21c, base-database-service-23ai)
				    --user <user>              user name to be used (if several, then comma separated list without any space)
				    --password <password>      password to be used (if several users, they will have the same password)
				--drop: to de-provision the Oracle Cloud Infrastructure service
				    Options:
				    --oci-service <value>      OCI service type (autonomous-transaction-processing-serverless, base-database-service-19c, base-database-service-21c, base-database-service-23ai)
				    --user <user>              user name to be used (if several, then comma separated list without any space)
				--skip-testing
				    Options:
					--owner <owner>            GitHub project owner
					--repository <repository>  GitHub project repository
					--sha <sha>                GitHub commit sha to check
					--prefix-list <p1,p2,...>  comma separated list of prefixes that will NOT trigger tests (can be file and folders)
				""");
	}

	public void run() {
		if (action == null) return;

		switch (action) {
			case CREATE:
				//System.out.printf("%s%n", action.getBanner());
				createDatabase();
				break;

			case DROP:
				//System.out.printf("%s%n", action.getBanner());
				dropDatabase();
				break;

			case SKIP_TESTING:
				//System.out.printf("%s%n", action.getBanner());
				skipTesting();
				break;
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
			final String dbType = getInternalTechnologyType(technologyType);

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
					//.sslContext(sslContext)
					.version(HttpClient.Version.HTTP_1_1)
					.proxy(ProxySelector.getDefault())
					.followRedirects(HttpClient.Redirect.NORMAL)
					.build()) {

				final HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

				if (response.statusCode() == 200) {
					if (dbType.equals(TechnologyType.AUTONOMOUS)) {
						createDatabaseWithORDS(response.body());
					}
					else {
						createDatabaseWithSQL(response.body());
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

	private String getInternalTechnologyType(String technologyType) {
		return switch (technologyType) {
			case "autonomous-transaction-processing-serverless" -> TechnologyType.AUTONOMOUS;
			case "base-database-service-19c" -> TechnologyType.DB19C;
			case "base-database-service-21c" -> TechnologyType.DB21C;
			case "base-database-service-23ai" -> TechnologyType.DB23AI;
			default -> null;
		};
	}

	private void dropDatabase() {
		if (users == null || users.isEmpty()) {
			throw new TestPilotException(DROP_DATABASE_MISSING_USER_NAME);
		}
		if (technologyType == null) {
			throw new TestPilotException(DROP_DATABASE_MISSING_DB_TYPE);
		}

		try {
			final String dbType = getInternalTechnologyType(technologyType);

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
					//.sslContext(sslContext)
					.version(HttpClient.Version.HTTP_1_1)
					.proxy(ProxySelector.getDefault())
					.followRedirects(HttpClient.Redirect.NORMAL)
					.build()) {

				final HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

				if (response.statusCode() == 200) {
					if (dbType.equals(TechnologyType.AUTONOMOUS)) {
						dropDatabaseWithORDS(response.body());
					}
					else {
						dropDatabaseWithSQL(response.body());
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
	private void createDatabaseWithSQL(final String jsonInformation) {
		try {
			Database database = new JSON<>(Database.class).parse(jsonInformation);
			database = new JSON<>(Database.class).parse(database.getDatabase());

			final String dbType = getInternalTechnologyType(technologyType);

			final String connectionString = String.format("%s:1521/%s", database.getHost(), database.getService());

			System.out.printf("""
							database_host=%s
							database_service=%s
							database_version=%s
							connection_string_suffix="%s\"""",
					database.getHost(), database.getService(), database.getVersion(),
					connectionString);

			try (Connection c = DriverManager.getConnection("jdbc:oracle:thin:@" + connectionString, "pdbuser", database.getPassword())) {
				try (Statement s = c.createStatement()) {
					for (String user : users.split(",")) {
						if (dbType.equals(TechnologyType.DB23AI)) {
							s.execute(String.format("create user %s_%s identified by \"%s\" DEFAULT TABLESPACE USERS TEMPORARY TABLESPACE TEMP", user, runID, password));
							s.execute(String.format("alter user %s_%s quota unlimited on users", user, runID));
							s.execute(String.format("grant CREATE SESSION, CREATE TABLE, CREATE TYPE, CREATE TRIGGER, CREATE SEQUENCE, CREATE PROCEDURE, CREATE CLUSTER, CREATE VIEW, CREATE SYNONYM, CREATE ANY INDEX, EXECUTE ANY TYPE, CREATE DOMAIN to %s_%s", user, runID));
						}
						else {
							s.execute(String.format("create user %s_%s identified by \"%s\" DEFAULT TABLESPACE USERS TEMPORARY TABLESPACE TEMP", user, runID, password));
							s.execute(String.format("alter user %s_%s quota unlimited on users", user, runID));
							s.execute(String.format("grant CREATE SESSION, CREATE TABLE, CREATE TYPE, CREATE TRIGGER, CREATE SEQUENCE, CREATE PROCEDURE, CREATE CLUSTER, CREATE VIEW, CREATE SYNONYM, CREATE ANY INDEX, EXECUTE ANY TYPE to %s_%s", user, runID));
						}
					}

					System.out.println("create=ok");
				}
			}
		}
		catch (SQLException e) {
			throw new TestPilotException(SQL_ERROR, e);
		}
	}

	// For Base DB Systems
	private void dropDatabaseWithSQL(final String jsonInformation) {
		try {
			Database database = new JSON<>(Database.class).parse(jsonInformation);
			database = new JSON<>(Database.class).parse(database.getDatabase());

			final String connectionString = String.format("%s:1521/%s", database.getHost(), database.getService());

			try (Connection c = DriverManager.getConnection("jdbc:oracle:thin:@" + connectionString, "pdbuser", database.getPassword())) {
				try (Statement s = c.createStatement()) {
					for (String user : users.split(",")) {
						s.execute(String.format("drop user %s_%s cascade",
								user, runID));
					}

					System.out.println("drop=ok");
				}
			}
		}
		catch (SQLException e) {
			throw new TestPilotException(SQL_ERROR, e);
		}
	}

	private void createDatabaseWithORDS(final String jsonInformation) {
		try {
			Database database = new JSON<>(Database.class).parse(jsonInformation);
			database = new JSON<>(Database.class).parse(database.getDatabase());

			final String dbType = getInternalTechnologyType(technologyType);

			final String connectionString = dbType.equals(TechnologyType.AUTONOMOUS) ?
					String.format("(description=(retry_count=5)(retry_delay=1)(address=(protocol=tcps)(port=1521)(host=%s.oraclecloud.com))(connect_data=(USE_TCP_FAST_OPEN=ON)(service_name=%s_tp.adb.oraclecloud.com))(security=(ssl_server_dn_match=no)))", database.getHost(), database.getService())
					:
					String.format("%s:1521/%s", database.getHost(), database.getService());

			System.out.printf("""
							database_host=%s
							database_service=%s
							database_version=%s
							connection_string_suffix="%s\"""",
					database.getHost(), database.getService(), database.getVersion(),
					connectionString);

			final String uri = String.format("https://%s.oraclecloudapps.com/ords/admin/_/sql", database.getHost());

			final StringBuilder sql = new StringBuilder();

			for (String user : users.split(",")) {
				sql.append(String.format("""
						create user %s_%s identified by "%s" DEFAULT TABLESPACE DATA TEMPORARY TABLESPACE TEMP;
						alter user %s_%s quota unlimited on data;
						grant CREATE SESSION, CREATE TABLE, CREATE TYPE, CREATE TRIGGER, CREATE SEQUENCE, CREATE PROCEDURE, CREATE CLUSTER, CREATE VIEW, CREATE SYNONYM, CREATE ANY INDEX, EXECUTE ANY TYPE, CREATE DOMAIN to %s_%s;
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
					System.out.println("create=ok");
				}
				else {
					throw new TestPilotException(ATPS_REST_ENDPOINT_ISSUE,
							new IllegalStateException("HTTP/S status code: " + response.statusCode()));
				}
			}
		}
		catch (URISyntaxException | IOException | InterruptedException e) {
			throw new TestPilotException(WRONG_ATPS_REST_CALL, e);
		}
	}

	private void dropDatabaseWithORDS(final String jsonInformation) {
		try {
			Database database = new JSON<>(Database.class).parse(jsonInformation);
			database = new JSON<>(Database.class).parse(database.getDatabase());

			final String uri = String.format("https://%s.oraclecloudapps.com/ords/admin/_/sql", database.getHost());

			final StringBuilder sql = new StringBuilder();

			for (String user : users.split(",")) {
				sql.append(String.format("drop user %s_%s cascade;\n", user, runID));
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
					System.out.println("drop=ok");
				}
				else {
					throw new TestPilotException(ATPS_REST_ENDPOINT_ISSUE,
							new IllegalStateException("HTTP/S status code: " + response.statusCode()));
				}
			}
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
					final GitHubCommittedFiles files = new JSON<>(GitHubCommittedFiles.class).parse(response.body());

					// Test now against prefixes
					final String[] prefixes = prefixList.split(",");

					final int filesNumber = files.getFiles().length;
					int filesMatchingAnyPrefix = 0;

					for (GitHubFilename filename : files.getFiles()) {
						final String filenameToTest = filename.getFilename();
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
