package com.codexswitcher.service;

import com.codexswitcher.model.SkillInfo;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class SkillsService extends BaseSupport {

    public Path skillsRoot() {
        return CODEX_DIR.resolve("skills");
    }

    public List<SkillInfo> scanSkills() {
        Path root = skillsRoot();
        if (!Files.isDirectory(root)) {
            return List.of();
        }
        List<SkillInfo> items = new ArrayList<>();
        try {
            Files.walk(root, 2)
                .filter(Files::isDirectory)
                .filter(path -> Files.exists(path.resolve("SKILL.md")))
                .forEach(path -> items.add(buildSkill(path, root)));
        } catch (Exception ignored) {
        }
        items.sort(Comparator.comparing(SkillInfo::getSource).thenComparing(SkillInfo::getName, String.CASE_INSENSITIVE_ORDER));
        return items;
    }

    public Path backupSkills() throws IOException {
        Path root = skillsRoot();
        if (!Files.isDirectory(root)) {
            throw new IOException("技能目录不存在：" + root);
        }
        String stamp = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss").format(LocalDateTime.now());
        Path backup = root.getParent().resolve("skills_backup_" + stamp);
        copyDirectory(root, backup);
        pruneBackups(root.getParent());
        return backup;
    }

    public void importSkill(Path sourceDir) throws IOException {
        Path root = skillsRoot();
        Files.createDirectories(root);
        Path target = root.resolve(sourceDir.getFileName());
        if (Files.exists(target)) {
            throw new IOException("目标已存在：" + target.getFileName());
        }
        copyDirectory(sourceDir, target);
    }

    public void removeSkill(SkillInfo skill) throws IOException {
        if ("系统".equals(skill.getSource())) {
            throw new IOException("系统技能不允许删除");
        }
        deleteRecursively(skill.getPath());
    }

    private SkillInfo buildSkill(Path dir, Path root) {
        SkillInfo info = new SkillInfo();
        info.setPath(dir);
        String relative = root.relativize(dir).toString().replace('\\', '/');
        info.setSource(relative.startsWith(".system") ? "系统" : relative.startsWith("user") ? "用户" : "本地");
        try {
            String readme = readText(dir.resolve("SKILL.md"));
            info.setReadme(readme);
            String[] extracted = extractTitleAndDesc(readme, dir.getFileName().toString());
            info.setName(extracted[0]);
            info.setDescription(extracted[1]);
        } catch (Exception e) {
            info.setName(dir.getFileName().toString());
            info.setDescription("-");
            info.setReadme("");
        }
        return info;
    }

    private String[] extractTitleAndDesc(String text, String fallback) {
        String name = "";
        String description = "";
        String[] lines = text.split("\\R");
        if (lines.length > 0 && lines[0].trim().equals("---")) {
            for (int index = 1; index < lines.length; index++) {
                String line = lines[index].trim();
                if (line.equals("---")) {
                    break;
                }
                int colon = line.indexOf(':');
                if (colon < 0) {
                    continue;
                }
                String key = line.substring(0, colon).trim().toLowerCase();
                String value = line.substring(colon + 1).trim();
                if ("name".equals(key) && !isBlank(value)) {
                    name = value;
                } else if ("description".equals(key) && !isBlank(value)) {
                    description = value;
                }
            }
        }
        for (String line : lines) {
            String trimmed = line.trim();
            if (name.isEmpty() && trimmed.startsWith("#")) {
                name = trimmed.replaceFirst("^#+\\s*", "");
                continue;
            }
            if (!name.isEmpty() && !trimmed.isEmpty() && !trimmed.startsWith("#") && !trimmed.startsWith(">") && !trimmed.startsWith("-")) {
                description = trimmed;
                break;
            }
        }
        return new String[]{firstNonBlank(name, fallback), firstNonBlank(description, "-")};
    }

    private void pruneBackups(Path base) {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(base, "skills_backup_*")) {
            List<Path> backups = new ArrayList<>();
            for (Path path : stream) {
                backups.add(path);
            }
            backups.sort(Comparator.comparing(Path::getFileName).reversed());
            for (int index = 5; index < backups.size(); index++) {
                deleteRecursively(backups.get(index));
            }
        } catch (Exception ignored) {
        }
    }
}
