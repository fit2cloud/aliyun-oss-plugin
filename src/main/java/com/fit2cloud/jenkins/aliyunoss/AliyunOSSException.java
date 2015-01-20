package com.fit2cloud.jenkins.aliyunoss;

public class AliyunOSSException extends Exception {

	private static final long serialVersionUID = 1582215285822395979L;

	public AliyunOSSException() {
		super();
	}

	public AliyunOSSException(final String message, final Throwable cause) {
		super(message,cause); 
	}

	public AliyunOSSException(final String message) {
		super(message);
	}
}
