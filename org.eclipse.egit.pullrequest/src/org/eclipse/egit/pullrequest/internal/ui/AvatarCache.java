/*******************************************************************************
 * Copyright (C) 2026, Eclipse EGit contributors
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.egit.pullrequest.internal.ui;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.egit.pullrequest.Activator;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.ImageData;
import org.eclipse.swt.graphics.ImageLoader;
import org.eclipse.swt.widgets.Display;

/**
 * Cache for user avatar images. Downloads avatar images from URLs, caches them
 * on disk and in memory, and provides circular-cropped scaled versions for
 * display in the UI.
 */
public class AvatarCache {

	private static final int CACHE_TTL_DAYS = 7;

	private static final long CACHE_TTL_MILLIS = CACHE_TTL_DAYS * 24L * 60L
			* 60L * 1000L;

	private static final int DOWNLOAD_TIMEOUT_MS = 10000;

	private static AvatarCache instance;

	private final ConcurrentHashMap<String, Image> memoryCache = new ConcurrentHashMap<>();

	private final File cacheDir;

	private AvatarCache() {
		// Get plugin state location for disk cache
		IPath stateLocation = Activator.getDefault().getStateLocation();
		cacheDir = stateLocation.append("avatars").toFile(); //$NON-NLS-1$
		if (!cacheDir.exists()) {
			cacheDir.mkdirs();
		}
	}

	/**
	 * @return the singleton instance
	 */
	public static synchronized AvatarCache getInstance() {
		if (instance == null) {
			instance = new AvatarCache();
		}
		return instance;
	}

	/**
	 * Loads an avatar image asynchronously. If the image is in the memory
	 * cache, the callback is invoked immediately on the calling thread. If the
	 * image is in the disk cache and not stale, it is loaded in a background
	 * job and the callback is invoked on the UI thread. If the image is not
	 * cached or is stale, it is downloaded in a background job, saved to disk,
	 * and the callback is invoked on the UI thread. On any error, the callback
	 * is invoked with {@code null}.
	 *
	 * @param avatarUrl
	 *            the avatar image URL
	 * @param size
	 *            the desired size in pixels (both width and height)
	 * @param callback
	 *            consumer to receive the loaded Image (may be null on failure)
	 */
	public void loadAvatar(String avatarUrl, int size,
			Consumer<Image> callback) {
		if (avatarUrl == null || avatarUrl.isEmpty()) {
			callback.accept(null);
			return;
		}

		// Check memory cache first
		String cacheKey = getCacheKey(avatarUrl, size);
		Image cached = memoryCache.get(cacheKey);
		if (cached != null && !cached.isDisposed()) {
			callback.accept(cached);
			return;
		}

		// Load from disk or network in background
		Job.create("Loading avatar", monitor -> { //$NON-NLS-1$
			Image image = loadAvatarSync(avatarUrl, size);
			Display.getDefault().asyncExec(() -> {
				if (image != null && !image.isDisposed()) {
					memoryCache.put(cacheKey, image);
				}
				callback.accept(image);
			});
		}).schedule();
	}

	/**
	 * Synchronously loads an avatar image. First checks disk cache, then
	 * downloads if necessary. This method should be called from a background
	 * thread.
	 *
	 * @param avatarUrl
	 *            the avatar image URL
	 * @param size
	 *            the desired size in pixels
	 * @return the loaded and scaled circular Image, or null on failure
	 */
	private Image loadAvatarSync(String avatarUrl, int size) {
		try {
			File cachedFile = getCachedFile(avatarUrl);

			// Check if cached file exists and is fresh
			if (cachedFile.exists()) {
				long age = System.currentTimeMillis()
						- cachedFile.lastModified();
				if (age < CACHE_TTL_MILLIS) {
					// Load from disk
					try (FileInputStream fis = new FileInputStream(
							cachedFile)) {
						return loadAndProcessImage(fis, size);
					} catch (IOException e) {
						Activator.logWarning(
								"Failed to load cached avatar: " //$NON-NLS-1$
										+ e.getMessage());
					}
				}
			}

			// Download from network
			Image image = downloadAvatar(avatarUrl, size);
			if (image != null) {
				// Save raw image data to disk for future use
				saveToCache(avatarUrl, image);
			}
			return image;

		} catch (Exception e) {
			Activator.logWarning("Failed to load avatar from " + avatarUrl //$NON-NLS-1$
					+ ": " + e.getMessage()); //$NON-NLS-1$
			return null;
		}
	}

