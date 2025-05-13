package org.cli;

import org.junit.jupiter.api.*;

import java.io.*;
import java.nio.file.*;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

// Test class for Executor
class ExecutorTest {

    private Path tempFile;
    private Path tempFileSimple;
    private ByteArrayOutputStream output;
    private final String simpleInput = "Hello from file!!!\nHello from file\tagain!!!";

    @BeforeEach
    void setUp() throws IOException {
        // Create output buffer
        output = new ByteArrayOutputStream();
        // Create temporary test files with sample data
        tempFile = Files.createTempFile("testFile", ".txt");
        tempFileSimple = Files.createTempFile("testFileSimple", ".txt");


        Files.write(tempFile, List.of(
                "This is an ERROR message",
                "This is a warning",
                "Nothing important here",
                "Not whole worldERROR",
                "Another ERROR found",
                "Log: all systems normal"
        ));

        Files.write(tempFileSimple, List.of(simpleInput));
    }

    @AfterEach
    void tearDown() throws IOException {
        // Delete temporary test files
        Files.deleteIfExists(tempFile);
    }

    @Test
    // Test for cat command
    void testExecuteCat() {
        Command command = new Command(List.of("cat", tempFileSimple.toString()));
        command.setStdout(output);

        int exitCode = Executor.execute(command);

        assertEquals(0, exitCode);
        assertEquals(simpleInput + "\n", output.toString());
    }

    @Test
    // Test for echo command
    void testExecuteEcho() {
        String echoArg = "Hello from echo!!!\n";
        Command command = new Command(List.of("echo", echoArg));
        command.setStdout(output);

        int exitCode = Executor.execute(command);

        assertEquals(0, exitCode);
        assertEquals(echoArg + "\n", output.toString());
    }

    @Test
    // Test for pwd command
    void testExecutePwd() {
        Command command = new Command(List.of("pwd"));
        command.setStdout(output);

        int exitCode = Executor.execute(command);

        assertEquals(0, exitCode);
        assertEquals(System.getProperty("user.dir") + "\n", output.toString());
    }

    @Test
    // Test for wc command
    void testExecuteWc() {
        Command command = new Command(List.of("wc", tempFileSimple.toString()));
        command.setStdout(output);

        int exitCode = Executor.execute(command);

        assertEquals(0, exitCode);
        String expected = "      2       7      44 " + tempFileSimple + "\n";
        assertEquals(expected, output.toString());
    }

    @Test
    void testExecuteGrepSimple() {
        Command command = new Command(List.of("grep", "again", tempFileSimple.toString()));
        command.setStdout(output);

        int exitCode = Executor.execute(command);

        assertEquals(0, exitCode);
        assertEquals("Hello from file\tagain!!!\n", output.toString());
    }

    @Test
    void testExecuteGrepRegex() {
        Command command = new Command(List.of("grep", ".*ing", tempFile.toString()));
        command.setStdout(output);

        int exitCode = Executor.execute(command);

        assertEquals(0, exitCode);
        String expected = """
                This is a warning
                Nothing important here
                """;
        assertEquals(expected, output.toString());
    }

    @Test
    void testExecuteGrepCaseInsensitive() {
        Command command = new Command(List.of("grep", "error", tempFile.toString(), "-i"));
        command.setStdout(output);

        int exitCode = Executor.execute(command);

        assertEquals(0, exitCode);
        String expected = """
                This is an ERROR message
                Not whole worldERROR
                Another ERROR found
                """;
        assertEquals(expected, output.toString());
    }

    @Test
    void testExecuteGrepWholeWord() {
        Command command = new Command(List.of("grep", "-w", "ERROR", tempFile.toString()));
        command.setStdout(output);

        int exitCode = Executor.execute(command);

        assertEquals(0, exitCode);
        String expected = """
                This is an ERROR message
                Another ERROR found
                """;
        assertEquals(expected, output.toString());
    }

    @Test
    void testExecuteGrepWithAdditionalLines() {
        Command command = new Command(List.of("grep", "-A", "1", "ERROR", tempFile.toString()));
        command.setStdout(output);

        int exitCode = Executor.execute(command);

        assertEquals(0, exitCode);
        String expected = """
                This is an ERROR message
                This is a warning
                ------
                Not whole worldERROR
                Another ERROR found
                Log: all systems normal
                ------
                """;
        assertEquals(expected, output.toString());
    }

    @Test
    void testExecuteGrepWithAdditionalLinesCrossed() {
        Command command = new Command(List.of("grep", "-A", "4", "ERROR", tempFile.toString(), "-i"));
        command.setStdout(output);

        int exitCode = Executor.execute(command);

        assertEquals(0, exitCode);
        String expected = """
                This is an ERROR message
                This is a warning
                Nothing important here
                Not whole worldERROR
                Another ERROR found
                Log: all systems normal
                """;
        assertEquals(expected, output.toString());
    }

    @Test
    void testExecuteGrepWithNoMatch() {
        Command command = new Command(List.of("grep", "nonexistentpattern", "-i", tempFile.toString()));
        command.setStdout(output);

        int exitCode = Executor.execute(command);

        assertEquals(0, exitCode);
        assertEquals("", output.toString());
    }

    @Test
    void testExecuteCd() {
        String initialDir = System.getProperty("user.dir");
        Command command = new Command(List.of("cd", ".."));
        int exitCode = Executor.execute(command);

        assertEquals(0, exitCode);
        assertEquals(new File(initialDir).getParent(), System.getProperty("user.dir"));
    }

    @Test
    void testExecuteLs() {
        Command cdCommand = new Command(List.of("cd", "src"));
        int cdExitCode = Executor.execute(cdCommand);
        assertEquals(0, cdExitCode);

        Command lsCommand = new Command(List.of("ls"));
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        lsCommand.setStdout(output);

        int lsExitCode = Executor.execute(lsCommand);

        assertEquals(0, lsExitCode);
        assertTrue(output.toString().contains("main"));
    }

    @Test
    void testExecuteExternal() {
        Command command = new Command(List.of("ping", "-c", "1", "localhost"));
        command.setStdout(output);

        int exitCode = Executor.execute(command);

        assertEquals(0, exitCode);

        String outputString = output.toString().trim();
        assertTrue(outputString.contains("PING"),
                "Output does not contain expected PING header");
        assertTrue(outputString.contains("1 packets transmitted"),
                "Output does not contain packet transmission info");
    }
}