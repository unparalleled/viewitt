package com.viewittapp;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnPreparedListener;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.view.PagerAdapter;
import android.support.v4.view.ViewPager;
import android.support.v4.widget.DrawerLayout;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnLongClickListener;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.VideoView;

public class MainActivity extends Activity {

	private static final String TAG = MainActivity.class.getSimpleName();
	private static final String REDDIT_URL = "https://www.reddit.com/";

	private static Activity singleton;
	private Toast toaster;

	private DrawerLayout drawerLayout;

	// map subreddit name to list of image links
	private Map<String, List<MediaLink>> mediaLinks;
	private String subReddit;
	private MediaLink currentMedia;

	private ViewPager imagePager;
	private MediaPagerAdapter pagerAdapter;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		singleton = this;

		toaster = Toast.makeText(getApplicationContext(), "", Toast.LENGTH_LONG); // reusable toaster

		drawerLayout = (DrawerLayout) findViewById(R.id.drawerLayout);
		ListView drawerList = (ListView) findViewById(R.id.leftDrawer);

		// add Viewitt logo to header of list view
		View header = getLayoutInflater().inflate(R.layout.drawer_header, null);
		drawerList.addHeaderView(header, null, false);
		drawerLayout.openDrawer(Gravity.LEFT); // open to show user

