package com.fedex.smartpost.system.utilities;

import java.nio.charset.Charset;

import javax.jms.BytesMessage;
import javax.jms.JMSException;

public class JmsMessageUtility {
	private static final Charset CHARSET = Charset.forName("UTF-8");
	private static final int LOW_MEM_BUFFER_SIZE = 1024;

	public static String convertBytesMessageToText(BytesMessage bytesMessage) {
		try {
			byte[] je = new byte[(int)bytesMessage.getBodyLength()];
			bytesMessage.readBytes(je);
			return new String(je);
		} catch (JMSException var2) {
			return null;
		}
	}
}
