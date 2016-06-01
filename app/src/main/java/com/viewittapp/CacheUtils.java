package com.viewittapp;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Semaphore;

import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.BitmapFactory;
import android.util.Log;

/**
 * This class provides static methods for managing cached images.
 */
public class CacheUtils {

	/**
	 * Identifier tag for logging data within this class.
	 */
	private static final String TAG = CacheUtils.class.getSimpleName();

	/**
	 * Maximum number of bytes for buffering streams in/out of the cache (currently 128 KB).
	 */
	private static final int BUFFER_SIZE = 131072;

	/**
	 * Maximum width of bitmap images to pull from the cache. Any larger images will be under-sampled.
	 */
	private static final int MAX_WIDTH = 1920;

	/**
	 * Maximum height of bitmap images to pull from the cache. Any larger images will be under-sampled.
	 */
	private static final int MAX_HEIGHT = 1920;

	/**
	 * Time in mills for items in the cache to expire (currently 1 day).
	 */
	private static final int DEFAULT_EXPIRATION = 1000 * 60 * 60 * 24;

	/**
	 * File name for storing the cache index POJO.
	 */
	private static final String INDEX_NAME = "cache_index.pojo";

	/**
	 * Maps cache keys to an expiration date. Shared among thread pool.
	 * 
	 * ONLY ACCESS AFTER ACQUIRING MUTEX!
	 */
	private static final Map<String, Date> cacheIndex = loadIndex();

	/**
	 * Protects shared access to the cacheIndex.
	 */
	private static final Semaphore mutex = new Semaphore(1, true);

	/**
	 * Attempts to retrieve an image bitmap from the application cache.
	 * 
	 * @param key
	 *            String reference to cached image file
	 * @return Bitmap image from the cache, or null if file not found
	 */
	public static Bitmap getBitmapFromCache(String key) {
		File cacheFile = new File(ThisApp.getSingleton().getCacheDir(), key);
		if (cacheFile.exists()) {

			// check whether cache item has expired or not
			try {
				mutex.acquire();
				Date expireDate = cacheIndex.get(key);
				long expireTime = (expireDate == null) ? 0 : expireDate.getTime();
				mutex.release();
				if (new Date().getTime() > expireTime) {
					return null;
				}
			} catch (InterruptedException e) {
				e.printStackTrace();
			}

			// First decode with inJustDecodeBounds=true to check dimensions
			final BitmapFactory.Options options = new BitmapFactory.Options();
			options.inJustDecodeBounds = true;
			BitmapFactory.decodeFile(cacheFile.getAbsolutePath(), options);

			// Calculate inSampleSize
			options.inSampleSize = calculateInSampleSize(options.outWidth, options.outHeight, MAX_WIDTH, MAX_HEIGHT);

			// Decode bitmap with inSampleSize set
			options.inJustDecodeBounds = false;
			options.inPreferredConfig = Config.RGB_565;
			options.inDither = true;
			return BitmapFactory.decodeFile(cacheFile.getAbsolutePath(), options);

		} else {
			return null;
		}
	}

