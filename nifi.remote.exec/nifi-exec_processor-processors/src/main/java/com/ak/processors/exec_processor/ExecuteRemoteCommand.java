/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.ak.processors.exec_processor;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.Reader;
import java.io.StringWriter;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.apache.nifi.annotation.behavior.Restricted;
import org.apache.nifi.annotation.behavior.Restriction;
import org.apache.nifi.annotation.behavior.WritesAttribute;
import org.apache.nifi.annotation.behavior.WritesAttributes;
import org.apache.nifi.annotation.documentation.CapabilityDescription;
import org.apache.nifi.annotation.documentation.Tags;
import org.apache.nifi.annotation.lifecycle.OnScheduled;
import org.apache.nifi.components.PropertyDescriptor;
import org.apache.nifi.components.RequiredPermission;
import org.apache.nifi.expression.ExpressionLanguageScope;
import org.apache.nifi.flowfile.FlowFile;
import org.apache.nifi.processor.AbstractProcessor;
import org.apache.nifi.processor.ProcessContext;
import org.apache.nifi.processor.ProcessSession;
import org.apache.nifi.processor.ProcessorInitializationContext;
import org.apache.nifi.processor.Relationship;
import org.apache.nifi.processor.exception.ProcessException;
import org.apache.nifi.processor.io.OutputStreamCallback;
import org.apache.nifi.processor.util.StandardValidators;

import com.jcraft.jsch.Channel;
import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;

@Tags({ "ssh", "jsch", "remote", "execute", "command" })
@CapabilityDescription("Remote Login to a machine, executes the user specified command and returns the result as a flowfile or attribute based on user's choice. For commands that do not produce a result, an empty flow file/attribute")
@WritesAttributes({
		@WritesAttribute(attribute = "remote.execution.time", description = "Time it took to execute the command"),
		@WritesAttribute(attribute = "remote.execution.result", description = "Result of the command, if successful"),
		@WritesAttribute(attribute = "remote.execution.exitcode", description = "Exit Code from the remote execution command"),
		@WritesAttribute(attribute = "remote.execution.error", description = "If possible to grab the erros, publish the error message of a failed command") })

@Restricted(restrictions = {
		@Restriction(requiredPermission = RequiredPermission.EXECUTE_CODE, explanation = "Provides operator the ability to execute arbitrary code on a remote host, provided the user has access to do so.") })
public class ExecuteRemoteCommand extends AbstractProcessor {

	private static final String REMOTE_EXECUTION_EXITCODE = "remote.execution.exitcode";
	private static final String REMOTE_EXECUTION_TIME = "remote.execution.time";
	private static final String REMOTE_EXECUTION_RESULT = "remote.execution.result";
	private static final String REMOTE_EXECUTION_ERROR = "remote.execution.error";
	private static final String STRICT_HOST_KEY_CHECKING = "StrictHostKeyChecking";
	private static final String EXEC = "exec";
	private static final String YES = "yes";
	private static final String NO = "no";
	public static final String DESTINATION_ATTRIBUTE = "flowfile-attribute";
	public static final String DESTINATION_CONTENT = "flowfile-content";
	
	private static final String ERROR_CODE_MAPPING="1 - Catchall for general errors\n2 - Misuse of shell builtins (according to Bash documentation)\n126 - Command invoked cannot execute\n127 - command not found\n128 - Invalid argument to exit\n128+n - Fatal error signal 'n'\n130 - Script terminated by Control-C\n255\\* - Exit status out of range";

	private Result result;

	private JSch jschSSHChannel;
	private Session remoteSession;
	private long transferMillis;
	private boolean isDestinationFlowFile;
	private boolean isHostCheckDisabled;
	private Map<String, String> attributeMap;
	private boolean isExceptionTrue;

	FlowFile flowFileGenerated;

	public static final PropertyDescriptor ALLOW_STRICT_HOSTCHECKING = new PropertyDescriptor.Builder()
			.name("Allow Strict Host Check").description("Enable/Disable Strict Host Checking").required(true)
			.allowableValues(YES, NO).defaultValue(YES).build();

