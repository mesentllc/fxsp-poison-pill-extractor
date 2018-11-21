package com.fedex.smartpost.system.service;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Calendar;

import javax.jms.BytesMessage;
import javax.jms.Message;

import org.junit.Test;

import com.fedex.smartpost.system.enums.Environment;
import com.fedex.smartpost.system.utilities.JmsMessageUtility;

public class PurgeJMSMessages {
	@Test
	public void testJmsFix() {
		BufferedWriter bw;
		BufferedWriter bwxml;
		String filename;
		Message message;
		boolean reiterate = true;
		Calendar cal;
		long counter = 0;
		SimpleDateFormat sdf = new SimpleDateFormat("MM-dd-yyyy");
		JmsMessageExtractor jmsMessageExtractor = new JmsMessageExtractor("VS.SMARTPOST.FXSP.CL2.MDB",
																		  "FXSPSHIP.FXSP.MOVEMENT.SPEEDS",
																		  Environment.PROD);
		cal = Calendar.getInstance();
		try {
			filename = "/JMSMessages-" + sdf.format(cal.getTime()) + ".txt";
			bw = new BufferedWriter(new FileWriter(filename,true));
			filename = "/JMSMessages-" + sdf.format(cal.getTime()) + ".xml";
			bwxml = new BufferedWriter(new FileWriter(filename,true));
			while (reiterate) {
				message = jmsMessageExtractor.getMessage();
				if (message != null) {
					if ((++counter % 100) == 0) {
						System.out.println("Read " + counter + " records so far.");
					}
					bw.write(message + "\n");
					bwxml.write(JmsMessageUtility.convertBytesMessageToText((BytesMessage) message) + "\n");
				}
				else {
					System.out.println("Read " + counter + " records total.");
					reiterate = false;
					jmsMessageExtractor.closeAll();
				}
			}
			bw.flush();
			bw.close();
			bwxml.flush();
			bwxml.close();
		}
		catch (IOException ioe) {
			System.out.println(ioe.getMessage());
		}
	}
}
