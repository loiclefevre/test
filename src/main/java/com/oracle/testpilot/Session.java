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
import com.oracle.testpilot.model.OAuthToken;
import com.oracle.testpilot.model.TechnologyType;

import java.io.BufferedOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.ProxySelector;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Base64;

import static com.oracle.testpilot.exception.TestPilotException.*;
import static com.oracle.testpilot.model.Action.*;

/**
 * Oracle Test Pilot session.
 *
 * @author LLEFEVRE
 * @since 1.0.0
 */
public class Session {

	private static final int ONE_MINUTE_TIMEOUT = 60; // seconds

	public Action action;

	private final String githubOutput;

	private final String runID;
	private final String apiHOST;
	private String token;
	private String clientId;

	private String users;
	private String technologyType;

	private String prefixList;
	private String owner;
	private String repository;
	private String sha;

	public Session(final String[] args) {
		githubOutput = System.getenv("GITHUB_OUTPUT");
		runID = System.getenv("RUNID");
		apiHOST = System.getenv("API_HOST");
		token = System.getenv("TESTPILOT_TOKEN");
		clientId = System.getenv("TESTPILOT_CLIENT_ID");
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

				case "--delete":
					action = DELETE;
					break;

				case "--user":
					if (i + 1 < args.length) {
						users = args[++i];
					}
					else {
						throw new TestPilotException(USER_MISSING_PARAMETER, new IllegalArgumentException("Missing value for --user parameter"));
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
				--delete: to de-provision the Oracle Cloud Infrastructure service
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
				create();
				break;

			case DELETE:
				delete();
				break;

			case SKIP_TESTING:
				skipTesting();
				break;
		}
	}

