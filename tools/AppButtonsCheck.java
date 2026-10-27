import com.homestead.launcher.AppButtons;

/** java -cp classes tools/AppButtonsCheck.java : lines are real captures from an AFTSS (Fire OS 7.7.1.6). */
public class AppButtonsCheck {
    static int fails;
    static void eq(String what, int got, int want) {
        if (got != want) { fails++; System.out.println("FAIL " + what + ": got " + got + " want " + want); }
    }
    static String t(long ms) { return String.format("%d.%03d", ms / 1000, ms % 1000); }
    public static void main(String[] a) {
        long base = 1_800_000_000_000L;
        String AM = " I ActivityManager( 538): START u0 {";
        String launch = AM + "act=android.intent.action.VIEW (has data) flg=0x10000000 cmp=com.amazon.venezia/.pdi.AppLaunchActivity (has extras)} from uid 1000";
        String nflx = AM + "act=android.intent.action.MAIN cat=[android.intent.category.LAUNCHER] flg=0x10000000 pkg=com.netflix.ninja cmp=com.netflix.ninja/.MainActivity (has extras)} from uid 1000";
        String nflxUser = nflx.replace("from uid 1000", "from uid 10041");
        String prime = AM + "act=android.intent.action.MAIN cat=[android.intent.category.LEANBACK_LAUNCHER] flg=0x10000000 pkg=com.amazon.firebat cmp=com.amazon.firebat/com.amazon.pyrocore.IgnitionActivity (has extras)} from uid 1000";
        String primeUser = prime.replace("from uid 1000", "from uid 10041");
        String asin = " I AmazonAppstore.AppLaunchIntentProcessor( 1236): Evaluating launch decision for asin= ";

        AppButtons b = new AppButtons();
        eq("netflix from system", b.match(t(base) + nflx, 0), 1);
        eq("netflix from launcher uid is not the button", b.match(t(base) + nflxUser, 0), -1);

        b = new AppButtons();
        eq("partner launch alone", b.match(t(base) + launch, 0), -1);
        eq("youtube asin", b.match(t(base + 300) + asin + "B07T771SPH", 0), 0);
        eq("same asin again, no new partner launch", b.match(t(base + 400) + asin + "B07T771SPH", 0), -1);

        b = new AppButtons();
        b.match(t(base) + launch, 0);
        eq("disney asin", b.match(t(base + 500) + asin + "B07Y8SJGCV", 0), 3);

        b = new AppButtons();
        b.match(t(base) + launch, 0);
        eq("prime after partner launch", b.match(t(base + 17) + prime, 0), 2);
        eq("prime from launcher uid", new AppButtons().match(t(base) + primeUser, 0), -1);
        eq("prime with no partner launch", new AppButtons().match(t(base) + prime, 0), -1);

        b = new AppButtons();
        b.match(t(base) + launch, 0);
        eq("asin too late", b.match(t(base + 4000) + asin + "B07T771SPH", 0), -1);
        eq("unknown asin", new AppButtons().match(t(base) + asin + "B000000000", 0), -1);

        String details = AM + "flg=0x8080000 cmp=com.amazon.venezia/.details.AppDetailsActivity (has extras)} from uid 10165";
        b = new AppButtons();
        b.match(t(base) + launch, 0);
        eq("youtube asin", b.match(t(base + 300) + asin + "B07T771SPH", 0), 0);
        eq("appstore details page repeats the button", b.match(t(base + 1700) + details, 0), 0);
        eq("only once", b.match(t(base + 1800) + details, 0), -1);
        eq("details page much later is the user browsing", new AppButtons().match(t(base + 20000) + details, 0), -1);
        b = new AppButtons();
        b.match(t(base) + launch, 0); b.match(t(base + 300) + asin + "B07T771SPH", 0);
        eq("details page after 6 s is not ours", b.match(t(base + 6300) + details, 0), -1);

        b = new AppButtons();
        eq("replayed old line ignored", b.match(t(base) + nflx, base + 1000), -1);
        eq("unrelated line", b.match(t(base) + " I Foo( 1): hello", 0), -1);
        System.out.println(fails == 0 ? "OK" : fails + " failed");
        System.exit(fails == 0 ? 0 : 1);
    }
}
