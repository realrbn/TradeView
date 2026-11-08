package de.tradeview.update;

import java.util.regex.Pattern;

/**
 * Loader-independent semantic version comparison (numeric core of any length, optional pre-release,
 * ignored build metadata), matching how Fabric Loader orders mod versions.
 */
final class ModVersion implements Comparable<ModVersion> {
    private static final Pattern FORMAT = Pattern.compile("\\d+(\\.\\d+)*(-[0-9A-Za-z.-]+)?(\\+[0-9A-Za-z.-]+)?");
    private final int[] core;
    private final String[] preRelease;

    private ModVersion(int[] core, String[] preRelease) {
        this.core = core;
        this.preRelease = preRelease;
    }

    static ModVersion parse(String text) {
        if (!FORMAT.matcher(text).matches()) throw new IllegalArgumentException("Not a semantic version: " + text);
        int build = text.indexOf('+');
        if (build >= 0) text = text.substring(0, build);
        int dash = text.indexOf('-');
        String[] numbers = (dash >= 0 ? text.substring(0, dash) : text).split("\\.");
        int[] core = new int[numbers.length];
        for (int i = 0; i < numbers.length; i++) core[i] = Integer.parseInt(numbers[i]);
        String[] preRelease = dash >= 0 ? text.substring(dash + 1).split("\\.", -1) : new String[0];
        for (String part : preRelease) if (part.isEmpty()) throw new IllegalArgumentException("Empty pre-release part: " + text);
        return new ModVersion(core, preRelease);
    }

    @Override public int compareTo(ModVersion other) {
        for (int i = 0; i < Math.max(core.length, other.core.length); i++) {
            int a = i < core.length ? core[i] : 0, b = i < other.core.length ? other.core[i] : 0;
            if (a != b) return Integer.compare(a, b);
        }
        // A pre-release is older than the release it precedes.
        if (preRelease.length == 0 && other.preRelease.length == 0) return 0;
        if (preRelease.length == 0) return 1;
        if (other.preRelease.length == 0) return -1;
        for (int i = 0; i < Math.min(preRelease.length, other.preRelease.length); i++) {
            int result = comparePart(preRelease[i], other.preRelease[i]);
            if (result != 0) return result;
        }
        return Integer.compare(preRelease.length, other.preRelease.length);
    }

    private static int comparePart(String a, String b) {
        boolean numericA = a.chars().allMatch(Character::isDigit), numericB = b.chars().allMatch(Character::isDigit);
        if (numericA && numericB) return new java.math.BigInteger(a).compareTo(new java.math.BigInteger(b));
        if (numericA != numericB) return numericA ? -1 : 1;
        return a.compareTo(b);
    }
}
