/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0 and the Server Side Public License, v 1; you may not use this file except
 * in compliance with, at your election, the Elastic License 2.0 or the Server
 * Side Public License, v 1.
 */

package org.elasticsearch.server.cli;

import org.elasticsearch.Build;
import org.elasticsearch.cli.ExitCodes;
import org.elasticsearch.cli.UserException;
import org.elasticsearch.common.Strings;
import org.elasticsearch.common.settings.SecureSettings;
import org.elasticsearch.common.settings.SecureString;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.core.Nullable;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.StringJoiner;

/**
 * This class is responsible for working out if APM telemetry is configured and if so, preparing
 * a temporary config file for the APM Java agent and CLI options to the JVM to configure APM.
 * APM doesn't need to be enabled, as that can be toggled at runtime, but some configuration e.g.
 * server URL and secret key can only be provided when Elasticsearch starts.
 */
class APMJvmOptions {
    /**
     * Contains agent configuration that must always be applied, and cannot be overridden.
     */
    private static final Map<String, String> STATIC_CONFIG = Map.of(
        "service_version", Build.current().version(),

        "instrument", "false",
        "enable_experimental_instrumentations", "true"
        );

    /**
     * Contains default configuration that will be used unless overridden by explicit configuration.
     */
    private static final Map<String, String> CONFIG_DEFAULTS = Map.of(
        "service_name", "elasticsearch",

        "environment", "dev",

        "log_level", "warn",
        "log_format_file", "JSON",
        "application_packages", "org.elasticsearch,org.apache.lucene",
        "metrics_interval", "120s",
        "breakdown_metrics", "false",
        "central_config", "false",
        "transaction_sample_rate", "0.2"
        );

    /**
     * Lists all APM configuration keys that are not dynamic and must be configured via the config file.
     */
    private static final List<String> STATIC_AGENT_KEYS = List.of(
        "api_key",
        "aws_lambda_handler",
        "breakdown_metrics",
        "classes_excluded_from_instrumentation",
        "cloud_provider",
        "data_flush_timeout",
        "disable_metrics",
        "disable_send",
        "enabled",
        "enable_public_api_annotation_inheritance",
        "environment",
        "global_labels",
        "hostname",
        "include_process_args",
        "log_ecs_formatter_allow_list",
        "log_ecs_reformatting_additional_fields",
        "log_ecs_reformatting_dir",
        "log_file",
        "log_file_size",
        "log_format_file",
        "log_format_sout",
        "max_queue_size",
        "metrics_interval",
        "plugins_dir",
        "profiling_inferred_spans_lib_directory",
        "secret_token",
        "service_name",
        "service_node_name",
        "service_version",
        "stress_monitoring_interval",
        "trace_methods_duration_threshold",
        "use_jaxrs_path_as_transaction_name",
        "verify_server_cert"
    );

    /**
     * This method works out if APM telemetry is enabled, and if so, prepares a temporary config file
     * for the APM Java agent and CLI options to the JVM to configure APM. The config file is temporary
     * because it will be deleted once Elasticsearch starts.
     *
     * @param settings the Elasticsearch settings to consider
     * @param secrets a wrapper to access the secrets, or null if there is no secrets
     * @param logsDir the directory to write the apm log into
     * @param tmpdir Elasticsearch's temporary directory, where the config file will be written
     */
    static List<String> apmJvmOptions(Settings settings, @Nullable SecureSettings secrets, Path logsDir, Path tmpdir) throws UserException,
        IOException {
        final Path agentJar = findAgentJar();

        if (agentJar == null) {
            return List.of();
        }

        final Map<String, String> propertiesMap = extractApmSettings(settings);

        propertiesMap.put("log_file", logsDir.resolve("apm-agent.json").toString());

        if (propertiesMap.containsKey("server_url") == false && propertiesMap.containsKey("server_urls") == false) {
            return List.of();
        }

        if (propertiesMap.containsKey("service_node_name") == false) {
            final String nodeName = settings.get("node.name");
            if (nodeName != null) {
                propertiesMap.put("service_node_name", nodeName);
            }
        }

        if (secrets != null) {
            extractSecureSettings(secrets, propertiesMap);
        }
        final Map<String, String> dynamicSettings = extractDynamicSettings(propertiesMap);

        final Path tmpProperties = writeApmProperties(tmpdir, propertiesMap);

        final List<String> options = new ArrayList<>();
        options.add(agentCommandLineOption(agentJar, tmpProperties));

        dynamicSettings.forEach((key, value) -> options.add("-Delastic.apm." + key + "=" + value));

        return options;
    }

