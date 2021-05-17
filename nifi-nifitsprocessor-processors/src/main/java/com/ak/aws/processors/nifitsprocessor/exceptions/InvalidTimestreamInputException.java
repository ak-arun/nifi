package com.ak.aws.processors.nifitsprocessor.exceptions;

import java.io.IOException;

public class InvalidTimestreamInputException extends IOException {
	
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	public InvalidTimestreamInputException(String message) {
		super(message);
	}

}
