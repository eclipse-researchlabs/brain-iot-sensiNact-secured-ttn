/*
 * Copyright (c)  2020 - 2021 Kentyou.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Kentyou - initial API and implementation
 */
package org.eclipse.sensinact.gateway.brainiot.ttn.event;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpStatus;
import org.apache.http.auth.AUTH;
import org.apache.http.auth.AuthenticationException;
import org.apache.http.auth.MalformedChallengeException;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.auth.DigestScheme;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.protocol.HttpContext;
import org.eclipse.sensinact.gateway.core.message.AgentRelay;
import org.eclipse.sensinact.gateway.core.message.whiteboard.AbstractAgentRelay;
import org.eclipse.sensinact.gateway.core.message.MidCallbackException;
import org.eclipse.sensinact.gateway.core.message.SnaUpdateMessageImpl;
import org.eclipse.sensinact.gateway.util.UriUtils;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;
import org.osgi.service.component.annotations.Deactivate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Agent listening for Ttn devices updates
 */
@Component(
	immediate=true,
	service={ TtnAgentCallback.class, AgentRelay.class },
	configurationPolicy = ConfigurationPolicy.REQUIRE, 
	configurationPid = {"org.eclipse.sensinact.brainiot.ttn.TtnAgentConfiguration"} )
public class TtnAgentCallback extends AbstractAgentRelay {

	private static final Logger LOG = LoggerFactory.getLogger(TtnAgentCallback.class);
	
	
	public static class TtnDevice {
		@JsonProperty(value="name")
		private String name;
		
		@JsonProperty(value="euid")
		private String euid;

		@JsonProperty(value="groupId")
		private int groupId;
		
		
		private long timestamp = 0;
		private double liters = -1;
		private double total = -1;
		private String battery = null;
		private Integer secured = null;

		public TtnDevice(){
		}

		public TtnDevice(String name, String euid, int groupId){
			this.name = name;
			this.euid = euid;
			this.groupId = groupId;
		}

		public void setName(String name) {
			this.name = name;
		}
		
		public String getName() {
			return this.name;
		}
		
		public void setEuid(String euid) {
			this.euid = euid;
		}
		
		public String getEuid() {
			return this.euid;
		}		
		
		public void setGroupId(int groupId) {
			this.groupId = groupId;
		}		
		
		boolean complete() {
			// Battery level is not present in each request sent by Ttn device
			return (secured!=null) && ((liters>-1 && total>-1)|| secured.intValue()==2); 
		}

		public void setListers(double liters) {
			this.liters = liters;
		}

		public void setTotalListers(double total) {
			this.total = total;
		}

		public void setBatteryLevel(String battery) {
			this.battery = battery;
		}
		
		public void setSecured(int secured) {
			this.secured = Integer.valueOf(secured);
		}

		public void setTimestamp(long timestamp) {
			this.timestamp = timestamp;
		}
		
		@Override
		public String toString() {
			if(timestamp == 0)
				timestamp = System.currentTimeMillis();
			Integer b = null;
			if(Objects.nonNull(battery)) {
				switch(battery) {
					case "LOW":b=10;
						break;
					case "MEDIUM":b=50;
						break;
					case "FULL":b=100;
						break;
				}
			}			
			String json = null;
			
			if(this.secured.intValue() == 2) {
				json = String.format("[{\"GroupId\": %s,\"ServerId\": 1,\"Date\": \"/Date(%s)/\",\"Values\": [null,null,null,2]}]",this.groupId, timestamp);
				this.secured = null;
			} else
				json = String.format("[{\"GroupId\": %s,\"ServerId\": 1,\"Date\": \"/Date(%s)/\",\"Values\": [%s,%s,%s,%s]}]", 
				this.groupId, timestamp, String.valueOf(liters), String.valueOf(total), String.valueOf(b), this.secured.intValue());
			
			this.liters = -1;
			this.total = -1;
			return json;
		}
	}
		
	private Map<String,TtnDevice> map = null;
	
