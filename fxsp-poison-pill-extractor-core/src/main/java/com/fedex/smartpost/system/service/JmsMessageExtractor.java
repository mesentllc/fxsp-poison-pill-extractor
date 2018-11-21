package com.fedex.smartpost.system.service;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Map;
import java.util.TreeMap;

import javax.jms.BytesMessage;
import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.Queue;
import javax.jms.Session;
import javax.jms.TextMessage;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.joda.time.DateTime;

import com.fedex.smartpost.system.enums.Environment;
import com.fedex.smartpost.system.model.MessageModel;
import com.fedex.smartpost.system.utilities.JmsMessageUtility;

public class JmsMessageExtractor {
	private static final Log log = LogFactory.getLog(JmsMessageExtractor.class);
	private static final String PROD_JMS_URL = "ldap://appldap.prod.fedex.com/ou=messaging,dc=prod,dc=fedex,dc=com";
	private static final String DEV_JMS_URL = "ldap://apptstldap.corp.fedex.com/ou=messaging,dc=corp,dc=fedex,dc=com";
	private static final String CF_PREFIX = "fxClientUID=";
	private static final String Q_PREFIX = "fxClientDestinationUID=";
	public static final String ROOT_PATH = "/meta/";
	private Hashtable<String, Object> env = new Hashtable<String, Object>();
	private Context ctx;
	private Connection connection;
	private Session session;
	private MessageConsumer messageConsumer;

	JmsMessageExtractor(String cfString, String qString, Environment environment) {
		String selector = null;
		ConnectionFactory connectionFactory;
		Queue queue;

		cfString = cfString.trim();
		qString = qString.trim();
		if (!cfString.startsWith(CF_PREFIX)) {
			cfString = CF_PREFIX + cfString;
		}
    	if ((environment != null) && StringUtils.isNotBlank(environment.toString()) && !cfString.endsWith(environment.toString()))  {
    		cfString += "." + environment;
    	}
/*
    	if (!qString.startsWith("D.") && !qString.startsWith(Q_PREFIX + "D.")) {
    		qString = "D." + qString;
    	}
*/
    	if (!qString.startsWith(Q_PREFIX)) {
    		qString = Q_PREFIX + qString;
    	}
//		selector = "EventType = 'OVERLABEL'";
		log.info("Attempting to read CF: " + cfString + ", Q: " + qString);
		env.put(Context.INITIAL_CONTEXT_FACTORY,"com.fedex.mi.decorator.jms.FedexTibcoInitialContext");
		if (environment == Environment.PROD) {
			env.put(Context.PROVIDER_URL,PROD_JMS_URL);
		}
		else {
			env.put(Context.PROVIDER_URL,DEV_JMS_URL);
		}
		// Create the initial context
		try {
			ctx = new InitialContext(env);
			connectionFactory = (ConnectionFactory)ctx.lookup(cfString);
			queue = (Queue)ctx.lookup(qString);
			connection = connectionFactory.createConnection();
			// Establish a session - not transacted, Client Acknowledge - so we have control over the
			// acknowledging of messages
			session = connection.createSession(false, Session.CLIENT_ACKNOWLEDGE);
			messageConsumer = session.createConsumer(queue, selector);
			connection.start();
		}
		catch (Exception e) {
			log.error("Exception caught:", e);
		}
	}

	public static Map<String, Object> getJMSHeaders(Message message) throws JMSException {
		Map<String, Object> headers = new HashMap<String, Object>();
		headers.put("JMSCorrelationID", message.getJMSCorrelationID());
		headers.put("JMSDestination", message.getJMSDestination());
		headers.put("JMSExpiration", message.getJMSExpiration());
		headers.put("JMSMessageID", message.getJMSMessageID());
		headers.put("JMSTimestamp", new DateTime(message.getJMSTimestamp()));
		return headers;
	}

	private HashMap<String, Object> getMessageProperties(Message msg) throws JMSException {
		HashMap<String, Object> properties = new HashMap<String, Object>();
		Enumeration enumeration = msg.getPropertyNames();
		while (enumeration.hasMoreElements()) {
			String propertyName = (String)enumeration.nextElement();
			properties.put(propertyName, msg.getObjectProperty(propertyName));
		}
		return properties;
	}

