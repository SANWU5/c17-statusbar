package dev.puitheme;

import io.github.libxposed.api.XposedInterface;
import java.lang.reflect.Executable;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

/** The on-demand hook cost accounting must stay invisible to hooks: same arguments, results, exceptions and
 * native calls, and nothing recorded while it is off. */
public final class HookTraceCheck {
    private static int checks;
    private static void check(boolean value, String why) { checks++; if (!value) throw new AssertionError(why); }

    private static final class Native implements XposedInterface.Chain {
        final Method method; final Object receiver; final Object[] args; int proceeds; Object[] lastArgs; Object lastReceiver;
        Native(Method method, Object receiver, Object... args) { this.method = method; this.receiver = receiver; this.args = args; }
        @Override public Executable getExecutable() { return method; }
        @Override public Object getThisObject() { return receiver; }
        @Override public List<Object> getArgs() { return Arrays.asList(args); }
        @Override public Object getArg(int index) { return args[index]; }
        @Override public Object proceed() { proceeds++; lastArgs = args; lastReceiver = receiver; return "native"; }
        @Override public Object proceed(Object[] changed) { proceeds++; lastArgs = changed; lastReceiver = receiver; return "native-args"; }
        @Override public Object proceedWith(Object other) { proceeds++; lastArgs = args; lastReceiver = other; return "native-with"; }
        @Override public Object proceedWith(Object other, Object[] changed) { proceeds++; lastArgs = changed; lastReceiver = other; return "native-with-args"; }
    }

    public static void main(String[] args) throws Throwable {
        Method method = String.class.getDeclaredMethod("concat", String.class);
        check(HookTrace.label("BADGE", method).equals("BADGE String.concat"), "label names the owner, class and method");
        check(HookTrace.label("TEXT", StringBuilder.class.getDeclaredConstructor()).equals("TEXT StringBuilder.<init>"), "constructors are labelled");
        StringBuilder longOwner = new StringBuilder();
        for (int i = 0; i < 200; i++) longOwner.append('x');
        check(HookTrace.label(longOwner.toString(), method).length() <= 120, "labels fit a platform trace section name");
        HookTrace.Site site = HookTrace.site("CHECK", method);
        check(site == HookTrace.site("CHECK", method), "one site per owner and method");
        check(!HookTrace.on() && !HookTrace.refresh() && !HookTrace.on(), "off without the switch, and reading the switch changes nothing");

        // A hook body run through the accounting sees the same chain contract.
        Native chain = new Native(method, "receiver", "argument");
        Object result = HookTrace.run(site, c -> {
            check(c.getExecutable() == method && c.getThisObject() == "receiver", "method and receiver are forwarded");
            check(c.getArgs().size() == 1 && c.getArg(0) == "argument", "arguments are forwarded");
            return c.proceed() + "+hook";
        }, chain);
        check("native+hook".equals(result) && chain.proceeds == 1, "the native method runs once and its result is returned");
        Object[] changed = {"changed"};
        check("native-args".equals(HookTrace.run(site, c -> c.proceed(changed), chain)) && chain.lastArgs == changed, "changed arguments reach the native method");
        check("native-with".equals(HookTrace.run(site, c -> c.proceedWith("other"), chain)) && chain.lastReceiver == "other", "another receiver reaches the native method");
        check("native-with-args".equals(HookTrace.run(site, c -> c.proceedWith("third", changed), chain))
                && chain.lastReceiver == "third" && chain.lastArgs == changed, "receiver and arguments together reach the native method");
        check(HookTrace.run(site, c -> "skipped", chain).equals("skipped") && chain.proceeds == 4, "a hook may skip the native method");
        IllegalStateException failure = new IllegalStateException("native failure");
        try { HookTrace.run(site, c -> { throw failure; }, chain); check(false, "the hook's exception must propagate"); }
        catch (IllegalStateException seen) { check(seen == failure, "the same exception object propagates"); }

        check(site.calls == 6, "every run is counted, including the failing one");
        check(site.nanos >= site.nativeNanos && site.nativeNanos >= 0, "native time is part of the total");
        JSONObject report = HookTrace.report(10);
        JSONArray top = report.getJSONArray("top");
        boolean listed = false;
        for (int i = 0; i < top.length(); i++) if ("CHECK String.concat".equals(top.getJSONArray(i).getString(0)))
            listed = top.getJSONArray(i).getLong(1) == 6;
        check(listed && report.getLong("calls") >= 6 && report.getInt("mode") == HookTrace.OFF, "the report lists the site with its call count");
        check(HookTrace.report(0).getJSONArray("top").length() == 0, "the row limit is honoured");
        HookTrace.reset();
        check(site.calls == 0 && site.nanos == 0 && site.nativeNanos == 0 && HookTrace.report(10).getLong("calls") == 0, "reset clears the counters");
        System.out.println("HookTraceCheck: " + checks + " checks passed");
    }
}
