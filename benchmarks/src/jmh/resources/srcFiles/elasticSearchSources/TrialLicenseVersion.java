/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */

package org.elasticsearch.license.internal;

import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.io.stream.StreamOutput;
import org.elasticsearch.common.io.stream.Writeable;
import org.elasticsearch.xcontent.ToXContentFragment;
import org.elasticsearch.xcontent.XContentBuilder;

import java.io.IOException;
import java.util.Objects;

/**
 * Sometimes we release a version with a bunch of cool new features, and we want people to be able to start a new trial license in a cluster
 * that's already used a trial and let it expire. This class controls when we do that. The serialization of this class is designed to
 * maintain compatibility with old-school Elasticsearch versions (specifically the {@link org.elasticsearch.Version} class).
 */
public class TrialLicenseVersion implements ToXContentFragment, Writeable {

    static final int TRIAL_VERSION_CUTOVER = 8_12_00_99;
    public static final TrialLicenseVersion CURRENT = new TrialLicenseVersion(TRIAL_VERSION_CUTOVER);

    static final int TRIAL_VERSION_CUTOVER_MAJOR = 8;

    private final int trialVersion;

    public TrialLicenseVersion(int trialVersion) {
        this.trialVersion = trialVersion;
    }

    public TrialLicenseVersion(StreamInput in) throws IOException {
        this.trialVersion = in.readVInt();
    }

    public static TrialLicenseVersion fromXContent(String from) {
        try {
            return new TrialLicenseVersion(Integer.parseInt(from));
        } catch (NumberFormatException ex) {
            return new TrialLicenseVersion(parseVersionString(from));
        }
    }

    private static int parseVersionString(String version) {
        final boolean snapshot = version.endsWith("-SNAPSHOT"); 
        if (snapshot) {
            version = version.substring(0, version.length() - 9);
        }
        String[] parts = version.split("[.-]");
        if (parts.length != 3) {
            throw new IllegalArgumentException("unable to parse trial license version: " + version);
        }

        try {
            final int rawMajor = Integer.parseInt(parts[0]);
            final int major = rawMajor * 1000000;
            final int minor = Integer.parseInt(parts[1]) * 10000;
            final int revision = Integer.parseInt(parts[2]) * 100;

            return major + minor + revision + 99;

        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("unable to parse trial license version: " + version, e);
        }
    }

    int asInt() {
        return trialVersion;
    }

    public boolean ableToStartNewTrial() {
        assert trialVersion <= CURRENT.trialVersion
            : "trial version [" + trialVersion + "] cannot be greater than CURRENT [" + CURRENT.trialVersion + "]";
        if (trialVersion < TRIAL_VERSION_CUTOVER) {
            int sinceMajorVersion = trialVersion / 1_000_000; 
            return sinceMajorVersion < TRIAL_VERSION_CUTOVER_MAJOR;
        }
        return trialVersion != CURRENT.trialVersion;
    }

    @Override
    public String toString() {
        return Integer.toString(trialVersion);
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        return builder.value(trialVersion); 
    }

    String asVersionString() {
        return this + ".0.0";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TrialLicenseVersion that = (TrialLicenseVersion) o;
        return trialVersion == that.trialVersion;
    }

    @Override
    public int hashCode() {
        return Objects.hash(trialVersion);
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        out.writeVInt(trialVersion);
    }
}
