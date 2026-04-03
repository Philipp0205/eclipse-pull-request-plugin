package org.eclipse.egit.pullrequest.internal.ui;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
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
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.GC;
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
	private static final long CACHE_TTL_MILLIS = CACHE_TTL_DAYS * 24L * 60L * 60L * 1000L;
	private static final int DOWNLOAD_TIMEOUT_MS = 10000;
	private static final int MAX_REDIRECTS = 5;
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
	 * cache, the callback is invoked immediately on the calling thread. If
	 * the image is in the disk cache and not stale, it is loaded in a
	 * background job and the callback is invoked on the UI thread. If the
	 * image is not cached or is stale, it is downloaded in a background
	 * job, saved to disk, and the callback is invoked on the UI thread. On
	 * any error, the callback is invoked with {@code null}.
	 *
	 * @param avatarUrl
	 *            the avatar image URL
	 * @param size
	 *            the desired size in pixels (both width and height)
	 * @param callback
	 *            consumer to receive the loaded Image (may be null on
	 *            failure)
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

		// Load ImageData from disk or network in background,
		// then create the Image on the UI thread
		Job.create("Loading avatar", monitor -> { //$NON-NLS-1$
			ImageData data = loadImageDataSync(avatarUrl, size);
			Display.getDefault().asyncExec(() -> {
				Image image = createScaledImage(data, size);
				if (image != null) {
					memoryCache.put(cacheKey, image);
				}
				callback.accept(image);
			});
		}).schedule();
	}

	/**
	 * Synchronously loads avatar image data. First checks disk cache,
	 * then downloads if necessary. This method only produces
	 * {@link ImageData} (a plain Java object) and is safe to call from
	 * any thread.
	 *
	 * @param avatarUrl
	 *            the avatar image URL
	 * @param size
	 *            the desired display size in pixels (used to request
	 *            an appropriate resolution from the server)
	 * @return the loaded ImageData, or {@code null} on failure
	 */
	private ImageData loadImageDataSync(String avatarUrl, int size) {
		try {
			File cachedFile = getCachedFile(avatarUrl);

			// Check if cached file exists and is fresh
			if (cachedFile.exists()) {
				long age = System.currentTimeMillis()
						- cachedFile.lastModified();
				if (age < CACHE_TTL_MILLIS) {
					try (FileInputStream fis = new FileInputStream(
							cachedFile)) {
						return parseImageData(fis);
					} catch (IOException e) {
						Activator.logWarning(
								"Failed to load cached avatar: " //$NON-NLS-1$
										+ e.getMessage());
					}
				}
			}

			// Download from network
			byte[] rawBytes = downloadAvatarBytes(avatarUrl,
					size * 2);
			if (rawBytes == null) {
				return null;
			}

			// Save raw bytes to disk cache before parsing
			saveBytesToCache(avatarUrl, rawBytes);

			// Parse into ImageData (thread-safe)
			try (ByteArrayInputStream bis = new ByteArrayInputStream(
					rawBytes)) {
				return parseImageData(bis);
			}

		} catch (Exception e) {
			Activator.logWarning(
					"Failed to load avatar from " + avatarUrl //$NON-NLS-1$
							+ ": " + e.getMessage()); //$NON-NLS-1$
			return null;
		}
	}

	/**
	 * Downloads raw avatar image bytes from a URL, following redirects
	 * including cross-protocol redirects (HTTP to HTTPS).
	 *
	 * @param avatarUrl
	 *            the avatar URL
	 * @param requestedSize
	 *            the desired image size in pixels for URL enhancement
	 * @return the raw image bytes, or {@code null} on failure
	 */
	private byte[] downloadAvatarBytes(String avatarUrl,
			int requestedSize) {
		String currentUrl = enhanceAvatarUrl(avatarUrl, requestedSize);

		for (int redirects = 0; redirects < MAX_REDIRECTS; redirects++) {
			HttpURLConnection conn = null;
			try {
				URL url = new URL(currentUrl);
				conn = (HttpURLConnection) url.openConnection();
				conn.setInstanceFollowRedirects(false);
				conn.setConnectTimeout(DOWNLOAD_TIMEOUT_MS);
				conn.setReadTimeout(DOWNLOAD_TIMEOUT_MS);
				conn.setRequestProperty("User-Agent", //$NON-NLS-1$
						"Eclipse-EGit-PullRequest/1.0"); //$NON-NLS-1$

				int code = conn.getResponseCode();

				if (code == HttpURLConnection.HTTP_OK) {
					try (InputStream is = conn.getInputStream()) {
						return readAllBytes(is);
					}
				}

				if (code == HttpURLConnection.HTTP_MOVED_PERM
						|| code == HttpURLConnection.HTTP_MOVED_TEMP
						|| code == 307 || code == 308) {
					String location = conn.getHeaderField("Location"); //$NON-NLS-1$
					if (location == null || location.isEmpty()) {
						Activator.logWarning(
								"Redirect with no Location from " //$NON-NLS-1$
										+ currentUrl);
						return null;
					}
					currentUrl = location;
					continue;
				}

				Activator.logWarning(
						"Failed to download avatar from " //$NON-NLS-1$
								+ avatarUrl + ": HTTP " //$NON-NLS-1$
								+ code);
				return null;

			} catch (IOException e) {
				Activator.logWarning(
						"Failed to download avatar from " //$NON-NLS-1$
								+ avatarUrl + ": " //$NON-NLS-1$
								+ e.getMessage());
				return null;
			} finally {
				if (conn != null) {
					conn.disconnect();
				}
			}
		}

		Activator.logWarning(
				"Too many redirects for avatar: " + avatarUrl); //$NON-NLS-1$
		return null;
	}

	/**
	 * Reads all bytes from an input stream.
	 *
	 * @param is
	 *            the input stream
	 * @return the bytes
	 * @throws IOException
	 *             on I/O error
	 */
	private byte[] readAllBytes(InputStream is) throws IOException {
		ByteArrayOutputStream buffer = new ByteArrayOutputStream();
		byte[] chunk = new byte[8192];
		int bytesRead;
		while ((bytesRead = is.read(chunk)) != -1) {
			buffer.write(chunk, 0, bytesRead);
		}
		return buffer.toByteArray();
	}

	/**
	 * Parses an input stream into {@link ImageData}. This method uses
	 * {@link ImageLoader} which only produces plain Java data objects
	 * and is safe to call from any thread.
	 *
	 * @param is
	 *            the input stream containing image bytes
	 * @return the parsed ImageData, or {@code null} if parsing fails
	 */
	private ImageData parseImageData(InputStream is) {
		try {
			ImageLoader loader = new ImageLoader();
			ImageData[] data = loader.load(is);
			if (data != null && data.length > 0) {
				return data[0];
			}
		} catch (Exception e) {
			Activator.logWarning(
					"Failed to parse avatar image: " //$NON-NLS-1$
							+ e.getMessage());
		}
		return null;
	}

	/**
	 * Creates a scaled SWT {@link Image} from {@link ImageData}. This
	 * method uses SWT graphics resources ({@link Image}, {@link GC})
	 * and <b>must</b> be called on the UI thread.
	 *
	 * @param data
	 *            the source image data, may be {@code null}
	 * @param size
	 *            the desired size in pixels (width and height)
	 * @return the scaled Image, or {@code null} if data is null
	 */
	private Image createScaledImage(ImageData data, int size) {
		if (data == null) {
			return null;
		}
		Display display = Display.getCurrent();
		if (display == null) {
			return null;
		}
		Image sourceImage = new Image(display, data);
		try {
			Image scaledImage = new Image(display, size, size);
			GC gc = new GC(scaledImage);
			try {
				gc.setAntialias(SWT.ON);
				gc.setInterpolation(SWT.HIGH);
				gc.drawImage(sourceImage, 0, 0,
						sourceImage.getBounds().width,
						sourceImage.getBounds().height,
						0, 0, size, size);
			} finally {
				gc.dispose();
			}
			return scaledImage;
		} finally {
			sourceImage.dispose();
		}
	}

	/**
	 * Saves raw image bytes to the disk cache.
	 *
	 * @param avatarUrl
	 *            the avatar URL (used to generate the filename)
	 * @param bytes
	 *            the raw image bytes to save
	 */
	private void saveBytesToCache(String avatarUrl, byte[] bytes) {
		try {
			File cachedFile = getCachedFile(avatarUrl);
			try (FileOutputStream fos = new FileOutputStream(
					cachedFile)) {
				fos.write(bytes);
			}
		} catch (IOException e) {
			Activator.logWarning(
					"Failed to save avatar to cache: " //$NON-NLS-1$
							+ e.getMessage());
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