    static String agentCommandLineOption(Path agentJar, Path tmpPropertiesFile) {
        return "-javaagent:" + agentJar + "=c=" + tmpPropertiesFile;
    }

    static void extractSecureSettings(SecureSettings secrets, Map<String, String> propertiesMap) {
        final Set<String> settingNames = secrets.getSettingNames();
        for (String key : List.of("api_key", "secret_token")) {
            for (String prefix : List.of("telemetry.", "tracing.apm.")) {
                if (settingNames.contains(prefix + key)) {
                    if (propertiesMap.containsKey(key)) {
                        throw new IllegalStateException(
                            Strings.format("Duplicate telemetry setting: [telemetry.%s] and [tracing.apm.%s]", key, key)
                        );
                    }

                    try (SecureString token = secrets.getString(prefix + key)) {
                        propertiesMap.put(key, token.toString());
                    }
                }
            }

        }
    }

    /**
     * Removes settings that can be changed dynamically at runtime from the supplied map, and returns
     * those settings in a new map.
     */
    private static Map<String, String> extractDynamicSettings(Map<String, String> propertiesMap) {
        final Map<String, String> cliOptionsMap = new HashMap<>();

        final Iterator<Map.Entry<String, String>> propertiesIterator = propertiesMap.entrySet().iterator();
        while (propertiesIterator.hasNext()) {
            final Map.Entry<String, String> entry = propertiesIterator.next();
            if (STATIC_AGENT_KEYS.contains(entry.getKey()) == false) {
                propertiesIterator.remove();
                cliOptionsMap.put(entry.getKey(), entry.getValue());
            }
        }

        return cliOptionsMap;
    }

    static Map<String, String> extractApmSettings(Settings settings) throws UserException {
        final Map<String, String> propertiesMap = new HashMap<>();

        final String telemetryAgentPrefix = "telemetry.agent.";
        final String deprecatedTelemetryAgentPrefix = "tracing.apm.agent.";

        final Settings telemetryAgentSettings = settings.getByPrefix(telemetryAgentPrefix);
        telemetryAgentSettings.keySet().forEach(key -> propertiesMap.put(key, String.valueOf(telemetryAgentSettings.get(key))));

        final Settings apmAgentSettings = settings.getByPrefix(deprecatedTelemetryAgentPrefix);
        for (String key : apmAgentSettings.keySet()) {
            if (propertiesMap.containsKey(key)) {
                throw new IllegalStateException(
                    Strings.format(
                        "Duplicate telemetry setting: [%s%s] and [%s%s]",
                        telemetryAgentPrefix,
                        key,
                        deprecatedTelemetryAgentPrefix,
                        key
                    )
                );
            }
            propertiesMap.put(key, String.valueOf(apmAgentSettings.get(key)));
        }

        StringJoiner globalLabels = extractGlobalLabels(telemetryAgentPrefix, propertiesMap, settings);
        if (globalLabels.length() == 0) {
            globalLabels = extractGlobalLabels(deprecatedTelemetryAgentPrefix, propertiesMap, settings);
        } else {
            StringJoiner tracingGlobalLabels = extractGlobalLabels(deprecatedTelemetryAgentPrefix, propertiesMap, settings);
            if (tracingGlobalLabels.length() != 0) {
                throw new IllegalArgumentException(
                    "Cannot have global labels with tracing.agent prefix ["
                        + globalLabels
                        + "] and telemetry.apm.agent prefix ["
                        + tracingGlobalLabels
                        + "]"
                );
            }
        }
        if (globalLabels.length() > 0) {
            propertiesMap.put("global_labels", globalLabels.toString());
        }

        for (String key : STATIC_CONFIG.keySet()) {
            if (propertiesMap.containsKey(key)) {
                throw new UserException(
                    ExitCodes.CONFIG,
                    "Do not set a value for [tracing.apm.agent." + key + "], as this is configured automatically by Elasticsearch"
                );
            }
        }

        CONFIG_DEFAULTS.forEach(propertiesMap::putIfAbsent);

        propertiesMap.putAll(STATIC_CONFIG);
        return propertiesMap;
    }

