package fr.vorace;

/** Lance tous les tests sans serveur. */
public final class Tests {
    public static void main(String[] args) throws Exception {
        int code = 0;
        for (String c : new String[]{"fr.vorace.pack.TestFusion", "fr.vorace.hebergement.TestHttp", "fr.vorace.objets.TestObjets", "fr.vorace.meubles.TestMeubles"}) {
            System.out.println("== " + c);
            Process p = new ProcessBuilder(System.getProperty("java.home") + "/bin/java", "-cp", System.getProperty("java.class.path"), c)
                .inheritIO().start();
            code |= p.waitFor();
        }
        System.exit(code);
    }
}
