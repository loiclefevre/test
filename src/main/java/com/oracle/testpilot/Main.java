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
 * Test services main entry point.
 *
 * @author LLEFEVRE
 * @since 1.0.0
 */
public class Main {

	static {
		Locale.setDefault(Locale.US);
		System.setProperty("java.net.useSystemProxies", "false");
	}

	public static final String VERSION="1.0.16";

	public static void main(final String[] args) {
		int exitStatus = 0;

		Session session = null;

		try {
			session = new Session(args);
			session.run();
		}
		catch (TestPilotException te) {
			exitStatus = te.getErrorCode();
			if (session != null) {
				switch (session.action) {
					case CREATE:
						System.out.printf("Provisioning failed (%d)%n", exitStatus);
						break;

					case DELETE:
						System.out.printf("De-provisioning failed (%d)%n", exitStatus);
						break;

					case SKIP_TESTING:
						System.out.printf("Skip testing check failed (%d)%n", exitStatus);
						break;
				}
			}

			//System.out.println("Error: " + te.getMessage());
			te.printStackTrace();
		}

		System.exit(exitStatus);
	}
}
