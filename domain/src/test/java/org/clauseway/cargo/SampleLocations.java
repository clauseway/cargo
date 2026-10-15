package org.clauseway.cargo;

// ABOUTME: dddsample's sample locations, the ports its tests and in-memory
// ABOUTME: repository know: declared into a world as location facts.

final class SampleLocations {

	private SampleLocations() {
	}

	static Shipping declaredIn(Shipping s) {
		return s
				.withLocation("CNHKG", "Hongkong")
				.withLocation("AUMEL", "Melbourne")
				.withLocation("SESTO", "Stockholm")
				.withLocation("FIHEL", "Helsinki")
				.withLocation("USCHI", "Chicago")
				.withLocation("JNTKO", "Tokyo")
				.withLocation("DEHAM", "Hamburg")
				.withLocation("CNSHA", "Shanghai")
				.withLocation("NLRTM", "Rotterdam")
				.withLocation("SEGOT", "Gothenburg")
				.withLocation("CNHGH", "Hangzhou")
				.withLocation("USNYC", "New York")
				.withLocation("USDAL", "Dallas");
	}
}
