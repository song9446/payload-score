package com.socialallstar.esplugin;


import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.lucene.analysis.payloads.PayloadHelper;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.PostingsEnum;
import org.apache.lucene.index.Terms;
import org.apache.lucene.index.TermsEnum;
import org.apache.lucene.util.BytesRef;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.plugins.Plugin;
import org.elasticsearch.plugins.ScriptPlugin;
import org.elasticsearch.script.ScoreScript;
import org.elasticsearch.script.ScoreScript.LeafFactory;
import org.elasticsearch.script.ScriptContext;
import org.elasticsearch.script.ScriptEngine;
import org.elasticsearch.script.ScriptFactory;
import org.elasticsearch.search.lookup.SearchLookup;

import java.io.IOException;
import java.util.Collection;
import java.util.Map;
import java.util.Set;

public class PayloadPlugin extends Plugin implements ScriptPlugin {

    private static final Logger logger = LogManager.getLogger(PayloadPlugin.class);

    @Override
    public ScriptEngine getScriptEngine(Settings settings,
                                        Collection<ScriptContext<?>> contexts) {
        return new PayloadScriptEngine();
    }

    private static class PayloadScriptEngine implements ScriptEngine {

        @Override
        public String getType() {
            return "payload_scripts";
        }

        @Override
        public <T> T compile(
                String scriptName,
                String scriptSource,
                ScriptContext<T> context,
                Map<String, String> params) {

            if (context.equals(ScoreScript.CONTEXT) == false) {
                throw new IllegalArgumentException(getType()
                        + " scripts cannot be used for context ["
                        + context.name + "]");
            }

            if ("payload_rank".equals(scriptSource)){
                ScoreScript.Factory factory = new payloadFactory();
                return context.factoryClazz.cast(factory);
            }


            throw new IllegalArgumentException("Unknown script name "
                    + scriptSource);
        }

        @Override
        public void close() {

        }

        @Override
        public Set<ScriptContext<?>> getSupportedContexts() {
            return Set.of(ScoreScript.CONTEXT);
        }

        private static class payloadFactory implements ScoreScript.Factory,
                ScriptFactory {
            @Override
            public boolean isResultDeterministic() {
                // PureDfLeafFactory only uses deterministic APIs, this
                // implies the results are cacheable.
                return true;
            }

            @Override
            public ScoreScript.LeafFactory newFactory(
                    Map<String, Object> params,
                    SearchLookup lookup
            ) {
                return new PayloadLeafFactory(params, lookup);
            }
        }

        private static class PayloadLeafFactory implements LeafFactory {

            private final Map<String, Object> params;
            private final SearchLookup lookup;
            private final String field;
            private final String term;

            private PayloadLeafFactory(
                    Map<String, Object> params, SearchLookup lookup) {
                if (params.containsKey("field") == false) {
                    throw new IllegalArgumentException(
                            "Missing parameter [field]");
                }
                if (params.containsKey("term") == false) {
                    throw new IllegalArgumentException(
                            "Missing parameter [term]");
                }
                this.params = params;
                this.lookup = lookup;
                field = params.get("field").toString();
                term = params.get("term").toString();
            }

            @Override
            public boolean needs_score() {
                return false;// Return true if the script needs the score
            }

            @Override
            public ScoreScript newInstance(LeafReaderContext ctx) {
                return new ScoreScript(params, lookup, ctx) {
                    BytesRef payload = null;
                    @Override
                    public void setDocument(int docid){
                        try {
                            Terms terms = ctx.reader().getTermVector(docid, field);
                            if (terms != null) {
                                TermsEnum termsEnum = terms.iterator();
                                if (termsEnum.seekExact(new BytesRef(term.getBytes()))){
                                    PostingsEnum postings = termsEnum.postings(null, PostingsEnum.ALL);
                                    postings.nextDoc();
                                    postings.nextPosition();
                                    payload = postings.getPayload();
                                    return;
                                }
                            }
                        } catch (IOException e) {
                            logger.debug("docid:{},field:{},term:{},get payload error!,stack:{}", docid, field, term, e.getStackTrace());
                        }
                        payload = null;
                    }

                    @Override
                    public double execute(ExplanationHolder explanation) {
                        if (payload == null) {
                            return 0.0d;
                        }
                        return PayloadHelper.decodeFloat(payload.bytes, payload.offset);
                    }
                };
            }
        }
    }

}
