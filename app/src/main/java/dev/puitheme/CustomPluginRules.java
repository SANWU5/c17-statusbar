// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 aiingjie
package dev.puitheme;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import org.json.JSONArray;
import org.json.JSONObject;

/** A bounded, data-only SystemUI contract. No imported code enters SystemUI. */
public final class CustomPluginRules {
    public static final int MAX_PLUGIN_HOOKS = 32, MAX_PLAN_HOOKS = 64;
    private static final int MAX_TEXT = 256;
    private static final String[] PREFIXES = {"com.android.systemui.", "com.oplus.systemui.",
            "com.oplusos.systemui.", "com.oplus.keyguard."};

    static final class Rule {
        final String pluginId, id, className, method, kind, property, find, replace, prefix, suffix;
        final int argCount;
        final Object value;
        Rule(JSONObject data, boolean aggregate) {
            Set<String> allowed = new HashSet<>();
            java.util.Collections.addAll(allowed,"id","className","method","argCount","kind");
            if (aggregate) allowed.add("pluginId");
            pluginId = aggregate ? identifier(data,"pluginId") : "";
            id = identifier(data,"id");
            className = string(data,"className",240,false);
            boolean permitted = false;
            for (String head : PREFIXES) if (className.startsWith(head)) permitted = true;
            if (!permitted || !className.matches("[a-zA-Z_$][a-zA-Z0-9_$]*(\\.[a-zA-Z_$][a-zA-Z0-9_$]*)+"))
                throw invalid("className must name an explicit SystemUI or Oplus UI class");
            method = string(data,"method",100,false);
            if (!method.matches("[a-zA-Z_$][a-zA-Z0-9_$]*") || method.matches("getClass|clone|finalize|wait|notify|notifyAll|equals|hashCode|toString"))
                throw invalid("Unsupported method");
            Object count = data.opt("argCount");
            if (!(count instanceof Number) || ((Number) count).doubleValue() != ((Number) count).intValue()
                    || ((Number) count).intValue() < 0 || ((Number) count).intValue() > 8)
                throw invalid("argCount must be an integer from 0 to 8");
            argCount = ((Number) count).intValue();
            kind = string(data,"kind",20,false);
            if (kind.equals("text")) {
                java.util.Collections.addAll(allowed,"find","replace","prefix","suffix");
                find = optionalString(data,"find"); replace = optionalString(data,"replace");
                prefix = optionalString(data,"prefix"); suffix = optionalString(data,"suffix");
                if (data.has("find") != data.has("replace") || data.has("find") && find.isEmpty())
                    throw invalid("Text replacement requires a nonempty find and a replace");
                if (!data.has("find") && prefix.isEmpty() && suffix.isEmpty()) throw invalid("Text rule has no action");
                property = ""; value = null;
            } else if (kind.equals("property")) {
                java.util.Collections.addAll(allowed,"property","value");
                property = string(data,"property",30,false);
                if (!property.matches("alpha|translationX|translationY|scaleX|scaleY")) throw invalid("Unsupported View property");
                Object number = data.opt("value");
                if (!(number instanceof Number)) throw invalid("Property value must be a number");
                double n = ((Number) number).doubleValue();
                double low = property.equals("alpha") ? 0 : property.startsWith("scale") ? .1 : -200;
                double high = property.equals("alpha") ? 1 : property.startsWith("scale") ? 4 : 200;
                if (!Double.isFinite(n) || n < low || n > high) throw invalid("Property value is outside its range");
                value = number; find = replace = prefix = suffix = "";
            } else if (kind.equals("result")) {
                allowed.add("value"); property = find = replace = prefix = suffix = "";
                value = data.opt("value");
                if (!(value instanceof Boolean) && !(value instanceof Number)
                        && !(value instanceof String && ((String) value).length() == 1))
                    throw invalid("Result must be a primitive literal (strings only for char)");
                if (value instanceof Number && !Double.isFinite(((Number) value).doubleValue())) throw invalid("Nonfinite result");
            } else throw invalid("Unsupported hook kind");
            for (Iterator<String> keys = data.keys(); keys.hasNext();) {
                if (!allowed.contains(keys.next())) throw invalid("Unknown hook field");
            }
        }

