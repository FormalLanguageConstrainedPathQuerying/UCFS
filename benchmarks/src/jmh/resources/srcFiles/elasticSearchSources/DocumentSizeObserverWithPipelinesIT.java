/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0 and the Server Side Public License, v 1; you may not use this file except
 * in compliance with, at your election, the Elastic License 2.0 or the Server
 * Side Public License, v 1.
 */

package org.elasticsearch.plugins.internal;

import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.ingest.PutPipelineRequest;
import org.elasticsearch.common.bytes.BytesArray;
import org.elasticsearch.common.bytes.BytesReference;
import org.elasticsearch.ingest.common.IngestCommonPlugin;
import org.elasticsearch.plugins.IngestPlugin;
import org.elasticsearch.plugins.Plugin;
import org.elasticsearch.test.ESIntegTestCase;
import org.elasticsearch.xcontent.FilterXContentParserWrapper;
import org.elasticsearch.xcontent.XContentParser;
import org.elasticsearch.xcontent.XContentType;

import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import static org.elasticsearch.xcontent.XContentFactory.jsonBuilder;
import static org.hamcrest.Matchers.equalTo;

@ESIntegTestCase.ClusterScope(scope = ESIntegTestCase.Scope.TEST)
public class DocumentSizeObserverWithPipelinesIT extends ESIntegTestCase {

    private static String TEST_INDEX_NAME = "test-index-name";
    public static boolean hasWrappedParser;

    public void testDocumentIsReportedWithPipelines() throws IOException {
        hasWrappedParser = false;
        final BytesReference pipelineBody = new BytesArray("""
            {
              "processors": [
                {
                   "set": {
                     "field": "my-text-field",
                     "value": "xxxx"
                   }
                 },
                 {
                   "set": {
                     "field": "my-boolean-field",
                     "value": true
                   }
                 }
              ]
            }
            """);
        clusterAdmin().putPipeline(new PutPipelineRequest("pipeline", pipelineBody, XContentType.JSON)).actionGet();

        client().index(
            new IndexRequest(TEST_INDEX_NAME).setPipeline("pipeline")
                .id("1")
                .source(jsonBuilder().startObject().field("test", "I am sam i am").endObject())
        ).actionGet();
        assertTrue(hasWrappedParser);
    }

    @Override
    protected Collection<Class<? extends Plugin>> nodePlugins() {
        return List.of(TestDocumentParsingProviderPlugin.class, IngestCommonPlugin.class);
    }

    public static class TestDocumentParsingProviderPlugin extends Plugin implements DocumentParsingProviderPlugin, IngestPlugin {

        public TestDocumentParsingProviderPlugin() {}

        @Override
        public DocumentParsingProvider getDocumentParsingProvider() {
            return new DocumentParsingProvider() {
                @Override
                public DocumentSizeObserver newFixedSizeDocumentObserver(long normalisedBytesParsed) {
                    return new TestDocumentSizeObserver(normalisedBytesParsed);
                }

                @Override
                public DocumentSizeObserver newDocumentSizeObserver() {
                    return new TestDocumentSizeObserver(0L);
                }

                @Override
                public DocumentSizeReporter getDocumentParsingReporter(String indexName) {
                    return new TestDocumentSizeReporter();
                }
            };
        }
    }

    public static class TestDocumentSizeReporter implements DocumentSizeReporter {
        @Override
        public void onCompleted(String indexName, long normalizedBytesParsed) {
            assertThat(indexName, equalTo(TEST_INDEX_NAME));
            assertThat(normalizedBytesParsed, equalTo(1L));
        }
    }

    public static class TestDocumentSizeObserver implements DocumentSizeObserver {
        long mapCounter = 0;
        long wrapperCounter = 0;

        public TestDocumentSizeObserver(long mapCounter) {
            this.mapCounter = mapCounter;
        }

        @Override
        public XContentParser wrapParser(XContentParser xContentParser) {
            wrapperCounter++;
            hasWrappedParser = true;
            return new FilterXContentParserWrapper(xContentParser) {

                @Override
                public Map<String, Object> map() throws IOException {
                    mapCounter++;
                    return super.map();
                }
            };
        }

        @Override
        public long normalisedBytesParsed() {
            return mapCounter;
        }
    }

}
