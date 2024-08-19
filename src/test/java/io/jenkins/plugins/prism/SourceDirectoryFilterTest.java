package io.jenkins.plugins.prism;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import edu.hm.hafner.util.FilteredLog;
import edu.hm.hafner.util.PathUtil;

import static org.assertj.core.api.Assertions.*;

class SourceDirectoryFilterTest {
    private static final PathUtil PATH_UTIL = new PathUtil();
    private static final String SUB_FOLDER = "sub-folder";
    private static final Set<String> EMPTY = Collections.emptySet();

    @TempDir
    private Path workspace;
    @TempDir
    private Path otherFolder;
    private final FilteredLog log = new FilteredLog("Error");

    private String absoluteWorkspacePath() {
        return workspace.toString();
    }

    @Test
    void shouldSkipEmptyRequest() {
        SourceDirectoryFilter filter = new SourceDirectoryFilter();

        var allowedDirectories = filter.getPermittedSourceDirectories(absoluteWorkspacePath(), EMPTY, EMPTY, log);

        assertThat(allowedDirectories).isEmpty();
    }

    @Test
    void shouldSkipEmptyDirectories() {
        SourceDirectoryFilter filter = new SourceDirectoryFilter();

        var allowedDirectories = filter.getPermittedSourceDirectories(absoluteWorkspacePath(),
                EMPTY, Set.of(StringUtils.EMPTY, "-"), log);

        assertThat(allowedDirectories).isEmpty();
    }

    @Test
    void shouldNotFilterRelativePaths() {
        SourceDirectoryFilter filter = new SourceDirectoryFilter();

        var relative = "src/main/java";
        var allowedDirectories = filter.getPermittedSourceDirectories(absoluteWorkspacePath(),
                EMPTY, Set.of(relative), log);

        assertThat(allowedDirectories).contains(makeAbsolutePath(relative));
    }

    private String makeAbsolutePath(final String relative) {
        return PATH_UTIL.createAbsolutePath(PATH_UTIL.getAbsolutePath(absoluteWorkspacePath()), relative);
    }

    @Test
    void shouldAllowAbsoluteWorkspacePath() {
        SourceDirectoryFilter filter = new SourceDirectoryFilter();

        var allowedDirectories = filter.getPermittedSourceDirectories(absoluteWorkspacePath(),
                EMPTY, Set.of(absoluteWorkspacePath()), log);

        assertThat(allowedDirectories).isEmpty();
    }

    @Test
    void shouldAllowAbsoluteWorkspaceChildren() throws IOException {
        var subFolder = workspace.resolve(SUB_FOLDER);
        Files.createDirectory(subFolder);

        SourceDirectoryFilter filter = new SourceDirectoryFilter();

        var allowedDirectories = filter.getPermittedSourceDirectories(absoluteWorkspacePath(),
                EMPTY, Set.of(PATH_UTIL.getAbsolutePath(subFolder)), log);

        assertThat(allowedDirectories).containsExactly(SUB_FOLDER);
    }

    @Test
    void shouldAllowPatternForRelativePaths() throws IOException {
        var subFolder = workspace.resolve(SUB_FOLDER);
        Files.createDirectory(subFolder);
        Files.createDirectory(subFolder.resolve("ok-1"));
        Files.createDirectory(subFolder.resolve("ok-2"));
        Files.createDirectory(subFolder.resolve("nok-1"));
        Files.createDirectory(subFolder.resolve("nok-2"));
        Files.createDirectory(subFolder.resolve("ok-3"));

        SourceDirectoryFilter filter = new SourceDirectoryFilter();

        var allowedDirectories = filter.getPermittedSourceDirectories(absoluteWorkspacePath(),
                EMPTY, Set.of("glob:**/ok-*"), log);

        assertThat(allowedDirectories).map(s -> StringUtils.substringAfterLast(s, "/"))
                .containsExactlyInAnyOrder("ok-1", "ok-2", "ok-3");

        var singleGlob = filter.getPermittedSourceDirectories(absoluteWorkspacePath(),
                EMPTY, Set.of("glob:sub-folder/ok-*"), log);

        assertThat(singleGlob).map(s -> StringUtils.substringAfterLast(s, "/"))
                .containsExactlyInAnyOrder("ok-1", "ok-2", "ok-3");
    }

    @Test
    void shouldIgnoreBrokenGlobs() {
        SourceDirectoryFilter filter = new SourceDirectoryFilter();

        var allowedDirectories = filter.getPermittedSourceDirectories(absoluteWorkspacePath(),
                EMPTY, Set.of("regex:a(]"), log);

        assertThat(log.getErrorMessages()).isNotEmpty();
        assertThat(allowedDirectories).isEmpty();
    }

    @Test
    void shouldNotAllowOtherFolder() throws IOException {
        var subFolder = workspace.resolve(SUB_FOLDER);
        Files.createDirectory(subFolder);

        SourceDirectoryFilter filter = new SourceDirectoryFilter();

        var allowedDirectories = filter.getPermittedSourceDirectories(absoluteWorkspacePath(),
                EMPTY, Set.of(PATH_UTIL.getAbsolutePath(otherFolder)), log);

        assertThat(allowedDirectories).isEmpty();
        assertThat(log.getErrorMessages()).last().asString()
                .contains("Removing non-workspace source directory",
                        "it has not been approved in Jenkins' global configuration");
    }

    @Test
    void shouldAllowPermittedFolder() throws IOException {
        var subFolder = workspace.resolve(SUB_FOLDER);
        Files.createDirectory(subFolder);

        SourceDirectoryFilter filter = new SourceDirectoryFilter();

        var approvedFolder = PATH_UTIL.getAbsolutePath(otherFolder);
        var allowedDirectories = filter.getPermittedSourceDirectories(absoluteWorkspacePath(),
                Set.of(approvedFolder), Set.of(approvedFolder), log);
        assertThat(allowedDirectories).containsExactly(approvedFolder);
    }
}
