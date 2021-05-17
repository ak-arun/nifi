package com.ak.aws.processors.nifitsprocessor;

import org.apache.nifi.processor.ProcessContext;
import org.apache.nifi.processors.aws.AbstractAWSCredentialsProviderProcessor;

import com.amazonaws.ClientConfiguration;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.timestreamwrite.AmazonTimestreamWriteAsyncClientBuilder;
import com.amazonaws.services.timestreamwrite.AmazonTimestreamWriteClient;

public abstract class AbstractTimestreamWriteProcessor extends AbstractAWSCredentialsProviderProcessor<AmazonTimestreamWriteClient>{

	@SuppressWarnings("deprecation")
	@Override
	protected AmazonTimestreamWriteClient createClient(ProcessContext context,
			AWSCredentialsProvider credentialsProvider, ClientConfiguration config) {
		
		getLogger().info("AmazonTimestreamWriteClient createClient 1");
		
		
		
		Regions regions = Regions.fromName(context.getProperty(REGION).getValue());
		return (AmazonTimestreamWriteClient) AmazonTimestreamWriteAsyncClientBuilder.standard().withRegion(regions)
				.withClientConfiguration(config).withCredentials(credentialsProvider).build();

	}
	
	@Override
	protected void initializeRegionAndEndpoint(ProcessContext context) {
		// TODO Auto-generated method stub
		//super.initializeRegionAndEndpoint(context);
	}

	@Override
	protected AmazonTimestreamWriteClient createClient(ProcessContext context, AWSCredentials credentials,
			ClientConfiguration config) {
		
		getLogger().info("AmazonTimestreamWriteClient createClient 2");
		
		
		AWSCredentialsProvider credentialsProvider = new AWSStaticCredentialsProvider(
				new BasicAWSCredentials(credentials.getAWSAccessKeyId(), credentials.getAWSSecretKey()));
		return createClient(context, credentialsProvider, config);
	}

}
