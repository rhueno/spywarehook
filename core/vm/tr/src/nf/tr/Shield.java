package nf.tr;

import java.nio.file.Path;
import java.nio.file.Paths;

public final class Shield {

    public static void main(String[] args) throws Exception {
        Path input = null;
        Path output = null;
        Path config = Paths.get("vm/config.json");

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--input", "-i" -> input = Paths.get(args[++i]);
                case "--output", "-o" -> output = Paths.get(args[++i]);
                case "--config", "-c" -> config = Paths.get(args[++i]);
                default -> {}
            }
        }

        if (input == null || output == null) {
            System.err.println("usage: Shield -i in.jar -o out.jar [-c config.json]");
            System.exit(1);
        }

        Config cfg = CfgLoad.read(config);
        System.out.println("[*] input:  " + input);
        System.out.println("[*] output: " + output);
        new Engine(cfg).run(input, output);
    }

    private Shield() {}
}
