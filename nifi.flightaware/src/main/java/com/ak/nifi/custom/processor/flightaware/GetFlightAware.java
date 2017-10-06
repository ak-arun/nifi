package com.ak.nifi.custom.processor.flightaware;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

import org.apache.nifi.annotation.behavior.InputRequirement;
import org.apache.nifi.annotation.behavior.InputRequirement.Requirement;
import org.apache.nifi.annotation.behavior.TriggerSerially;
import org.apache.nifi.annotation.behavior.WritesAttribute;
import org.apache.nifi.annotation.behavior.WritesAttributes;
import org.apache.nifi.annotation.documentation.CapabilityDescription;
import org.apache.nifi.annotation.documentation.Tags;
import org.apache.nifi.annotation.lifecycle.OnShutdown;
import org.apache.nifi.annotation.lifecycle.OnStopped;
import org.apache.nifi.annotation.lifecycle.OnUnscheduled;
import org.apache.nifi.components.AllowableValue;
import org.apache.nifi.components.PropertyDescriptor;
import org.apache.nifi.flowfile.FlowFile;
import org.apache.nifi.flowfile.attributes.CoreAttributes;
import org.apache.nifi.processor.AbstractProcessor;
import org.apache.nifi.processor.ProcessContext;
import org.apache.nifi.processor.ProcessSession;
import org.apache.nifi.processor.ProcessorInitializationContext;
import org.apache.nifi.processor.Relationship;
import org.apache.nifi.processor.exception.ProcessException;
import org.apache.nifi.processor.io.OutputStreamCallback;
import org.apache.nifi.processor.util.StandardValidators;
import org.json.JSONObject;

import com.ak.nifi.custom.processor.flightaware.utils.FlightawareDataReader;
import com.ak.nifi.custom.processor.flightaware.utils.FlightawareRelationType;
import com.ak.nifi.custom.processor.flightaware.utils.FlightawareValidator;


@Tags({ "flightaware, flight, plan, departure, gate, live, pitr, range" })
@CapabilityDescription("Pulls data from the Flightaware Flight API over TCP on SSL and passes on the data to the success, keepalive/ invalid relations based on the incoming message. "
		+ "Username,API Key and query type are mandatory fields for data fetch."
		+ " This processor must be run with exactly one concurrent task on the primary node.")
@TriggerSerially
@InputRequirement(Requirement.INPUT_FORBIDDEN)
@WritesAttributes({ 
	@WritesAttribute(attribute = "mime.type", description = "Sets mime type to application/json for valid json messages from Flightaware and text/plain for invalid messages"),
	@WritesAttribute(attribute = "nifi.host.name", description = "hostname of the nifi node from which the data fetch was initaited") 
	})
public class GetFlightAware extends AbstractProcessor {

	private static final String USERNAME = "username";
	private static final String SPACE = " ";
	private static final String PASSWORD = "password";
	private static final String HTTPS = "HTTPS";
	private static final String UTF_8 = "UTF8";
	private static final String NEWLINE = "\n";
	private static final String FLIGHTAWARE_DEFAULT_HOSTNAME = "firehose.flightaware.com";
	private static final int FLIGHTAWARE_DEFAULT_PORT = 1501;
	
	private static BlockingQueue<String> queue;
	private static Thread readerThread;

	static final AllowableValue LIVE = new AllowableValue("live", "live",
			"Request live data from the present time forward");
	static final AllowableValue PITR = new AllowableValue("pitr", "pitr",
			"pitr <epoch> - Request data from a specified time, in POSIX epoch format, in the past until the current time, and continue with the live behavior");
	static final AllowableValue RANGE = new AllowableValue("range", "range",
			"range <start epoch> <end epoch> - Send data between two specified times, in POSIX epoch format. FlightAware will disconnect the connection when last message has been sent");

	private static boolean isConnected = false;
	private static BufferedReader reader = null;
	private static OutputStreamWriter writer = null;
	private static InputStream inputStream = null;
	private static SSLSocket ssl_socket = null;

	public static final PropertyDescriptor FLIGHTAWARE_HOSTNAME = new PropertyDescriptor.Builder()
			.name("Flightaware Hostname").description("Specifies the flightaware api hostname to pull data from")
			.required(true).addValidator(StandardValidators.NON_EMPTY_VALIDATOR)
			.defaultValue(FLIGHTAWARE_DEFAULT_HOSTNAME).expressionLanguageSupported(true).build();

