package com.proxy.server.controller;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Enumeration;
import java.util.TimeZone;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.http.Header;
import org.apache.http.HttpStatus;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.methods.RequestBuilder;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
public class ProxyController {
	org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(ProxyController.class);
	String server = "localhost";
	String port   = "";
	
	public ProxyController(@Value("${server.port}") String port) {
		TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
		this.port = port;
		logger.info("Loaded Variables -> Port:" + this.port);
	}
	
	@RequestMapping("/**")
	public void proxyMain(HttpServletRequest request, HttpServletResponse response) {
		//Verify its not called it self - avoiding loop
		if (isCalledToItSelf(request,response))
			return;
		
		//Preparing proxy request from request data of old request
		HttpUriRequest proxiedRequest = prepareProxyRequest(request);
		
		//Executing proxy request with httpClient
		CloseableHttpClient httpClient = HttpClients.createDefault();
		
		HttpHeaders headers = new HttpHeaders();
		for(Header header : proxiedRequest.getAllHeaders()){
			headers.add(header.getName(), header.getValue());
	    }
		
		CloseableHttpResponse proxiedHttpResponse = null;
		try {
			proxiedHttpResponse = httpClient.execute(proxiedRequest);
		} catch (IOException e1) {
			e1.printStackTrace();
			logger.error(e1.getMessage());
			return;
		}
		
		//Returning response to old Request by Proxied Response - Using Out/In Streams
		if (proxiedHttpResponse != null)
			transferProxyResponse(proxiedHttpResponse, response);
	}
	
	private boolean isCalledToItSelf(HttpServletRequest request, HttpServletResponse response) {
		String url=request.getRequestURL().toString();
		if (url.startsWith("http://"+server+":"+port) || url.startsWith("http://127.0.0.1:"+port))
		{
			logger.warn("Proxy Called ItSelf: " + "URL Request: " + url + " Method:"+ request.getMethod());
			try {
				OutputStream os = response.getOutputStream();
				response.setStatus(HttpStatus.SC_OK);
				response.addHeader("Server", server+":"+port);
				response.addHeader("Content-type", "text/html");
				response.addHeader("Content-length", "37");
				response.addHeader("", "HTTP/1.1 200 OK");
				PrintWriter printwriter = new PrintWriter(os);
				printwriter.println("");
				printwriter.println("<br><br><h1>Welcome i am a PROXY!!");
				printwriter.println("");
				printwriter.flush();
				if (os != null)
					os.close();
			} catch (IOException e) {
				e.printStackTrace();
				logger.error(e.getMessage());
				return true;
			}
			return true;
		}
		else 
			return false;
	}
	
	private HttpUriRequest prepareProxyRequest(HttpServletRequest oldrequest)
	{
		String url = oldrequest.getRequestURL().toString() + (oldrequest.getQueryString() != null? ("?" + oldrequest.getQueryString()): "");
		URI uri = null;
		try {
			uri = new URI(url);
		} catch (URISyntaxException e1) {
			logger.error(e1.getMessage());
			e1.printStackTrace();
		}
		
	    RequestBuilder rb = RequestBuilder.create(oldrequest.getMethod());
	    rb.setUri(uri);
	    
	    //Request Header
		HttpHeaders headers = new HttpHeaders();
		Enumeration<String> headerNames = oldrequest.getHeaderNames();
		while (headerNames.hasMoreElements()) {
			String headerName = headerNames.nextElement();
			headers.set(headerName, oldrequest.getHeader(headerName));
			if (headerName.toLowerCase().equals("user-agent") || headerName.toLowerCase().equals("cookie") || headerName.toLowerCase().equals("referer"))
				rb.addHeader(headerName, oldrequest.getHeader(headerName));
		}
		//Request Body
	    Enumeration<String> parameterNames = oldrequest.getParameterNames() ;
	    HttpHeaders parameters = new HttpHeaders();
	    while (parameterNames.hasMoreElements()) {
			String parameterName = parameterNames.nextElement();
			rb.addParameter(parameterName, oldrequest.getParameter(parameterName));
			parameters.add(parameterName, oldrequest.getParameter(parameterName));
		}

		HttpUriRequest result = rb.build();
		logger.info("REQ. LINE: " + result.getRequestLine());
		logger.info("HEADERS IN : " + headers + " || PARAMS  : " + parameters);
	    return result;
	}
	
	private void transferProxyResponse(CloseableHttpResponse proxiedResponse, HttpServletResponse finalResponse){
		HttpHeaders headers = new HttpHeaders();
		for(Header header : proxiedResponse.getAllHeaders()) { 
			headers.add(header.getName(), header.getValue());
			if (!header.getName().toLowerCase().equals("content-type") &&
				!header.getName().toLowerCase().equals("content-length") &&
				!header.getName().toLowerCase().equals("transfer-encoding") &&
				!header.getValue().toLowerCase().equals("chunked"))
				finalResponse.addHeader(header.getName(), header.getValue()); 
		}
		//logger.info("HEADERS OUT: " + headers); 
		if (proxiedResponse.getEntity()==null) {
			logger.warn("No entity founded. ");
    		return;
		}
		OutputStream os = null;
	    InputStream is = null;
	    try {
	        is = proxiedResponse.getEntity().getContent();
	        os = finalResponse.getOutputStream();
	        is.transferTo(os);
	    } catch (IOException e) {
	    	logger.error(e.getMessage());
	        e.printStackTrace();
	    } finally {
	        if (os != null) {
	            try {
	                os.close();
	            } catch (IOException e) {
	            	logger.error(e.getMessage());
	                e.printStackTrace();
	            }
	        }
	        if (is != null) {
	            try {
	                is.close();
	            } catch (IOException e) {
	            	logger.error(e.getMessage());
	                e.printStackTrace();
	            }
	        }
	    }
	}

}
