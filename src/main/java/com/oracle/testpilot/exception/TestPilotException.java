/*
 ** Oracle Test Pilot
 **
 ** Copyright (c) 2025 Oracle
 ** Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl/
 */
package com.oracle.testpilot.exception;

public class TestPilotException extends RuntimeException {
	public static final int UNKNOWN_COMMAND_LINE_ARGUMENT = 1;
	public static final int WRONG_DATABASE_TYPE_PARAMETER = 2;
	public static final int WRONG_MAIN_CONTROLLER_URI = 4;
	public static final int CREATE_SCHEMA_REST_ENDPOINT_ISSUE = 5;
	public static final int BAD_CREATE_SCHEMA_RESPONSE = 6;
	public static final int WRONG_MAIN_CONTROLLER_REST_CALL = 7;
	public static final int WRONG_ATPS_REST_CALL = 8;
	public static final int ATPS_REST_ENDPOINT_ISSUE = 9;
	public static final int CREATE_SCHEMA_MISSING_USER_NAME = 10;
	public static final int CREATE_SCHEMA_MISSING_PASSWORD = 11;
	public static final int CREATE_SCHEMA_MISSING_DB_TYPE = 12;
	public static final int SKIP_TESTING_MISSING_OWNER = 13;
	public static final int SKIP_TESTING_MISSING_REPOSITORY = 14;
	public static final int SKIP_TESTING_MISSING_SHA = 15;
	public static final int SKIP_TESTING_MISSING_PREFIX_LIST = 16;
	public static final int SKIP_TESTING_WRONG_URI = 17;
	public static final int SKIP_TESTING_WRONG_REST_CALL = 18;
	public static final int SKIP_TESTING_REST_ENDPOINT_ISSUE = 19;
	public static final int USER_MISSING_PARAMETER = 20;
	public static final int PASSWORD_MISSING_PARAMETER = 21;
	public static final int DBTYPE_MISSING_PARAMETER = 22;
	public static final int PREFIX_LIST_MISSING_PARAMETER = 23;
	public static final int OWNER_MISSING_PARAMETER = 24;
	public static final int REPOSITORY_MISSING_PARAMETER = 25;
	public static final int SHA_MISSING_PARAMETER = 26;
	public static final int WRONG_SQLCL_USAGE = 27;
	public static final int SQLCL_INTERRUPTED = 28;
	public static final int SQLCL_ERROR = 29;
	public static final int GET_DB_INFO_MISSING_DB_TYPE = 30;
	public static final int GET_DB_INFO_REST_ENDPOINT_ISSUE = 31;
	public static final int DROP_SCHEMA_MISSING_USER_NAME = 32;
	public static final int DROP_SCHEMA_MISSING_DB_TYPE = 33;
	public static final int DROP_SCHEMA_REST_ENDPOINT_ISSUE = 34;
	public static final int BAD_DROP_SCHEMA_RESPONSE = 35;

	private final int errorCode;

	public TestPilotException(final int errorCode, final Throwable cause) {
		super(String.valueOf(errorCode), cause);
		this.errorCode = errorCode;
	}

	public TestPilotException(final int errorCode) {
		this(errorCode, null);
	}

	public int getErrorCode() {
		return errorCode;
	}
}