	public static final PropertyDescriptor FLIGHTAWARE_PORT = new PropertyDescriptor.Builder().name("Flightaware Port")
			.description("Specifies the flightaware api port to pull data from").required(true)
			.addValidator(StandardValidators.PORT_VALIDATOR).defaultValue(String.valueOf(FLIGHTAWARE_DEFAULT_PORT))
			.expressionLanguageSupported(true).build();

	public static final PropertyDescriptor TIME_RANGE = new PropertyDescriptor.Builder().name("Time Range")
			.description("Specifies the time range for the data pull").required(true).allowableValues(LIVE, PITR, RANGE)
			.defaultValue(LIVE.getValue()).build();

	public static final PropertyDescriptor FLIGHTAWARE_USERNAME = new PropertyDescriptor.Builder().name("User Name")
			.description("The username provided by Flightaware").required(true).expressionLanguageSupported(true)
			.addValidator(StandardValidators.NON_EMPTY_VALIDATOR).build();

	public static final PropertyDescriptor API_KEY = new PropertyDescriptor.Builder().name("Api Key")
			.description("The Api Key provided by Flightaware").required(true).sensitive(true)
			.expressionLanguageSupported(true).addValidator(StandardValidators.NON_EMPTY_VALIDATOR).build();

	public static final PropertyDescriptor QUERY_CONDITION = new PropertyDescriptor.Builder().name("Query Conditions")
			.description("Add filters, versions, epoch etc to the query").expressionLanguageSupported(true)
			.addValidator(FlightawareValidator.ALWAYS_VALID_VALIDATOR).build();

	public static final Relationship SUCCESS = new Relationship.Builder().name("Success")
			.description("Fetched Json Messages").build();

	public static final Relationship INVALID = new Relationship.Builder().name("Invalid")
			.description("Invalid Messages").build();

	public static final Relationship KEEPALIVE = new Relationship.Builder().name("Keepalive")
			.description("Keepalive Messages").build();

	private List<PropertyDescriptor> descriptors;

	private Set<Relationship> relationships;

	@Override
	protected void init(final ProcessorInitializationContext context) {
		final List<PropertyDescriptor> descriptors = new ArrayList<PropertyDescriptor>();
		descriptors.add(FLIGHTAWARE_HOSTNAME);
		descriptors.add(FLIGHTAWARE_PORT);
		descriptors.add(TIME_RANGE);
		descriptors.add(FLIGHTAWARE_USERNAME);
		descriptors.add(API_KEY);
		descriptors.add(QUERY_CONDITION);
		this.descriptors = Collections.unmodifiableList(descriptors);
		final Set<Relationship> relationships = new HashSet<Relationship>();
		relationships.add(SUCCESS);
		relationships.add(INVALID);
		relationships.add(KEEPALIVE);
		this.relationships = Collections.unmodifiableSet(relationships);
	}

	@Override
	public Set<Relationship> getRelationships() {
		return this.relationships;
	}

	@Override
	public final List<PropertyDescriptor> getSupportedPropertyDescriptors() {
		return descriptors;
	}

	private void getConnected(final ProcessContext context) throws ProcessException {
		try {
			ssl_socket = (SSLSocket) SSLSocketFactory.getDefault().createSocket(
					getProperty(context, FLIGHTAWARE_HOSTNAME),
					Integer.parseInt(getProperty(context, FLIGHTAWARE_PORT)));
			SSLParameters sslParams = new SSLParameters();
			sslParams.setEndpointIdentificationAlgorithm(HTTPS);
			ssl_socket.setSSLParameters(sslParams);
			writer = new OutputStreamWriter(ssl_socket.getOutputStream(), UTF_8);
			writer.write(getInitiationCommand(context));
			writer.flush();
			inputStream = ssl_socket.getInputStream();
			reader = new BufferedReader(new InputStreamReader(inputStream));
		} catch (Exception e) {
			throw new ProcessException(e);
		}
	}

