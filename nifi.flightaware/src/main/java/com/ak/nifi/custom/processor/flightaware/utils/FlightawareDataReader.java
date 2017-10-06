package com.ak.nifi.custom.processor.flightaware.utils;

import java.io.BufferedReader;
import java.util.concurrent.BlockingQueue;

public class FlightawareDataReader extends Thread {

	BlockingQueue<String> queue;
	BufferedReader reader;

	public FlightawareDataReader(BlockingQueue<String> queue, BufferedReader reader) {
		this.queue = queue;
		this.reader = reader;
	}

	@Override
	public void run() {
		String message;
		try {
			while ((message = reader.readLine()) != null) {
				queue.put(message);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

}
