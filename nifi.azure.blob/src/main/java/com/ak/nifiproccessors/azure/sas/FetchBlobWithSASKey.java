package com.ak.nifiproccessors.azure.sas;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.nifi.annotation.behavior.InputRequirement;
import org.apache.nifi.annotation.behavior.InputRequirement.Requirement;
import org.apache.nifi.annotation.documentation.CapabilityDescription;
import org.apache.nifi.annotation.documentation.Tags;
import org.apache.nifi.components.PropertyDescriptor;
import org.apache.nifi.processor.ProcessContext;
import org.apache.nifi.processor.ProcessSession;
import org.apache.nifi.processor.ProcessorInitializationContext;
import org.apache.nifi.processor.exception.ProcessException;
import org.apache.nifi.processor.util.StandardValidators;

@Tags({ "azure", "microsoft", "cloud", "storage", "blob", "sas key","fetch" })
@CapabilityDescription("Fetches a blob from a azure container based on the provided container name and  SAS key")
@InputRequirement(Requirement.INPUT_REQUIRED)
public class FetchBlobWithSASKey extends AbstractAzureSASKeyProcessor{
	
	 public static final PropertyDescriptor BLOB_NAME = new PropertyDescriptor.Builder().name("Blob Name")
				.description("The Destination Blob Name").required(true).expressionLanguageSupported(true)
				.addValidator(StandardValidators.NON_EMPTY_VALIDATOR).build();
	 
	 
	 
	 @Override
		protected void init(final ProcessorInitializationContext context) {
			final List<PropertyDescriptor> descriptors = new ArrayList<PropertyDescriptor>();
			descriptors.add(STORAGE_ACCOUNT_NAME);
			descriptors.add(CONTAINER_NAME);
			descriptors.add(SAS_QUERY_STRING);
			descriptors.add(USE_HTTP);
			descriptors.add(BLOB_NAME);
			this.descriptors = Collections.unmodifiableList(descriptors);
		}
	    
	    @Override
		public final List<PropertyDescriptor> getSupportedPropertyDescriptors() {
			return descriptors;
		}
	    
	@Override
	public void onTrigger(ProcessContext context, ProcessSession session) throws ProcessException {
		
	}

}