	/**
	 * Downloads an avatar image from a URL.
	 *
	 * @param avatarUrl
	 *            the URL to download from
	 * @param size
	 *            the desired size in pixels
	 * @return the downloaded and processed image, or null on failure
	 */
	private Image downloadAvatar(String avatarUrl, int size) {
		HttpURLConnection conn = null;
		try {
			// Request higher resolution for better quality (2x size)
			String enhancedUrl = enhanceAvatarUrl(avatarUrl, size * 2);
			URL url = new URL(enhancedUrl);
			conn = (HttpURLConnection) url.openConnection();
			conn.setConnectTimeout(DOWNLOAD_TIMEOUT_MS);
			conn.setReadTimeout(DOWNLOAD_TIMEOUT_MS);
			conn.setRequestProperty("User-Agent", //$NON-NLS-1$
					"Eclipse-EGit-PullRequest/1.0"); //$NON-NLS-1$

			int responseCode = conn.getResponseCode();
			if (responseCode != HttpURLConnection.HTTP_OK) {
				Activator.logWarning("Failed to download avatar from " //$NON-NLS-1$
						+ avatarUrl + ": HTTP " + responseCode); //$NON-NLS-1$
				return null;
			}

			try (InputStream is = conn.getInputStream()) {
				return loadAndProcessImage(is, size);
			}

		} catch (IOException e) {
			Activator.logWarning("Failed to download avatar from " //$NON-NLS-1$
					+ avatarUrl + ": " + e.getMessage()); //$NON-NLS-1$
			return null;
		} finally {
			if (conn != null) {
				conn.disconnect();
			}
		}
	}

	/**
	 * Loads an image from an input stream, scales it, and crops it to a
	 * circle.
	 *
	 * @param is
	 *            the input stream to load from
	 * @param size
	 *            the desired size in pixels
	 * @return the processed Image, or null on failure
	 */
	private Image loadAndProcessImage(InputStream is, int size) {
		try {
			ImageLoader loader = new ImageLoader();
			ImageData[] imageData = loader.load(is);
			if (imageData == null || imageData.length == 0) {
				return null;
			}

			// Create image on UI thread's Display
			Display display = Display.getDefault();
			Image sourceImage = new Image(display, imageData[0]);

			// Use high-quality scaling with anti-aliasing via GC
			// This produces much better results than ImageData.scaledTo()
			Image scaledImage = new Image(display, size, size);
			org.eclipse.swt.graphics.GC gc = new org.eclipse.swt.graphics.GC(scaledImage);
			try {
				// Enable anti-aliasing and interpolation for smooth scaling
				gc.setAntialias(org.eclipse.swt.SWT.ON);
				gc.setInterpolation(org.eclipse.swt.SWT.HIGH);
				
				// Draw the source image scaled to the target size
				gc.drawImage(sourceImage, 0, 0, sourceImage.getBounds().width,
						sourceImage.getBounds().height, 0, 0, size, size);
			} finally {
				gc.dispose();
				sourceImage.dispose();
			}

			// Note: createCircularImage returns source image directly
			// (circular clipping is done at draw time in AvatarCanvas)
			// so we must NOT dispose scaledImage here
			Image circularImage = createCircularImage(display, scaledImage,
					size);

			return circularImage;

		} catch (Exception e) {
			Activator.logWarning(
					"Failed to process avatar image: " + e.getMessage()); //$NON-NLS-1$
			return null;
		}
	}

	/**
	 * Creates a circular version of an image by drawing it clipped to a
	 * circular region with proper transparency.
	 *
	 * @param display
	 *            the Display to create the image on
	 * @param source
	 *            the source image (must be square)
	 * @param size
	 *            the size in pixels
	 * @return a new circular Image
	 */
	private Image createCircularImage(Display display, Image source, int size) {
		// Just return the source image - circular clipping is done at draw time
		// in AvatarCanvas to avoid alpha compositing issues
		return source;
	}