    private static StringJoiner extractGlobalLabels(String prefix, Map<String, String> propertiesMap, Settings settings) {
        final Settings globalLabelsSettings = settings.getByPrefix(prefix + "global_labels.");
        final StringJoiner globalLabels = new StringJoiner(",");

        for (var globalLabel : globalLabelsSettings.keySet()) {
            propertiesMap.remove("global_labels." + globalLabel);
            var globalLabelValue = globalLabelsSettings.get(globalLabel);
            if (Strings.isNullOrBlank(globalLabelValue) == false) {
                globalLabelValue = globalLabelValue.replaceAll("[,=]", "_");
                globalLabels.add(String.join("=", globalLabel, globalLabelValue));
            }
        }
        return globalLabels;
    }

    static Path createTemporaryPropertiesFile(Path tmpdir) throws IOException {
        return Files.createTempFile(tmpdir, ".elstcapm.", ".tmp");
    }

    /**
     * Writes a Java properties file with data from supplied map to a temporary config, and returns
     * the file that was created.
     * <p>
     * We expect that the deleteTemporaryApmConfig function in Node will delete this temporary
     * configuration file, however if we fail to launch the node (because of an error) we might leave the
     * file behind. Therefore, we register a CLI shutdown hook that will also attempt to delete the file.
     *
     * @param tmpdir        the directory for the file
     * @param propertiesMap the data to write
     * @return the file that was created
     * @throws IOException if writing the file fails
     */
    private static Path writeApmProperties(Path tmpdir, Map<String, String> propertiesMap) throws IOException {
        final Properties p = new Properties();
        p.putAll(propertiesMap);

        final Path tmpFile = createTemporaryPropertiesFile(tmpdir);
        try (OutputStream os = Files.newOutputStream(tmpFile)) {
            p.store(os, " Automatically generated by Elasticsearch, do not edit!");
        }
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                Files.deleteIfExists(tmpFile);
            } catch (IOException e) {
            }
        }, "elasticsearch[apmagent-cleanup]"));

        return tmpFile;
    }

    /**
     * The JVM argument that configure the APM agent needs to specify the agent jar path, so this method
     * finds the jar by inspecting the filesystem.
     * @return the agent jar file
     * @throws IOException if a problem occurs reading the filesystem
     */
    @Nullable
    private static Path findAgentJar() throws IOException, UserException {
        return findAgentJar(System.getProperty("user.dir"));
    }

    static Path findAgentJar(String installDir) throws IOException, UserException {
        final Path apmModule = Path.of(installDir).resolve("modules").resolve("apm");

        if (Files.notExists(apmModule)) {
            if (Build.current().isProductionRelease()) {
                throw new UserException(
                    ExitCodes.CODE_ERROR,
                    "Expected to find [apm] module in [" + apmModule + "]! Installation is corrupt"
                );
            }
            return null;
        }

        try (var apmStream = Files.list(apmModule)) {
            final List<Path> paths = apmStream.filter(
                path -> path.getFileName().toString().matches("elastic-apm-agent-\\d+\\.\\d+\\.\\d+\\.jar")
            ).toList();

            if (paths.size() > 1) {
                throw new UserException(
                    ExitCodes.CODE_ERROR,
                    "Found multiple [elastic-apm-agent] jars under [" + apmModule + "]! Installation is corrupt."
                );
            }

            if (paths.isEmpty()) {
                throw new UserException(
                    ExitCodes.CODE_ERROR,
                    "Found no [elastic-apm-agent] jar under [" + apmModule + "]! Installation is corrupt."
                );
            }

            return paths.get(0);
        }
    }
}
