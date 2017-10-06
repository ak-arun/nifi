package com.ak.nifi.custom.processor.flightaware.utils;

import org.apache.nifi.components.ValidationContext;
import org.apache.nifi.components.ValidationResult;
import org.apache.nifi.components.Validator;
import org.apache.nifi.processor.util.StandardValidators;

public class FlightawareValidator extends StandardValidators {
	
	public static final Validator ALWAYS_VALID_VALIDATOR = new Validator() {

		@Override
		public ValidationResult validate(String subject, String input, ValidationContext context) {
			return new ValidationResult.Builder().subject(subject).input(input).explanation("").valid(true).build();
		}
		
	};

}
