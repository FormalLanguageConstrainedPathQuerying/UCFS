/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0 and the Server Side Public License, v 1; you may not use this file except
 * in compliance with, at your election, the Elastic License 2.0 or the Server
 * Side Public License, v 1.
 */

package org.elasticsearch.cluster.metadata;

import org.elasticsearch.TransportVersion;
import org.elasticsearch.TransportVersions;
import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.io.stream.StreamOutput;
import org.elasticsearch.common.io.stream.Writeable;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.unit.ByteSizeValue;
import org.elasticsearch.common.unit.Processors;
import org.elasticsearch.xcontent.ConstructingObjectParser;
import org.elasticsearch.xcontent.ObjectParser;
import org.elasticsearch.xcontent.ParseField;
import org.elasticsearch.xcontent.ToXContentObject;
import org.elasticsearch.xcontent.XContentBuilder;
import org.elasticsearch.xcontent.XContentParser;

import java.io.IOException;

public record DesiredNodeWithStatus(DesiredNode desiredNode, Status status)
    implements
        Writeable,
        ToXContentObject,
        Comparable<DesiredNodeWithStatus> {

    private static final TransportVersion STATUS_TRACKING_SUPPORT_VERSION = TransportVersions.V_8_4_0;
    private static final ParseField STATUS_FIELD = new ParseField("status");

    public static final ConstructingObjectParser<DesiredNodeWithStatus, Void> PARSER = new ConstructingObjectParser<>(
        "desired_node_with_status",
        false,
        (args, unused) -> new DesiredNodeWithStatus(
            new DesiredNode(
                (Settings) args[0],
                (Processors) args[1],
                (DesiredNode.ProcessorsRange) args[2],
                (ByteSizeValue) args[3],
                (ByteSizeValue) args[4],
                (String) args[5]
            ),
            args[6] == null ? Status.PENDING : (Status) args[6]
        )
    );

    static {
        DesiredNode.configureParser(PARSER);
        PARSER.declareField(
            ConstructingObjectParser.optionalConstructorArg(),
            (p, c) -> Status.fromValue(p.shortValue()),
            STATUS_FIELD,
            ObjectParser.ValueType.INT
        );
    }

    public boolean pending() {
        return status == Status.PENDING;
    }

    public boolean actualized() {
        return status == Status.ACTUALIZED;
    }

    public String externalId() {
        return desiredNode.externalId();
    }

    public static DesiredNodeWithStatus readFrom(StreamInput in) throws IOException {
        final var desiredNode = DesiredNode.readFrom(in);
        final Status status;
        if (in.getTransportVersion().onOrAfter(STATUS_TRACKING_SUPPORT_VERSION)) {
            status = Status.fromValue(in.readShort());
        } else {
            status = Status.PENDING;
        }
        return new DesiredNodeWithStatus(desiredNode, status);
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        desiredNode.writeTo(out);
        if (out.getTransportVersion().onOrAfter(STATUS_TRACKING_SUPPORT_VERSION)) {
            out.writeShort(status.value);
        }
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        final var desiredNodesSerializationContext = DesiredNodes.SerializationContext.valueOf(
            params.param(DesiredNodes.CONTEXT_MODE_PARAM, DesiredNodes.CONTEXT_MODE_CLUSTER_STATE)
        );
        builder.startObject();
        desiredNode.toInnerXContent(builder, params);
        if (desiredNodesSerializationContext == DesiredNodes.SerializationContext.CLUSTER_STATE) {
            builder.field(STATUS_FIELD.getPreferredName(), status.value);
        }
        builder.endObject();
        return builder;
    }

    static DesiredNodeWithStatus fromXContent(XContentParser parser) throws IOException {
        return PARSER.parse(parser, null);
    }

    @Override
    public int compareTo(DesiredNodeWithStatus o) {
        return desiredNode.compareTo(o.desiredNode);
    }

    public boolean equalsWithProcessorsCloseTo(DesiredNodeWithStatus other) {
        return other != null && status == other.status && desiredNode.equalsWithProcessorsCloseTo(other.desiredNode);
    }

    public enum Status {
        PENDING((short) 0),
        ACTUALIZED((short) 1);

        private final short value;

        Status(short value) {
            this.value = value;
        }

        public short getValue() {
            return value;
        }

        static Status fromValue(short value) {
            return switch (value) {
                case 0 -> PENDING;
                case 1 -> ACTUALIZED;
                default -> throw new IllegalArgumentException("Unknown status " + value);
            };
        }
    }
}
