package com.zuznow;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.math.BigInteger;
import java.nio.charset.Charset;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Locale;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpServletResponseWrapper;


public class mobFilter implements Filter {

	private static class ByteArrayServletStream extends ServletOutputStream {

		ByteArrayOutputStream baos;

		ByteArrayServletStream(ByteArrayOutputStream baos) {
			this.baos = baos;
		}

		public void write(int param) throws IOException {
			baos.write(param);
		}		
	}	
	
	
	private static class MobServletResponseWrapper extends HttpServletResponseWrapper {

		private int httpStatus = 200;
		private StringWriter wrapperWriter;
		private ByteArrayOutputStream wrapperByteOutputStream;
		private ServletOutputStream wrapperOutputStream;
		private PrintWriter printWriter;
		private Boolean streamUsed = null;
		private Object lock = new Object();


		public MobServletResponseWrapper(HttpServletResponse response) {
			super(response);
			wrapperWriter = new StringWriter();
			wrapperByteOutputStream = new ByteArrayOutputStream();
			wrapperOutputStream = new ByteArrayServletStream(wrapperByteOutputStream);
			printWriter = new PrintWriter(wrapperWriter);
		}
		
		String dataEncoding = null;		
		public String getDataEncoding(){
			return dataEncoding;
		}
		
		@Override
		public void setCharacterEncoding(String arg0) {
			dataEncoding = arg0;
			super.setCharacterEncoding(arg0);
		}			

		@Override
		public void setContentType(String arg0) {
			super.setContentType(arg0);
			dataEncoding = getCharacterEncoding();
		}

		@Override
		public void setLocale(Locale arg0) {			
			super.setLocale(arg0);
			dataEncoding = getCharacterEncoding();
		}
		
		@Override
		public void sendError(int sc) throws IOException {
			httpStatus = sc;
			Thread.dumpStack();
			super.sendError(sc);
		}

		@Override
		public void sendError(int sc, String msg) throws IOException {
			httpStatus = sc;
			Thread.dumpStack();
			super.sendError(sc, msg);
		}

		@Override
		public void setStatus(int sc) {
			Thread.dumpStack();
			httpStatus = sc;
			super.setStatus(sc);
		}

	    public int getMyStatus() {
	    	
	        return httpStatus;
	    }

	    public boolean isStreamUsed() {
	    	if(streamUsed == null){
	    		return false;
	    	}
			return streamUsed;
		}

	    public StringBuffer getStringBufferData(){
	    	if(streamUsed){
	    		return null;
	    	}
	    	return wrapperWriter.getBuffer();
	    }
	    
	    public byte[] getByteArrayData() {
	    	if(!streamUsed){
	    		return null;
	    	}
			return wrapperByteOutputStream.toByteArray();
		}
	    
	    public PrintWriter getWriter() {
	    	synchronized(lock){
		    	if(streamUsed!= null && streamUsed){
		    		throw new IllegalStateException("Stream is used");
		    	}
		    	streamUsed = false;
	    	}
			return printWriter;
		}

		public ServletOutputStream getOutputStream() {
			synchronized(lock){
				if(streamUsed!= null && !streamUsed){
		    		throw new IllegalStateException("Writer is used");
		    	}
				streamUsed = true;
			}
			return wrapperOutputStream;
		}
		
	}

	private FilterConfig config;
	private String domainID = "";
	private String APIKey = "";
	private String originalDomain = "";
	private String cacheType = "none";
	private String charset_conf = "";
	private int cacheTTL = 30;
	private boolean cacheSSl = false;
	private ArrayList<String> apiServers;
	private String[] excludePaterns = null;

	private boolean useFilter(HttpServletRequest httpRequest,
			HttpServletResponse response) {
		String url = httpRequest.getRequestURL().toString();
		String query = httpRequest.getQueryString();
		if(query != null && !query.isEmpty()){
			url = url + "?" + query;
		}	
				
		if(excludePaterns !=null && excludePaterns.length >0)
		{
			String urlStr = url.toString().toLowerCase();
			for (int i = 0; i < excludePaterns.length; i++) {
				if (urlStr.indexOf(excludePaterns[i]) != -1) {
					return false;
				}
			}
		}
		
		if(httpRequest.getRequestURI().matches(".*.png|.*.gif|.*.jpg|.*.css|.*.js") )
		{
			return false;
		}
						
		String userAgent = httpRequest.getHeader("User-Agent");
		boolean isSupported = false;
		if (userAgent.matches("(?i:.*iPhone.*)|(?i:.*Android.*Mobile.*)")) {
			isSupported = true;
		}
		if (httpRequest.getParameter("mobtest") !=null && httpRequest.getParameter("mobtest").equals("true")) {
			Cookie cookie = new Cookie("mobtest", "true");
			cookie.setPath("/");
			response.addCookie(cookie);
			isSupported = true;
		} else if (httpRequest.getParameter("mobtest") !=null &&  httpRequest.getParameter("mobtest").equals("false")) {
			Cookie cookie = new Cookie("mobtest", "false");
			cookie.setPath("/");
			response.addCookie(cookie);
			isSupported = false;
		}
		if(getCookieVal(httpRequest,"mobtest") != null && getCookieVal(httpRequest,"mobtest").equals("true") )
		{
			isSupported = true;
		}
		if(getCookieVal(httpRequest,"mobtest") != null && getCookieVal(httpRequest,"mobtest").equals("false") )
		{
			isSupported = false;
		}
		if (!isSupported) {
			return false;
		}
		

		return isSupported;

	}

