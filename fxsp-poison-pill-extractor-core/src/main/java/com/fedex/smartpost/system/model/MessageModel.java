package com.fedex.smartpost.system.model;

import java.util.Map;

public class MessageModel {
	private Map<String, Object> properties;
	private Map<String, Object> jmsHeaderFields;
	private String xml;

	public MessageModel(Map<String, Object> jmsHeaderFields, Map<String, Object> properties, String xml) {
		this.jmsHeaderFields = jmsHeaderFields;
		this.properties = properties;
		this.xml = xml;
	}

	public Map<String, Object> getJmsHeaderFields() {
		return jmsHeaderFields;
	}

	public Map<String, Object> getProperties() {
		return properties;
	}

	public String getXml() {
		return xml;
	}
}