	public static final PropertyDescriptor DESTINATION = new PropertyDescriptor.Builder().name("Result Destination")
			.description(
					"Should the result of the command be an attribute or the flow file or should it replace the flowfile content")
			.required(true).allowableValues(DESTINATION_CONTENT, DESTINATION_ATTRIBUTE)
			.defaultValue(DESTINATION_ATTRIBUTE).build();

	public static final PropertyDescriptor COMMAND = new PropertyDescriptor.Builder().name("Remote Command")
			.displayName("Remote Command").description("Command to be executed on the remote host").required(true)
			.addValidator(StandardValidators.NON_EMPTY_VALIDATOR)
			.expressionLanguageSupported(ExpressionLanguageScope.FLOWFILE_ATTRIBUTES).build();

	public static final PropertyDescriptor REMOTE_HOST = new PropertyDescriptor.Builder().name("Remote Host")
			.displayName("Remote Host").description("Remote hostname").required(true)
			.addValidator(StandardValidators.NON_BLANK_VALIDATOR)
			.expressionLanguageSupported(ExpressionLanguageScope.FLOWFILE_ATTRIBUTES).build();

	public static final PropertyDescriptor REMOTE_PORT = new PropertyDescriptor.Builder().name("Remote Port")
			.displayName("Remote Port").defaultValue("22").description("Remote Port").required(true)
			.addValidator(StandardValidators.PORT_VALIDATOR)
			.expressionLanguageSupported(ExpressionLanguageScope.FLOWFILE_ATTRIBUTES).build();

	public static final PropertyDescriptor REMOTE_USER = new PropertyDescriptor.Builder().name("Remote Username")
			.displayName("Remote username").description("Remote username").required(true)
			.addValidator(StandardValidators.NON_BLANK_VALIDATOR)
			.expressionLanguageSupported(ExpressionLanguageScope.FLOWFILE_ATTRIBUTES).build();

	public static final PropertyDescriptor REMOTE_USERPASSWORD = new PropertyDescriptor.Builder()
			.name("Remote User Password").displayName("Remote User Password").description("Remote User Password")
			.required(true).sensitive(true).addValidator(StandardValidators.NON_BLANK_VALIDATOR)
			.expressionLanguageSupported(ExpressionLanguageScope.FLOWFILE_ATTRIBUTES).build();

	public static final Relationship FAILURE = new Relationship.Builder().name("Failure").description("Failure")
			.build();

	public static final Relationship ORIGINAL = new Relationship.Builder().name("Original")
			.description("Original flowfile").build();

	public static final Relationship SUCCESS = new Relationship.Builder().name("Success").description("Success")
			.build();

	private List<PropertyDescriptor> descriptors;

	private Set<Relationship> relationships;

