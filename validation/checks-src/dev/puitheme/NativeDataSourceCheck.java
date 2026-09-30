package dev.puitheme;

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import kotlinx.coroutines.flow.StateFlow;

/** Subscription ownership and live-state fallback, using no Android framework or radio changes. */
public final class NativeDataSourceCheck {
    private static int checks;
    private static void equal(Object expected, Object actual) {
        checks++;
        if (!expected.equals(actual)) throw new AssertionError(expected + " != " + actual);
    }
    private static final class Flow implements StateFlow<Object> {
        Object value;
        @Override public Object getValue() { return value; }
    }
    private static final class ColdFlow { public Object getValue() { return icon("5G"); } }
    private static final class Name {
        final String name;
        Name(String name) { this.name = name; }
        public String getName() { return name; }
    }
    private static final class TextModel {
        final Object type;
        TextModel(Object type) { this.type = type; }
        public Object getNetworkTypeText() { return type; }
    }
    private static final class Icon {
        final Object model;
        Icon(Object model) { this.model = model; }
        public Object getNetworkTypeTextModel() { return model; }
    }
    private static Object icon(String name) { return new Icon(new TextModel(new Name(name))); }
    private static final class MobileInteractor {
        int subId;
        Object source;
        MobileInteractor(int subId, Object source) { this.subId = subId; this.source = source; }
        public int getSubscriptionId() { return subId; }
        public Object getNetworkTypeIconGroup() { return source; }
    }
    private static final class IconsInteractor {
        final Map<Integer, MobileInteractor> subscriptions = new HashMap<>();
        public Object getMobileConnectionInteractorForSubId(int subId) { return subscriptions.get(subId); }
    }
    private static class BaseOwner {
        private final Object interactor;
        BaseOwner(Object interactor) { this.interactor = interactor; }
    }
    private static final class Owner extends BaseOwner { Owner(Object interactor) { super(interactor); } }
    @SuppressWarnings("unchecked") private static void clearWeakOwner(int subId) throws Exception {
        Field field = NativeDataSource.class.getDeclaredField("SOURCES"); field.setAccessible(true);
        Object entry = ((Map<Integer, ?>) field.get(null)).get(subId);
        Field owner = entry.getClass().getDeclaredField("owner"); owner.setAccessible(true);
        ((WeakReference<Object>) owner.get(entry)).clear();
    }
    public static void main(String[] args) throws Exception {
        NativeDataSource.clear(); equal("", NativeDataSource.label(1)); equal("", NativeDataSource.label(-1));
        Flow first = new Flow(), second = new Flow(); first.value = icon("5G+"); second.value = icon("LTE");
        IconsInteractor icons = new IconsInteractor();
        MobileInteractor mobile = new MobileInteractor(1, first);
        icons.subscriptions.put(1, mobile); icons.subscriptions.put(2, new MobileInteractor(2, second));
        Owner owner = new Owner(icons);
        NativeDataSource.capture(owner, 1); NativeDataSource.capture(owner, 2);
        equal("5G+", NativeDataSource.label(1)); equal("LTE", NativeDataSource.label(2)); equal("", NativeDataSource.label(3));
        first.value = icon("NR NSA"); equal("NR NSA", NativeDataSource.label(1));
        first.value = null; equal("", NativeDataSource.label(1)); equal("LTE", NativeDataSource.label(2));
        first.value = new Icon(null); equal("", NativeDataSource.label(1));
        first.value = new Icon(new TextModel(null)); equal("", NativeDataSource.label(1));
        first.value = icon(null); equal("", NativeDataSource.label(1));
        first.value = icon(""); equal("", NativeDataSource.label(1));
        first.value = new Object(); equal("", NativeDataSource.label(1));
        first.value = icon("3G"); equal("3G", NativeDataSource.label(1));
        mobile.subId = 9; equal("", NativeDataSource.label(1)); mobile.subId = 1;
        NativeDataSource.capture(owner, 9); equal("", NativeDataSource.label(9));
        NativeDataSource.capture(null, 1); equal("", NativeDataSource.label(1));
        NativeDataSource.capture(owner, 1); equal("3G", NativeDataSource.label(1));
        NativeDataSource.capture(new Object(), 1); equal("", NativeDataSource.label(1));
        NativeDataSource.capture(owner, 1); mobile.source = new ColdFlow(); NativeDataSource.capture(owner, 1);
        equal("", NativeDataSource.label(1));
        mobile.source = first; NativeDataSource.capture(owner, 1); equal("3G", NativeDataSource.label(1));
        clearWeakOwner(1); equal("", NativeDataSource.label(1)); equal("LTE", NativeDataSource.label(2));
        NativeDataSource.clear(2); equal("", NativeDataSource.label(2));
        NativeDataSource.capture(owner, 2); equal("LTE", NativeDataSource.label(2));
        NativeDataSource.clear(); equal("", NativeDataSource.label(2));
        System.out.println(checks + " native subscription source, live-flow and no-history fallback checks passed");
    }
}
