/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
 */
/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http:
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.sun.org.apache.xerces.internal.dom;

import java.util.ArrayList;
import java.util.List;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * This class implements the DOM's NodeList behavior for
 * Element.getElementsByTagName()
 * <P>
 * The DOM describes NodeList as follows:
 * <P>
 * 1) It may represent EITHER nodes scattered through a subtree (when
 * returned by Element.getElementsByTagName), or just the immediate
 * children (when returned by Node.getChildNodes). The latter is easy,
 * but the former (which this class addresses) is more challenging.
 * <P>
 * 2) Its behavior is "live" -- that is, it always reflects the
 * current state of the document tree. To put it another way, the
 * NodeLists obtained before and after a series of insertions and
 * deletions are effectively identical (as far as the user is
 * concerned, the former has been dynamically updated as the changes
 * have been made).
 * <P>
 * 3) Its API accesses individual nodes via an integer index, with the
 * listed nodes numbered sequentially in the order that they were
 * found during a preorder depth-first left-to-right search of the tree.
 * (Of course in the case of getChildNodes, depth is not involved.) As
 * nodes are inserted or deleted in the tree, and hence the NodeList,
 * the numbering of nodes that follow them in the NodeList will
 * change.
 * <P>
 * It is rather painful to support the latter two in the
 * getElementsByTagName case. The current solution is for Nodes to
 * maintain a change count (eventually that may be a Digest instead),
 * which the NodeList tracks and uses to invalidate itself.
 * <P>
 * Unfortunately, this does _not_ respond efficiently in the case that
 * the dynamic behavior was supposed to address: scanning a tree while
 * it is being extended. That requires knowing which subtrees have
 * changed, which can become an arbitrarily complex problem.
 * <P>
 * We save some work by filling the vector only as we access the
 * item()s... but I suspect the same users who demanded index-based
 * access will also start by doing a getLength() to control their loop,
 * blowing this optimization out of the water.
 * <P>
 * NOTE: Level 2 of the DOM will probably _not_ use NodeList for its
 * extended search mechanisms, partly for the reasons just discussed.
 *
 * @xerces.internal
 *
 * @since  PR-DOM-Level-1-19980818.
 * @LastModified: Oct 2017
 */
public class DeepNodeListImpl
        implements NodeList {

    protected NodeImpl rootNode; 
    protected String tagName;   
    protected int changes = 0;
    protected List<Node> nodes;

    protected String nsName;
    protected boolean enableNS = false;


    /** Constructor. */
    public DeepNodeListImpl(NodeImpl rootNode, String tagName) {
        this.rootNode = rootNode;
        this.tagName = tagName;
        nodes = new ArrayList<>();
    }

    /** Constructor for Namespace support. */
    public DeepNodeListImpl(NodeImpl rootNode,
            String nsName, String tagName) {
        this(rootNode, tagName);
        this.nsName = (nsName != null && nsName.length() != 0) ? nsName : null;
        enableNS = true;
    }


    /** Returns the length of the node list. */
    public int getLength() {
        item(java.lang.Integer.MAX_VALUE);
        return nodes.size();
    }

    /** Returns the node at the specified index. */
    public Node item(int index) {
        Node thisNode;

        if (rootNode.changes() != changes) {
            nodes = new ArrayList<>();
            changes = rootNode.changes();
        }

        final int currentSize = nodes.size();
        if (index < currentSize) {
            return nodes.get(index);
        } 
        else {

            if (currentSize == 0) {
                thisNode = rootNode;
            } else {
                thisNode = (NodeImpl) (nodes.get(currentSize - 1));
            }

            while (thisNode != null && index >= nodes.size()) {
                thisNode = nextMatchingElementAfter(thisNode);
                if (thisNode != null) {
                    nodes.add(thisNode);
                }
            }

            return thisNode;
        }

    } 


    /**
     * Iterative tree-walker. When you have a Parent link, there's often no
     * need to resort to recursion. NOTE THAT only Element nodes are matched
     * since we're specifically supporting getElementsByTagName().
     */
    protected Node nextMatchingElementAfter(Node current) {

        Node next;
        while (current != null) {
            if (current.hasChildNodes()) {
                current = (current.getFirstChild());
            } 
            else if (current != rootNode && null != (next = current.getNextSibling())) {
                current = next;
            } 
            else {
                next = null;
                for (; current != rootNode; 
                        current = current.getParentNode()) {

                    next = current.getNextSibling();
                    if (next != null) {
                        break;
                    }
                }
                current = next;
            }

            if (current != rootNode
                    && current != null
                    && current.getNodeType() == Node.ELEMENT_NODE) {
                if (!enableNS) {
                    if (tagName.equals("*")
                            || ((ElementImpl) current).getTagName().equals(tagName)) {
                        return current;
                    }
                } else {
                    if (tagName.equals("*")) {
                        if (nsName != null && nsName.equals("*")) {
                            return current;
                        } else {
                            ElementImpl el = (ElementImpl) current;
                            if ((nsName == null
                                    && el.getNamespaceURI() == null)
                                    || (nsName != null
                                    && nsName.equals(el.getNamespaceURI()))) {
                                return current;
                            }
                        }
                    } else {
                        ElementImpl el = (ElementImpl) current;
                        if (el.getLocalName() != null
                                && el.getLocalName().equals(tagName)) {
                            if (nsName != null && nsName.equals("*")) {
                                return current;
                            } else {
                                if ((nsName == null
                                        && el.getNamespaceURI() == null)
                                        || (nsName != null
                                        && nsName.equals(el.getNamespaceURI()))) {
                                    return current;
                                }
                            }
                        }
                    }
                }
            }

        }

        return null;

    } 

} 
