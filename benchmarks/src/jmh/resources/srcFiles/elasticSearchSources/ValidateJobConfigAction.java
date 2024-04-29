/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */
package org.elasticsearch.xpack.core.ml.action;

import org.elasticsearch.action.ActionRequest;
import org.elasticsearch.action.ActionRequestValidationException;
import org.elasticsearch.action.ActionType;
import org.elasticsearch.action.support.master.AcknowledgedResponse;
import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.io.stream.StreamOutput;
import org.elasticsearch.xcontent.XContentParser;
import org.elasticsearch.xpack.core.ml.job.config.Job;

import java.io.IOException;
import java.util.Date;
import java.util.Objects;

public class ValidateJobConfigAction extends ActionType<AcknowledgedResponse> {

    public static final ValidateJobConfigAction INSTANCE = new ValidateJobConfigAction();
    public static final String NAME = "cluster:admin/xpack/ml/job/validate";

    protected ValidateJobConfigAction() {
        super(NAME);
    }

    public static class Request extends ActionRequest {

        private final Job job;

        public static Request parseRequest(XContentParser parser) {
            Job.Builder jobBuilder = Job.REST_REQUEST_PARSER.apply(parser, null);
            jobBuilder.setId(jobBuilder.getId() != null ? jobBuilder.getId() : "ok");

            jobBuilder.validateDetectorsAreUnique();

            return new Request(jobBuilder.build(new Date()));
        }

        public Request() {
            this.job = null;
        }

        public Request(Job job) {
            this.job = job;
        }

        public Request(StreamInput in) throws IOException {
            super(in);
            job = new Job(in);
        }

        public Job getJob() {
            return job;
        }

        @Override
        public ActionRequestValidationException validate() {
            return null;
        }

        @Override
        public void writeTo(StreamOutput out) throws IOException {
            super.writeTo(out);
            job.writeTo(out);
        }

        @Override
        public int hashCode() {
            return Objects.hash(job);
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            Request other = (Request) obj;
            return Objects.equals(job, other.job);
        }

    }
}