		final String[] drawerOptions = getResources().getStringArray(R.array.drawerOptions);
		drawerList.setAdapter(new ArrayAdapter<String>(this, R.id.optionText, drawerOptions) {
			@Override
			public View getView(int position, View convertView, ViewGroup parent) {
				View view = getLayoutInflater().inflate(R.layout.drawer_item, parent, false);
				TextView option = (TextView) view.findViewById(R.id.optionText);
				option.setText(drawerOptions[position]);
				return view;
			}
		});
		drawerList.setOnItemClickListener(new OnItemClickListener() {
			@Override
			public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
				position--; // account for header
				String option = drawerOptions[position];
				if (option.equals("Share")) {
					if (currentMedia != null) {
						showShareDialog(currentMedia);
					} else {
						toast("no image to share");
					}
				} else if (option.equals("Save")) {
					if (currentMedia != null) {
						Bitmap bmp = CacheUtils.getBitmapFromCache(currentMedia.getCacheKey());
						if (bmp != null) {
							MediaUtils.insertImage(singleton.getContentResolver(), bmp, "Viewitt", "Viewitt");
							toast("image saved");
						} else {
							toast("cannot save video");
						}
					} else {
						toast("no image to save");
					}
				} else if (option.equals("Popular")) {
					showChooseSubDialog();
				} else if (option.equals("Custom")) {
					showCustomSubDialog();
				} else if (option.equals("Settings")) {
					showSettingsDialog();
				}
				drawerLayout.closeDrawers();
			}
		});

		mediaLinks = new HashMap<String, List<MediaLink>>();

		subReddit = "";
		List<MediaLink> mediaList = new ArrayList<MediaLink>();
		mediaLinks.put(subReddit, mediaList);

		imagePager = (ViewPager) findViewById(R.id.mediaPager);
		imagePager.setOffscreenPageLimit(3);
		pagerAdapter = new MediaPagerAdapter(mediaList);
		imagePager.setAdapter(pagerAdapter);
		imagePager.setOnPageChangeListener(pagerAdapter);

		new LoadPageSourceTask(mediaList).executeOnExecutor(ThisApp.getThreadPool(), REDDIT_URL);
	}

	@Override
	public void onStop() {
		super.onStop();
		toaster.cancel();
		CacheUtils.saveIndex(); // refresh cache data
		ThisApp.getSingleton().saveSettings();
	}

	private class MediaPagerAdapter extends PagerAdapter implements ViewPager.OnPageChangeListener {
		private List<MediaLink> media;
		private int lastPosition = -1;

		public MediaPagerAdapter(List<MediaLink> media) {
			this.media = media;
		}

		@Override
		public int getCount() {
			return media.size();
		}

		@Override
		public int getItemPosition(Object object) {
			return (Integer) ((View) object).getTag();
		}

		@Override
		public boolean isViewFromObject(View view, Object object) {
			return view == ((View) object);
		}

		@Override
		public Object instantiateItem(ViewGroup container, int position) {
			final MediaLink mediaLink = media.get(position);
			String url = mediaLink.getMediaUrl();

			RelativeLayout layout = (RelativeLayout) singleton.getLayoutInflater().inflate(R.layout.layout_media, container, false);
			ImageView imageView = (ImageView) layout.findViewById(R.id.imageView);
			VideoView videoView = (VideoView) layout.findViewById(R.id.videoView);
			imageView.setVisibility(View.GONE);
			videoView.setVisibility(View.GONE);

			View clickable; // the 2 views respond differently to click listeners...

			if (url.endsWith("gifv")) {
				clickable = layout;
				videoView.setVisibility(View.VISIBLE);
				videoView.setVideoPath(url.replace("gifv", "webm"));
				videoView.start();
				videoView.setOnPreparedListener(new OnPreparedListener() {
					@Override
					public void onPrepared(MediaPlayer mp) {
						mp.setLooping(true);
					}
				});
				// videoView.setMediaController(new MediaController(singleton));
			} else {
				clickable = imageView;
				imageView.setVisibility(View.VISIBLE);
				// set place holder image
				imageView.setImageResource(R.drawable.default_image);
				// download actual image in background
				new DownloadImageTask(url, imageView).executeOnExecutor(ThisApp.getThreadPool());
			}

			clickable.setOnClickListener(new OnClickListener() {
				@Override
				public void onClick(View v) {
					toast(currentMedia.getMediaTitle());
				}
			});
			clickable.setOnLongClickListener(new OnLongClickListener() {
				@Override
				public boolean onLongClick(View v) {
					drawerLayout.openDrawer(Gravity.LEFT);
					return true;
				}
			});

			layout.setTag(Integer.valueOf(position));
			((ViewPager) container).addView(layout, 0);
			return layout;
		}

		@Override
		public void destroyItem(ViewGroup container, int position, Object object) {
			((ViewPager) container).removeView((View) object);
		}

		@Override
		public void onPageScrollStateChanged(int state) {

		}

		@Override
		public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {

		}

		@Override
		public void onPageSelected(int position) {
			currentMedia = media.get(position);
			if (position > lastPosition) {
				ThisApp.incrementUniqueImageViews();
				lastPosition = position;
			}
			if (ThisApp.getShowTitle())
				toast(currentMedia.getMediaTitle());
		}
	}

	private class LoadPageSourceTask extends AsyncTask<String, Void, Void> {
		private List<MediaLink> media;

		public LoadPageSourceTask(List<MediaLink> images) {
			this.media = images;
			toast("loading content");
		}

		protected Void doInBackground(String... urls) {
			String url = urls[0];
			Log.d(TAG, "fetching: " + url);
			try {
				URL obj = new URL(url);
				HttpURLConnection con = (HttpURLConnection) obj.openConnection();
				con.setRequestMethod("GET");
				con.setRequestProperty("User-Agent", "Mozilla/5.0");
				con.setRequestProperty("Cookie", "over18=1");
				if (con.getResponseCode() != 200)
					throw new IOException();
				BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()));
				String inputLine;
				StringBuffer response = new StringBuffer();
				while ((inputLine = in.readLine()) != null)
					response.append(inputLine);
				in.close();
				Log.d(TAG, "page length (chars) : " + response.length());
				parsePageSource(response.toString());
			} catch (IOException e) {
				e.printStackTrace();
				Log.e(TAG, e.toString());
				displayContentUnavailable(media);
			}
			return null;
		}

		private void parsePageSource(String page) {
			final String anyChars = "[-a-zA-Z0-9+&@#/%?=~_|!:,.;]*";

			// parse image urls and titles from the page source using regex
			String mediaRegex;
			if (ThisApp.getShowVideo())
				mediaRegex = "http://" + anyChars + "(\\.png|\\.jpg|\\.jpeg|\\.gifv)";
			else
				mediaRegex = "http://" + anyChars + "(\\.png|\\.jpg|\\.jpeg)";

			String titleRegex = ">[^<>]+?<"; // hacky
			Matcher m = Pattern.compile(mediaRegex + "[^<>]*?" + titleRegex).matcher(page);
			while (m.find()) {
				String match = m.group();
				Matcher imageM = Pattern.compile(mediaRegex).matcher(match);
				String url = imageM.find() ? imageM.group() : null;
				Matcher titleM = Pattern.compile(titleRegex).matcher(match);
				String title = titleM.find() ? titleM.group().substring(1, titleM.group().length() - 1) : "";
				// unescape html quotes
				title = title.replace("&quot;", "\"");
				title = title.replace("&amp;", "&");
				MediaLink newLink = new MediaLink(url, title);
				if (!media.contains(newLink)) {
					addNewLink(media, newLink);
					Log.d(TAG, "number: " + media.size() + ", " + newLink.getMediaUrl());
					Log.d(TAG, "title: " + title);
				}
			}

			// finally parse the next page url
			String nextUrlregex = REDDIT_URL + subReddit + "\\?" + anyChars + "count=\\d+" + anyChars + "after=" + anyChars + "\"";
			Log.d(TAG, "regex: " + nextUrlregex);
			m = Pattern.compile(nextUrlregex).matcher(page);
			String nextPageUrl = null;
			while (m.find()) {
				nextPageUrl = m.group().substring(0, m.group().length() - 1);
				nextPageUrl = nextPageUrl.replace("amp=&amp;", ""); // delete strange artifacts
				Log.d(TAG, nextPageUrl);
			}

			if (nextPageUrl != null)
				doInBackground(nextPageUrl);
			else if (media.isEmpty() && nextPageUrl == null)
				displayContentUnavailable(media);
		}
	}

	private class DownloadImageTask extends AsyncTask<Void, Void, Bitmap> {
		private String url;
		private ImageView imgView;

		public DownloadImageTask(String url, ImageView imgView) {
			this.url = url;
			this.imgView = imgView;
		}

		protected Bitmap doInBackground(Void... v) {
			Log.d(TAG, "downloading: " + url);
			String cacheKey = HashUtils.unsaltedHash(url);
			Bitmap image = CacheUtils.getBitmapFromCache(cacheKey);
			if (image != null)
				return image;
			try {
				InputStream in = new java.net.URL(url).openStream();
				int bytes = CacheUtils.streamImageIntoCache(cacheKey, in);
				if (bytes > 0) {
					Log.d(TAG, "bytes downloaded: " + bytes);
					ThisApp.incrementBytesDownloaded(bytes);
					image = CacheUtils.getBitmapFromCache(cacheKey);
				} else {
					Log.e(TAG, "download failed");
				}
			} catch (MalformedURLException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			}
			return image;
		}

		protected void onPostExecute(Bitmap picture) {
			if (picture != null) {
				imgView.setImageBitmap(picture);
			}
		}
	}

	private void showShareDialog(MediaLink imgLink) {
		Intent sharingIntent = new Intent(android.content.Intent.ACTION_SEND);
		sharingIntent.setType("text/plain");
		String shareBody = "" + imgLink.getMediaUrl() + " - " + imgLink.getMediaTitle();
		sharingIntent.putExtra(android.content.Intent.EXTRA_SUBJECT, "Viewitt media");
		sharingIntent.putExtra(android.content.Intent.EXTRA_TEXT, shareBody);
		startActivity(Intent.createChooser(sharingIntent, "Share image via"));
	}

	private void showChooseSubDialog() {
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setTitle("Choose a subreddit");
		final String[] items = { "FRONT", "ALL", "funny", "pics", "food", "aww", "gifs" };
		builder.setItems(items, new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int which) {
				String newSub = items[which];
				newSub = newSub.equals("FRONT") ? "" : "r/" + newSub + "/";
				if (newSub.equals(subReddit)) {
					toast("current choice");
					return;
				} else {
					resetSubReddit(newSub);
				}
			}
		});
		builder.show();
	}

	private void showCustomSubDialog() {
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setTitle("Type any subreddit");

		// Set up the input
		final EditText input = new EditText(singleton);
		input.setHint("e.g. pics, aww, etc.");
		builder.setView(input);

		// Set up the buttons
		builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
				String newSub = "r/" + input.getText().toString() + "/";
				resetSubReddit(newSub);
			}
		});
		builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
				dialog.cancel();
			}
		});

		builder.show();
	}

	private void showSettingsDialog() {
		View view = getLayoutInflater().inflate(R.layout.dialog_settings, null);

		final CheckBox showTitle = (CheckBox) view.findViewById(R.id.settingsTitleCheckBox);
		final CheckBox showVideo = (CheckBox) view.findViewById(R.id.settingsVideoCheckBox);
		final TextView imagesViewed = (TextView) view.findViewById(R.id.settingsImagesViewed);
		final TextView mbDownloaded = (TextView) view.findViewById(R.id.settingsMbDownloaded);
		final TextView versionTextView = (TextView) view.findViewById(R.id.settingsVersionTextView);

		showTitle.setChecked(ThisApp.getShowTitle());
		showVideo.setChecked(ThisApp.getShowVideo());

		imagesViewed.setText(ThisApp.getUniqueImageViews() + " Images Viewed");
		mbDownloaded.setText((ThisApp.getBytesDownloaded() / 1000000) + " MB downloaded");

		String versionText = "Version: " + ThisApp.getSingleton().getAppVersionName();
		versionText += " (" + ThisApp.getSingleton().getAppVersionCode() + ")";
		versionTextView.setText(versionText);

		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setTitle("Viewitt settings");
		builder.setView(view);
		builder.setPositiveButton("Save", new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
				ThisApp.setShowTitle(showTitle.isChecked());
				ThisApp.setShowVideo(showVideo.isChecked());
				ThisApp.getSingleton().saveSettings();
			}
		});
		builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
				dialog.cancel();
			}
		});
		builder.show();
	}

	private void resetSubReddit(String newSub) {
		subReddit = newSub;
		List<MediaLink> mediaList = new ArrayList<MediaLink>();
		mediaLinks.put(subReddit, mediaList);
		// reset image pager adapter
		pagerAdapter = new MediaPagerAdapter(mediaList);
		imagePager.setAdapter(pagerAdapter);
		imagePager.setOnPageChangeListener(pagerAdapter);
		// load new subreddit links
		String newUrl = REDDIT_URL + subReddit;
		new LoadPageSourceTask(mediaList).executeOnExecutor(ThisApp.getThreadPool(), newUrl);
	}

	private void displayContentUnavailable(List<MediaLink> images) {
		toast("content unavailable");
		String url = "http://www.redditstatic.com/subreddit-banned.png";
		String title = "content unavailable";
		MediaLink image = new MediaLink(url, title);
		addNewLink(images, image);
	}

	/**
	 * Data set changes must occur on the main thread and must end with a call to notifyDataSetChanged()
	 * http://developer.android.com/reference/android/support/v4/view/PagerAdapter.html
	 * 
	 * @param media
	 * @param newLink
	 */
	private void addNewLink(final List<MediaLink> media, final MediaLink newLink) {
		singleton.runOnUiThread(new Runnable() {
			public void run() {
				if (media.isEmpty()) {
					// initialize very first item
					currentMedia = newLink;
					if (ThisApp.getShowTitle())
						toast(currentMedia.getMediaTitle());
				}
				media.add(newLink);
				pagerAdapter.notifyDataSetChanged();
			}
		});
	}

	private void toast(final String toastText) {
		singleton.runOnUiThread(new Runnable() {
			public void run() {
				toaster.setText(toastText);
				toaster.show();
			}
		});
	}

}
