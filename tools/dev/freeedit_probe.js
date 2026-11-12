// Read-only, bounded UI-thread snapshot. No title, notification, preference or image content.
(function () {
    function readField(owner, name) {
        if (!owner) return { available: false, value: null };
        var cls = owner instanceof java.lang.Class ? owner : owner.getClass();
        while (cls) {
            try {
                var member = cls.getDeclaredField(name); member.setAccessible(true);
                return { available: true, value: member.get(owner instanceof java.lang.Class ? null : owner) };
            } catch (ignored) { cls = cls.getSuperclass(); }
        }
        return { available: false, value: null };
    }
    function field(owner, name) {
        return readField(owner, name).value;
    }
    function hasValue(owner, name) {
        var found = readField(owner, name);
        return found.available ? found.value !== null : null;
    }
    function invoke(owner, name) {
        if (!owner) return null;
        try { var method = owner.getClass().getMethod(name); return method.invoke(owner, []); }
        catch (ignored) { return null; }
    }
    function identity(value) {
        return value ? { type: String(value.getClass().getName()), id: Number(java.lang.System.identityHashCode(value)) } : null;
    }
    function flags(owner, names) {
        var out = {};
        for (var i = 0; i < names.length; i++) {
            var value = field(owner, names[i]);
            out[names[i]] = value === null ? null : String(value);
        }
        return out;
    }
    function booleanFlags(owner, names) {
        var out = {};
        for (var i = 0; i < names.length; i++) {
            var value = field(owner, names[i]);
            out[names[i]] = value === null ? null : String(value) === "true";
        }
        return out;
    }
    function view(value) {
        var out = identity(value); if (!out) return null;
        try {
            out.width = value.getWidth(); out.height = value.getHeight();
            out.left = value.getLeft(); out.top = value.getTop();
            out.translationX = value.getTranslationX(); out.translationY = value.getTranslationY();
            out.scaleX = value.getScaleX(); out.scaleY = value.getScaleY();
            out.alpha = value.getAlpha(); out.visibility = value.getVisibility();
            out.attached = value.isAttachedToWindow(); out.shown = value.isShown();
            out.parent = identity(value.getParent());
        } catch (unavailable) { out.geometryUnavailable = true; }
        return out;
    }
    function animation(value) {
        var out = identity(value); if (!out) return null;
        out.flags = flags(value, ["isDragAnimRunning", "isDeformationEnded", "isMoveAnimEnded"]);
        var names = ["pressAnim", "moveAnim", "curViewHolder", "copyViewProxy"];
        for (var i = 0; i < names.length; i++) out[names[i]] = identity(field(value, names[i]));
        var press = field(value, "pressAnim"), move = field(value, "moveAnim");
        out.pressScale = motion(field(press, "scaleAnimation"));
        out.moveX = motion(field(move, "xAnimation")); out.moveY = motion(field(move, "yAnimation"));
        out.moveFlags = flags(move, ["followLayoutChange"]);
        var frame = invoke(field(value, "curViewHolder"), "getDeformationContainer");
        out.deformationFlags = flags(field(frame, "fixedResizeHelperCallback"), ["isDeforming"]);
        return out;
    }
    function motion(value) {
        var out = identity(value); if (!out) return null;
        out.flags = flags(value, ["mRunning"]);
        var running = invoke(value, "isRunning"); out.running = running === null ? null : String(running);
        return out;
    }
    function sequence(value, project) {
        var out = []; if (!value) return out;
        try {
            var iterator = value.iterator();
            while (iterator.hasNext() && out.length < 64) out.push(project(iterator.next()));
        } catch (ignored) { out.push({ unavailable: true, source: identity(value) }); }
        return out;
    }
    function root(value) {
        var out = identity(value); if (!out) return null;
        out.flags = flags(value, ["isEditMode", "isAddingMode"]);
        out.exitPending = hasValue(value, "exitEditRunnableAfterDragAnim");
        var drag = field(value, "dragHelper"); out.dragHelper = identity(drag);
        if (drag) {
            var flow = invoke(drag, "getDragAnimationStateFlow");
            out.dragState = identity(invoke(flow, "getValue"));
            out.dragFlags = flags(drag, ["isDragMode", "hasDropped"]);
            var names = ["dragViewHolder", "copyViewProxy", "outputDragData"];
            for (var i = 0; i < names.length; i++) out[names[i]] = identity(field(drag, names[i]));
            var animations = field(drag, "animationList");
            out.animationListAvailable = animations !== null;
            out.animations = sequence(animations, animation);
            out.currentAnimation = animation(field(drag, "dragAnimationHelper"));
        }
        return out;
    }
    var result = { schema: 1, uptimeMillis: Number(android.os.SystemClock.uptimeMillis()), sessions: [], roots: [] };
    var cls = C17.findClass("dev.c17.plugins.freeedit.QsCardProjectionRuntime");
    var sessions = field(cls, "sessions");
    if (sessions) result.sessions = sequence(sessions.values(), function (session) {
        var out = { flags: flags(session, ["phase", "projected", "restoring", "failed", "paused", "detached", "editorEditing", "nativeEditInProgress", "awaitingEdit"]), holders: [] };
        var viewModel = field(session, "viewModel"); out.viewModel = identity(viewModel ? viewModel.get() : null);
        var weak = field(session, "controlRoot"); out.root = root(weak ? weak.get() : null);
        var state = field(session, "latestState"); out.latestState = identity(state);
        out.latestStateFlags = flags(state, ["isEditMode"]);
        var holders = field(session, "holders");
        if (holders) out.holders = sequence(holders.keySet(), function (holder) {
            var frame = invoke(holder, "getDeformationContainer"), inner = invoke(frame, "getCurrInnerView");
            return { holder: identity(holder), flags: flags(holder, ["isEditMode", "isDragMode", "isBeingDragged", "dragResult"]), frame: view(frame), inner: view(inner), creator: identity(field(frame, "tileViewCreator")) };
        });
        return out;
    });
    var bridge = field(C17.findClass("dev.c17.plugins.freeedit.QsCardEditorBridge"), "INSTANCE");
    var bridgeSessions = field(bridge, "sessions");
    result.bridge = { available: bridgeSessions !== null, sessions: [] };
    if (bridgeSessions) result.bridge.sessions = sequence(bridgeSessions.entrySet(), function (entry) {
        var session = entry.getValue();
        return { viewModel: identity(entry.getKey()), session: identity(session),
            flags: booleanFlags(session, ["editing", "dirty", "resizing"]),
            hasDraggingId: hasValue(session, "draggingId") };
    });
    var navigation = field(C17.findClass("dev.c17.plugins.freeedit.QsSmallTilesVisibility"), "sessions");
    if (navigation) result.roots = sequence(navigation.values(), function (session) {
        var weak = field(session, "component"); return root(weak ? weak.get() : null);
    });
    return JSON.stringify(result);
}());
