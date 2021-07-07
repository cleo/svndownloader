# SVNDownloader

This simple class recursively downloads a folder location from an SVN repository
to a Path location on disk.

Authentication to the SVN repository uses Basic credentials. Pass the `username`
and `password` to the constructor. Then call the `svnCheckout` method, passing
the URL of the project to download and a `Path` location destination:

```java
import com.cleo.labs.snvdownloader.SVNDownloader;
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
```

The SVNDownloader logs its progress at `Level.FINE` in the Java `ijava.util.logging`
subsystem. The `checkout` method throws an exception in case of any problems. Otherwise
it returns when the download is complete.
