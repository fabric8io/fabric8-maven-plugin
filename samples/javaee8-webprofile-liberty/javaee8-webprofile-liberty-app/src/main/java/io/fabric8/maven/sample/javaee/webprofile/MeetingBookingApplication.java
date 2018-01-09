package io.fabric8.maven.sample.javaee.webprofile;

import javax.annotation.PostConstruct;
import javax.ws.rs.ApplicationPath;
import javax.ws.rs.core.Application;

@ApplicationPath("/")
public class MeetingBookingApplication extends Application {
	
	@PostConstruct
	public void init() {
		System.out.println("---- Init Meeting Booking Application");
	}
}
