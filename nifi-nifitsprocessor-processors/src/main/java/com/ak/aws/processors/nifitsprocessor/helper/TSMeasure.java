package com.ak.aws.processors.nifitsprocessor.helper;

public class TSMeasure {
	
	@Override
	public String toString() {
		return "TSMeasure [measureName=" + measureName + ", measureValue=" + measureValue + ", measureType="
				+ measureType + ", time=" + time + "]";
	}
	private String measureName;
	private String measureValue;
	private String measureType;
	private String time;
	private String timeUnit;
	
	
	public String getMeasureName() {
		return measureName;
	}
	public void setMeasureName(String measureName) {
		this.measureName = measureName;
	}
	public String getMeasureValue() {
		return measureValue;
	}
	public void setMeasureValue(String measureValue) {
		this.measureValue = measureValue;
	}
	public String getMeasureType() {
		return measureType;
	}
	public void setMeasureType(String measureType) {
		this.measureType = measureType;
	}
	public String getTime() {
		return time;
	}
	public void setTime(String time) {
		this.time = time;
	}
	public String getTimeUnit() {
		return timeUnit;
	}
	public void setTimeUnit(String timeUnit) {
		this.timeUnit = timeUnit;
	}
	
	
}