	/**
	 * Places an image bitmap into the application cache.
	 * 
	 * @param key
	 *            String reference to later retrieve the cached file
	 * @param image
	 *            Bitmap image to place into the cache
	 */
	public static void putBitmapIntoCache(String key, Bitmap image) {
		File cacheFile = new File(ThisApp.getSingleton().getCacheDir(), key);
		try {
			FileOutputStream cacheOut = new FileOutputStream(cacheFile);
			image.compress(Bitmap.CompressFormat.PNG, 100, cacheOut);
			cacheOut.flush();
			cacheOut.close();
			try {
				mutex.acquire();
				cacheIndex.put(key, new Date(new Date().getTime() + DEFAULT_EXPIRATION));
				mutex.release();
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	/**
	 * Places an image into the cache by streaming the data (prevents OutOfMemory errors).
	 * 
	 * @param key
	 *            String reference to later retrieve the cached file
	 * @param imageStream
	 *            InputStream for streaming the image into the cache
	 * @return the total number of bytes saved to the cache
	 */
	public static int streamImageIntoCache(String key, InputStream imageStream) {
		int totalBytes = 0;
		File cacheFile = new File(ThisApp.getSingleton().getCacheDir(), key);
		BufferedInputStream bis = new BufferedInputStream(imageStream, BUFFER_SIZE);
		try {
			FileOutputStream fos = new FileOutputStream(cacheFile);
			byte[] baf = new byte[BUFFER_SIZE];
			int bytesRead = 0;
			while (bytesRead != -1) {
				totalBytes += bytesRead;
				fos.write(baf, 0, bytesRead);
				bytesRead = bis.read(baf, 0, BUFFER_SIZE);
			}
			fos.close();
			try {
				mutex.acquire();
				cacheIndex.put(key, new Date(new Date().getTime() + DEFAULT_EXPIRATION));
				mutex.release();
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		return totalBytes;
	}

	/**
	 * Calculates an image scaling factor based on a required width and height.
	 * 
	 * @param width
	 *            The image's raw width
	 * @param height
	 *            The image's raw height
	 * @param reqWidth
	 *            Maximum width of the scaled image
	 * @param reqHeight
	 *            Maximum height of the scaled image
	 * @return Calculated scaling factor
	 */
	private static int calculateInSampleSize(int width, int height, int reqWidth, int reqHeight) {
		int inSampleSize = 1;
		if (height > reqHeight || width > reqWidth) {
			// Calculate the largest inSampleSize value that is a power of 2 and keeps both
			// height and width larger than the requested height and width.
			while ((height / inSampleSize) > reqHeight || (width / inSampleSize) > reqWidth) {
				inSampleSize *= 2;
			}
		}
		if (inSampleSize > 1) {
			Log.d(TAG, "under-sampling image by a factor of " + inSampleSize);
			Log.d(TAG, "original dimensions: " + width + "x" + height);
			Log.d(TAG, "new dimensions: " + (width / inSampleSize) + "x" + (height / inSampleSize));
		}
		return inSampleSize;
	}

	/**
	 * Loads the cache index from a file in the cache.
	 * 
	 * @return the index map
	 */
	private static Map<String, Date> loadIndex() {
		Map<String, Date> index = new HashMap<String, Date>();
		try {
			File indexFile = new File(ThisApp.getSingleton().getCacheDir(), INDEX_NAME);
			FileInputStream fin = new FileInputStream(indexFile);
			ObjectInputStream ois = new ObjectInputStream(fin);
			index = (HashMap<String, Date>) ois.readObject();
			ois.close();
		} catch (IOException e) {
			e.printStackTrace();
		} catch (ClassNotFoundException e) {
			e.printStackTrace();
		}
		return index;
	}

	/**
	 * Saves the cache index to a file in the cache.
	 */
	public static void saveIndex() {
		try {
			mutex.acquire();
			cleanCache();
			File indexFile = new File(ThisApp.getSingleton().getCacheDir(), INDEX_NAME);
			FileOutputStream fout = new FileOutputStream(indexFile);
			ObjectOutputStream oos = new ObjectOutputStream(fout);
			oos.writeObject(cacheIndex);
			oos.flush();
			oos.close();
			mutex.release();
		} catch (IOException e) {
			e.printStackTrace();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}

	/**
	 * Cleans the cache of expired data and unindexed files.
	 * 
	 * NOT THREAD SAFE. Acquire mutex before calling this method.
	 */
	private static void cleanCache() {
		long time = new Date().getTime();
		File cache = ThisApp.getSingleton().getCacheDir();
		List<File> files = Arrays.asList(cache.listFiles());
		// clean index of any missing files
		ArrayList<String> missing = new ArrayList<String>();
		for (String key : cacheIndex.keySet()) {
			if (!files.contains(key))
				missing.add("key");
		}
		for (String key : missing)
			cacheIndex.remove(key);
		// delete expired or unindexed files
		for (File f : files) {
			if (!f.getName().equals(INDEX_NAME)) {
				Date expires = cacheIndex.get(f.getName());
				if (expires == null) {
					// file missing in index
					if (!f.delete())
						Log.e(TAG, "failed to delete file");
				} else if (time > expires.getTime()) {
					// file past expiration
					if (!f.delete())
						Log.e(TAG, "failed to delete file");
					else
						cacheIndex.remove(f.getName());
				}
			}
		}
	}

}
