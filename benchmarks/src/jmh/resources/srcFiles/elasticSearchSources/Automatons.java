/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */
package org.elasticsearch.xpack.core.security.support;

import org.apache.lucene.util.automaton.Automata;
import org.apache.lucene.util.automaton.Automaton;
import org.apache.lucene.util.automaton.CharacterRunAutomaton;
import org.apache.lucene.util.automaton.MinimizationOperations;
import org.apache.lucene.util.automaton.Operations;
import org.apache.lucene.util.automaton.RegExp;
import org.elasticsearch.common.cache.Cache;
import org.elasticsearch.common.cache.CacheBuilder;
import org.elasticsearch.common.settings.Setting;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.util.set.Sets;
import org.elasticsearch.core.Predicates;
import org.elasticsearch.core.TimeValue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;
import java.util.function.Predicate;

import static org.apache.lucene.util.automaton.Operations.DEFAULT_DETERMINIZE_WORK_LIMIT;
import static org.apache.lucene.util.automaton.Operations.concatenate;
import static org.apache.lucene.util.automaton.Operations.intersection;
import static org.apache.lucene.util.automaton.Operations.minus;
import static org.apache.lucene.util.automaton.Operations.union;
import static org.elasticsearch.common.Strings.collectionToDelimitedString;

public final class Automatons {

    static final Setting<Integer> MAX_DETERMINIZED_STATES_SETTING = Setting.intSetting(
        "xpack.security.automata.max_determinized_states",
        100000,
        DEFAULT_DETERMINIZE_WORK_LIMIT,
        Setting.Property.NodeScope
    );

    static final Setting<Boolean> CACHE_ENABLED = Setting.boolSetting(
        "xpack.security.automata.cache.enabled",
        true,
        Setting.Property.NodeScope
    );
    static final Setting<Integer> CACHE_SIZE = Setting.intSetting("xpack.security.automata.cache.size", 10_000, Setting.Property.NodeScope);
    static final Setting<TimeValue> CACHE_TTL = Setting.timeSetting(
        "xpack.security.automata.cache.ttl",
        TimeValue.timeValueHours(48),
        Setting.Property.NodeScope
    );

    public static final Automaton EMPTY = Automata.makeEmpty();
    public static final Automaton MATCH_ALL = Automata.makeAnyString();

    private static int maxDeterminizedStates = 100000;
    private static Cache<Object, Automaton> cache = buildCache(Settings.EMPTY);

    static final char WILDCARD_STRING = '*';     
    static final char WILDCARD_CHAR = '?';       
    static final char WILDCARD_ESCAPE = '\\';    

    private Automatons() {}

    /**
     * Builds and returns an automaton that will represent the union of all the given patterns.
     */
    public static Automaton patterns(String... patterns) {
        return patterns(Arrays.asList(patterns));
    }

    /**
     * Builds and returns an automaton that will represent the union of all the given patterns.
     */
    @SuppressWarnings("unchecked")
    public static Automaton patterns(Collection<String> patterns) {
        if (patterns.isEmpty()) {
            return EMPTY;
        }
        if (cache == null) {
            return buildAutomaton(patterns);
        } else {
            try {
                return cache.computeIfAbsent(Sets.newHashSet(patterns), p -> buildAutomaton((Set<String>) p));
            } catch (ExecutionException e) {
                throw unwrapCacheException(e);
            }
        }
    }

    private static Automaton buildAutomaton(Collection<String> patterns) {
        if (patterns.size() == 1) {
            return minimize(pattern(patterns.iterator().next()));
        }

        final Function<Collection<String>, Automaton> build = strings -> {
            List<Automaton> automata = new ArrayList<>(strings.size());
            for (String pattern : strings) {
                final Automaton patternAutomaton = pattern(pattern);
                automata.add(patternAutomaton);
            }
            return unionAndMinimize(automata);
        };


        final Set<String> prefix = new HashSet<>();
        final Set<String> infix = new HashSet<>();
        final Set<String> suffix = new HashSet<>();
        final Set<String> misc = new HashSet<>();

        for (String p : patterns) {
            if (p.length() <= 1) {
                misc.add(p);
                continue;
            }

            final char first = p.charAt(0);
            final char last = p.charAt(p.length() - 1);
            if (first == '/') {
                misc.add(p);
            } else if (first == '*') {
                if (last == '*') {
                    infix.add(p.substring(1, p.length() - 1));
                } else {
                    suffix.add(p.substring(1));
                }
            } else if (last == '*' && p.indexOf('*') != p.length() - 1) {
                prefix.add(p.substring(0, p.length() - 1));
            } else {
                misc.add(p);
            }
        }

        final List<Automaton> automata = new ArrayList<>();
        if (prefix.isEmpty() == false) {
            automata.add(Operations.concatenate(build.apply(prefix), Automata.makeAnyString()));
        }
        if (suffix.isEmpty() == false) {
            automata.add(Operations.concatenate(Automata.makeAnyString(), build.apply(suffix)));
        }
        if (infix.isEmpty() == false) {
            automata.add(Operations.concatenate(List.of(Automata.makeAnyString(), build.apply(infix), Automata.makeAnyString())));
        }
        if (misc.isEmpty() == false) {
            automata.add(build.apply(misc));
        }
        return unionAndMinimize(automata);
    }

