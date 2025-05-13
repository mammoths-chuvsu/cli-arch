package org.cli;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParameterException;

public class Executor {

    /**
     * Executes a command depending on whether it is a builtin or external command
     * Determines command type and delegate to executeExternal or executeBuiltin
     *
     * @param command The command to execute, containing both the command name and arguments. Must not be null.
     * @return        The exit status of the executed command:
     *                  0 for successful execution
     *                  Non-zero for errors
     */
    public static int execute(Command command) {
        if (BUILTIN_FUNCTIONS.containsKey(command.getName())) {
            return executeBuiltin(command);
        }
        return executeExternal(command);
    }


    // Method to execute the external command
    private static int executeExternal(Command command) {
        ProcessBuilder pb = new ProcessBuilder(command.getName());
        pb.command().addAll(command.getArgs());
        pb.redirectErrorStream(true);
        pb.directory(new File(System.getProperty("user.dir")));

        try {
            Process process = pb.start();

            // Redirect stdin to the process (if not System.in)
            if (command.getStdin() != System.in) {
                try (OutputStream processInput = process.getOutputStream()) {
                    command.getStdin().transferTo(processInput);
                }
            }

            // Redirect process stdout and command.getStdout()
            try (InputStream processOutput = process.getInputStream()) {
                processOutput.transferTo(command.getStdout());
            }
            // Wait process
            return process.waitFor();
        } catch (IOException | InterruptedException e) {
            if (!e.getMessage().contains("Broken pipe")) {
                System.err.println(command.getName() + ": " + e.getMessage());
                return 1;
            }
            return 0;
        }
    }


    // Method to execute the builtin command
    private static int executeBuiltin(Command command) {
        return BUILTIN_FUNCTIONS.containsKey(command.getName()) ?
                BUILTIN_FUNCTIONS.get(command.getName()).apply(command) :
                unknownBuiltinCommand(command);
    }


    // Method to execute the `cat` command
    private static int executeCat(Command command) {
        OutputStream output = command.getStdout();
        InputStream input = null;
        int exitCode = 0;
        try {
            // Read data from input stream and write to output stream
            input = getInputStream(command);
            input.transferTo(output);
        } catch (IOException e) {
            System.err.println("cat: " + e.getMessage());
            exitCode = 1;
        } finally {
            if (input != null && input != System.in) {
                try {
                    input.close();
                } catch (IOException e) {
                    System.err.println("cat: " + e.getMessage());
                    exitCode = 1;
                }
            }
        }
        return exitCode;
    }


    // Method to execute the `echo` command
    private static int executeEcho(Command command) {
        OutputStream output = command.getStdout();
        int exitCode = 0;
        try {
            // Join all arguments in a single line with spaces
            String result = String.join(" ", command.getArgs()) + "\n";
            // Write the result to the output
            output.write(result.getBytes());
            // Flush the output stream to ensure data is written
            output.flush();
        } catch (IOException e) {
            System.err.println("echo: " + e.getMessage());
            exitCode = 1;
        }
        return exitCode;
    }


    // Set input for wc and cat: stdin or file
    private static InputStream getInputStream(Command command) throws IOException {
        if (command.getArgs().isEmpty()) {
            return command.getStdin();
        }
        String file = command.getArgs().getFirst();
        Path path = Paths.get(file);
        if (!path.isAbsolute()) {
            file = Paths.get(System.getProperty("user.dir"), file).toString();
        }
        return new FileInputStream(new File(file));
    }


    // Method to execute the `wc` command
    private static int executeWc(Command command) {
        OutputStream output = command.getStdout();
        String fileName = command.getArgs().isEmpty() ? "" : command.getArgs().getFirst();
        InputStream input = null;
        int exitCode = 0;
        long lineCnt = 0, wordCnt = 0, byteCnt = 0;
        try {
            input = getInputStream(command);
            BufferedReader reader = new BufferedReader(new InputStreamReader(input));
            String line;
            while ((line = reader.readLine()) != null) {
                lineCnt++;
                wordCnt += line.isBlank() ? 0 : line.trim().split("\\s+").length;
                byteCnt += line.getBytes().length + 1;
            }
            // Join all stat in a single line with spaces
            String result = String.format("%7d %7d %7d %s%n", lineCnt, wordCnt, byteCnt, fileName);
            // Write the result to the output
            output.write(result.getBytes());
            // Flush the output stream to ensure data is written
            output.flush();
        } catch (IOException e) {
            System.err.println("wc: " + e.getMessage());
            exitCode = 1;
        } finally {
            if (input != null && input != System.in) {
                try {
                    input.close();
                } catch (IOException e) {
                    System.err.println("wc: " + e.getMessage());
                    exitCode = 1;
                }
            }
        }
        return exitCode;
    }


    // Method to execute the `pwd` command
    private static int executePwd(Command command) {
        OutputStream output = command.getStdout();
        int exitCode = 0;
        try {
            // Get the current working directory
            String currentDirectory = System.getProperty("user.dir") + "\n";
            // Write the directory path to the output stream
            output.write(currentDirectory.getBytes());
            // Flush the output stream to ensure data is written
            output.flush();
        } catch (IOException e) {
            exitCode = 1;
        }
        return exitCode;
    }

