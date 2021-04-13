/*
 * Copyright (c) 2020 - 2021 Kentyou.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
*    Kentyou - initial API and implementation
*/
package org.eclipse.sensinact.gateway.brainiot.ttn.decoder;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.eclipse.sensinact.gateway.sthbnd.ttn.listener.TtnUplinkListener;
import org.eclipse.sensinact.gateway.brainiot.ttn.event.TtnAgentCallback;
import org.eclipse.sensinact.gateway.brainiot.ttn.event.TtnAgentConfiguration;
import org.eclipse.sensinact.gateway.common.bundle.Mediator;
import org.eclipse.sensinact.gateway.common.execution.Executable;
import org.eclipse.sensinact.gateway.core.Core;
import org.eclipse.sensinact.gateway.sthbnd.ttn.packet.PayloadDecoder;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import eu.brain.iot.eventing.annotation.SmartBehaviourDefinition;
import eu.brain.iot.eventing.api.EventBus;
import eu.brain.iot.eventing.api.SmartBehaviour;
import fr.cea.brain.iot.security.smart.behaviour.api.DecodedMessageEvent;
import fr.cea.brain.iot.security.smart.behaviour.api.EncodedMessageEvent;

@SmartBehaviourDefinition(consumed = {DecodedMessageEvent.class}, filter="(timestamp=*)", name="Extended TTN Connector")
@Component(
	immediate=true,
	service = { SmartBehaviour.class, PayloadDecoder.class }, 
	property = {"app=brainIoT","profile=ttn"})
public class TtnPayloadDecoder implements PayloadDecoder, SmartBehaviour<DecodedMessageEvent>  {

	private static enum BATTERY {
		LOW,
		MEDIUM,
		FULL;
	}
	
    private static final Map<String, String> FIELDS;
    private static final Logger LOG = LoggerFactory.getLogger(TtnPayloadDecoder.class);

    static {
        FIELDS = new HashMap<>();
        FIELDS.put("75", "level_battery:1:1:msb");
        FIELDS.put("e5", "liters:2:1:lsb");
        FIELDS.put("e6", "total_liters:4:0.001:lsb");
    }    
    
	@Reference
    private TtnAgentCallback agent;
	
	private byte[] _decoded = null;
	private boolean deviceResponse = false;
	private Mediator mediator;
	private String registration;
	
	@Activate
	protected void activate(ComponentContext componentContext) {
		LOG.info("TtnPayloadDecoder Component ACTIVATED");
		this.mediator = new Mediator(componentContext.getBundleContext());
	}	
	
	@Deactivate
	protected void deactivate() {
		LOG.info("TtnPayloadDecoder Component DEACTIVATED");
	}	
	
    @Override
    public Map<String, Object> decodeRawPayload(String payload) {
        Map<String, Object> iofPayload = new HashMap<>();
        final EncodedMessageEvent event = new  EncodedMessageEvent();
        event.message = PayloadDecoder.Base64BinaryDecoder.parseBase64Binary(payload);
        this.mediator.callService(EventBus.class, new Executable<EventBus,Void>() {
			@Override
			public Void execute(EventBus eventBus) throws Exception {				
				eventBus.deliver(event);
				LOG.info("Event delivered :" + event);
				return null;
			}
		});        
        int wait=10000;
        byte[] decoded = null;
        while(wait > 0){
        	try {
        		if(this._decoded!=null) {
        			decoded = this._decoded;
        			this._decoded = null;
        			break;
        		}
				wait-=250;
				Thread.sleep(250);
			} catch (InterruptedException e) {
				Thread.interrupted();
				break;
			}        	
        }
        if(decoded == null) {
        	LOG.error("Encoded payload processing timeout");
        	return Collections.emptyMap();
    	}
        if(decoded.length == 0) {
        	StringBuilder euidBuilder = new StringBuilder();
        	for(int i=0;i<8;i++) 
        		euidBuilder.append(String.format("%02x", event.message[i]));
        	if(this.agent.get(euidBuilder.toString()) == null) 
        		iofPayload.putAll(decodePayload(event.message, false));
        	else 
        		iofPayload.put("secured",2);
        } else {
	        if(deviceResponse) {
	        	deviceResponse = false;
	        	iofPayload.put(TtnUplinkListener.DOWNLINK_MARKER, decoded);
	        } else
	        	iofPayload.putAll(decodePayload(decoded, true));
        }
        System.out.println(iofPayload);
        return iofPayload;
    }
    
    private static Map<String, Object> decodePayload(byte[] payload, boolean secured) {
        int i = 1;        
        Map<String, Object> map = new HashMap<>();        
        try {
            while (i < payload.length) {
                String feature = ByteConverter.byteToHex(payload[i]);
                if (FIELDS.get(feature) != null) {
	                LOG.debug("decoding feature: " + feature);	                
	                String[] values = FIELDS.get(feature).split(":");
	                int size = Integer.parseInt(values[1]);
	                byte[] bytes = new byte[size];
	                for (int j = 0; j < size; j++) 
	                    bytes[j] = payload[++i];
	                
	                double value =  ("lsb".equals(values[3])
	                ?ByteConverter.lsbUnsignedByteArrayToDouble(bytes):ByteConverter.unsignedByteArrayToDouble(bytes)) * Double.parseDouble(values[2]);
	                
	                if("level_battery".equals(values[0])) {
	                	if(value > 0 && value < 4) 
	                		map.put("level_battery", BATTERY.values()[((int)value)-1]);
	                } else 
	                	map.put(values[0],value);	   
	                
	                map.put("secured",secured?0:1);
                }
	            i += 2;
            }
        } catch (Exception ex) {
            LOG.error(ex.getMessage(),ex);
        }
        return map;
    }
    
	@Override
	public void notify(DecodedMessageEvent event) {
		if(event == null) {
			LOG.error("Null event");
			return;
		}
		if(event.valid) {
			if(event.device)
				deviceResponse = true;
			this._decoded = event.decoded;
		} else
			this._decoded = new byte[0];
	}
}
