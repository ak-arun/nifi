package com.ak.aws.processors.nifitsprocessor.helper;

import java.util.List;

public class TSData {
	
	private List<TSDimension> dimensions;
	private List<TSMeasure> measures;
	private String time;
	private String timeUnit;
	
	
	public List<TSDimension> getDimensions() {
		return dimensions;
	}
	public void setDimensions(List<TSDimension> dimensions) {
		this.dimensions = dimensions;
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
	public List<TSMeasure> getMeasures() {
		return measures;
	}
	public void setMeasures(List<TSMeasure> measures) {
		this.measures = measures;
	}
	@Override
	public String toString() {
		return "TSData [dimensions=" + dimensions + ", measures=" + measures + ", time=" + time + ", timeUnit="
				+ timeUnit + "]";
	}
	
	
	
}
