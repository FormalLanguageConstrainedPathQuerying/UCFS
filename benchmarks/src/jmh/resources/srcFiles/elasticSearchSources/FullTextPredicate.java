/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */
package org.elasticsearch.xpack.ql.expression.predicate.fulltext;

import org.elasticsearch.xpack.ql.expression.Expression;
import org.elasticsearch.xpack.ql.expression.Nullability;
import org.elasticsearch.xpack.ql.tree.Source;
import org.elasticsearch.xpack.ql.type.DataType;
import org.elasticsearch.xpack.ql.type.DataTypes;

import java.util.List;
import java.util.Map;
import java.util.Objects;

public abstract class FullTextPredicate extends Expression {

    public enum Operator {
        AND,
        OR;

        public org.elasticsearch.index.query.Operator toEs() {
            return org.elasticsearch.index.query.Operator.fromString(name());
        }
    }

    private final String query;
    private final String options;
    private final Map<String, String> optionMap;
    private final String analyzer;

    FullTextPredicate(Source source, String query, String options, List<Expression> children) {
        super(source, children);
        this.query = query;
        this.options = options;
        this.optionMap = FullTextUtils.parseSettings(options, source);
        this.analyzer = optionMap.get("analyzer");
    }

    public String query() {
        return query;
    }

    public String options() {
        return options;
    }

    public Map<String, String> optionMap() {
        return optionMap;
    }

    public String analyzer() {
        return analyzer;
    }

    @Override
    public Nullability nullable() {
        return Nullability.FALSE;
    }

    @Override
    public DataType dataType() {
        return DataTypes.BOOLEAN;
    }

    @Override
    public int hashCode() {
        return Objects.hash(query, options);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }

        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }

        FullTextPredicate other = (FullTextPredicate) obj;
        return Objects.equals(query, other.query) && Objects.equals(options, other.options);
    }
}
