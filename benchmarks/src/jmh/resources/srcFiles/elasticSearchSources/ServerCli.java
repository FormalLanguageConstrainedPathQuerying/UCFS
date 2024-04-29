/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0 and the Server Side Public License, v 1; you may not use this file except
 * in compliance with, at your election, the Elastic License 2.0 or the Server
 * Side Public License, v 1.
 */

package org.elasticsearch.server.cli;

import joptsimple.OptionSet;
import joptsimple.OptionSpec;
import joptsimple.OptionSpecBuilder;
import joptsimple.util.PathConverter;

import org.elasticsearch.Build;
import org.elasticsearch.bootstrap.ServerArgs;
import org.elasticsearch.cli.CliToolProvider;
import org.elasticsearch.cli.Command;
import org.elasticsearch.cli.ExitCodes;
import org.elasticsearch.cli.ProcessInfo;
import org.elasticsearch.cli.Terminal;
import org.elasticsearch.cli.UserException;
import org.elasticsearch.common.cli.EnvironmentAwareCommand;
import org.elasticsearch.common.settings.SecureSettings;
import org.elasticsearch.common.settings.SecureString;
import org.elasticsearch.env.Environment;
import org.elasticsearch.monitor.jvm.JvmInfo;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Locale;

/**
 * The main CLI for running Elasticsearch.
 */
class ServerCli extends EnvironmentAwareCommand {

    private final OptionSpecBuilder versionOption;
    private final OptionSpecBuilder daemonizeOption;
    private final OptionSpec<Path> pidfileOption;
    private final OptionSpecBuilder quietOption;
    private final OptionSpec<String> enrollmentTokenOption;

    private volatile ServerProcess server;

    ServerCli() {
        super("Starts Elasticsearch"); 
        versionOption = parser.acceptsAll(Arrays.asList("V", "version"), "Prints Elasticsearch version information and exits");
        daemonizeOption = parser.acceptsAll(Arrays.asList("d", "daemonize"), "Starts Elasticsearch in the background")
            .availableUnless(versionOption);
        pidfileOption = parser.acceptsAll(Arrays.asList("p", "pidfile"), "Creates a pid file in the specified path on start")
            .availableUnless(versionOption)
            .withRequiredArg()
            .withValuesConvertedBy(new PathConverter());
        quietOption = parser.acceptsAll(Arrays.asList("q", "quiet"), "Turns off standard output/error streams logging in console")
            .availableUnless(versionOption)
            .availableUnless(daemonizeOption);
        enrollmentTokenOption = parser.accepts("enrollment-token", "An existing enrollment token for securely joining a cluster")
            .availableUnless(versionOption)
            .withRequiredArg();
    }

    @Override
    public void execute(Terminal terminal, OptionSet options, Environment env, ProcessInfo processInfo) throws Exception {
        if (options.nonOptionArguments().isEmpty() == false) {
            throw new UserException(ExitCodes.USAGE, "Positional arguments not allowed, found " + options.nonOptionArguments());
        }
        if (options.has(versionOption)) {
            printVersion(terminal);
            return;
        }

        validateConfig(options, env);

        var secureSettingsLoader = secureSettingsLoader(env);

        try (
            var loadedSecrets = secureSettingsLoader.load(env, terminal);
            var password = (loadedSecrets.password().isPresent()) ? loadedSecrets.password().get() : new SecureString(new char[0]);
        ) {
            SecureSettings secrets = loadedSecrets.secrets();
            if (secureSettingsLoader.supportsSecurityAutoConfiguration()) {
                env = autoConfigureSecurity(terminal, options, processInfo, env, password);
                secrets = secureSettingsLoader.bootstrap(env, password);
            }

            if (secrets == null) {
                throw new UserException(ExitCodes.CONFIG, "Elasticsearch secure settings not configured");
            }

            syncPlugins(terminal, env, processInfo);

            ServerArgs args = createArgs(options, env, secrets, processInfo);
            this.server = startServer(terminal, processInfo, args);
        }

        if (options.has(daemonizeOption)) {
            server.detach();
            return;
        }

        int exitCode = server.waitFor();
        onExit(exitCode);
    }

    /**
     * A post-exit hook to perform additional processing before the command terminates
     * @param exitCode the server process exit code
     */
    protected void onExit(int exitCode) throws UserException {
        if (exitCode != ExitCodes.OK) {
            throw new UserException(exitCode, "Elasticsearch exited unexpectedly");
        }
    }

    private static void printVersion(Terminal terminal) {
        final String versionOutput = String.format(
            Locale.ROOT,
            "Version: %s, Build: %s/%s/%s, JVM: %s",
            Build.current().qualifiedVersion(),
            Build.current().type().displayName(),
            Build.current().hash(),
            Build.current().date(),
            JvmInfo.jvmInfo().version()
        );
        terminal.println(versionOutput);
    }