	public void init(FilterConfig filterConfig) throws ServletException {

		this.config = filterConfig;

		this.domainID = config.getInitParameter("domainID");
		this.APIKey = config.getInitParameter("APIKey");
		this.originalDomain = config.getInitParameter("originalDomain");
		if (config.getInitParameter("originalDomain") != null
				&& !config.getInitParameter("originalDomain").isEmpty()) {
			this.originalDomain = config.getInitParameter("originalDomain");
		}
		if (config.getInitParameter("cacheType") != null
				&& !config.getInitParameter("cacheType").isEmpty()) {
			this.cacheType = config.getInitParameter("cacheType");
		}
		if (config.getInitParameter("charset") != null
				&& config.getInitParameter("charset").isEmpty()) {
			this.charset_conf = config.getInitParameter("charset");
		}
		if (config.getInitParameter("cacheTTL") != null
				&& !config.getInitParameter("cacheTTL").isEmpty()) {
			this.cacheTTL = Integer.parseInt(config
					.getInitParameter("cacheTTL"));
		}
		if (config.getInitParameter("apiServers") != null
				&& !config.getInitParameter("apiServers").isEmpty()) {
			
			apiServers = new ArrayList<String>(Arrays.asList(config.getInitParameter("apiServers").split(","))) ;
		}
		if (config.getInitParameter("exclude") != null
				&& !config.getInitParameter("exclude").isEmpty()) {
			
			String excludesString = config.getInitParameter("exclude");
			excludePaterns = excludesString.split(",");
			if(excludePaterns !=null && excludePaterns.length >0)
			{
				for (int i = 0; i < excludePaterns.length; i++) {
					excludePaterns[i] = excludePaterns[i].toLowerCase();
				}
			}
		}

	}
	private String getCookieVal(HttpServletRequest httpRequest , String cookie)
	{
		Cookie[] cookies = httpRequest.getCookies();
		if (cookies != null) {
			for (int i = 0; i < cookies.length; i++) {
				if (cookies[i].getName().equals(cookie)) {
					return  cookies[i].getValue();
				}
			}
		}
		return null;
	}

	private String MD5(String str)
	{
		try {
			MessageDigest md = MessageDigest.getInstance("MD5");
			md.update(str.getBytes());
			byte[] digest = md.digest();
			BigInteger bigInt = new BigInteger(1,digest);
			String hashtext = bigInt.toString(16);
			// Now we need to zero pad it if you actually want the full 32 chars.
			while(hashtext.length() < 32 ){
				hashtext = "0"+hashtext;
			}
			return hashtext;
		} catch (NoSuchAlgorithmException e) {
			return null;
		}
	}
	private String MD5(byte[] data)
	{
		try {
			MessageDigest md = MessageDigest.getInstance("MD5");
			md.update(data);
			byte[] digest = md.digest();
			BigInteger bigInt = new BigInteger(1,digest);
			String hashtext = bigInt.toString(16);
			// Now we need to zero pad it if you actually want the full 32 chars.
			while(hashtext.length() < 32 ){
				hashtext = "0"+hashtext;
			}
			return hashtext;
		} catch (NoSuchAlgorithmException e) {
			return null;
		}
	}

	private String getCacheKey(HttpServletRequest httpRequest,byte[] data)
	{
		if(cacheType.equals("none") || !cacheSSl && httpRequest.isSecure() )
		{
			return null;
		}
		if(cacheType.equals("anonymous") )
		{
			if(getCookieVal(httpRequest,"mob_login") != null && !getCookieVal(httpRequest,"mob_login").equals("true") )
			{
				StringBuffer url = httpRequest.getRequestURL();
				url.append(httpRequest.getQueryString());
				return MD5(url.toString());
			}
			else 
			{
				return null;
			}
		}
		else if(cacheType.equals("anonymous"))
		{
			return MD5(data);
		}
		return null;
	}

