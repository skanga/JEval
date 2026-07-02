package dev.jeval;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class DeepEval {
    public static final String VERSION = "4.0.7";

    private DeepEval() {
    }

    public static boolean compareVersions(String version1, String version2) {
        var first = normalize(version1);
        var second = normalize(version2);
        for (var i = 0; i < Math.min(first.size(), second.size()); i++) {
            var comparison = Integer.compare(first.get(i), second.get(i));
            if (comparison != 0) {
                return comparison > 0;
            }
        }
        return first.size() > second.size();
    }

    public static boolean updateWarningOptIn() {
        return updateWarningOptIn(System.getenv());
    }

    static boolean updateWarningOptIn(Map<String, String> env) {
        return "1".equals(env.get("DEEPEVAL_UPDATE_WARNING_OPT_IN"));
    }

    private static List<Integer> normalize(String version) {
        var normalized = version.replaceFirst("(\\.0+)*$", "");
        var values = new ArrayList<Integer>();
        for (var part : normalized.split("\\.")) {
            values.add(Integer.parseInt(part));
        }
        return values;
    }
}
