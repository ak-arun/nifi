package com.ak.processors.exec_processor;

import java.io.InputStream;

import com.jcraft.jsch.Channel;
import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;

public class TestLocal {
	public static void main(String[] args) throws Exception {

		JSch jsch = new JSch();
		Session session = jsch.getSession("", "localhost", 22);
		session.setConfig("StrictHostKeyChecking", "no");
		session.setPassword("");
		session.connect(30000);
		
		
		Channel channel = session.openChannel("exec");
		((ChannelExec) channel).setCommand("lmnop");
		channel.setInputStream(null);

		((ChannelExec) channel).setErrStream(System.err);

		InputStream in = channel.getInputStream();

		channel.connect();
		
		StringBuffer buffer = new StringBuffer();

		int exitStatus=-1;
		
		byte[] tmp = new byte[1024];
		while (true) {

			while (in.available() > 0) {
				int i = in.read(tmp, 0, 1024);
				if (i < 0)
					break;
				buffer.append(new String(tmp, 0, i));
			}
			if (channel.isClosed()) {
				if (in.available() > 0)
					continue;
				exitStatus =  channel.getExitStatus();
				break;
			}
		}

		System.out.println("Exit Code "+exitStatus);
		System.out.println("Result "+buffer.toString());
		
		//System.out.println(executeCommand("lmnop", session));
		channel.disconnect();
		session.disconnect();
	}
	
	private static String executeCommand(String command, Session session) throws Exception {
		StringBuilder outputBuffer = new StringBuilder();
		Channel channel = session.openChannel("exec");
		((ChannelExec) channel).setCommand(command);
		InputStream commandOutput = channel.getInputStream();
		channel.connect();
		int readByte = commandOutput.read();
		while (readByte != 0xffffffff) {
			outputBuffer.append((char) readByte);
			readByte = commandOutput.read();
		}
		channel.disconnect();
		return outputBuffer.toString();
	}

}
