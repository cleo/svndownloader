package com.cleo.labs.svndownloader;

import static org.junit.Assert.*;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import org.junit.Test;
import org.w3c.dom.Document;

/**
 * Runs a sample download against plan.io using the credentials found in
 * {@code ~/.m2/settings.xml}. Create a {@code <server>} as follows for this
 * to work:
 * <pre>
 *   &lt;server&gt;
 *     &lt;id&gt;plan.io&lt;/id&gt;
 *     &lt;username&gt;your username&lt;/username&gt;
 *     &lt;password&gt;your password&lt;/password&gt;
 *   &lt;/server&gt;
 * </pre>
 *
 */
public class TestDownload {

    private void setLogLevel(Level level) {
        Logger rootLogger = LogManager.getLogManager().getLogger("");
        rootLogger.setLevel(Level.FINE);
        Arrays.stream(rootLogger.getHandlers()).forEach(h -> h.setLevel(Level.FINE));
    }

    private static Document file2xml(String fn) throws Exception {
        return DocumentBuilderFactory.newInstance()
                                     .newDocumentBuilder()
                                     .parse(new File(fn));
    }

    private static String getPlanioProperty(Document settings, String property) throws XPathExpressionException {
        XPath xPath = XPathFactory.newInstance().newXPath();
        return xPath.compile("/settings/servers/server[id=\"plan.io\"]/"+property).evaluate(settings);
    }

    private SVNDownloader getSVN() throws Exception {
        Document settings = file2xml(System.getProperty("user.home")+"/.m2/settings.xml");
        String username = getPlanioProperty(settings, "username");
        String password = getPlanioProperty(settings, "password");
        return new SVNDownloader(username, password);
    }

    @Test
    public void test() throws Exception {
        setLogLevel(Level.FINE);
        String url = "https://cleo.plan.io/svn/jupiter-alpha/com.cleo.labs.johnt.cloud.pingpong";
        SVNDownloader svn = getSVN();
        try {
            svn.checkout(url, "tmp");
        } catch (IOException e) {
            e.printStackTrace();
            fail();
        }
    }

}
