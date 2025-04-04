/*
 ** Oracle Test Pilot
 **
 ** Copyright (c) 2025 Oracle
 ** Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl/
 */
package com.oracle.testpilot;

import com.oracle.testpilot.exception.TestPilotException;

import java.util.Locale;

/**
 * Oracle Test Pilot services main entry point.
 *
 * @author LLEFEVRE
 * @since 0.0.1
 */
public class Main {

	static {
		Locale.setDefault(Locale.US);
		System.setProperty("java.net.useSystemProxies", "true");
		System.setProperty("jdk.internal.httpclient.disableHostnameVerification", "true");
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
		catch (TestPilotException te) {
			exitStatus = te.getErrorCode();
			if (session != null) {
				switch (session.action) {
					// TODO: change to DATABASE
					// TODO: be able to create several users!
					case CREATE_SCHEMA:
						System.out.printf("Schema creation failed (%d)%n", exitStatus);
						break;

					// TODO: change to DATABASE
					// TODO: be able to create several users!
					case DROP_SCHEMA:
						System.out.printf("Schema deletion failed (%d)%n", exitStatus);
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
