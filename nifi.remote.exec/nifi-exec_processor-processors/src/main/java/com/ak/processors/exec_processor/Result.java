package com.ak.processors.exec_processor;

public class Result {

	public String getResultString() {
		return resultString;
	}
	public void setResultString(String resultString) {
		this.resultString = resultString;
	}
	public int getExitStatus() {
		return exitStatus;
	}
	public void setExitStatus(int exitStatus) {
		this.exitStatus = exitStatus;
	}
	
	
	private String resultString;
	private int exitStatus;
	public Result(String resultString, int exitStatus) {
		super();
		this.resultString = resultString;
		this.exitStatus = exitStatus;
	}
	
	public boolean isExecutionSuccessful() {
		return exitStatus==0?true:false;
	}
	
	
}
