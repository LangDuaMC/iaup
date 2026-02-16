package net.langdua.iaup.service;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

public final class ItemsAdderConfigUpdater {
    public boolean update(File iaConfigFile, String newUrl) {
        if (iaConfigFile == null || !iaConfigFile.exists()) {
            return false;
        }

        try {
            List<String> lines = Files.readAllLines(iaConfigFile.toPath(), StandardCharsets.UTF_8);
            if (replaceItemsAdderUrlLine(lines, newUrl)) {
                Files.write(iaConfigFile.toPath(), lines, StandardCharsets.UTF_8);
                return true;
            }
        } catch (Exception ignored) {
        }

        YamlConfiguration iaConfig = new YamlConfiguration();
        try {
            iaConfig.load(iaConfigFile);
            iaConfig.set("resource-pack.hosting.external-host.url", newUrl);
            iaConfig.save(iaConfigFile);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean replaceItemsAdderUrlLine(List<String> lines, String newUrl) {
        List<PathNode> stack = new ArrayList<PathNode>();
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }

            int indent = countLeadingSpaces(line);
            while (!stack.isEmpty() && indent <= stack.get(stack.size() - 1).indent) {
                stack.remove(stack.size() - 1);
            }

            int colon = trimmed.indexOf(':');
            if (colon <= 0) {
                continue;
            }

            String key = trimmed.substring(0, colon).trim();
            String path = buildPath(stack, key);
            if ("resource-pack.hosting.external-host.url".equals(path)) {
                String comment = "";
                int hashIndex = line.indexOf('#');
                if (hashIndex >= 0) {
                    comment = line.substring(hashIndex).trim();
                }

                String indentSpaces = line.substring(0, indent);
                String escaped = escapeYamlDoubleQuotes(newUrl);
                String replacement = indentSpaces + "url: \"" + escaped + "\"";
                if (!comment.isEmpty()) {
                    replacement += " " + comment;
                }
                lines.set(i, replacement);
                return true;
            }

            stack.add(new PathNode(key, indent));
        }

        return false;
    }

    private int countLeadingSpaces(String line) {
        int count = 0;
        while (count < line.length() && line.charAt(count) == ' ') {
            count++;
        }
        return count;
    }

    private String buildPath(List<PathNode> stack, String key) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < stack.size(); i++) {
            if (i > 0) {
                sb.append('.');
            }
            sb.append(stack.get(i).key);
        }
        if (sb.length() > 0) {
            sb.append('.');
        }
        sb.append(key);
        return sb.toString();
    }

    private String escapeYamlDoubleQuotes(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static final class PathNode {
        private final String key;
        private final int indent;

        private PathNode(String key, int indent) {
            this.key = key;
            this.indent = indent;
        }
    }
}
