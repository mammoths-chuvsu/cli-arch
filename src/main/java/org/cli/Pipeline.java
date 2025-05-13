package org.cli;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

// Class for executing pipelines
public class Pipeline {

    /**
     * Executes a pipeline of commands where the output of each command is passed as input to the next one,
     * similar to Unix shell pipes (|). Handles both single commands and command chains.
     *
     * For a single command:
     *  If the command is "exit", terminates the program with status 0
     *  Otherwise executes the command normally
     * For multiple commands:
     *  Connects stdout of each process to stdin of the next process
     *  Executes the command normally
     *
     * @param commands      list of Command objects representing the pipeline
     * @return              exit status of the last command in the pipeline
     */
    public static int pipe(List<Command> commands) {
        // Single command
        if (commands.size() == 1) {
            Command command = commands.getFirst();
            // Command is exit command
            if (command.isExit()) {
                System.exit(0);
            }
            return Executor.execute(command);
        }

        // Pipe with more than one command
        List<Process> processes = new ArrayList<>();
        try {
            // Start all the processes and connect their stdin/stdout
            Process prevProcess = null;
            ProcessBuilder pb = new ProcessBuilder();

            for (Command command : commands) {
                // Skip exit command, do not process it
                if (command.isExit()) {
                    continue;
                }
                pb.command(command.getName());
                pb.command().addAll(command.getArgs());

                pb.directory(new File(System.getProperty("user.dir")));

                // Redirect stdin (except first command)
                if (prevProcess != null) {
                    pb.redirectInput(ProcessBuilder.Redirect.PIPE);
                }

                // Redirect stdout (except last command)
                if (command != commands.getLast()) {
                    pb.redirectOutput(ProcessBuilder.Redirect.PIPE);
                } else {
                    pb.redirectOutput(ProcessBuilder.Redirect.INHERIT);
                }

                // Redirect stderr
                pb.redirectError(ProcessBuilder.Redirect.INHERIT);

                Process process = pb.start();
                processes.add(process);

                // Connect the stdout of the previous process to the stdin of the current process
                if (prevProcess != null) {
                    try (InputStream prevOut = prevProcess.getInputStream();
                         OutputStream currIn = process.getOutputStream()) {
                        prevOut.transferTo(currIn);
                    } catch (IOException e) {
                        // Skip broken pipe
                        if (!e.getMessage().contains("Broken pipe")) {
                            System.err.println(command.getName() + ": " + e.getMessage());
                        }
                    }
                }
                prevProcess = process;
            }

            int exitCode = 0;
            // Wait processes
            for (Process p : processes) {
                exitCode = p.waitFor();
            }
            // exit code of the last command in pipeline
            return exitCode;
        } catch (IOException | InterruptedException e) {
            System.err.println("Pipeline error: " + e.getMessage());
            return 1;
        }
    }
}