/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */
package org.elasticsearch.xpack.textstructure.structurefinder;

public class LogTextStructureFinderFactoryTests extends TextStructureTestCase {

    private final TextStructureFinderFactory factory = new LogTextStructureFinderFactory();


    public void testCanCreateFromSampleGivenText() {

        assertTrue(factory.canCreateFromSample(explanation, TEXT_SAMPLE, 0.0));
    }
}