	public void doFilter(ServletRequest servletRequest,
			ServletResponse servletResponse, FilterChain filterChain)
					throws IOException, ServletException {
		
		String charset = charset_conf;

		final HttpServletRequest httpRequest = (HttpServletRequest) servletRequest;
		final HttpServletResponse response = (HttpServletResponse) servletResponse;
		long startTime = System.currentTimeMillis();
		if (!useFilter(httpRequest, response)) {
			filterChain.doFilter(servletRequest, servletResponse);
			return;
		}

		final MobServletResponseWrapper wrappedResp = new MobServletResponseWrapper(response);
		filterChain.doFilter(httpRequest, wrappedResp);
		
		
		try{
			if(!wrappedResp.isStreamUsed()){
				wrappedResp.getWriter().flush();
			}
			else{
				wrappedResp.getOutputStream().flush();
			}
		} catch (Exception e) {
			System.out.println("Zuznow error for flush");
		}
		
		if(!charset_conf.isEmpty()){
			charset = charset_conf;
		}
		else if(wrappedResp.getDataEncoding() != null){
			charset = wrappedResp.getDataEncoding();
		}
		else{
			//no encoding was set need to use the default
			charset = java.nio.charset.Charset.defaultCharset().name();
		}
		
		//String devcharset = java.nio.charset.Charset.defaultCharset().name();		
		StringBuffer dataStringBuffer = null;
		byte[] dataByte = null;
		if(!wrappedResp.isStreamUsed()){
			dataStringBuffer = wrappedResp.getStringBufferData();
		}
		else{
			dataByte = wrappedResp.getByteArrayData();
		}		
		
		wrappedResp.addHeader("X-Zuznow-Backend-Time", Double.toString((double)((System.currentTimeMillis() - startTime )/ 1000.0)));
		
		//check if no data	
		if((dataStringBuffer == null || dataStringBuffer.length() == 0) && (dataByte == null || dataByte.length == 0))
		{		
			return;
		}

		//check if the status is not 200 or content type should not mobilize -> return original data
		if(wrappedResp.getMyStatus() != 200 || wrappedResp.getContentType() != null && 
				(wrappedResp.getContentType().indexOf("text/html") == -1 && wrappedResp.getContentType().indexOf("text/plain") == -1)
				)
		{
			writeOriginalData(response, dataStringBuffer, dataByte);
			return;
		}
		
		StringBuffer url = httpRequest.getRequestURL();
		if(httpRequest.getQueryString() != null)
		{
			url.append("?");
			url.append(httpRequest.getQueryString());	
		}
		
		//System.out.println("url "+url + " charset " + charset);
		
		mobilezer mob = new mobilezer(domainID, APIKey, originalDomain, apiServers);
		boolean isAjax = false;
		if ((	httpRequest.getHeader("X-Requested-With") != null &&
				httpRequest.getHeader("X-Requested-With").equalsIgnoreCase("xmlhttprequest")) ||
				httpRequest.getHeader("X-MicrosoftAjax") != null
				) 
		{
			isAjax = true;
		}		
		
		String userAgent = httpRequest.getHeader("User-Agent");
		byte[] data = dataToByteArray(dataStringBuffer, dataByte, charset);
		String cacheKey = getCacheKey(httpRequest, data);
		mob.setCacheTTL(cacheTTL);
		mob.setCacheType(cacheType);
		if(getCookieVal(httpRequest,"mobtest") != null && getCookieVal(httpRequest,"mobtest").equals("true") )
		{
			mob.setMobtest(true);
		}
		
		//call mobilize API
		try {
			wrappedResp.addHeader("X-Zuznow-filert","On");
			byte[] mobData = mob.mobelize(url.toString(),userAgent, charset, data, isAjax, cacheKey);
			try {
				response.getOutputStream().write(mobData);
			} catch (IllegalStateException ex) {
				//response.setCharacterEncoding(charset);
				//response.getWriter().write(new String(mobData,charset));
				response.getWriter().write(new String(mobData, charset));
			}

		} catch (Exception e) {
			//error from API
			writeOriginalData(response, dataStringBuffer, dataByte);
			try {
			if(!isAjax && !wrappedResp.isStreamUsed()){
				response.getWriter().write("<!-error in mobilization API->");
			}
			} catch (Exception ex) {
				//TODO: add log
			}
			e.printStackTrace();
			
		}

	}

	private void writeOriginalData(final HttpServletResponse response,
			StringBuffer dataStringBuffer, byte[] dataByte) throws IOException {
		try {
			if(dataByte != null){
				response.getOutputStream().write(dataByte);
			}
			else if(dataStringBuffer != null){
				response.getWriter().write(dataStringBuffer.toString());
			}
		} catch (IllegalStateException ex) {
			response.getWriter().write(dataStringBuffer.toString());
		}
	}
	
	private byte[] dataToByteArray(StringBuffer dataStringBuffer, byte[] dataByte, String charset){
		if(dataByte != null){
			return dataByte;
		}
		else{
			return (dataStringBuffer.toString()).getBytes(Charset.forName(charset));
		}
	}

	public void destroy() {
	}

}