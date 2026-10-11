package com.kooritea.fcmfix.util;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/** Known non-target package names and IPs only. This does not promise full anonymization. */
public final class ReportRedactor {
    private static final Pattern IPV4 = Pattern.compile("(?<![\\w.])(?:[0-9]{1,3}\\.){3}[0-9]{1,3}(?![\\w.])");
    private static final Pattern IP_TOKEN = Pattern.compile("[0-9A-Fa-f:.%]+");
    private final Map<String, String> packages = new LinkedHashMap<>();
    private final Pattern packageNames;
    public ReportRedactor(List<String> installedPackages, Set<String> keep) {
        java.util.TreeSet<String> knownNames = new java.util.TreeSet<>(installedPackages);
        knownNames.addAll(keep);
        List<String> names = new ArrayList<>(knownNames);
        names.sort(Comparator.comparingInt(String::length).reversed().thenComparing(Comparator.naturalOrder()));
        int index = 0;
        List<String> alternatives = new ArrayList<>();
        for (String name : names) {
            if (name.isEmpty()) continue;
            alternatives.add(Pattern.quote(name));
            packages.put(name, keep.contains(name) ? name : "[应用" + (++index) + "]");
        }
        // Match once, longest name first, including names that must remain readable.
        // Repeated replacements let short packages such as "android" corrupt a kept
        // package's interior (com.google.android.gms) and component/action names.
        packageNames = Pattern.compile(alternatives.isEmpty() ? "(?!)"
                : "(?<![A-Za-z0-9_])(?:" + String.join("|", alternatives) + ")(?![A-Za-z0-9_])");
    }
    public String redact(String line) {
        java.util.regex.Matcher names = packageNames.matcher(line);
        StringBuffer packageOutput = new StringBuffer();
        while (names.find()) names.appendReplacement(packageOutput,
                java.util.regex.Matcher.quoteReplacement(packages.get(names.group())));
        names.appendTail(packageOutput);
        String result = packageOutput.toString();
        result = IPV4.matcher(result).replaceAll("[IPv4]");
        java.util.regex.Matcher tokens = IP_TOKEN.matcher(result);
        StringBuffer output = new StringBuffer();
        while (tokens.find()) {
            String token = tokens.group();
            boolean ipv6 = token.contains("::") || token.chars().filter(c -> c == ':').count() >= 3;
            tokens.appendReplacement(output, java.util.regex.Matcher.quoteReplacement(ipv6 ? "[IPv6]" : token));
        }
        tokens.appendTail(output);
        return output.toString();
    }
}
