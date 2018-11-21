package com.fedex.smartpost.system.thread;

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
import javax.jms.Session;
import javax.jms.TextMessage;
import javax.jms.Topic;
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

public class JmsMessageTopicReaderThread implements Runnable {
	private static final Log log = LogFactory.getLog(JmsMessageTopicReaderThread.class);
	private static final String PROD_JMS_URL = "ldap://appldap.prod.fedex.com/ou=messaging,dc=prod,dc=fedex,dc=com";
	private static final String DEV_JMS_URL = "ldap://apptstldap.corp.fedex.com/ou=messaging,dc=corp,dc=fedex,dc=com";
	private static final String CF_PREFIX = "fxClientUID=";
	private static final String T_PREFIX = "fxClientDestinationUID=";
	private static final String ROOT_PATH = "f:/meta/special/";
	private static String threadName;
	private Hashtable<String, Object> env = new Hashtable<String, Object>();
	private Context ctx;
	private Connection connection;
	private Session session;
	private MessageConsumer messageConsumer;
	private String oldDir;
	private boolean alive = false;

	public JmsMessageTopicReaderThread(String threadName, String cfString, String tString, Environment environment) {
		this.threadName = threadName;
		String selector = null;
		ConnectionFactory connectionFactory;
		Topic topic;

		cfString = cfString.trim();
		tString = tString.trim();
		if (!cfString.startsWith(CF_PREFIX)) {
			cfString = CF_PREFIX + cfString;
		}
		if ((environment != null) && StringUtils.isNotBlank(environment.toString()) && !cfString.endsWith(environment.toString()))  {
			cfString += "." + environment;
		}
		if (!tString.startsWith("D.") && !tString.startsWith(T_PREFIX + "D.")) {
			tString = "D." + tString;
		}
		if (!tString.startsWith(T_PREFIX)) {
			tString = T_PREFIX + tString;
		}
		//		selector = "EventType = 'OVERLABEL'";
		log.info("Attempting to read CF: " + cfString + ", Q: " + tString);
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
			topic = (Topic)ctx.lookup(tString);
			connection = connectionFactory.createConnection();
			// Establish a session - not transacted, Client Acknowledge - so we have control over the
			// acknowledging of messages
			session = connection.createSession(false, Session.CLIENT_ACKNOWLEDGE);
			messageConsumer = session.createConsumer(topic, selector);
			connection.start();
		}
		catch (Exception e) {
			log.error("Exception caught:", e);
		}
	}

	private static Map<String, Object> getJMSHeaders(Message message) throws JMSException {
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

		String dirName = ROOT_PATH + sdf.format(new Date()) + "/topic/" + threadName;
		File checkDir = new File(dirName);
		if (!checkDir.exists()) {
			if (!checkDir.mkdirs()) {
				throw new IOException("Could not create the " + checkDir + " directory.");
			}
		}
		return dirName;
	}

	private String getElement(String tag, String xml) {
		String fullTag = "<" + tag + ">";
		try {
			return xml.substring(xml.indexOf(fullTag) + fullTag.length(), xml.indexOf("</" + tag + ">")).trim();
		}
		catch (Exception e) {
			return "";
		}
	}

	private Integer getIndexNumber(Map<String, Integer> map, String eventType) {
		if (map.containsKey(eventType)) {
			return map.get(eventType);
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

	private void dumpTypes(Map<String, Integer> msgType) {
		try {
			BufferedWriter bw = new BufferedWriter(new FileWriter("f:/meta/dump.txt", false));
			for (String key : msgType.keySet()) {
				bw.write(key + ": " + msgType.get(key) + "\r\n");
			}
			bw.close();
		}
		catch (IOException ioe) {
			log.error("Can't create dump.", ioe);
		}
	}

	@Override
	public void run() {
		Map<String, Integer> msgType = new TreeMap<String, Integer>();
		Map<String, Integer> fxspMap = new TreeMap<String, Integer>();
		alive = true;

		while (alive) {
			String dirName;
			try {
				dirName = setupDirectory();
			}
			catch (IOException e) {
				log.error("Exception Caught: ", e);
				alive = false;
				break;
			}
			if (!dirName.equals(oldDir)) {
				oldDir = dirName;
				msgType = new TreeMap<String, Integer>();
			}
			MessageModel messageModel = readQueue();
			if (messageModel == null) {
				continue;
			}
			String xml = messageModel.getXml();
			String eventType = getElement("eventName", xml);
			if (StringUtils.isNotEmpty(eventType)) {
				Integer count = getIndexNumber(msgType, eventType);
				++count;
				String eventDesc = getElement("eventDesc", xml);
				String custFileName = getElement("customerFileName", xml);
				if (eventDesc.contains("USPSSMARTPOST") ||
					"USPSSMARTPOST".equals(getElement("senderTP", xml)) || "USPSSMARTPOST".equals(getElement("receiverTP", xml)) ||
					"USPSPTSEVS".equals(getElement("senderTP", xml)) || "USPSPTSEVS".equals(getElement("receiverTP", xml)) ||
					xml.contains("EVS_2016"))	{
					Integer smartpost = getIndexNumber(fxspMap, eventType);
					smartpost++;
					if (smartpost < 1001) {
						try {
							writeToFile(messageModel, dirName, xml, eventType, smartpost);
						}
						catch (IOException e) {
							log.error("Exception Caught: ", e);
							alive = false;
							break;
						}
					}
					fxspMap.put(eventType, smartpost);
				}
				//				if (count < 1001) {
				//					writeToFile(messageModel, dirName, xml, eventType, count);
				//				}
				msgType.put(eventType, count);
				dumpTypes(msgType);
			}
		}
	}

	public boolean isAlive() {
		return alive;
	}
}
