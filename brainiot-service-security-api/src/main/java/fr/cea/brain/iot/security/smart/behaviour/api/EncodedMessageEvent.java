package fr.cea.brain.iot.security.smart.behaviour.api;

public class EncodedMessageEvent extends SecuredEvent {
	// fields must be public to respect DTO
	// https://osgi.org/specification/osgi.core/7.0.0/framework.dto.html
	public byte[] message;
}