	/**
	 * Saves an avatar image to the disk cache.
	 *
	 * @param avatarUrl
	 *            the avatar URL (used to generate filename)
	 * @param image
	 *            the image to save
	 */
	private void saveToCache(String avatarUrl, Image image) {
		try {
			File cachedFile = getCachedFile(avatarUrl);
			ImageLoader loader = new ImageLoader();
			loader.data = new ImageData[] { image.getImageData() };
			try (FileOutputStream fos = new FileOutputStream(cachedFile)) {
				loader.save(fos, org.eclipse.swt.SWT.IMAGE_PNG);
			}
		} catch (IOException e) {
			Activator.logWarning(
					"Failed to save avatar to cache: " + e.getMessage()); //$NON-NLS-1$
		}
	}

	/**
	 * Generates a cache file path for a given avatar URL.
	 *
	 * @param avatarUrl
	 *            the avatar URL
	 * @return the File to use for caching
	 */
	private File getCachedFile(String avatarUrl) {
		String hash = hashUrl(avatarUrl);
		return new File(cacheDir, hash + ".png"); //$NON-NLS-1$
	}

	/**
	 * Generates a cache key for the in-memory cache.
	 *
	 * @param avatarUrl
	 *            the avatar URL
	 * @param size
	 *            the image size
	 * @return the cache key
	 */
	private String getCacheKey(String avatarUrl, int size) {
		return avatarUrl + ":" + size; //$NON-NLS-1$
	}

	/**
	 * Hashes a URL to generate a filename for the disk cache.
	 *
	 * @param url
	 *            the URL to hash
	 * @return the SHA-256 hash as a hex string
	 */
	private String hashUrl(String url) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256"); //$NON-NLS-1$
			byte[] hash = digest.digest(url.getBytes(StandardCharsets.UTF_8));
			StringBuilder hexString = new StringBuilder();
			for (byte b : hash) {
				String hex = Integer.toHexString(0xff & b);
				if (hex.length() == 1) {
					hexString.append('0');
				}
				hexString.append(hex);
			}
			return hexString.toString();
		} catch (Exception e) {
			// Fallback to hashCode if SHA-256 not available
			return String.valueOf(url.hashCode());
		}
	}

	/**
	 * Enhances an avatar URL with size parameters for better quality.
	 * GitHub avatars support ?s=size parameter for requesting specific sizes.
	 * Bitbucket avatars have a default size but may benefit from parameters in the future.
	 *
	 * @param avatarUrl
	 *            the original avatar URL
	 * @param requestedSize
	 *            the desired image size in pixels
	 * @return the enhanced URL with size parameters
	 */
	private String enhanceAvatarUrl(String avatarUrl, int requestedSize) {
		if (avatarUrl == null || avatarUrl.isEmpty()) {
			return avatarUrl;
		}

		// GitHub avatars: add/update ?s= parameter
		if (avatarUrl.contains("avatars.githubusercontent.com") || //$NON-NLS-1$
				avatarUrl.contains("github.com/avatars")) { //$NON-NLS-1$
			// Remove existing size parameter if present
			String baseUrl = avatarUrl.split("\\?")[0]; //$NON-NLS-1$
			// Request size, capped at 460 (GitHub's max)
			int size = Math.min(requestedSize, 460);
			return baseUrl + "?s=" + size; //$NON-NLS-1$
		}

		// Bitbucket Server avatars: the URL format is /users/{slug}/avatar.png
		// They support ?s= parameter for size
		if (avatarUrl.contains("/users/") && avatarUrl.contains("/avatar.png")) { //$NON-NLS-1$ //$NON-NLS-2$
			// Remove existing parameters
			String baseUrl = avatarUrl.split("\\?")[0]; //$NON-NLS-1$
			// Request higher resolution (Bitbucket supports various sizes)
			return baseUrl + "?s=" + requestedSize; //$NON-NLS-1$
		}

		// For other avatar services, return as-is
		return avatarUrl;
	}

	/**
	 * Disposes all cached images and clears the memory cache. Should be called
	 * when the plugin is stopping.
	 */
	public void dispose() {
		for (Image image : memoryCache.values()) {
			if (image != null && !image.isDisposed()) {
				image.dispose();
			}
		}
		memoryCache.clear();
	}
}