	@Override
	protected void init(final ProcessorInitializationContext context) {
		final List<PropertyDescriptor> descriptors = new ArrayList<PropertyDescriptor>();
		descriptors.add(DESTINATION);
		descriptors.add(REMOTE_HOST);
		descriptors.add(REMOTE_PORT);
		descriptors.add(ALLOW_STRICT_HOSTCHECKING);
		descriptors.add(COMMAND);
		descriptors.add(REMOTE_USER);
		descriptors.add(REMOTE_USERPASSWORD);

		this.descriptors = Collections.unmodifiableList(descriptors);

		final Set<Relationship> relationships = new HashSet<Relationship>();
		relationships.add(SUCCESS);
		relationships.add(FAILURE);
		relationships.add(ORIGINAL);
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

	@OnScheduled
	public void onScheduled(final ProcessContext context) {

	}

	@Override
	public void onTrigger(final ProcessContext context, final ProcessSession session) throws ProcessException {

		result = null;
		jschSSHChannel = new JSch();
		attributeMap = new HashMap<String, String>();

		FlowFile flowFile = session.get();
		if (flowFile == null) {
			return;
		}

		isDestinationFlowFile = getProperty(context, DESTINATION).equalsIgnoreCase(DESTINATION_CONTENT);
		isHostCheckDisabled = getProperty(context, ALLOW_STRICT_HOSTCHECKING).equalsIgnoreCase(NO);

		String command = getProperty(context, COMMAND, flowFile);
		String hostName = getProperty(context, REMOTE_HOST, flowFile);
		int port = Integer.valueOf(getProperty(context, REMOTE_PORT, flowFile));
		String userName = getProperty(context, REMOTE_USER, flowFile);
		String password = getProperty(context, REMOTE_USERPASSWORD, flowFile);

		final long startNanos = System.nanoTime();
		try {
			remoteSession = jschSSHChannel.getSession(userName, hostName, port);
			remoteSession.setPassword(password);
			if (isHostCheckDisabled) {
				remoteSession.setConfig(STRICT_HOST_KEY_CHECKING, NO);
			}
			remoteSession.connect();
			result = executeCommand(command, remoteSession);
			remoteSession.disconnect();

		} catch (Exception e) {
			isExceptionTrue = true;
			getLogger().error("Failed to execute remote command", new Object[] { command }, e);
			StringWriter sw = new StringWriter();
			e.printStackTrace(new PrintWriter(sw));
			result = new Result(sw.toString(), -127);

		}
		transferMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);

		for (String key : flowFile.getAttributes().keySet()) {
			attributeMap.put(key, flowFile.getAttributes().get(key));
		}

		attributeMap.put(REMOTE_EXECUTION_TIME, String.valueOf(transferMillis));
		if (!isExceptionTrue && result.isExecutionSuccessful()) {

			if (isDestinationFlowFile) {
				flowFileGenerated = session.create();
				flowFileGenerated = session.write(flowFileGenerated, new OutputStreamCallback() {
					@Override
					public void process(final OutputStream out) throws IOException {
						out.write(result.getResultString().getBytes(StandardCharsets.UTF_8));
					}
				});
				attributeMap.put(REMOTE_EXECUTION_EXITCODE, String.valueOf(result.getExitStatus()));
				flowFileGenerated = session.putAllAttributes(flowFileGenerated, attributeMap);
				session.getProvenanceReporter().send(flowFileGenerated, command, transferMillis);
				session.transfer(flowFileGenerated, SUCCESS);
			} else {
				flowFileGenerated = session.clone(flowFile);
				attributeMap.put(REMOTE_EXECUTION_RESULT, result.getResultString());
				attributeMap.put(REMOTE_EXECUTION_EXITCODE, String.valueOf(result.getExitStatus()));
				flowFileGenerated = session.putAllAttributes(flowFileGenerated, attributeMap);
				session.getProvenanceReporter().send(flowFileGenerated, command, transferMillis);
				session.transfer(flowFileGenerated, SUCCESS);
			}

		} else {
			flowFileGenerated = session.clone(flowFile);
			attributeMap.put(REMOTE_EXECUTION_ERROR, result.getResultString()+"\nError Code Mapping "+ERROR_CODE_MAPPING);
			attributeMap.put(REMOTE_EXECUTION_EXITCODE, String.valueOf(result.getExitStatus()));
			flowFileGenerated = session.putAllAttributes(flowFileGenerated, attributeMap);
			session.getProvenanceReporter().send(flowFileGenerated, command, transferMillis);
			session.transfer(flowFileGenerated, FAILURE);
		}

		flowFile = session.putAllAttributes(flowFile, attributeMap);
		session.getProvenanceReporter().send(flowFile, command, transferMillis);
		session.transfer(flowFile, ORIGINAL);
		session.commit();

	}

	public String getProperty(ProcessContext context, PropertyDescriptor descriptor) {
		return context.getProperty(descriptor).getValue().toString().trim();
	}

	public String getProperty(ProcessContext context, PropertyDescriptor descriptor, FlowFile flowfile) {
		return context.getProperty(descriptor).evaluateAttributeExpressions(flowfile).getValue().toString().trim();

	}

	private Result executeCommand(String command, Session session) throws Exception {
		StringBuilder outputBuffer = new StringBuilder();
		Channel channel = session.openChannel(EXEC);
		((ChannelExec) channel).setCommand(command);

		InputStream commandOutput = channel.getInputStream();
		channel.connect();
		int readByte = commandOutput.read();
		while (readByte != 0xffffffff) {
			outputBuffer.append((char) readByte);
			readByte = commandOutput.read();
		}
		result = new Result(outputBuffer.toString(), channel.getExitStatus());
		channel.disconnect();
		return result;
	}


}
