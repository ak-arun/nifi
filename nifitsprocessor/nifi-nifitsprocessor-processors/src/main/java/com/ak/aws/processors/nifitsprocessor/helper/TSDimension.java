package com.ak.aws.processors.nifitsprocessor.helper;

public class TSDimension {

	private String dimensionName;
	
	private String dimensionType;
	private String dimensionValue;
	
	public String getDimensionName() {
		return dimensionName;
	}
	public void setDimensionName(String dimensionName) {
		this.dimensionName = dimensionName;
	}
	public String getDimensionType() {
		return dimensionType;
	}
	public void setDimensionType(String dimensionType) {
		this.dimensionType = dimensionType;
	}
	public String getDimensionValue() {
		return dimensionValue;
	}
	public void setDimensionValue(String dimensionValue) {
		this.dimensionValue = dimensionValue;
	}
	
	
	@Override
	public String toString() {
		return "TSDimension [dimensionName=" + dimensionName + ", dimensionType=" + dimensionType + ", dimensionValue="
				+ dimensionValue + "]";
	}
	
}
