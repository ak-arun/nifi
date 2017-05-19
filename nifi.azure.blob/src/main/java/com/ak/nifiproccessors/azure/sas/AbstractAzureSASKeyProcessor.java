package com.ak.nifiproccessors.azure.sas;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.nifi.annotation.lifecycle.OnScheduled;
import org.apache.nifi.components.AllowableValue;
import org.apache.nifi.components.PropertyDescriptor;
import org.apache.nifi.processor.AbstractProcessor;
import org.apache.nifi.processor.ProcessContext;
import org.apache.nifi.processor.Relationship;
import org.apache.nifi.processor.util.StandardValidators;

import com.microsoft.azure.storage.StorageCredentialsSharedAccessSignature;
import com.microsoft.azure.storage.StorageException;
import com.microsoft.azure.storage.blob.CloudBlobContainer;
import com.microsoft.azure.storage.blob.CloudBlockBlob;

public abstract class AbstractAzureSASKeyProcessor extends AbstractProcessor {
	
	public CloudBlobContainer container = null;
	public CloudBlockBlob blob = null;
	
	static final AllowableValue NO = new AllowableValue("no", "no",
			"do not use http");
	static final AllowableValue YES = new AllowableValue("yes", "yes",
			"use http in place of https");

	public static final Relationship REL_SUCCESS = new Relationship.Builder().name("success").description("All successfully processed FlowFiles are routed to this relationship").build();
    public static final Relationship REL_FAILURE = new Relationship.Builder().name("failure").description("Unsuccessful operations will be transferred to the failure relationship.").build();
    private static final Set<Relationship> RELATIONSHIPS = Collections.unmodifiableSet(new HashSet<Relationship>(Arrays.asList(REL_SUCCESS, REL_FAILURE)));

    
    public static final PropertyDescriptor STORAGE_ACCOUNT_NAME = new PropertyDescriptor.Builder().name("Storage Account Name")
			.description("The Blob Storage Account Name").required(true).expressionLanguageSupported(true).sensitive(true)
			.addValidator(StandardValidators.NON_EMPTY_VALIDATOR).build();
    
    public static final PropertyDescriptor CONTAINER_NAME = new PropertyDescriptor.Builder().name("Container Name")
			.description("The Container Name").required(true).expressionLanguageSupported(true)
			.addValidator(StandardValidators.NON_EMPTY_VALIDATOR).build();
    
    
    public static final PropertyDescriptor SAS_QUERY_STRING = new PropertyDescriptor.Builder().name("SAS Key Query String")
			.description("The SAS Key Query String, begins with a ?").required(true).expressionLanguageSupported(true).sensitive(true)
			.addValidator(StandardValidators.NON_EMPTY_VALIDATOR).build();
    
    
    public static final PropertyDescriptor USE_HTTP = new PropertyDescriptor.Builder().name("USE HTTP")
			.description("Use http instead of https").required(true).allowableValues(NO,YES)
			.defaultValue(NO.getValue()).build();
    
    @Override
    public Set<Relationship> getRelationships() {
        return RELATIONSHIPS;
    }
    
    List<PropertyDescriptor> descriptors;
    
    
    
    @OnScheduled
	public void onScheduled(final ProcessContext context) throws URISyntaxException, StorageException {
    	 String storageAccountName = getProperty(context, STORAGE_ACCOUNT_NAME);
    	 String containerName = getProperty(context, CONTAINER_NAME);
    	 String uriProtocol = "https://";
    	 if(getProperty(context, USE_HTTP).equalsIgnoreCase("yes")){
    		 uriProtocol = "http://";
    	 }
    	 String uriString = uriProtocol+storageAccountName+".blob.core.windows.net/"+containerName;
    	 URI uri = new URI(uriString);
    	 container = new CloudBlobContainer(uri,new StorageCredentialsSharedAccessSignature(getProperty(context, SAS_QUERY_STRING)));
	}

    
   
    
    public String getProperty(ProcessContext context, PropertyDescriptor descriptor) {
		if(descriptor.isExpressionLanguageSupported()){
			return context.getProperty(descriptor).evaluateAttributeExpressions().getValue().toString().trim();
		}else{
			return context.getProperty(descriptor).getValue().toString().trim();
		}
	}

}