	private void create() {
		if (users == null || users.isEmpty()) {
			throw new TestPilotException(CREATE_DATABASE_MISSING_USER_NAME);
		}
		if(badChars(users)) {
			throw new TestPilotException(CREATE_DATABASE_WRONG_USER_NAME);
		}
		if (technologyType == null) {
			throw new TestPilotException(CREATE_DATABASE_MISSING_DB_TYPE);
		}

		try {
			final String type = getInternalTechnologyType(technologyType);

			setOAuth2Token();

			final String uri = String.format("https://%s/ords/testpilot/resources/create", apiHOST);

			final HttpRequest request = HttpRequest.newBuilder()
					.uri(new URI(uri))
					.headers("Accept", "application/json",
							"Content-Type", "application/json",
							"Pragma", "no-cache",
							"Cache-Control", "no-store",
							"User-Agent", "setup-testpilot/" + Main.VERSION,
							"Authorization", "Bearer " + token)
					.POST(HttpRequest.BodyPublishers.ofString(
							String.format("""
									{
									     "runID": "%s",
									     "type": "%s",
									     "user": [%s]
									}""", runID, type, buildUserList(users))
					))
					.build();

			boolean done = false;

			do {
				try (HttpClient client = HttpClient
						.newBuilder()
						.connectTimeout(Duration.ofSeconds(ONE_MINUTE_TIMEOUT))
						.version(HttpClient.Version.HTTP_1_1)
						.proxy(ProxySelector.getDefault())
						.followRedirects(HttpClient.Redirect.NORMAL)
						.build()) {

					final HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

					if (response.statusCode() == 200 || response.statusCode() == 201) {
						done = true;

						// retrieve JSON response
						final String jsonInformation = response.body();

						switch (type) {
							case TechnologyType.AUTONOMOUS: {
								Database database = new JSON<>(Database.class).parse(jsonInformation);
								database = new JSON<>(Database.class).parse(database.getDatabase());

								final String connectionString = String.format("(description=(retry_count=5)(retry_delay=1)(address=(protocol=tcps)(port=1521)(host=%s.oraclecloud.com))(connect_data=(USE_TCP_FAST_OPEN=ON)(service_name=%s_tp.adb.oraclecloud.com))(security=(ssl_server_dn_match=no)))", database.getHost(), database.getService());

								if (githubOutput != null) {
									try (PrintWriter out = new PrintWriter(new BufferedOutputStream(new FileOutputStream(githubOutput, true)))) {
										System.out.printf("::add-mask::%s%n", database.getPassword());
										out.printf("""
														database_host=%s
														database_service=%s
														database_password="%s"
														database_version=%s
														connection_string_suffix="%s"%n""",
												database.getHost(), database.getService(), database.getPassword(), database.getVersion(),
												connectionString);
									}
								}
							}
							break;
							case TechnologyType.DB19C:
							case TechnologyType.DB21C:
							case TechnologyType.DB23AI: {
								Database database = new JSON<>(Database.class).parse(jsonInformation);
								database = new JSON<>(Database.class).parse(database.getDatabase());

								final String connectionString = String.format("%s:1521/%s", database.getHost(), database.getService());

								if (githubOutput != null) {
									try (PrintWriter out = new PrintWriter(new BufferedOutputStream(new FileOutputStream(githubOutput, true)))) {
										System.out.printf("::add-mask::%s%n", database.getPassword());
										out.printf("""
														database_host=%s
														database_service=%s
														database_password="%s"
														database_version=%s
														connection_string_suffix="%s"%n""",
												database.getHost(), database.getService(), database.getPassword(), database.getVersion(),
												connectionString);
									}
								}
							}
							break;
						}

						if (githubOutput != null) {
							try (PrintWriter out = new PrintWriter(new BufferedOutputStream(new FileOutputStream(githubOutput, true)))) {
								out.println("create=ok");
							}
						}
					}
					else if(response.statusCode() == 429) {
						// too many requests (rate limiting)
						Thread.sleep(10 * 1000L);
					}
					else {
						throw new TestPilotException(CREATE_DATABASE_REST_ENDPOINT_ISSUE,
								new IllegalStateException("HTTP/S status code: " + response.statusCode(),
										new IllegalStateException(response.body())));
					}
				}
			} while(!done);
		}
		catch (URISyntaxException e) {
			throw new TestPilotException(WRONG_MAIN_CONTROLLER_URI, e);
		}
		catch (IOException | InterruptedException e) {
			throw new TestPilotException(WRONG_MAIN_CONTROLLER_REST_CALL, e);
		}
	}

	private void delete() {
		if (users == null || users.isEmpty()) {
			throw new TestPilotException(DROP_DATABASE_MISSING_USER_NAME);
		}
		if(badChars(users)) {
			throw new TestPilotException(DROP_DATABASE_WRONG_USER_NAME);
		}
		if (technologyType == null) {
			throw new TestPilotException(DROP_DATABASE_MISSING_DB_TYPE);
		}

		try {
			final String type = getInternalTechnologyType(technologyType);

			setOAuth2Token();

			final String uri = String.format("https://%s/ords/testpilot/resources/delete", apiHOST);

			final HttpRequest request = HttpRequest.newBuilder()
					.uri(new URI(uri))
					.headers("Accept", "application/json",
							"Content-Type", "application/json",
							"Pragma", "no-cache",
							"Cache-Control", "no-store",
							"User-Agent", "setup-testpilot/" + Main.VERSION,
							"Authorization", "Bearer " + token)
					.POST(HttpRequest.BodyPublishers.ofString(
							String.format("""
									{
									     "runID": "%s",
									     "type": "%s",
									     "user": [%s]
									}""", runID, type, buildUserList(users))
					))
					.build();

			boolean done = false;

			do {
				try (HttpClient client = HttpClient
						.newBuilder()
						.connectTimeout(Duration.ofSeconds(ONE_MINUTE_TIMEOUT))
						.version(HttpClient.Version.HTTP_1_1)
						.proxy(ProxySelector.getDefault())
						.followRedirects(HttpClient.Redirect.NORMAL)
						.build()) {

					final HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

					if (response.statusCode() == 200 || response.statusCode() == 204) {
						done = true;
						if (githubOutput != null) {
							try (PrintWriter out = new PrintWriter(new BufferedOutputStream(new FileOutputStream(githubOutput, true)))) {
								out.println("delete=ok");
							}
						}
					}
					else if(response.statusCode() == 429) {
						// too many requests (rate limiting)
						Thread.sleep(10 * 1000L);
					}
					else {
						throw new TestPilotException(DROP_DATABASE_REST_ENDPOINT_ISSUE,
								new IllegalStateException("HTTP/S status code: " + response.statusCode()));
					}
				}
			} while(!done);
		}
		catch (URISyntaxException e) {
			throw new TestPilotException(WRONG_MAIN_CONTROLLER_URI, e);
		}
		catch (IOException | InterruptedException e) {
			throw new TestPilotException(WRONG_MAIN_CONTROLLER_REST_CALL, e);
		}
	}

