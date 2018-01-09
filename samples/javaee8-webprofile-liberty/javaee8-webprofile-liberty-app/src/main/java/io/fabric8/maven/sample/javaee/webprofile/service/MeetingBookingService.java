package io.fabric8.maven.sample.javaee.webprofile.service;

import static javax.ws.rs.core.MediaType.TEXT_HTML;

import java.text.MessageFormat;

import javax.enterprise.context.ApplicationScoped;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;

@Path("/")
@ApplicationScoped
public class MeetingBookingService {
	
	private static final String BANNER = "Microservice Meeting Room Booking API Application";
	
	@GET
	@Path("/")
	@Produces(TEXT_HTML)
	public String info() {
		return BANNER;
	}
}
