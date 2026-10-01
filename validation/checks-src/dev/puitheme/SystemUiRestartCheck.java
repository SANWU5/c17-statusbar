package dev.puitheme;

/** Only result fixtures are inspected. This never starts su or signals any process. */
public final class SystemUiRestartCheck {
    private static int checks;
    private static void result(SystemUiRestart.State expected, int exit, String output, boolean timeout) {
        SystemUiRestart.Result result = SystemUiRestart.classify(exit, output, timeout);
        checks++;
        if (result.state != expected || result.success != (expected == SystemUiRestart.State.RESTARTED)
                || result.message.isEmpty()) throw new AssertionError("Restart check " + checks);
    }
    public static void main(String[] args) {
        result(SystemUiRestart.State.RESTARTED, 0, "C17_OK:4321\n", false);
        result(SystemUiRestart.State.RESTARTED, 0, "su notice\r\nC17_OK:4321 4322\r\n", false);
        result(SystemUiRestart.State.FAILED, 1, "C17_OK:4321", false);
        result(SystemUiRestart.State.FAILED, 0, "", false);
        result(SystemUiRestart.State.FAILED, 0, "C17_OK:abc", false);
        result(SystemUiRestart.State.FAILED, 0, "C17_OK:0", false);
        result(SystemUiRestart.State.ROOT_REQUIRED, 21, "", false);
        result(SystemUiRestart.State.ROOT_REQUIRED, 1, "su: Permission denied", false);
        result(SystemUiRestart.State.ROOT_REQUIRED, 1, "C17_ERROR:NOT_ROOT", false);
        result(SystemUiRestart.State.NOT_RUNNING, 22, "C17_ERROR:NOT_RUNNING", false);
        result(SystemUiRestart.State.FAILED, 23, "C17_ERROR:KILL_FAILED", false);
        result(SystemUiRestart.State.FAILED, 24, "C17_ERROR:NOT_RESTARTED", false);
        result(SystemUiRestart.State.TIMEOUT, 0, "C17_OK:4321", true);
        System.out.println("SystemUI restart checks passed: " + checks);
    }
}