	private boolean badChars(final String users) {
		for(int i = 0; i < users.length(); i++) {
			final char c = users.charAt(i);
			if( !((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || c == '_' || c == ',' || (c >= '0' && c <= '9')) ) return true;
		}
		return false;
	}

	private void setOAuth2Token() throws URISyntaxException, IOException, InterruptedException {
		final String uri = String.format("https://%s/ords/testpilot/oauth/token", apiHOST);

		final HttpRequest request = HttpRequest.newBuilder()
				.uri(new URI(uri))
				.headers("Accept", "application/json",
						"Content-Type", "application/x-www-form-urlencoded",
						"Pragma", "no-cache",
						"Cache-Control", "no-store",
						"User-Agent", "setup-testpilot/" + Main.VERSION,
						"Authorization", basicAuth())
				.POST(HttpRequest.BodyPublishers.ofString("grant_type=client_credentials"))
				.build();

		try (HttpClient client = HttpClient
				.newBuilder()
				.connectTimeout(Duration.ofSeconds(ONE_MINUTE_TIMEOUT))
				.version(HttpClient.Version.HTTP_1_1)
				.proxy(ProxySelector.getDefault())
				.followRedirects(HttpClient.Redirect.NORMAL)
				.build()) {

			final HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

			if (response.statusCode() == 200) {
				OAuthToken oauthToken = new JSON<>(OAuthToken.class).parse(response.body());
				token = oauthToken.getAccess_token();
			}
			else {
				throw new TestPilotException(RETRIEVE_OAUTH2_TOKEN,
						new IllegalStateException("HTTP/S status code: " + response.statusCode()));
			}
		}
	}

	private String buildUserList(final String users) {
		final StringBuilder sb = new StringBuilder();
		int i = 0;
		for (String user : users.split(",")) {
			if (i > 0) {
				sb.append(',');
			}
			sb.append("\"").append(user).append("\"");
			i++;
		}

		return sb.toString();
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

	private String basicAuth() {
		return String.format("Basic %s", Base64.getEncoder().encodeToString((String.format("%s:%s", clientId, token)).getBytes()));
	}

	/**
	 * Analyze the list of files present inside the commit and compare it
	 * with a list of files and folder prefixes that must not trigger any
	 * build and test (example: documentation). In that case, the response
	 * is clear: no need to build.
	 */
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
							"Cache-Control", "no-store",
							"User-Agent", "setup-testpilot/" + Main.VERSION)
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
						if(githubOutput != null) {
							try (PrintWriter out = new PrintWriter(new BufferedOutputStream(new FileOutputStream(githubOutput, true)))) {
								out.println("skip_tests=yes");
							}
						}
						System.exit(0);
					}
					else {
						if(githubOutput != null) {
							try (PrintWriter out = new PrintWriter(new BufferedOutputStream(new FileOutputStream(githubOutput, true)))) {
								out.println("skip_tests=no");
							}
						}
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