    // Method to execute the `grep` command
    private static int executeGrep(Command command) {
        OutputStream output = command.getStdout();
        int exitCode = 0;
        InputStream input = null;

        GrepArgs grepArgs = new GrepArgs();
        JCommander grepCommander = JCommander.newBuilder()
                .addObject(grepArgs)
                .build();
        try {
            // parse grep arguments using JCommander
            grepCommander.parse(command.getArgs().toArray(new String[0]));

            // specify inputStream
            if (grepArgs.getFileNames().isEmpty()) {
                input = command.getStdin();
            } else {
                String fileName = grepArgs.getFileNames().getFirst();
                Path path = Paths.get(fileName);
                if (!path.isAbsolute()) {
                    fileName = Paths.get(System.getProperty("user.dir"), fileName).toString();
                }
                input = new FileInputStream(fileName);
            }

            // Call function for detailed grep execution
            StringBuilder result = grepExecutionDetails(input, grepArgs);

            output.write(result.toString().getBytes());
            // Flush the output stream to ensure data is written
            output.flush();
        } catch (IOException | ParameterException e) { // add exception from JCommander parser
            System.err.println("grep: " + e.getMessage());
            exitCode = 1;
        } finally {
            if (input != null && input != System.in) {
                try {
                    input.close();
                } catch (IOException e) {
                    System.err.println("grep: " + e.getMessage());
                    exitCode = 1;
                }
            }
        }
        return exitCode;
    }

    private static StringBuilder grepExecutionDetails(InputStream input, GrepArgs grepArgs) throws IOException {
        // return pattern for grep consider arguments
        Pattern pattern = grepArgs.getPattern();

        BufferedReader reader = new BufferedReader(new InputStreamReader(input));
        String line;

        // counter of remaining additional lines after match
        int additionalLineCnt = 0;

        StringBuilder result = new StringBuilder();
        while ((line = reader.readLine()) != null) {
            Matcher matcher = pattern.matcher(line);
            if (matcher.find()) { // check if was match in current line
                result.append(line).append("\n");
                // update additionalLineCnt
                additionalLineCnt = grepArgs.getAdditionalLines();
            } else if (additionalLineCnt > 0) { // if there was no match but need additional line
                result.append(line).append("\n");
                additionalLineCnt--;

                // after printing last additional line add break.
                // Will reach only if no cross with other match
                if (additionalLineCnt == 0) {
                    result.append("------\n");
                }
            }
        }
        return result;
    }

    private static int executeCd(Command command) {
        OutputStream output = command.getStdout();
        List<String> args = command.getArgs();
        String targetDir = args.isEmpty() ? System.getProperty("user.home") : args.get(0);
        File newDir = new File(targetDir);

        if (!newDir.isDirectory()) {
            try {
                output.write(("cd: " + targetDir + ": No such directory\n").getBytes());
                output.flush();
            } catch (IOException e) {
                System.err.println("cd: " + e.getMessage());
            }
            return 1;
        }

        try {
            // Normalize the path
            String normalizedPath = newDir.getCanonicalPath();
            System.setProperty("user.dir", normalizedPath);
            output.write(("Changed directory to: " + normalizedPath + "\n").getBytes());
            output.flush();
        } catch (IOException e) {
            System.err.println("cd: " + e.getMessage());
            return 1;
        }
        return 0;
    }

    // Method to execute the `ls` command
    private static int executeLs(Command command) {
        OutputStream output = command.getStdout();
        List<String> args = command.getArgs();
        String targetDir = args.isEmpty() ? System.getProperty("user.dir") : args.get(0);
        File dir = new File(targetDir);

        if (!dir.isDirectory()) {
            try {
                output.write(("ls: " + targetDir + ": No such directory\n").getBytes());
                output.flush();
            } catch (IOException e) {
                System.err.println("ls: " + e.getMessage());
            }
            return 1;
        }

        try {
            Files.list(dir.toPath())
                .map(Path::getFileName)
                .map(Path::toString)
                .forEach(fileName -> {
                    try {
                        output.write((fileName + "\n").getBytes());
                    } catch (IOException e) {
                        System.err.println("ls: " + e.getMessage());
                    }
                });
            output.flush();
            return 0;
        } catch (IOException e) {
            System.err.println("ls: " + e.getMessage());
            return 1;
        }
    }

    // Process unknown builtin command
    private static int unknownBuiltinCommand(Command command) {
        System.err.println(command.getName() + ": unknown command");
        return 1;
    }

    // Map of methods for builtin commands
    private static final Map<String, Function<Command, Integer>> BUILTIN_FUNCTIONS = Map.of(
        "cat", Executor::executeCat,
        "echo", Executor::executeEcho,
        "wc", Executor::executeWc,
        "pwd", Executor::executePwd,
        "grep", Executor::executeGrep,
        "cd", Executor::executeCd,
        "ls", Executor::executeLs
    );
}

// class specifically for parsing arguments for grep using JCommander
class GrepArgs {
    @Parameter(names = "-w", description = "Search only whole word")
    private boolean wholeWord;

    @Parameter(names = "-i", description = "Case-insensitive search")
    private boolean caseInsensitive;

    @Parameter(names = "-A", arity = 1, description = "Print 'A' lines after match")
    private int additionalLines = 0;

    // always assume that pattern goes before files
    @Parameter(description = "Pattern and files to search")
    private List<String> positionalParams;

    // return list of fileNames for grep. Currently, will process only first of them
    public List<String> getFileNames() {
        return positionalParams.subList(1, positionalParams.size());
    }

    public int getAdditionalLines() {
        return additionalLines;
    }

    // return Pattern for grep match
    public Pattern getPattern() {
        String patternString;
        // update it for wholeWord
        if (wholeWord) {
            patternString = "\\b" + positionalParams.getFirst() + "\\b";
        } else {
            patternString = positionalParams.getFirst();
        }
        // update for case sensation
        if (caseInsensitive) {
            return Pattern.compile(patternString, Pattern.CASE_INSENSITIVE);
        }
        return Pattern.compile(patternString);
    }
}
