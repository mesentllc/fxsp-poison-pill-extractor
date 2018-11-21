package com.fedex.smartpost.system.listeners;

import java.sql.Timestamp;

import javax.jms.BytesMessage;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageListener;
import javax.jms.TextMessage;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.fedex.smartpost.system.utilities.JmsMessageUtility;

public class PoisonPillListener implements MessageListener {
	private Log LOGGER = LogFactory.getLog(PoisonPillListener.class);

	@Override
	public void onMessage(Message message) {
		String messageText = "";
		StringBuilder sb = new StringBuilder();

		try {
			sb.append("[Redelivered: ").append(message.getJMSRedelivered()).append("] ")
              .append("[Message Id: ").append(message.getJMSMessageID()).append("] ")
			  .append("[Timestamp: ").append(new Timestamp(message.getJMSTimestamp())).append("] ")
			  .append("[Retry Attempts: ").append(message.getIntProperty("JMSXDeliveryCount"))
              .append("] ");
			if (message instanceof TextMessage) {
				messageText = ((TextMessage)message).getText();
			}
			if (message instanceof BytesMessage) {
				messageText = JmsMessageUtility.convertBytesMessageToText((BytesMessage)message);
			}
			LOGGER.info(sb + "Message: " + messageText);
		}
		catch (JMSException e) {
			LOGGER.error(e.getMessage());
		}
	}
}