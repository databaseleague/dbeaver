/*
 * DBeaver - Universal Database Manager
 * Copyright (C) 2010-2022 DBeaver Corp and others
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jkiss.dbeaver.parser;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.jkiss.dbeaver.parser.grammar.GrammarInfo;
import org.jkiss.dbeaver.parser.grammar.GrammarRule;
import org.jkiss.dbeaver.parser.grammar.nfa.GrammarNfaOperation;

/**
 * Parse result represented with discovered valid sequences of terminals
 */
public class ParseResult {
    private final String text;
    private final GrammarInfo grammar;
    private final List<ParserState> results;
    private final List<ParseTreeNode> trees;

    public ParseResult(String text, GrammarInfo grammar, List<ParserState> results) {
        this.text = text;
        this.grammar = grammar;
        this.results = results;
        this.trees = new ArrayList<>(results.size());
    }

    /**
     * Parse tree describing text structure according to the grammar rules.
     * @param withWhitespaces true to include meaningless parts like whitespaces in a tree
     * @return a collection of parse trees. If there is no ambiguity in grammar then only one tree will be returned.
     */
    public List<ParseTreeNode> getTrees(boolean withWhitespaces) {
        if (this.trees.size() != this.results.size()) {
            this.collectOperations(withWhitespaces);
        }
        return Collections.unmodifiableList(this.trees);
    }

    /**
     * Collect and evaluate all grammar graph operations of presented parse results producing a set of cached parse trees
     * @param withWhitespaces true to include meaningless parts like whitespaces
     */
    private void collectOperations(boolean withWhitespaces) {
        //System.out.println("Results { ");
        for (ParserState result : results) {
            List<ParserState> states = new ArrayList<>();
            for (ParserState state = result; state != null; state = state.getPrev()) {
                states.add(state);
            }
            Collections.reverse(states);
            /*int pos = 0;
            for (ParserState state : states) {
                if (state.getStep() != null && state.getStep().getPattern() != null) {
                    System.out.println("\t\t\"" + text.substring(pos, state.getPosition()) + "\" @" + pos + " is " + state.getStep().getPattern());
                }
                pos = state.getPosition();
            }*/

            ParseTreeNode tree = makeParseTree(withWhitespaces, states);
            //System.out.println(tree.collectString());
            trees.add(tree);
        }
        //System.out.println("} ");
    }

    /**
     * Build parse tree based on a given sequence of parsing steps by evaluating grammar graph operations
     * @param withWhitespaces
     * @param states
     * @return
     */
    private ParseTreeNode makeParseTree(boolean withWhitespaces, List<ParserState> states) {
        GrammarRule skipRule = this.grammar.findRule(this.grammar.getSkipRuleName());
        //System.out.println("Tree operations:");
        ParseTreeNode treeRoot = new ParseTreeNode(null, 0, null, new ArrayList<>());
        ParseTreeNode current = treeRoot;
        int pos = 0;
        int skipDepth = 0;
        for (ParserState state : states) {
//            if(withWhitespaces && skipRule.getId() == state.getStep().getFrom().getId()) {
//                continue;
//            }
            if (state.getStep() != null && state.getStep().getOperations() != null) {
                for (GrammarNfaOperation op : state.getStep().getOperations()) {
                    switch (op.getKind()) {
                    case RULE_START:
                        if (skipDepth == 0) {
                            if (!withWhitespaces && op.getRule() == skipRule) {
                                skipDepth++;
                            } else {
                                ParseTreeNode newNode = new ParseTreeNode(op.getRule(), pos, current, new ArrayList<>());
                                current.getChilds().add(newNode);
                                current = newNode;
                            }
                        } else {
                            skipDepth++;
                        }
                        //System.out.println("    " + op);
                        break;
                    case RULE_END:
                        if (skipDepth == 0) {
                            current = current.getParent();
                        } else {
                            skipDepth--;
                        }
                        //System.out.println("    " + op);
                        break;
                    default:
                        break;
                    }
                }
                if (state.getStep().getPattern() != null && skipDepth == 0) {
                    current.getChilds().add(new ParseTreeNode(null, pos, current, new ArrayList<>()));
                }
                //System.out.println("  capture term \"" + this.text.substring(pos, state.getPosition()) + "\" @" + pos + " is " + state.getStep().getPattern());
                pos = state.getPosition();
            }
        }
        return treeRoot;
    }
}
