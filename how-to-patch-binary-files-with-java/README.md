---
posted: 2010-02-18
tags: [java, snippet]
---

# How to patch binary files with Java

Recently, I wanted to create an "intelligent" binary patcher, which not
only replaces some chunk of binary data at a file's predefined offset,
but instead performs search and replace. Here is the Java class I came
up with.

```java
import java.io.*;
import java.math.BigInteger;

public class BinaryPatcher {
    private String content;

    private final String filename;
    private final String searchPattern;
    private final String replacePattern;

    public BinaryPatcher(String filename, String searchPattern,
            String replacePattern) throws IOException {

        this.filename = filename;
        this.searchPattern = convertHexStringToBinaryString(searchPattern);
        this.replacePattern = convertHexStringToBinaryString(replacePattern);

        initializeContent();
    }

    public boolean isPatchable() {
        return getContent().contains(searchPattern);
    }

    public void patch() throws IOException {
        if (!isPatchable()) {
            throw new IOException("Search pattern not found");
        }

        String originalContent = getContent();
        String patchedContent = originalContent.replace(searchPattern,
                replacePattern);

        saveStringToFile(patchedContent, filename);
    }

    public void createBackup(String filename) throws IOException {
        saveStringToFile(getContent(), filename);
    }

    private static String convertHexStringToBinaryString(String theHexString) {
        byte[] byteSequence = new BigInteger(theHexString, 16).toByteArray();
        return new String(byteSequence);
    }

    private void initializeContent() throws IOException {
        byte[] buffer = new byte[(int) new File(filename).length()];
        FileInputStream in = new FileInputStream(filename);
        in.read(buffer);
        in.close();
        content = new String(buffer);
    }

    private void saveStringToFile(String content, String filename)
            throws IOException {
        FileOutputStream out = new FileOutputStream(filename);
        out.write(content.getBytes());
        out.close();
    }

    private String getContent() {
        return content;
    }
}
```

The usage is pretty straight forward. Let's have a look at an basic
example:

```java
class Tester {
    public static void main(String[] args) {
        try {
            String filename = "patchme";
            BinaryPatcher p = new BinaryPatcher(filename, "AB00FF14", "AB11FF14");

            if (p.isPatchable()) {
                p.createBackup(filename + ".org");
                p.patch();
            }
        } catch(IOException e) {
            System.out.println("ERROR: " + e.getMessage());
        }
    }
}
```

We try to patch the file *patchme*. The search and replace binary data
is given by hex sequences, in this example *AB00FF14* is replaced by
*AB11FF14*. If the file is patchable, i.e. it contains the search
pattern, a backup named *patchme.org* is created an finally, the
original file is modified.

That's all. Pretty simple but useful.