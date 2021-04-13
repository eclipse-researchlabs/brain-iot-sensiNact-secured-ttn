package fr.cea.brain.iot.security.smart.behaviour.api;

public class DecodedMessageEvent extends SecuredEvent {
	// fields must be public to respect DTO
	// https://osgi.org/specification/osgi.core/7.0.0/framework.dto.html
	public byte[] encoded;
	public byte[] decoded;
	public boolean secured;
	public boolean valid;
	public boolean device;

	public DecodedMessageEvent() {
		// Default constructor
	}
	
	public DecodedMessageEvent(byte[] encoded, byte[] decoded, boolean secured, boolean valid, boolean device) {
		this.encoded = encoded;
		this.decoded = decoded;
		this.secured = secured;
		this.valid = valid;
		this.device = device;
	}
}
