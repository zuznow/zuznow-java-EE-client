package com.zuznow;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.zip.GZIPOutputStream;

import com.zuznow.util.Base64;




public class mobilezer {

	private String domainID = "";
	private String APIKey = "";
	private String originalDomain = "";
	private String cacheType = "none";
	private int cacheTTL = 30;
	private boolean mobtest = false;
	private ArrayList<String> apiServers; 


	/**
	 * @param domainID domain ID
	 * @param APIKey API key 
	 * @param url the url for the request 
	 * @param originalDomain original host name, if the  @param url host name is different then original host
	 * the original host will be replaced with the new one 
	 * @param userAgent 
	 */
	public mobilezer(String domainID, String aPIKey, String originalDomain, ArrayList<String> apiServers) {
		this.domainID = domainID;
		APIKey = aPIKey;
		this.originalDomain = originalDomain;
		this.apiServers = apiServers;
	}

	public String getDomainID() {
		return domainID;
	}


	public void setDomainID(String domainID) {
		this.domainID = domainID;
	}

	public String getAPIKey() {
		return APIKey;
	}

	public void setAPIKey(String aPIKey) {
		APIKey = aPIKey;
	}

	public String getOriginalDomain() {
		return originalDomain;
	}

	public void setOriginalDomain(String originalDomain) {
		this.originalDomain = originalDomain;
	}


	public String getCacheType() {
		return cacheType;
	}

	public void setCacheType(String cacheType) {
		this.cacheType = cacheType;
	}

	public int getCacheTTL() {
		return cacheTTL;
	}

	public void setCacheTTL(int cacheTTL) {
		this.cacheTTL = cacheTTL;
	}



	private byte[] readFully(InputStream input) throws IOException
	{
		byte[] buffer = new byte[8192];
		int bytesRead;
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		while ((bytesRead = input.read(buffer)) != -1)
		{
			output.write(buffer, 0, bytesRead);
		}
		return output.toByteArray();
	}
	
	private byte[] readFullyAndAppend(InputStream input, String tail) throws IOException
	{
		byte[] buffer = new byte[8192];
		int bytesRead;
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		while ((bytesRead = input.read(buffer)) != -1)
		{
			output.write(buffer, 0, bytesRead);
		}
		output.write(tail.getBytes());
		return output.toByteArray();
	}
	
	private byte[] readFullyAndAppend(InputStream input, byte[] tail) throws IOException
	{
		byte[] buffer = new byte[8192];
		int bytesRead;
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		while ((bytesRead = input.read(buffer)) != -1)
		{
			output.write(buffer, 0, bytesRead);
		}
		output.write(tail);
		return output.toByteArray();
	}

	private String commentLine( String line) {
		//System.out.println(line);
		return "<!--"+line+"-->";

	}
	public byte[] mobelize(String url,String userAgent,String charset, byte[] data , boolean ajax , String cacheKey) throws Exception {


		long startTime = System.currentTimeMillis();
		LinkedHashMap<String,String> params = new LinkedHashMap<String,String>();
		StringBuilder postData = new StringBuilder();
		StringBuilder mobInfo = new StringBuilder();
		
		
		
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		GZIPOutputStream gzipOS;
		try {
			gzipOS = new GZIPOutputStream(bos);
			gzipOS.write(data);
			gzipOS.flush();
			gzipOS.finish();
			gzipOS.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}


		String base64Data = Base64.encodeToString(data, false);

		params.put("domain_id", domainID);
		params.put("key", APIKey);
		params.put("url", url);
		params.put("data", base64Data);
		params.put("charset", charset);		
		params.put("user_agent", userAgent);
		
		if(cacheKey != null &&  !cacheKey.isEmpty())
		{
			params.put("cache_key", cacheKey);
			params.put("cache_ttl",Integer.toString(cacheTTL));
		}
		if(ajax)
		{
			params.put("ajax", "true");
		}
		if(mobtest)
		{
			params.put("force", "true");
		}
		
		for(String key : params.keySet()  )
		{
			try {
				postData.append("&"+key+"="+URLEncoder.encode(params.get(key),"UTF-8"));
			} catch (UnsupportedEncodingException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		Collections.shuffle(apiServers);
		boolean sucess = false;
		int count = 0;
		int maxCount = 60;
		for (Iterator<String> it = apiServers.iterator(); it.hasNext();) {
			String server =  it.next();
			mobInfo.append(commentLine("request: "+server ));
			//	String serverUrl =  server +  "mobilize.php";
			try {
				URL serverUrl =new URL(server +  "mobilize.php");
				HttpURLConnection  conn = (HttpURLConnection )serverUrl.openConnection();
				OutputStreamWriter wr;
				conn.setDoOutput(true);
				conn.setRequestMethod("POST");
				conn.setInstanceFollowRedirects(false);
				conn.connect();
				wr = new OutputStreamWriter(conn.getOutputStream());
				wr.write(postData.toString());
				wr.flush();
				int responseCode = conn.getResponseCode();
				byte[] responseBody = null;
				if(responseCode == 200)
				{
					
					mobInfo.append(commentLine("Filter time: from chache" + (double)((System.currentTimeMillis() - startTime )/ 1000.0)  ));
					if(!ajax)
					{
						responseBody = readFullyAndAppend(conn.getInputStream(),mobInfo.toString());
					}
					else
					{
						responseBody = readFully(conn.getInputStream());
					}
					
					sucess = true;				
				}

				else if(responseCode == 302)
				{

					String loacation = conn.getHeaderField("Location");						
					URL dataUrl =  new URL( loacation+"&key="+APIKey+"&domain_id="+domainID+"&cache_ttl="+cacheTTL+"&user_agent="+userAgent);
					
					while( count < maxCount)
					{

						HttpURLConnection dataConn = (HttpURLConnection) dataUrl.openConnection();
						dataConn.setRequestMethod("GET");
						if(dataConn.getResponseCode() != 200 )
						{
					
							if(dataConn.getResponseCode() != 404)
							{
								try {
									BufferedReader rd = new BufferedReader(new InputStreamReader(dataConn.getErrorStream()));
									String line = null;
									StringBuilder errorData = new StringBuilder();
									while ((line = rd.readLine()) != null) {
										//System.out.println(line);
										errorData.append(line);
									}
								} catch (Exception e) {
									// TODO Auto-generated catch block
									e.printStackTrace();
								}
							}

						}
						else
						{
							mobInfo.append(commentLine("Filter time:" + (System.currentTimeMillis() - startTime )/1000.0  ));
							if(!ajax)
							{
								responseBody = readFullyAndAppend(dataConn.getInputStream(),mobInfo.toString());
							}
							else
							{
								responseBody = readFully(dataConn.getInputStream());
							}
							sucess = true;
							break;
						}
						try {
							Thread.sleep(250);
						} catch (InterruptedException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}
						count ++;
						mobInfo.append(commentLine("Response " + count+" "+  (System.currentTimeMillis() - startTime )/1000.0  ));
					}


				}
				else
				{
					  String error =  new String( readFully(conn.getErrorStream()));
					  System.out.println(error+"url: "+url);
					  //System.out.println(error);
				}
				if(sucess)
				{
					return responseBody;
				}
				if(count >= maxCount  )
				{
					break;
				}


			} catch (IOException e) {
				
			}


		}

		throw new Exception("mobilizer : error mobilizing request ");

	}

	public boolean isMobtest() {
		return mobtest;
	}

	public void setMobtest(boolean mobtest) {
		this.mobtest = mobtest;
	}


}
