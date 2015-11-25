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