        Object resultFor(Class<?> type) {
            if (type == boolean.class && value instanceof Boolean) return value;
            if (type == char.class && value instanceof String && ((String) value).length() == 1) return ((String) value).charAt(0);
            if (!(value instanceof Number)) throw invalid("Hook result type differs from its literal");
            Number number = (Number) value; double n = number.doubleValue();
            if (type == double.class) return n;
            if (type == float.class && Math.abs(n) <= Float.MAX_VALUE) return number.floatValue();
            if (n != Math.rint(n)) throw invalid("Integral result requires an integer");
            if (type == byte.class && n >= Byte.MIN_VALUE && n <= Byte.MAX_VALUE) return number.byteValue();
            if (type == short.class && n >= Short.MIN_VALUE && n <= Short.MAX_VALUE) return number.shortValue();
            if (type == int.class && n >= Integer.MIN_VALUE && n <= Integer.MAX_VALUE) return number.intValue();
            if (type == long.class) {
                // JSON doubles cannot represent the inclusive upper long boundary exactly.
                try { return new java.math.BigDecimal(number.toString()).longValueExact(); }
                catch (ArithmeticException invalid) { throw invalid("Long result is outside its range"); }
            }
            throw invalid("Hook result must match a nonvoid primitive return type");
        }

        Method resolve(ClassLoader loader) throws ReflectiveOperationException {
            Class<?> type = Class.forName(className,false,loader);
            Method resolved = null;
            for (Method candidate : type.getDeclaredMethods()) if (candidate.getName().equals(method)
                    && candidate.getParameterCount() == argCount) {
                if (resolved != null) throw new NoSuchMethodException("Ambiguous overload; use a distinct argument count");
                resolved = candidate;
            }
            if (resolved == null) throw new NoSuchMethodException("No exact declared method");
            int flags = resolved.getModifiers();
            if (Modifier.isNative(flags) || Modifier.isAbstract(flags) || resolved.isBridge() || resolved.isSynthetic())
                throw new NoSuchMethodException("Method has no supported Java body");
            if (kind.equals("result")) resultFor(resolved.getReturnType());
            else if (Modifier.isStatic(flags) || !android.view.View.class.isAssignableFrom(type)
                    || kind.equals("text") && !android.widget.TextView.class.isAssignableFrom(type))
                throw new NoSuchMethodException("View rule requires its own native View instance");
            return resolved;
        }
    }

    /** Called before an import can be enabled or its data crosses the provider. */
    public static void validate(JSONObject descriptor) {
        Object hooks = descriptor.opt("hooks");
        if (hooks == null) return;
        if (!(hooks instanceof JSONArray)) throw invalid("hooks must be an array");
        parse((JSONArray) hooks,false);
    }

    static List<Rule> parse(JSONArray hooks, boolean aggregate) {
        if (hooks.length() > (aggregate ? MAX_PLAN_HOOKS : MAX_PLUGIN_HOOKS)) throw invalid("Too many hook rules");
        List<Rule> result = new ArrayList<>(); Set<String> ids = new HashSet<>();
        for (int i = 0; i < hooks.length(); i++) {
            Object data = hooks.opt(i);
            if (!(data instanceof JSONObject)) throw invalid("Every hook must be an object");
            Rule rule = new Rule((JSONObject) data,aggregate);
            if (!ids.add(rule.pluginId + "/" + rule.id)) throw invalid("Duplicate hook id");
            result.add(rule);
        }
        return result;
    }

    private static String identifier(JSONObject data,String key) {
        String value = string(data,key,80,false);
        if (!value.matches("[a-zA-Z0-9_.-]{1,80}")) throw invalid("Invalid " + key);
        return value;
    }
    private static String optionalString(JSONObject data,String key) {
        return data.has(key) ? string(data,key,MAX_TEXT,true) : "";
    }
    private static String string(JSONObject data,String key,int max,boolean empty) {
        Object raw = data.opt(key);
        if (!(raw instanceof String) || ((String) raw).length() > max || !empty && ((String) raw).isEmpty())
            throw invalid("Invalid " + key);
        String value = (String) raw;
        if (value.indexOf('\0') >= 0) throw invalid("NUL is not supported");
        return value;
    }
    private static IllegalArgumentException invalid(String text) { return new IllegalArgumentException(text); }
    private CustomPluginRules() { }
}
