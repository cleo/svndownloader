package com.cleo.labs.svndownloader;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SVNDownloader {
    private static Logger logger = Logger.getGlobal();

    private String auth;

    private static final Charset UTF8 = Charset.forName("UTF-8");
    private static final String AUTHORIZATION = "Authorization";
    private static final String USER_AGENT = "User-Agent";
    private static final String LOCATION = "Location";

    public SVNDownloader(String username, String password) {
        auth = "Basic "+Base64.getEncoder().encodeToString((username+":"+password).getBytes(UTF8));
    }

    /**
     * Drains an {@code InputStream} and closes it. If a non-negative
     * size is provided, allocates a buffer of that size and returns
     * the first {@code size} bytes pulled from the stream. If the
     * stream has additional data, it is discarded. If the stream does
     * not fill the allocated size, the full buffer is still returned.
     * <p/>
     * Typical intended usage is to retrieve {@code Content-Length}
     * bytes from an HTTP InputStream.
     *
     * @param s the (possibly {@code null}) {@code InputStream} to drain
     * @param size the (possibly {@code -1}) number of bytes to return
     * @return the (possibly {@code null}, if {@code size} is {@code -1}) buffer
     */
    private byte[] drain(InputStream s, int size) {
        byte[] b = size >= 0 ? new byte[size] : null;
        if (s != null) {
            try {
                byte[] d = new byte[8192];
                int o = 0;
                int n;
                while ((n = s.read(d)) >= 0) {
                    if (size >= 0 && o < size) {
                        System.arraycopy(d,  0,  b, o, Math.min(size-o, n));
                        o += n;
                    }
                }
                s.close();
            } catch (IOException e) {
                logger.warning("error reading response: "+e.getMessage());
            }
        }
        if (s != null) {
            try {
                s.close();
            } catch (IOException e) {
                // did our best
            }
        }
        return b;
    }

    /**
     * Returns either the {@code getInputStream} (if that works), or
     * the {@code getErrorStream} (if {@code getInputStream} throws an
     * exception) for a {@code HttpURLConnection}.
     *
     * @param http the {@code HttpURLConnection} to inspect
     * @return a (possibly {@code null}) {@code InputStream}
     */
    private InputStream getEitherStream(HttpURLConnection http) {
        try {
            return http.getInputStream();
        } catch (IOException e) {
            return http.getErrorStream();
        }
    }

    /**
     * Drains and discards the content from either the InputStream
     * or ErrorStream of a {@code HttpURLConnection}.
     *
     * @param http the {@code HttpURLConnection} to drain
     */
    private void drain(HttpURLConnection http) {
        drain(getEitherStream(http), -1);
    }

    /**
     * Maximum number of redirects to follow on a GET.
     */
    private static final int MAX_REDIRECTS = 10;

    /**
     * Returns a {@code HttpURLConnection} for {@code url},
     * ready for inspection and content retrieval. Follows
     * any redirects up to {@link #MAX_REDIRECTS}.
     *
     * @param url the URL to retrieve
     * @return a {@code HttpURLConnection} ready for processing
     * @throws IOException
     */
    private HttpURLConnection get(String url) throws IOException {
        HttpURLConnection http = (HttpURLConnection) new URL(url).openConnection();
        http.setRequestProperty(AUTHORIZATION, auth);
        http.setRequestProperty(USER_AGENT, "curl/7.37.1");
        int code = http.getResponseCode();
        logger.fine("GET "+url+": "+code);
        int redirects = 0;
        while (code == HttpURLConnection.HTTP_MOVED_PERM ||
                code == HttpURLConnection.HTTP_MOVED_TEMP ||
                code == HttpURLConnection.HTTP_SEE_OTHER) {
            drain(http);
            redirects++;
            if (redirects >= MAX_REDIRECTS) {
                throw new IOException("too many redirects: "+redirects+" > "+MAX_REDIRECTS);
            }
            String location = http.getHeaderField(LOCATION);
            http = (HttpURLConnection) new URL(location).openConnection();
            http.setRequestProperty(AUTHORIZATION, auth);
            http.setRequestProperty(USER_AGENT, "curl/7.37.1");
            code = http.getResponseCode();
            logger.fine("GET "+url+": "+code);
        }
        return http;
    }

    /**
     * Returns the content downloaded from a URL as a String.
     *
     * @param url the URL to download
     * @return the contents as a String
     * @throws IOException
     */
    private String getContent(String url) throws IOException {
        HttpURLConnection http = get(url);
        int length = http.getContentLength();
        return new String(drain(http.getInputStream(), length));
    }

    /**
     * Downloads the content from a URL to a File.
     *
     * @param url the URL to download
     * @param destination the {@code Path} where a new file should be created (with {@link StandardCopyOption#REPLACE_EXISTING})
     * @throws IOException
     */
    private void downloadContent(String url, Path destination) throws IOException {
        HttpURLConnection http = get(url);
        try (InputStream in = http.getInputStream()) {
            Files.copy(in, destination, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /**
     * {@code Pattern} for capturing {@code <a href="...">} from an SVN "directory listing".
     */
    private static final Pattern HREF = Pattern.compile("<li><a href=\"([^\"]*)\">");

    /**
     * Downloads an SVN directory listing from a URL and parses the
     * {@code <a href=} links from it, returning a list of links found.
     * Note that {@code "../"} is discarded.
     *
     * @param url the URL to download
     * @return a list of HREFs found
     * @throws IOException
     */
    private List<String> dir(String url) throws IOException {
        // append / if not already there
        url = url.replaceFirst("(?<!/)$", "/");
        // get blah blah <ul><li><a href="entry">entry</a></li>...</ul> blah blah
        String html = getContent(url);
        // parse out the hrefs, ignoring ../
        List<String> result = new ArrayList<>();
        Matcher m = HREF.matcher(html);
        while (m.find()) {
            String find = m.group(1);
            if (!find.equals("../")) {
                result.add(find);
            }
        }
        return result;
    }

    /**
     * Convenience method to call {@link #checkout(String, Path)} with a
     * {@code String} instead of a {@code Path}.
     *
     * @param svnurl
     * @param destination
     * @throws Exception
     */
    public void checkout(String svnurl, String destination) throws IOException {
        checkout(svnurl, Paths.get(destination));
    }

    /**
     * Connects to SVN at the provided URL and recursively downloads all the directories
     * and files found to a designated {@code Path} location. Creates all needed directories,
     * including if they are empty. Any existing files are overwritten.
     *
     * @param svnurl the URL to the SVN location to download (it must be a directory, not a file)
     * @param destination the {@code Path} designating the intended download location
     * @throws Exception if something went wrong
     */
    public void checkout(String svnurl, Path destination) throws IOException {
        // append / if not already there
        svnurl = svnurl.replaceFirst("(?<!/)$", "/");
        List<String> dirs = new ArrayList<>();
        dirs.add("");
        while (!dirs.isEmpty()) {
            String dir = dirs.remove(0);
            List<String> entries = dir(svnurl+dir);
            Files.createDirectories(destination.resolve(dir));
            logger.fine("mkdir    "+destination.resolve(dir));
            for (String entry : entries) {
                String direntry = dir+entry;
                if (entry.endsWith("/")) {
                    dirs.add(direntry);
                } else {
                    downloadContent(svnurl+direntry, destination.resolve(direntry));
                    logger.fine("download "+destination.resolve(direntry)+" from "+svnurl+direntry);
                }
            }
        }
    }

}
