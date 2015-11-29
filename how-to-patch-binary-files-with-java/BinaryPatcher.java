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