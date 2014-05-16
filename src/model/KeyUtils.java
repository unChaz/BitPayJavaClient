package model;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import com.google.bitcoin.core.ECKey;

public class KeyUtils {
	
	public KeyUtils() {
		
	}
	
	public static ECKey loadKeys(String privateKey, String publicKey) {
		return new ECKey(privateKey.getBytes(), publicKey.getBytes());
	}
	
	public static String readKeyFromFile(String filename) {
		BufferedReader br;
	    try {
	    	br = new BufferedReader(new FileReader(filename));
	        String line = br.readLine();
	        br.close();
	        return line;
	    } catch (IOException e) {
			e.printStackTrace();
		}
	    return "";
	}
	
	public static String signString(ECKey key, String input) {
		return key.signMessage(input);
	}
}