    /**
     * Builds and returns an automaton that represents the given pattern.
     */
    static Automaton pattern(String pattern) {
        if (cache == null) {
            return buildAutomaton(pattern);
        } else {
            try {
                return cache.computeIfAbsent(pattern, p -> buildAutomaton((String) p));
            } catch (ExecutionException e) {
                throw unwrapCacheException(e);
            }
        }
    }

    /**
     * Is the str a lucene type of pattern
     */
    public static boolean isLuceneRegex(String str) {
        return str.length() > 1 && str.charAt(0) == '/' && str.charAt(str.length() - 1) == '/';
    }

    private static Automaton buildAutomaton(String pattern) {
        if (pattern.startsWith("/")) { 
            if (pattern.length() == 1 || pattern.endsWith("/") == false) {
                throw new IllegalArgumentException(
                    "invalid pattern ["
                        + pattern
                        + "]. patterns starting with '/' "
                        + "indicate regular expression pattern and therefore must also end with '/'."
                        + " other patterns (those that do not start with '/') will be treated as simple wildcard patterns"
                );
            }
            String regex = pattern.substring(1, pattern.length() - 1);
            return new RegExp(regex).toAutomaton();
        } else if (pattern.equals("*")) {
            return MATCH_ALL;
        } else {
            return wildcard(pattern);
        }
    }

    private static RuntimeException unwrapCacheException(ExecutionException e) {
        final Throwable cause = e.getCause();
        if (cause instanceof RuntimeException) {
            return (RuntimeException) cause;
        } else {
            return new RuntimeException(cause);
        }
    }

    /**
     * Builds and returns an automaton that represents the given pattern.
     */
    @SuppressWarnings("fallthrough") 
    static Automaton wildcard(String text) {
        List<Automaton> automata = new ArrayList<>();
        for (int i = 0; i < text.length();) {
            final char c = text.charAt(i);
            int length = 1;
            switch (c) {
                case WILDCARD_STRING:
                    automata.add(Automata.makeAnyString());
                    break;
                case WILDCARD_CHAR:
                    automata.add(Automata.makeAnyChar());
                    break;
                case WILDCARD_ESCAPE:
                    if (i + length < text.length()) {
                        final char nextChar = text.charAt(i + length);
                        length += 1;
                        automata.add(Automata.makeChar(nextChar));
                        break;
                    } 
                default:
                    automata.add(Automata.makeChar(c));
            }
            i += length;
        }
        return concatenate(automata);
    }

    public static Automaton unionAndMinimize(Collection<Automaton> automata) {
        Automaton res = automata.size() == 1 ? automata.iterator().next() : union(automata);
        return minimize(res);
    }

    public static Automaton minusAndMinimize(Automaton a1, Automaton a2) {
        Automaton res = minus(a1, a2, maxDeterminizedStates);
        return minimize(res);
    }

    public static Automaton intersectAndMinimize(Automaton a1, Automaton a2) {
        Automaton res = intersection(a1, a2);
        return minimize(res);
    }

    private static Automaton minimize(Automaton automaton) {
        return MinimizationOperations.minimize(automaton, maxDeterminizedStates);
    }

    public static Predicate<String> predicate(String... patterns) {
        return predicate(Arrays.asList(patterns));
    }

    public static Predicate<String> predicate(Collection<String> patterns) {
        return predicate(patterns(patterns), collectionToDelimitedString(patterns, "|"));
    }

    public static Predicate<String> predicate(Automaton automaton) {
        return predicate(automaton, "Predicate for " + automaton);
    }

    public static void updateConfiguration(Settings settings) {
        maxDeterminizedStates = MAX_DETERMINIZED_STATES_SETTING.get(settings);
        cache = buildCache(settings);
    }

    private static Cache<Object, Automaton> buildCache(Settings settings) {
        if (CACHE_ENABLED.get(settings) == false) {
            return null;
        }
        return CacheBuilder.<Object, Automaton>builder()
            .setExpireAfterAccess(CACHE_TTL.get(settings))
            .setMaximumWeight(CACHE_SIZE.get(settings))
            .build();
    }

    static int getMaxDeterminizedStates() {
        return maxDeterminizedStates;
    }

    private static Predicate<String> predicate(Automaton automaton, final String toString) {
        if (automaton == MATCH_ALL) {
            return Predicates.always();
        } else if (automaton == EMPTY) {
            return Predicates.never();
        }
        CharacterRunAutomaton runAutomaton = new CharacterRunAutomaton(automaton, maxDeterminizedStates);
        return new Predicate<String>() {
            @Override
            public boolean test(String s) {
                return runAutomaton.run(s);
            }

            @Override
            public String toString() {
                return toString;
            }
        };
    }

    public static void addSettings(List<Setting<?>> settingsList) {
        settingsList.add(MAX_DETERMINIZED_STATES_SETTING);
        settingsList.add(CACHE_ENABLED);
        settingsList.add(CACHE_SIZE);
        settingsList.add(CACHE_TTL);
    }
}
