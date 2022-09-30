package com.rulesservice.exception;


public class AccessDeniedException extends RuntimeException{

	private static final long serialVersionUID = 895616911464801474L;

	public AccessDeniedException() {
		super();
	}

	/**
	 * @param message
	 */
	public AccessDeniedException(String message) {
		super(message);
	}

	
	
}
