package controller;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import model.Invoice;
import model.InvoiceParams;
import model.PayoutRequest;
import model.Rates;

import org.json.JSONException;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;
import org.json.simple.parser.JSONParser;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.ParseException;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;
import com.google.bitcoin.core.ECKey;

/**
 * 
 * @author Chaz Ferguson
 * @date 11.11.2013
 * 
 * Wrapper for BitPay's *new* BitAuth API.
 * 
 * In order to authenticate with the new API, you must generate a
 * public/private key pair using ECDSA curve secp256k1 and derive 
 * your SIN. The SIN is defined as the base58 check representation 
 * of: 	0x0F + 0x02 + RIPEMD-160(SHA-256(public key)), and using
 * the submiyKey method, then approve your key at 
 * bitpay.com/key-manager.
 * 
 * See bitpay.com/api for more information.
 */
public class BitPay {
	
	private String baseUrl;
	
	private HttpClient client;
	ECKey privateKey;
	long nonce;
	String SIN;
	
	public BitPay(){
		this.baseUrl = "https://test.bitpay.com/";
		this.nonce = new Date().getTime();
		if(KeyUtils.privateKeyExists()){
			try {
				this.privateKey = KeyUtils.readExistingKey();
			} catch (IOException e) {
				e.printStackTrace();
			}
		} else {
			this.privateKey = KeyUtils.generateNewECKey();
			try {
				KeyUtils.saveECKey(this.privateKey);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		this.SIN = KeyUtils.deriveSIN(this.privateKey);
		client = HttpClientBuilder.create().build();
	}

	public BitPay(String accountEmail, String deviceLabel) {
		this();
		this.submitKey(accountEmail, deviceLabel);
	}
	
	public BitPay(ECKey privateKey, String SIN) {
		this.baseUrl = "https://test.bitpay.com/";
		this.SIN = SIN;
		this.nonce = new Date().getTime();
		this.privateKey = privateKey;
		client = HttpClientBuilder.create().build();
	}
	
	public BitPay(ECKey privateKey, String SIN, String envUrl) {
		this.baseUrl = envUrl;
		this.SIN = SIN;
		this.nonce = new Date().getTime();
		this.privateKey = privateKey;
		client = HttpClientBuilder.create().build();
	}
	
	public String getSIN() {
		return this.SIN;
	}
	
	public JSONObject submitKey(String accountEmail, String label) {
		List<NameValuePair> params = this.getParams(accountEmail, this.SIN, label);
		String url = baseUrl + "keys";
		HttpResponse response = this.post(url, params, false);
		JSONObject obj = responseToObject(response);
		System.out.println(obj);
		return obj;
	}

	public JSONObject getKeys() {
		List<NameValuePair> params = this.getParams();
		String url = baseUrl + "keys";
		HttpResponse response = this.get(url, params);
		return responseToObject(response);
	}
	
	public JSONObject getTokens() {
		List<NameValuePair> params = this.getParams();
		String url = baseUrl + "tokens";
		HttpResponse response = this.get(url, params);
		JSONObject obj = responseToObject(response);
		System.out.println("Tokens: " +obj);
		return obj;
	}
	
	public String getToken(JSONObject dataObject, String key) {
		if (!dataObject.containsKey("data")){
			System.out.println("Object does not contain data array.");
			return null;
		}
		String tokenValue = "";
		JSONArray tokens = (JSONArray)dataObject.get("data");
		for(Object obj : tokens) {
			JSONObject token = (JSONObject)obj;
			if(token.containsKey(key)){
				tokenValue = (String)token.get(key);
			}
		}
		return tokenValue;
	}
	
	public Invoice createInvoice(double price, String currency) {
		if(currency.length() > 3) {
			throw new IllegalArgumentException("Must be a valid currency code");
		}
		
		String url = baseUrl + "invoices";
		List<NameValuePair> params = this.getParams(price, currency);
		params.add(new BasicNameValuePair("token", this.getToken(this.getTokens(), "merchant")));
		HttpResponse response = this.post(url, params, true);
		JSONObject obj = responseToObject(response);
		try {
			JSONObject invoiceData = (JSONObject)obj.get("data");
			return new Invoice(invoiceData);
		} catch (JSONException e) {
			e.printStackTrace();
		}
		return null;
	}
	
	public Invoice createInvoice(double price, String currency, InvoiceParams optionalParams) {
		if(currency.length() > 3) {
			throw new IllegalArgumentException("Must be a valid currency code");
		}
		
		String url = baseUrl + "invoices";
		List<NameValuePair> params = this.getParams(price, currency, optionalParams);
		params.add(new BasicNameValuePair("token", this.getToken(this.getTokens(), "merchant")));
		HttpResponse response = this.post(url, params, true);
		JSONObject obj = responseToObject(response);
		try {
			JSONObject invoiceData = (JSONObject)obj.get("data");
			return new Invoice(invoiceData);
		} catch (JSONException e) {
			e.printStackTrace();
		}
		return null;
	}
	

	public Invoice getInvoice(String invoiceId) {
		String url = baseUrl + "invoices/" + invoiceId;
		List<NameValuePair> params = this.getParams();
		params.add(new BasicNameValuePair("token", this.getToken(this.getTokens(), "merchant")));
		HttpResponse response = this.get(url, params);
		JSONObject obj = responseToObject(response);
		try {
			if(obj.containsKey("error")){
				System.out.println(obj.get("error"));
				return null;
			}
			JSONObject invoiceData = (JSONObject)obj.get("data");
			return new Invoice(invoiceData);
		} catch (JSONException e) {
			e.printStackTrace();
		}
		return null;
	}
	
	public List<Invoice> getInvoices(String javascriptDateString) {
		String url = baseUrl + "invoices/";
		List<NameValuePair> params = this.getParams();
		params.add(new BasicNameValuePair("dateStart", javascriptDateString));
		params.add(new BasicNameValuePair("token", this.getToken(this.getTokens(), "merchant")));
		HttpResponse response = this.get(url, params);
		JSONObject obj = responseToObject(response);
		try {
			List<Invoice> invoices = new ArrayList<Invoice>();
			JSONArray invoiceObjs = (JSONArray)obj.get("data");
			for(Object invoiceObj : invoiceObjs){
				invoices.add(new Invoice((JSONObject)invoiceObj));
			}
			return invoices;
		} catch (JSONException e) {
			e.printStackTrace();
		}
		return null;
	}
	
	public JSONObject submitPayoutRequest(PayoutRequest payoutRequest) {
		String url = baseUrl + "payouts";
		List<NameValuePair> params = this.getParams();
		params.add(new BasicNameValuePair("token", this.getToken(this.getTokens(), "payroll")));
		HttpResponse response = this.post(url, params, true);
		System.out.println(responseToObject(response));
		return responseToObject(response);
	}
	
	public Rates getRates() {
		String url = baseUrl + "rates";
		
		HttpGet get = new HttpGet(url);
		
		try {
			HttpResponse response = client.execute(get);
			JSONObject ratesObj = responseToObject(response);
	        return new Rates((JSONArray)ratesObj.get("data"), this);

		} catch (ClientProtocolException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		return null;
	}
	
	private String signData(String url) {
		return KeyUtils.signString(privateKey, url);
	}

	
	private String signData(String url, List<NameValuePair> body) {
		try {
			JSONObject json = (JSONObject)new JSONParser().parse(toJsonString(body));
			String data = url + json.toString();
			return KeyUtils.signString(privateKey, data);
		} catch (org.json.simple.parser.ParseException e) {
			e.printStackTrace();
		}
		return null;
	}

	private String toJsonString(List<NameValuePair> body) {
		String data = "{";
		for(int i = 0;i<body.size();i++){
			data += '"' + body.get(i).getName() + "\":\"" + body.get(i).getValue() + "\",";
		}
		data = data.substring(0,data.length() - 1);
		data += "}";
		return data;
	}

	private List<NameValuePair> getParams() {
		List<NameValuePair> params = new ArrayList<NameValuePair>(2);
		params.add(new BasicNameValuePair("nonce", this.nonce + ""));
		this.nonce++;
		return params;
	}
	
	private List<NameValuePair> getParams(String email, String sin, String label) {
		List<NameValuePair> params = new ArrayList<NameValuePair>(2);
		params.add(new BasicNameValuePair("nonce", this.nonce + ""));
		params.add(new BasicNameValuePair("sin", sin));
		params.add(new BasicNameValuePair("email", email));
		params.add(new BasicNameValuePair("label", label));
		this.nonce++;
		return params;
	}
	
	private List<NameValuePair> getParams(double price,
			String currency) {
		List<NameValuePair> params = new ArrayList<NameValuePair>(2);
		params.add(new BasicNameValuePair("price", price + ""));
		params.add(new BasicNameValuePair("currency", currency));
		params.add(new BasicNameValuePair("nonce", this.nonce + ""));
		this.nonce++;
		return params;
	}
	
	private List<NameValuePair> getParams(double price,
			String currency, InvoiceParams optionalParams) {
		List<NameValuePair> params = new ArrayList<NameValuePair>(2);
		for (BasicNameValuePair param : optionalParams.getNameValuePairs()) {
			params.add(param);
		}
		params.add(new BasicNameValuePair("price", price + ""));
		params.add(new BasicNameValuePair("currency", currency));
		params.add(new BasicNameValuePair("nonce", this.nonce + ""));
		this.nonce++;
		return params;
	}

	private HttpResponse post(String url, List<NameValuePair> params, Boolean requiresSignature) {
		try {
			params.add(new BasicNameValuePair("guid", this.getGuid()));
			JSONObject json = (JSONObject)new JSONParser().parse(toJsonString(params));
			HttpPost post = new HttpPost(url);
		    post.setEntity(new ByteArrayEntity(json.toString().getBytes("UTF8")));
		    if (requiresSignature) {
		    	String signature = signData(url, params);
		    	post.addHeader("x-signature", signature);
		    	post.addHeader("x-pubkey", KeyUtils.bytesToHex(privateKey.getPubKey()));
		    }
			post.addHeader("X-BitPay-Plugin-Info", "Javalib0.1.0");
			
			post.addHeader("Content-Type","application/json");
			
			HttpResponse response = this.client.execute(post);
	        return response;
	        
		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
		} catch (ClientProtocolException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		} catch (org.json.simple.parser.ParseException e) {
			e.printStackTrace();
		}
		System.out.println("Could not get a POST response from the server.");
		return null;
	}

	private HttpResponse get(String url, List<NameValuePair> params) {
		String fullURL = url + "?";
		for(int i = 0;i<params.size();i++){
			fullURL += params.get(i).getName() + "=" + params.get(i).getValue() + "&";
		}
		fullURL = fullURL.substring(0,fullURL.length() - 1);
		System.out.println(fullURL);
		try {
			HttpGet get = new HttpGet(fullURL);
			
			String sig = signData(fullURL);
			get.addHeader("X-BitPay-Plugin-Info", "Javalib0.1.0");
			get.addHeader("x-signature", sig);
			get.addHeader("x-pubkey", KeyUtils.bytesToHex(privateKey.getPubKey()));
			HttpResponse response = client.execute(get);
	        return response;
		} catch (ClientProtocolException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		System.out.println("Could not get a GET response from the server.");
		return null;
	}
	
	private JSONObject responseToObject(HttpResponse response) {
		HttpEntity entity = response.getEntity();
		try {
			String responseString = EntityUtils.toString(entity, "UTF-8");
			Object obj=JSONValue.parse(responseString);
			JSONObject finalResult = (JSONObject)obj;
			if(finalResult.containsKey("error")) {
				System.out.println("Error: " + finalResult.get("error"));
			}
			return finalResult;
		} catch (ParseException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		System.out.println("Could not create a JSONObject from the HttpResponse.");
		return null;
	}

	private String getGuid(){
		int Min = 0;
		int Max = 99999999;
		return Min + (int)(Math.random() * ((Max - Min) + 1)) + "";
	}
	
}