    private void validateConfig(OptionSet options, Environment env) throws UserException {
        if (options.valuesOf(enrollmentTokenOption).size() > 1) {
            throw new UserException(ExitCodes.USAGE, "Multiple --enrollment-token parameters are not allowed");
        }

        Path log4jConfig = env.configFile().resolve("log4j2.properties");
        if (Files.exists(log4jConfig) == false) {
            throw new UserException(ExitCodes.CONFIG, "Missing logging config file at " + log4jConfig);
        }
    }

    Environment autoConfigureSecurity(
        Terminal terminal,
        OptionSet options,
        ProcessInfo processInfo,
        Environment env,
        SecureString keystorePassword
    ) throws Exception {
        assert secureSettingsLoader(env) instanceof KeyStoreLoader;

        String autoConfigLibs = "modules/x-pack-core,modules/x-pack-security,lib/tools/security-cli";
        Command cmd = loadTool("auto-configure-node", autoConfigLibs);
        assert cmd instanceof EnvironmentAwareCommand;
        @SuppressWarnings("raw")
        var autoConfigNode = (EnvironmentAwareCommand) cmd;
        final String[] autoConfigArgs;
        if (options.has(enrollmentTokenOption)) {
            autoConfigArgs = new String[] { "--enrollment-token", options.valueOf(enrollmentTokenOption) };
        } else {
            autoConfigArgs = new String[0];
        }
        OptionSet autoConfigOptions = autoConfigNode.parseOptions(autoConfigArgs);

        boolean changed = true;
        try (var autoConfigTerminal = new KeystorePasswordTerminal(terminal, keystorePassword.clone())) {
            autoConfigNode.execute(autoConfigTerminal, autoConfigOptions, env, processInfo);
        } catch (UserException e) {
            boolean okCode = switch (e.exitCode) {
                case ExitCodes.CANT_CREATE, ExitCodes.CONFIG, ExitCodes.NOOP -> true;
                default -> false;
            };
            if (options.has(enrollmentTokenOption) == false && okCode) {
                if (e.getMessage() != null) {
                    terminal.errorPrintln(e.getMessage());
                }
                changed = false;
            } else {
                throw e;
            }
        }
        if (changed) {
            env = createEnv(options, processInfo);
        }
        return env;
    }

    void syncPlugins(Terminal terminal, Environment env, ProcessInfo processInfo) throws Exception {
        String pluginCliLibs = "lib/tools/plugin-cli";
        Command cmd = loadTool("sync-plugins", pluginCliLibs);
        assert cmd instanceof EnvironmentAwareCommand;
        @SuppressWarnings("raw")
        var syncPlugins = (EnvironmentAwareCommand) cmd;
        syncPlugins.execute(terminal, syncPlugins.parseOptions(new String[0]), env, processInfo);
    }

    private static void validatePidFile(Path pidFile) throws UserException {
        Path parent = pidFile.getParent();
        if (parent != null && Files.exists(parent) && Files.isDirectory(parent) == false) {
            throw new UserException(ExitCodes.USAGE, "pid file parent [" + parent + "] exists but is not a directory");
        }
        if (Files.exists(pidFile) && Files.isRegularFile(pidFile) == false) {
            throw new UserException(ExitCodes.USAGE, pidFile + " exists but is not a regular file");
        }
    }

    private ServerArgs createArgs(OptionSet options, Environment env, SecureSettings secrets, ProcessInfo processInfo)
        throws UserException {
        boolean daemonize = options.has(daemonizeOption);
        boolean quiet = options.has(quietOption);
        Path pidFile = null;
        if (options.has(pidfileOption)) {
            pidFile = options.valueOf(pidfileOption);
            if (pidFile.isAbsolute() == false) {
                pidFile = processInfo.workingDir().resolve(pidFile.toString()).toAbsolutePath();
            }
            validatePidFile(pidFile);
        }
        return new ServerArgs(daemonize, quiet, pidFile, secrets, env.settings(), env.configFile(), env.logsFile());
    }

    @Override
    public void close() {
        if (server != null) {
            server.stop();
        }
    }

    protected ServerProcess getServer() {
        return server;
    }

    protected Command loadTool(String toolname, String libs) {
        return CliToolProvider.load(toolname, libs).create();
    }

    protected ServerProcess startServer(Terminal terminal, ProcessInfo processInfo, ServerArgs args) throws Exception {
        var tempDir = ServerProcessUtils.setupTempDir(processInfo);
        var jvmOptions = JvmOptionsParser.determineJvmOptions(args, processInfo, tempDir);
        var serverProcessBuilder = new ServerProcessBuilder().withTerminal(terminal)
            .withProcessInfo(processInfo)
            .withServerArgs(args)
            .withTempDir(tempDir)
            .withJvmOptions(jvmOptions);
        return serverProcessBuilder.start();
    }

    protected SecureSettingsLoader secureSettingsLoader(Environment env) {
        return new KeyStoreLoader();
    }
}
