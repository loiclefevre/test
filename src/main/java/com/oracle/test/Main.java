package com.oracle.test;

import com.oracle.test.exception.TestException;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;
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

		disableSslVerification();
	}

	private static void disableSslVerification() {
		try {
			// Create a trust manager that does not validate certificate chains
			TrustManager[] trustAllCerts = new TrustManager[]{new X509TrustManager() {
				public java.security.cert.X509Certificate[] getAcceptedIssuers() {
					return null;
				}

				public void checkClientTrusted(X509Certificate[] certs, String authType) {
				}

				public void checkServerTrusted(X509Certificate[] certs, String authType) {
				}
			}
			};

			// Install the all-trusting trust manager
			SSLContext sc = SSLContext.getInstance("SSL");
			sc.init(null, trustAllCerts, new java.security.SecureRandom());
			HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());

			// Create all-trusting host name verifier
			HostnameVerifier allHostsValid = new HostnameVerifier() {
				public boolean verify(String hostname, SSLSession session) {
					return true;
				}
			};

			// Install the all-trusting host verifier
			HttpsURLConnection.setDefaultHostnameVerifier(allHostsValid);
		}
		catch (NoSuchAlgorithmException e) {
			e.printStackTrace();
		}
		catch (KeyManagementException e) {
			e.printStackTrace();
		}
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
