package com.oracle.test.exception;

public class TestException extends RuntimeException {
	public static final int UNKNOWN_COMMAND_LINE_ARGUMENT = 1;
	public static final int WRONG_DATABASE_TYPE_PARAMETER = 2;
	public static final int UNKNOWN_HOSTNAME = 3;
	public static final int WRONG_MAIN_CONTROLLER_URI = 4;

	private final int errorCode;

	public TestException(final int errorCode, final Throwable cause) {
		super(String.valueOf(errorCode), cause);
		this.errorCode = errorCode;
	}

	public TestException(final int errorCode) {
		this(errorCode, null);
	}

	public int getErrorCode() {
		return errorCode;
	}
}