	@Override
	public void onTrigger(final ProcessContext context, final ProcessSession session) throws ProcessException {

		if (queue == null) {
			queue = new LinkedBlockingQueue<String>();
		}

		if (!isConnected) {
			getConnected(context);
			performNonBlockingFlightDataFetch(queue);
			isConnected = true;
		}

		List<String> messages = new ArrayList<String>();
		queue.drainTo(messages);
		for (final String message : messages) {
			FlowFile flowFile = session.create();
			flowFile = session.write(flowFile, new OutputStreamCallback() {
				@Override
				public void process(final OutputStream out) throws IOException {
					out.write(message.getBytes(StandardCharsets.UTF_8));
				}
			});

			switch (getRelationshipType(message)) {
			case SUCCESS:
				flowFile = session.putAllAttributes(flowFile, getAttributes(FlightawareRelationType.SUCCESS, flowFile));
				transferFlowfile(flowFile, session, SUCCESS);
				break;
			case KEEPALIVE:
				flowFile = session.putAllAttributes(flowFile, getAttributes(FlightawareRelationType.KEEPALIVE, flowFile));
				transferFlowfile(flowFile, session, KEEPALIVE);
				break;
			case INVALID:
				;
			default:
				flowFile = session.putAllAttributes(flowFile, getAttributes(FlightawareRelationType.INVALID, flowFile));
				transferFlowfile(flowFile, session, INVALID);
				break;
			}

		}

	}

	private void performNonBlockingFlightDataFetch(BlockingQueue<String> queue) {
		readerThread = new FlightawareDataReader(queue, reader);
		readerThread.start();
	}

	@OnStopped
	public void stop() {
		getLogger().info("GetFlightaware : Initiated Stop");
		cleanUp();
	}

	void cleanUp() {
		isConnected = false;
		try {
			writer.close();
			reader.close();
			inputStream.close();
			ssl_socket.close();
			queue = null;
			readerThread.interrupt();
		} catch (Exception e) {
			getLogger().info("GetFlightaware : Exception while performing cleanup "+Arrays.toString(e.getStackTrace()));
		}
	}

	@OnShutdown
	public void shutDown() {
		getLogger().info("GetFlightaware : Initiated Shutdown");
		cleanUp();
	}

	@OnUnscheduled
	public void unSchedule() {
		getLogger().info("GetFlightaware : Initiated Un-Schedule");
		cleanUp();
	}

	private String getProperty(ProcessContext context, PropertyDescriptor descriptor) {
		if (descriptor.isExpressionLanguageSupported()) {
			return context.getProperty(descriptor).evaluateAttributeExpressions().getValue().toString().trim();
		} else {
			return context.getProperty(descriptor).getValue().toString().trim();
		}
	}

	private String getInitiationCommand(final ProcessContext context) {
		if (isExistQueryCondition(context)) {
			return getProperty(context, TIME_RANGE) + SPACE + getProperty(context, QUERY_CONDITION) + SPACE + USERNAME
					+ SPACE + getProperty(context, FLIGHTAWARE_USERNAME) + SPACE + PASSWORD + SPACE
					+ getProperty(context, API_KEY) + NEWLINE;
		} else {
			return getProperty(context, TIME_RANGE) + SPACE + USERNAME + SPACE
					+ getProperty(context, FLIGHTAWARE_USERNAME) + SPACE + PASSWORD + SPACE
					+ getProperty(context, API_KEY) + NEWLINE;
		}
	}

	private boolean isExistQueryCondition(final ProcessContext context) {
		return context.getProperty(QUERY_CONDITION).getValue() == null ? false : true;
	}

	private void transferFlowfile(FlowFile flowfile, ProcessSession session, Relationship relationship) {
		session.transfer(flowfile, relationship);
		session.commit();
	}

	private Map<String, String> getAttributes(FlightawareRelationType type, FlowFile f) {

		final Map<String, String> attributes = new HashMap<>();
		attributes.put("nifi.host.name", getHostname());
		switch (type) {
		case SUCCESS:
		case KEEPALIVE:
			attributes.put(CoreAttributes.MIME_TYPE.key(), "application/json");
			attributes.put(CoreAttributes.FILENAME.key(), f.getAttribute(CoreAttributes.FILENAME.key()) + ".json");
			break;
		case INVALID:
		default:
			attributes.put(CoreAttributes.MIME_TYPE.key(), "text/plain");
			break;
		}
		return attributes;
	}

	private FlightawareRelationType getRelationshipType(String message) {
		FlightawareRelationType type = FlightawareRelationType.INVALID;
		try {
			if (new JSONObject(message).get("type").toString().trim().equalsIgnoreCase("keepalive")) {
				type = FlightawareRelationType.KEEPALIVE;
			} else {
				type = FlightawareRelationType.SUCCESS;
			}
		} catch (Exception e) {
		}
		return type;

	}
	
	public static String getHostname(){
		try{
	    	return InetAddress.getLocalHost().getHostName();
	    }catch(Exception e){}
		return "";
	}

}