package com.ak.aws.processors.nifitsprocessor.helper;

import java.util.List;

import org.apache.nifi.logging.ComponentLog;

import com.amazonaws.services.timestreamwrite.model.DimensionValueType;
import com.amazonaws.services.timestreamwrite.model.MeasureValueType;

public class TSUtil {

	private ComponentLog logger;

	public TSUtil(ComponentLog componentLog) {
		this.logger = componentLog;

	}

	public MeasureValueType getMeasureValueType(String type) {
		try {
			return MeasureValueType.fromValue(type.toUpperCase());

		} catch (Exception e) {
			logger.warn("TimestreamProcessorUtil : unrecognized type, casting to VARCHAR");
			return MeasureValueType.VARCHAR;
		}
	}

	public DimensionValueType getDimensionValueType(String type) {
		try {
			return DimensionValueType.fromValue(type.toUpperCase());
		} catch (Exception e) {
			logger.warn("TimestreamProcessorUtil : unrecognized type, casting to VARCHAR");
			return DimensionValueType.VARCHAR;
		}
	}
	
	public boolean isListNullOrEmpty(List<?> list) {
		try {
			if (list == null || list.size() == 0) {
				return true;
			}
		} catch (Exception e) {

		}
		return false;
	}
	
	public boolean isValueNullOrEmpty(String x) {
		try {
			if (x == null || x.trim().equalsIgnoreCase("")) {
				return true;
			}
		} catch (Exception e) {
		}
		return false;
	}

}
