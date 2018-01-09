package io.fabric8.maven.sample.javaee.webprofile.servlet;

import java.io.IOException;

import java.util.Properties;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@WebServlet("/demo")
public class DemoServlet extends HttpServlet {

	private static final long serialVersionUID = 3745576337037604707L;

	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
	    Properties prop = System.getProperties();            
		resp.getWriter().write("<h1>Welcome to Kubernetes!</h1>");
		resp.getWriter().write("<p><b>JVM Vendor:</b> " + prop.getProperty("java.vendor") + "</p>");
		resp.flushBuffer();
	}
}