	private DigestScheme digestAuth;
	private CloseableHttpClient client;

	private TtnAgentConfiguration config;
	
	/**
	 * Constructor
	 */
	public TtnAgentCallback() {
		super();
	}

	@Activate
	protected void activate(TtnAgentConfiguration config) {
		this.map = new HashMap<>();
		HttpClientBuilder clientbuilder = HttpClients.custom();
		this.client = clientbuilder.build();
		this.config = config;
		try {
			ObjectMapper mapper = new ObjectMapper();
			TtnDevice[] ttnDevices = mapper.readValue(Arrays.toString(this.config.devices()), TtnDevice[].class);
			for(TtnDevice ttnDevice:ttnDevices) 
				this.map.put(ttnDevice.getName(),ttnDevice);	
		} catch(Exception e) {
			LOG.error(e.getMessage(),e);
		}
	}	
	
	@Deactivate
	protected void deactivate() {
		if(this.client != null)
			try {
				this.client.close();
			} catch (IOException e) {
				LOG.error(e.getMessage(),e);
			}
		this.client = null;
		LOG.info("Ttn agent stopped");
	}	
	
	@Override
	public void doHandle(SnaUpdateMessageImpl message) throws MidCallbackException {
		System.out.println(message.getJSON());
		
		String path = message.getPath();
		String[] pathElements = UriUtils.getUriElements(path);
		String device = pathElements[0];
		String resource = pathElements[2];
		
		TtnDevice ttn = map.get(device);
		if(ttn == null) {
			LOG.error(String.format("Device '%s' not found",device));
			return;
		}
		ttn.setTimestamp(message.getNotification(long.class, "timestamp"));
		switch(resource) {
			case "liters":
				ttn.setListers(message.getNotification(double.class, "value"));
				break;
			case "total_liters":
				ttn.setTotalListers(message.getNotification(double.class, "value"));
				break;
			case "level_battery":
				ttn.setBatteryLevel(message.getNotification(String.class, "value"));
				break;
			case "secured":
				ttn.setSecured(message.getNotification(int.class, "value"));
				break;
			default:
				LOG.error(String.format("Resource '%s' not found",resource));
				return;
		}
		if(ttn.complete()) {
			HttpContext httpContext = new BasicHttpContext();
			CloseableHttpResponse response = null;
			try {
				HttpPut put = new HttpPut(this.config.medusaPutAddress());
				HttpEntity entity = new StringEntity(ttn.toString());
				put.setEntity(entity);
				put.setHeader(HttpHeaders.CONTENT_TYPE, "application/json");
				response = client.execute(put, httpContext);

				if (response.getStatusLine().getStatusCode() == HttpStatus.SC_UNAUTHORIZED) {
					Header authHeader = response.getFirstHeader(AUTH.WWW_AUTH);
					DigestScheme digestScheme = new DigestScheme();
					digestScheme.overrideParamter("realm", "DataWebService");
					digestScheme.processChallenge(authHeader);
					response.close();

					UsernamePasswordCredentials creds = new UsernamePasswordCredentials(this.config.medusaLogin(), this.config._medusaPassword());
					put.addHeader(digestScheme.authenticate(creds, put, httpContext));
					response = client.execute(put);
					int code = response.getStatusLine().getStatusCode();
					LOG.debug(String.format("%s [%s]",response,code));
				}
			} catch (IOException | MalformedChallengeException | AuthenticationException e) {
				LOG.error(e.getMessage(),e);
				e.printStackTrace();
			}
			finally {
				if (response != null) {
					try {
						response.close();
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
			}
		}
	}
	
	/**
	 * Returns the TtnDevice whose identifier is passed as parameter
	 * 
	 * @param euid the String identifier of the TtnDevice
	 * 
	 * @return the TtnDevice with the specified String identifier 
	 */
	public TtnDevice get(String euid) {
		for(TtnDevice ttnDevice : map.values()) {
			if(ttnDevice.getEuid().equals(euid))
				return ttnDevice;
		}
		return null;
	}
	

}