	private MessageModel readQueue() {
		MessageModel messageModel = null;
		String messageText = null;
		Message m;

		// Wait for 1 minute (max) to see if there are any messages
		try {
			if (messageConsumer != null) {
				m = messageConsumer.receive(60000);
				if (m != null) {
					if (m instanceof TextMessage) {
						messageText = ((TextMessage)m).getText();
					}
					if (m instanceof BytesMessage) {
						messageText = JmsMessageUtility.convertBytesMessageToText((BytesMessage)m);
					}
					messageModel = new MessageModel(getJMSHeaders(m), getMessageProperties(m), messageText);
					// Now that we retrieved a valid message, we can acknowledge it.
					m.acknowledge();
				}
			}
		}
		catch (JMSException e) {
			log.error("Captured a JMS exception.", e);
		}
		return messageModel;
	}

	Message getMessage() {
		Message m = null;

		// Wait for 15 seconds (max) to see if there are any messages
		try {
			if (messageConsumer != null) {
				m = messageConsumer.receive(15000);
				if (m != null) {
					m.acknowledge();
				}
			}
		}
		catch (JMSException e) {
			log.error("Captured a JMS exception.", e);
		}
		return m;
	}

	protected void closeAll() {
		try {
			session.close();
			connection.close();
			ctx.close();
		}
		catch (JMSException e) {
			log.error("Captured a JMS exception.", e);
		}
		catch (NamingException e) {
			log.error("Captured a Naming exception.", e);
		}
	}

	private static String dumpProperties(MessageModel messageModel) {
		StringBuilder sb = new StringBuilder();

		sb.append(dumpMap(messageModel.getJmsHeaderFields())).append("\r\n");
		sb.append(dumpMap(messageModel.getProperties()));
		return sb.toString();
	}

	private static String dumpMap(Map<String, Object> map) {
		StringBuilder sb = new StringBuilder();

		if (map == null) {
			return "";
		}
		for (String key : map.keySet()) {
			sb.append("[Key: ").append(key).append("] [Value: ").append(map.get(key)).append("]\r\n");
		}
		return sb.toString();
	}

	private String setupDirectory() throws IOException {
		SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");

		String dirName = ROOT_PATH + sdf.format(new Date());
		File checkDir = new File(dirName);
		if (!checkDir.exists()) {
			if (!checkDir.mkdirs()) {
				throw new IOException("Could not create the " + checkDir + " directory.");
			}
		}
		return dirName;
	}

	private String getEventType(String xml) {
		try {
			return xml.substring(xml.indexOf("<eventName>") + 11, xml.indexOf("</eventName>"));
		}
		catch (Exception e) {
			return null;
		}
	}

	private Integer getIndexNumber(Map<String, Integer> msgType, String eventType) {
		if (msgType.containsKey(eventType)) {
			return msgType.get(eventType);
		}
		else {
			return 0;
		}
	}

	private void writeToFile(MessageModel messageModel, String dirName, String xml, String eventType, Integer count) throws IOException {
		BufferedWriter bw = new BufferedWriter(new FileWriter(dirName + "/" + eventType +
															  String.format("-%d.xml", count)));
		bw.write(dumpProperties(messageModel));
		bw.write("\r\n");
		bw.write(xml);
		bw.close();
	}

	private void process(JmsMessageExtractor extractor) throws IOException {
		Map<String, Integer> msgType = new TreeMap<String, Integer>();

//		while (messageModel != null) {
		while (true) {
//			String dirName = setupDirectory();
			MessageModel messageModel = extractor.readQueue();
			if (messageModel == null) {
				continue;
			}
			String xml = messageModel.getXml();
			String eventType = getEventType(xml);
			if (eventType != null) {
				Integer count = getIndexNumber(msgType, eventType);
//				writeToFile(messageModel, dirName, xml, eventType, ++count);
				count++;
				msgType.put(eventType, count);
				log.info(eventType + " Record found.");
			}
		}
	}

	public static void main(String[] args) throws IOException {
		JmsMessageExtractor extractor = new JmsMessageExtractor("VS.FXSPIPC.EVS.PARCEL.POSTAGE.QCF.L3",
																"D.FXSPIPC.EVS.PARCEL.POSTAGE.POSTAL_4717",
																Environment.L3);
		extractor.process(extractor);
	}
}