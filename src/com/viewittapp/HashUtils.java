package com.viewittapp;

import java.io.UnsupportedEncodingException;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

import android.util.Log;

public class HashUtils {
	
	private static final String TAG = HashUtils.class.getSimpleName();

	public static String saltedHash(String input, String salt) {
		SecureRandom srandom = new SecureRandom();
		if (salt == null)
			salt = new BigInteger(130, srandom).toString(32);
		String salted_pass = salt + input;
		try {
			MessageDigest md = MessageDigest.getInstance("SHA-256");
			md.update(salted_pass.getBytes("UTF-8"));
			BigInteger salted_hash = new BigInteger(1, md.digest());
			return salted_hash.toString(32);
		} catch (NoSuchAlgorithmException e) {
			e.printStackTrace();
			Log.d(TAG, "Cannot hash string");
		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
			Log.d(TAG, "Cannot hash string");
		}
		return null;
	}

	public static String unsaltedHash(String input) {
		try {
			MessageDigest md = MessageDigest.getInstance("SHA-1");
			md.update(input.getBytes("UTF-8"));
			return bytesToHexString(md.digest());
		} catch (NoSuchAlgorithmException e) {
			e.printStackTrace();
			Log.d(TAG, "Cannot hash string");
		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
			Log.d(TAG, "Cannot hash string");
		}
		return null;
	}

	public static String randomHash() {
		SecureRandom srandom = new SecureRandom();
		String randomString = new BigInteger(256, srandom).toString(32);
		try {
			MessageDigest md = MessageDigest.getInstance("SHA-1");
			md.update(randomString.getBytes("UTF-8"));
			return bytesToHexString(md.digest());
		} catch (NoSuchAlgorithmException e) {
			e.printStackTrace();
			Log.d(TAG, "Cannot hash random string");
		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
			Log.d(TAG, "Cannot hash random string");
		}
		return null;
	}
	
	public static String bytesToHexString(byte[] bytes) {
		final char[] hexArray = "0123456789ABCDEF".toCharArray();
	    char[] hexChars = new char[bytes.length * 2];
	    for ( int j = 0; j < bytes.length; j++ ) {
	        int v = bytes[j] & 0xFF;
	        hexChars[j * 2] = hexArray[v >>> 4];
	        hexChars[j * 2 + 1] = hexArray[v & 0x0F];
	    }
	    return new String(hexChars);
	}

}
