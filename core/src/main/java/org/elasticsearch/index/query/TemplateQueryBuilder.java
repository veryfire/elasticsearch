/*
 * Licensed to Elasticsearch under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.elasticsearch.index.query;

import org.apache.lucene.search.Query;
import org.elasticsearch.common.ParseField;
import org.elasticsearch.common.ParsingException;
import org.elasticsearch.common.bytes.BytesReference;
import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.io.stream.StreamOutput;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentFactory;
import org.elasticsearch.common.xcontent.XContentParser;
import org.elasticsearch.script.ExecutableScript;
import org.elasticsearch.script.ScriptContext;
import org.elasticsearch.script.Template;

import java.io.IOException;
import java.util.Collections;
import java.util.Objects;
import java.util.Optional;

/**
 * Facilitates creating template query requests.
 * */
public class TemplateQueryBuilder extends AbstractQueryBuilder<TemplateQueryBuilder> {
    /** Name to reference this type of query. */
    public static final String NAME = "template";
    public static final ParseField QUERY_NAME_FIELD = new ParseField(NAME);

    /** Template to fill. */
    private final Template template;

    /**
     * @param template
     *            the template to use for that query.
     * */
    public TemplateQueryBuilder(Template template) {
        if (template == null) {
            throw new IllegalArgumentException("query template cannot be null");
        }
        this.template = template;
    }

    public Template template() {
        return template;
    }

    /**
     * Read from a stream.
     */
    public TemplateQueryBuilder(StreamInput in) throws IOException {
        super(in);
        template = new Template(in);
    }

    @Override
    protected void doWriteTo(StreamOutput out) throws IOException {
        template.writeTo(out);
    }

    @Override
    protected void doXContent(XContentBuilder builder, Params builderParams) throws IOException {
        builder.field(TemplateQueryBuilder.NAME);
        template.toXContent(builder, builderParams);
    }

    @Override
    public String getWriteableName() {
        return NAME;
    }

    @Override
    protected Query doToQuery(QueryShardContext context) throws IOException {
        throw new UnsupportedOperationException("this query must be rewritten first");
    }

    @Override
    protected int doHashCode() {
        return Objects.hash(template);
    }

    @Override
    protected boolean doEquals(TemplateQueryBuilder other) {
        return Objects.equals(template, other.template);
    }

    @Override
    protected QueryBuilder doRewrite(QueryRewriteContext queryRewriteContext) throws IOException {
        ExecutableScript executable = queryRewriteContext.getScriptService().executable(template,
            ScriptContext.Standard.SEARCH, Collections.emptyMap());
        BytesReference querySource = (BytesReference) executable.run();
        try (XContentParser qSourceParser = XContentFactory.xContent(querySource).createParser(querySource)) {
            final QueryParseContext queryParseContext = queryRewriteContext.newParseContext(qSourceParser);
            final QueryBuilder queryBuilder = queryParseContext.parseInnerQueryBuilder().orElseThrow(
                    () -> new ParsingException(qSourceParser.getTokenLocation(), "inner query in [" + NAME + "] cannot be empty"));
            if (boost() != DEFAULT_BOOST || queryName() != null) {
                final BoolQueryBuilder boolQueryBuilder = new BoolQueryBuilder();
                boolQueryBuilder.must(queryBuilder);
                return boolQueryBuilder;
            }
            return queryBuilder;
        }
    }

    /**
     * In the simplest case, parse template string and variables from the request,
     * compile the template and execute the template against the given variables.
     */
    public static Optional<TemplateQueryBuilder> fromXContent(QueryParseContext parseContext) throws IOException {
        XContentParser parser = parseContext.parser();
        Template template =  Template.parse(parser, parseContext.getParseFieldMatcher());
        return Optional.of(new TemplateQueryBuilder(template));
    }
}
