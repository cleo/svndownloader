import java.io.IOException;
import java.nio.file.Paths;

import com.cleo.labs.svndownloader.SVNDownloader;

public class Example {

    public static void main(String[] args) {
        SVNDownloader svn = new SVNDownloader("myuser", "mypassword");
        try {
            svn.checkout("https://svn.example.com/svn/project/branch/", 
                Paths.get("project"));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}
