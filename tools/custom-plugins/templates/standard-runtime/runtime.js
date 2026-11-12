// SPDX-License-Identifier: MIT
// Host dispatches lifecycle and actions on the owning main thread.
(function () {
    var current = C17.state;
    function publish() {
        var text = String(C17.load("last-record", "尚无记录")) || "尚无记录";
        // The stored record stays complete; status text has a 4096-character limit.
        if (text.length > 4096) text = text.substring(0, 4088) + "…（已截断）";
        C17.publishStatus({journal_status: text});
    }
    function save() {
        C17.save("last-record", JSON.stringify({note: String(current.note || ""), savedAt: new Date().toISOString()}));
        publish();
    }
    C17.onStateChanged(function (state) {
        current = state;
        if (current.record_changes) save();
    });
    C17.onAction("save_record", function () { save(); return {message: "记录已保存"}; });
    C17.onAction("clear_record", function () {
        C17.save("last-record", ""); publish(); return {message: "记录已清空"};
    });
    publish();
    // This example owns no hooks/views/threads. Added native adapters must register
    // C17.onCleanup to restore their own resources; don't delete saved data there.
}());
