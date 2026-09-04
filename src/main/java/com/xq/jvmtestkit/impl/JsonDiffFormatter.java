package com.xq.jvmtestkit.impl;

import com.github.difflib.DiffUtils;
import com.github.difflib.patch.AbstractDelta;
import com.github.difflib.patch.Patch;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

final class JsonDiffFormatter {
    private JsonDiffFormatter() {
    }

    static String format(String expected, String actual) {
        List<String> expectedLines = lines(expected);
        List<String> actualLines = lines(actual);
        Patch<String> patch = DiffUtils.diff(expectedLines, actualLines);
        StringBuilder output = new StringBuilder("JSON mismatch\n--- expected\n+++ actual\n");
        for (AbstractDelta<String> delta : patch.getDeltas()) {
            delta.getSource().getLines().forEach(line -> output.append("- ").append(line).append('\n'));
            delta.getTarget().getLines().forEach(line -> output.append("+ ").append(line).append('\n'));
        }
        return output.toString().stripTrailing();
    }

    private static List<String> lines(String value) {
        return Arrays.stream(value.split("\\R", -1)).collect(Collectors.toList());
    }
}
