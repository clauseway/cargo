package org.clauseway.shipping;

// ABOUTME: Where the cargo physically is, read off its last handling: not yet
// ABOUTME: received, in a port, aboard a carrier, or claimed by the customer.

public enum TransportStatus {
	NOT_RECEIVED,
	IN_PORT,
	ONBOARD_CARRIER,
	CLAIMED
}
