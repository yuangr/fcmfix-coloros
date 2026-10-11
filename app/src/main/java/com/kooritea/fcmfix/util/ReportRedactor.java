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
    private final Map<Pattern, String> packages = new LinkedHashMap<>();
    public ReportRedactor(List<String> installedPackages, Set<String> keep) {
        List<String> names = new ArrayList<>(new java.util.TreeSet<>(installedPackages));
        names.sort(Comparator.comparingInt(String::length).reversed().thenComparing(Comparator.naturalOrder()));
        int index = 0;
        for (String name : names) {
            if (keep.contains(name)) continue;
            packages.put(Pattern.compile("(?<![A-Za-z0-9_])" + Pattern.quote(name) + "(?![A-Za-z0-9_])"),
                    "[应用" + (++index) + "]");
        }
    }
    public String redact(String line) {
        String result = line;
        for (Map.Entry<Pattern, String> entry : packages.entrySet())
            result = entry.getKey().matcher(result).replaceAll(java.util.regex.Matcher.quoteReplacement(entry.getValue()));
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
