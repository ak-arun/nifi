package com.ak.nifiproccessors.azure.sas;

import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.apache.nifi.annotation.behavior.InputRequirement;
import org.apache.nifi.annotation.behavior.InputRequirement.Requirement;
import org.apache.nifi.annotation.behavior.WritesAttribute;
import org.apache.nifi.annotation.behavior.WritesAttributes;
import org.apache.nifi.annotation.documentation.CapabilityDescription;
import org.apache.nifi.annotation.documentation.Tags;
import org.apache.nifi.components.PropertyDescriptor;
import org.apache.nifi.flowfile.FlowFile;
import org.apache.nifi.processor.ProcessContext;
import org.apache.nifi.processor.ProcessSession;
import org.apache.nifi.processor.ProcessorInitializationContext;
import org.apache.nifi.processor.exception.ProcessException;
import org.apache.nifi.processor.util.StandardValidators;

import com.microsoft.azure.storage.StorageException;
import com.microsoft.azure.storage.blob.BlobProperties;
@Tags({ "azure", "microsoft", "cloud", "storage", "blob", "sas key","put" })
@CapabilityDescription("Accepts an azure blob container, sas key and stores an incoming flowfile into the container")
@WritesAttributes({ 
	@WritesAttribute(attribute = "azure.container", description = "The name of the Azure container"),
    @WritesAttribute(attribute = "azure.blobname", description = "The name of the Azure blob"),
    @WritesAttribute(attribute = "azure.primaryUri", description = "Primary location for blob content"),
    @WritesAttribute(attribute = "azure.etag", description = "Etag for the Azure blob"),
    @WritesAttribute(attribute = "azure.timestamp", description = "The timestamp in Azure for the blob")})
@InputRequirement(Requirement.INPUT_REQUIRED)
public class PutBlobWithSASKey extends AbstractAzureSASKeyProcessor {
	
	
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
		 Map<String, String> attributes = new HashMap<String, String>();
		 FlowFile flowFile = session.get();
	        if (flowFile == null) {
	            return;
	        }
	        
	        final long startNanos = System.nanoTime();
	        String blobName = super.getProperty(context, BLOB_NAME);
	        try {
	        	blob = container.getBlockBlobReference(blobName);
				blob.upload(session.read(flowFile), flowFile.getSize());
				attributes = getAttributes(context);
				
		} catch (Exception e) {
			getLogger().error("Failed to put Azure blob {}", new Object[] { blobName }, e);
			flowFile = session.penalize(flowFile);
			session.transfer(flowFile, REL_FAILURE);
		} 
	        if(!attributes.isEmpty()){
	        	final long transferMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
	            session.getProvenanceReporter().send(flowFile, attributes.get("azure.primaryUri"), transferMillis);
	        	flowFile = session.putAllAttributes(flowFile, attributes);
	        	session.transfer(flowFile,REL_SUCCESS);
	        	session.commit();
	        }
	        
		
	}
	
	private Map<String, String> getAttributes(ProcessContext context) throws URISyntaxException, StorageException {
		final Map<String, String> attributes = new HashMap<String, String>();
		BlobProperties properties = blob.getProperties();
		attributes.put("azure.container", getProperty(context, CONTAINER_NAME));
		attributes.put("azure.blobname", getProperty(context, BLOB_NAME));
		attributes.put("azure.primaryUri", blob.getSnapshotQualifiedUri().toString());
		attributes.put("azure.etag", properties.getEtag());
		attributes.put("azure.timestamp", String.valueOf(properties.getLastModified()));
		return attributes;
	}

}
