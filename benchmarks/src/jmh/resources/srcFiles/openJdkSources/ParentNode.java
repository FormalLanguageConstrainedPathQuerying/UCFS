/*
 * Copyright (c) 2015, 2021, Oracle and/or its affiliates. All rights reserved.
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

import java.io.Serializable;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import org.w3c.dom.DOMException;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.UserDataHandler;

/**
 * ParentNode inherits from ChildNode and adds the capability of having child
 * nodes. Not every node in the DOM can have children, so only nodes that can
 * should inherit from this class and pay the price for it.
 * <P>
 * ParentNode, just like NodeImpl, also implements NodeList, so it can
 * return itself in response to the getChildNodes() query. This eliminiates
 * the need for a separate ChildNodeList object. Note that this is an
 * IMPLEMENTATION DETAIL; applications should _never_ assume that
 * this identity exists. On the other hand, subclasses may need to override
 * this, in case of conflicting names. This is the case for the classes
 * HTMLSelectElementImpl and HTMLFormElementImpl of the HTML DOM.
 * <P>
 * While we have a direct reference to the first child, the last child is
 * stored as the previous sibling of the first child. First child nodes are
 * marked as being so, and getNextSibling hides this fact.
 * <P>Note: Not all parent nodes actually need to also be a child. At some
 * point we used to have ParentNode inheriting from NodeImpl and another class
 * called ChildAndParentNode that inherited from ChildNode. But due to the lack
 * of multiple inheritance a lot of code had to be duplicated which led to a
 * maintenance nightmare. At the same time only a few nodes (Document,
 * DocumentFragment, Entity, and Attribute) cannot be a child so the gain in
 * memory wasn't really worth it. The only type for which this would be the
 * case is Attribute, but we deal with there in another special way, so this is
 * not applicable.
 * <p>
 * This class doesn't directly support mutation events, however, it notifies
 * the document when mutations are performed so that the document class do so.
 *
 * <p><b>WARNING</b>: Some of the code here is partially duplicated in
 * AttrImpl, be careful to keep these two classes in sync!
 *
 * @xerces.internal
 *
 * @author Arnaud  Le Hors, IBM
 * @author Joe Kesselman, IBM
 * @author Andy Clark, IBM
 * @LastModified: Apr 2019
 */
public abstract class ParentNode
    extends ChildNode {

    /** Serialization version. */
    static final long serialVersionUID = 2815829867152120872L;

    /** Owner document. */
    protected CoreDocumentImpl ownerDocument;

    /** First child. */
    protected ChildNode firstChild = null;


    /** NodeList cache */
    protected transient NodeListCache fNodeListCache = null;


    /**
     * No public constructor; only subclasses of ParentNode should be
     * instantiated, and those normally via a Document's factory methods
     */
    protected ParentNode(CoreDocumentImpl ownerDocument) {
        super(ownerDocument);
        this.ownerDocument = ownerDocument;
    }

    /** Constructor for serialization. */
    public ParentNode() {}


    /**
     * Returns a duplicate of a given node. You can consider this a
     * generic "copy constructor" for nodes. The newly returned object should
     * be completely independent of the source object's subtree, so changes
     * in one after the clone has been made will not affect the other.
     * <p>
     * Example: Cloning a Text node will copy both the node and the text it
     * contains.
     * <p>
     * Example: Cloning something that has children -- Element or Attr, for
     * example -- will _not_ clone those children unless a "deep clone"
     * has been requested. A shallow clone of an Attr node will yield an
     * empty Attr of the same name.
     * <p>
     * NOTE: Clones will always be read/write, even if the node being cloned
     * is read-only, to permit applications using only the DOM API to obtain
     * editable copies of locked portions of the tree.
     */
    public Node cloneNode(boolean deep) {

        if (needsSyncChildren()) {
            synchronizeChildren();
        }
        ParentNode newnode = (ParentNode) super.cloneNode(deep);

        newnode.ownerDocument = ownerDocument;

        newnode.firstChild      = null;

        newnode.fNodeListCache = null;

        if (deep) {
            for (ChildNode child = firstChild;
                 child != null;
                 child = child.nextSibling) {
                newnode.appendChild(child.cloneNode(true));
            }
        }

        return newnode;

    } 

    /**
     * Find the Document that this Node belongs to (the document in
     * whose context the Node was created). The Node may or may not
     * currently be part of that Document's actual contents.
     */
    public Document getOwnerDocument() {
        return ownerDocument;
    }

    /**
     * same as above but returns internal type and this one is not overridden
     * by CoreDocumentImpl to return null
     */
    CoreDocumentImpl ownerDocument() {
        return ownerDocument;
    }

    /**
     * NON-DOM
     * set the ownerDocument of this node and its children
     */
    protected void setOwnerDocument(CoreDocumentImpl doc) {
        if (needsSyncChildren()) {
            synchronizeChildren();
        }
        super.setOwnerDocument(doc);
        ownerDocument = doc;
        for (ChildNode child = firstChild;
        child != null; child = child.nextSibling) {
            child.setOwnerDocument(doc);
        }
    }

    /**
     * Test whether this node has any children. Convenience shorthand
     * for (Node.getFirstChild()!=null)
     */
    public boolean hasChildNodes() {
        if (needsSyncChildren()) {
            synchronizeChildren();
        }
        return firstChild != null;
    }

    /**
     * Obtain a NodeList enumerating all children of this node. If there
     * are none, an (initially) empty NodeList is returned.
     * <p>
     * NodeLists are "live"; as children are added/removed the NodeList
     * will immediately reflect those changes. Also, the NodeList refers
     * to the actual nodes, so changes to those nodes made via the DOM tree
     * will be reflected in the NodeList and vice versa.
     * <p>
     * In this implementation, Nodes implement the NodeList interface and
     * provide their own getChildNodes() support. Other DOMs may solve this
     * differently.
     */
    public NodeList getChildNodes() {

        if (needsSyncChildren()) {
            synchronizeChildren();
        }
        return this;

    } 

    /** The first child of this Node, or null if none. */
    public Node getFirstChild() {

        if (needsSyncChildren()) {
            synchronizeChildren();
        }
        return firstChild;

    }   

    /** The last child of this Node, or null if none. */
    public Node getLastChild() {

        if (needsSyncChildren()) {
            synchronizeChildren();
        }
        return lastChild();

    } 

    final ChildNode lastChild() {
        return firstChild != null ? firstChild.previousSibling : null;
    }

    final void lastChild(ChildNode node) {
        if (firstChild != null) {
            firstChild.previousSibling = node;
        }
    }

    /**
     * Move one or more node(s) to our list of children. Note that this
     * implicitly removes them from their previous parent.
     *
     * @param newChild The Node to be moved to our subtree. As a
     * convenience feature, inserting a DocumentNode will instead insert
     * all its children.
     *
     * @param refChild Current child which newChild should be placed
     * immediately before. If refChild is null, the insertion occurs
     * after all existing Nodes, like appendChild().
     *
     * @return newChild, in its new state (relocated, or emptied in the case of
     * DocumentNode.)
     *
     * @throws DOMException(HIERARCHY_REQUEST_ERR) if newChild is of a
     * type that shouldn't be a child of this node, or if newChild is an
     * ancestor of this node.
     *
     * @throws DOMException(WRONG_DOCUMENT_ERR) if newChild has a
     * different owner document than we do.
     *
     * @throws DOMException(NOT_FOUND_ERR) if refChild is not a child of
     * this node.
     *
     * @throws DOMException(NO_MODIFICATION_ALLOWED_ERR) if this node is
     * read-only.
     */
    public Node insertBefore(Node newChild, Node refChild)
        throws DOMException {
        return internalInsertBefore(newChild, refChild, false);
    } 

    /** NON-DOM INTERNAL: Within DOM actions,we sometimes need to be able
     * to control which mutation events are spawned. This version of the
     * insertBefore operation allows us to do so. It is not intended
     * for use by application programs.
     */
    Node internalInsertBefore(Node newChild, Node refChild, boolean replace)
        throws DOMException {

        boolean errorChecking = ownerDocument.errorChecking;

        if (newChild.getNodeType() == Node.DOCUMENT_FRAGMENT_NODE) {


            if (errorChecking) {
                for (Node kid = newChild.getFirstChild(); 
                     kid != null; kid = kid.getNextSibling()) {

                    if (!ownerDocument.isKidOK(this, kid)) {
                        throw new DOMException(
                              DOMException.HIERARCHY_REQUEST_ERR,
                              DOMMessageFormatter.formatMessage(DOMMessageFormatter.DOM_DOMAIN, "HIERARCHY_REQUEST_ERR", null));
                    }
                }
            }

            while (newChild.hasChildNodes()) {
                insertBefore(newChild.getFirstChild(), refChild);
            }
            return newChild;
        }

        if (newChild == refChild) {
            refChild = refChild.getNextSibling();
            removeChild(newChild);
            insertBefore(newChild, refChild);
            return newChild;
        }

        if (needsSyncChildren()) {
            synchronizeChildren();
        }

        if (errorChecking) {
            if (isReadOnly()) {
                throw new DOMException(
                              DOMException.NO_MODIFICATION_ALLOWED_ERR,
                              DOMMessageFormatter.formatMessage(DOMMessageFormatter.DOM_DOMAIN, "NO_MODIFICATION_ALLOWED_ERR", null));
            }
            if (newChild.getOwnerDocument() != ownerDocument && newChild != ownerDocument) {
                throw new DOMException(DOMException.WRONG_DOCUMENT_ERR,
                            DOMMessageFormatter.formatMessage(DOMMessageFormatter.DOM_DOMAIN, "WRONG_DOCUMENT_ERR", null));
            }
            if (!ownerDocument.isKidOK(this, newChild)) {
                throw new DOMException(DOMException.HIERARCHY_REQUEST_ERR,
                            DOMMessageFormatter.formatMessage(DOMMessageFormatter.DOM_DOMAIN, "HIERARCHY_REQUEST_ERR", null));
            }
            if (refChild != null && refChild.getParentNode() != this) {
                throw new DOMException(DOMException.NOT_FOUND_ERR,
                            DOMMessageFormatter.formatMessage(DOMMessageFormatter.DOM_DOMAIN, "NOT_FOUND_ERR", null));
            }

            boolean treeSafe = true;
            for (NodeImpl a = this; treeSafe && a != null; a = a.parentNode())
            {
                treeSafe = newChild != a;
            }
            if(!treeSafe) {
                throw new DOMException(DOMException.HIERARCHY_REQUEST_ERR,
                            DOMMessageFormatter.formatMessage(DOMMessageFormatter.DOM_DOMAIN, "HIERARCHY_REQUEST_ERR", null));
            }
        }

        ownerDocument.insertingNode(this, replace);

        ChildNode newInternal = (ChildNode)newChild;

        Node oldparent = newInternal.parentNode();
        if (oldparent != null) {
            oldparent.removeChild(newInternal);
        }

        ChildNode refInternal = (ChildNode)refChild;

        newInternal.ownerNode = this;
        newInternal.isOwned(true);

        if (firstChild == null) {
            firstChild = newInternal;
            newInternal.isFirstChild(true);
            newInternal.previousSibling = newInternal;
        }
        else {
            if (refInternal == null) {
                ChildNode lastChild = firstChild.previousSibling;
                lastChild.nextSibling = newInternal;
                newInternal.previousSibling = lastChild;
                firstChild.previousSibling = newInternal;
            }
            else {
                if (refChild == firstChild) {
                    firstChild.isFirstChild(false);
                    newInternal.nextSibling = firstChild;
                    newInternal.previousSibling = firstChild.previousSibling;
                    firstChild.previousSibling = newInternal;
                    firstChild = newInternal;
                    newInternal.isFirstChild(true);
                }
                else {
                    ChildNode prev = refInternal.previousSibling;
                    newInternal.nextSibling = refInternal;
                    prev.nextSibling = newInternal;
                    refInternal.previousSibling = newInternal;
                    newInternal.previousSibling = prev;
                }
            }
        }

        changed();

        if (fNodeListCache != null) {
            if (fNodeListCache.fLength != -1) {
                fNodeListCache.fLength++;
            }
            if (fNodeListCache.fChildIndex != -1) {
                if (fNodeListCache.fChild == refInternal) {
                    fNodeListCache.fChild = newInternal;
                } else {
                    fNodeListCache.fChildIndex = -1;
                }
            }
        }

        ownerDocument.insertedNode(this, newInternal, replace);

        checkNormalizationAfterInsert(newInternal);

        return newChild;

    } 

    /**
     * Remove a child from this Node. The removed child's subtree
     * remains intact so it may be re-inserted elsewhere.
     *
     * @return oldChild, in its new state (removed).
     *
     * @throws DOMException(NOT_FOUND_ERR) if oldChild is not a child of
     * this node.
     *
     * @throws DOMException(NO_MODIFICATION_ALLOWED_ERR) if this node is
     * read-only.
     */
    public Node removeChild(Node oldChild)
        throws DOMException {
        return internalRemoveChild(oldChild, false);
    } 

    /** NON-DOM INTERNAL: Within DOM actions,we sometimes need to be able
     * to control which mutation events are spawned. This version of the
     * removeChild operation allows us to do so. It is not intended
     * for use by application programs.
     */
    Node internalRemoveChild(Node oldChild, boolean replace)
        throws DOMException {

        CoreDocumentImpl ownerDocument = ownerDocument();
        if (ownerDocument.errorChecking) {
            if (isReadOnly()) {
                throw new DOMException(
                            DOMException.NO_MODIFICATION_ALLOWED_ERR,
                            DOMMessageFormatter.formatMessage(DOMMessageFormatter.DOM_DOMAIN, "NO_MODIFICATION_ALLOWED_ERR", null));
            }
            if (oldChild != null && oldChild.getParentNode() != this) {
                throw new DOMException(DOMException.NOT_FOUND_ERR,
                            DOMMessageFormatter.formatMessage(DOMMessageFormatter.DOM_DOMAIN, "NOT_FOUND_ERR", null));
            }
        }

        ChildNode oldInternal = (ChildNode) oldChild;

        ownerDocument.removingNode(this, oldInternal, replace);

        final ChildNode oldPreviousSibling = oldInternal.previousSibling();

        if (fNodeListCache != null) {
            if (fNodeListCache.fLength != -1) {
                fNodeListCache.fLength--;
            }
            if (fNodeListCache.fChildIndex != -1) {
                if (fNodeListCache.fChild == oldInternal) {
                    fNodeListCache.fChildIndex--;
                    fNodeListCache.fChild = oldPreviousSibling;
                } else {
                    fNodeListCache.fChildIndex = -1;
                }
            }
        }

        if (oldInternal == firstChild) {
            oldInternal.isFirstChild(false);
            firstChild = oldInternal.nextSibling;
            if (firstChild != null) {
                firstChild.isFirstChild(true);
                firstChild.previousSibling = oldInternal.previousSibling;
            }
        } else {
            ChildNode prev = oldInternal.previousSibling;
            ChildNode next = oldInternal.nextSibling;
            prev.nextSibling = next;
            if (next == null) {
                firstChild.previousSibling = prev;
            } else {
                next.previousSibling = prev;
            }
        }

        oldInternal.ownerNode       = ownerDocument;
        oldInternal.isOwned(false);
        oldInternal.nextSibling     = null;
        oldInternal.previousSibling = null;

        changed();

        ownerDocument.removedNode(this, replace);

        checkNormalizationAfterRemove(oldPreviousSibling);

        return oldInternal;

    } 

    /**
     * Make newChild occupy the location that oldChild used to
     * have. Note that newChild will first be removed from its previous
     * parent, if any. Equivalent to inserting newChild before oldChild,
     * then removing oldChild.
     *
     * @return oldChild, in its new state (removed).
     *
     * @throws DOMException(HIERARCHY_REQUEST_ERR) if newChild is of a
     * type that shouldn't be a child of this node, or if newChild is
     * one of our ancestors.
     *
     * @throws DOMException(WRONG_DOCUMENT_ERR) if newChild has a
     * different owner document than we do.
     *
     * @throws DOMException(NOT_FOUND_ERR) if oldChild is not a child of
     * this node.
     *
     * @throws DOMException(NO_MODIFICATION_ALLOWED_ERR) if this node is
     * read-only.
     */
    public Node replaceChild(Node newChild, Node oldChild)
        throws DOMException {

        ownerDocument.replacingNode(this);

        internalInsertBefore(newChild, oldChild, true);
        if (newChild != oldChild) {
            internalRemoveChild(oldChild, true);
        }

        ownerDocument.replacedNode(this);

        return oldChild;
    }

    /*
     * Get Node text content
     * @since DOM Level 3
     */
    public String getTextContent() throws DOMException {
        Node child = getFirstChild();
        if (child != null) {
            Node next = child.getNextSibling();
            if (next == null) {
                return hasTextContent(child) ? ((NodeImpl) child).getTextContent() : "";
            }
            StringBuilder buf = new StringBuilder();
            getTextContent(buf);
            return buf.toString();
        }
        return "";
    }

    void getTextContent(StringBuilder buf) throws DOMException {
        Node child = getFirstChild();
        while (child != null) {
            if (hasTextContent(child)) {
                ((NodeImpl) child).getTextContent(buf);
            }
            child = child.getNextSibling();
        }
    }

    final boolean hasTextContent(Node child) {
        return child.getNodeType() != Node.COMMENT_NODE &&
            child.getNodeType() != Node.PROCESSING_INSTRUCTION_NODE &&
            (child.getNodeType() != Node.TEXT_NODE ||
             ((TextImpl) child).isIgnorableWhitespace() == false);
    }

    /*
     * Set Node text content
     * @since DOM Level 3
     */
    public void setTextContent(String textContent)
        throws DOMException {
        Node child;
        while ((child = getFirstChild()) != null) {
            removeChild(child);
        }
        if (textContent != null && textContent.length() != 0){
            appendChild(ownerDocument().createTextNode(textContent));
        }
    }


    /**
     * Count the immediate children of this node.  Use to implement
     * NodeList.getLength().
     * @return int
     */
    private int nodeListGetLength() {

        if (fNodeListCache == null) {
            if (needsSyncChildren()) {
                synchronizeChildren();
            }
            if (firstChild == null) {
                return 0;
            }
            if (firstChild == lastChild()) {
                return 1;
            }
            fNodeListCache = ownerDocument.getNodeListCache(this);
        }
        if (fNodeListCache.fLength == -1) { 
            int l;
            ChildNode n;
            if (fNodeListCache.fChildIndex != -1 &&
                fNodeListCache.fChild != null) {
                l = fNodeListCache.fChildIndex;
                n = fNodeListCache.fChild;
            } else {
                n = firstChild;
                l = 0;
            }
            while (n != null) {
                l++;
                n = n.nextSibling;
            }
            fNodeListCache.fLength = l;
        }

        return fNodeListCache.fLength;

    } 

    /**
     * NodeList method: Count the immediate children of this node
     * @return int
     */
    public int getLength() {
        return nodeListGetLength();
    }

    /**
     * Return the Nth immediate child of this node, or null if the index is
     * out of bounds.  Use to implement NodeList.item().
     * @param index int
     */
    private Node nodeListItem(int index) {

        if (fNodeListCache == null) {
            if (needsSyncChildren()) {
                synchronizeChildren();
            }
            if (firstChild == lastChild()) {
                return index == 0 ? firstChild : null;
            }
            fNodeListCache = ownerDocument.getNodeListCache(this);
        }
        int i = fNodeListCache.fChildIndex;
        ChildNode n = fNodeListCache.fChild;
        boolean firstAccess = true;
        if (i != -1 && n != null) {
            firstAccess = false;
            if (i < index) {
                while (i < index && n != null) {
                    i++;
                    n = n.nextSibling;
                }
            }
            else if (i > index) {
                while (i > index && n != null) {
                    i--;
                    n = n.previousSibling();
                }
            }
        }
        else {
            if (index < 0) {
                return null;
            }
            n = firstChild;
            for (i = 0; i < index && n != null; i++) {
                n = n.nextSibling;
            }
        }

        if (!firstAccess && (n == firstChild || n == lastChild())) {
            fNodeListCache.fChildIndex = -1;
            fNodeListCache.fChild = null;
            ownerDocument.freeNodeListCache(fNodeListCache);
        }
        else {
            fNodeListCache.fChildIndex = i;
            fNodeListCache.fChild = n;
        }
        return n;

    } 

    /**
     * NodeList method: Return the Nth immediate child of this node, or
     * null if the index is out of bounds.
     * @return org.w3c.dom.Node
     * @param index int
     */
    public Node item(int index) {
        return nodeListItem(index);
    } 

    /**
     * Create a NodeList to access children that is use by subclass elements
     * that have methods named getLength() or item(int).  ChildAndParentNode
     * optimizes getChildNodes() by implementing NodeList itself.  However if
     * a subclass Element implements methods with the same name as the NodeList
     * methods, they will override the actually methods in this class.
     * <p>
     * To use this method, the subclass should implement getChildNodes() and
     * have it call this method.  The resulting NodeList instance maybe
     * shared and cached in a transient field, but the cached value must be
     * cleared if the node is cloned.
     */
    protected final NodeList getChildNodesUnoptimized() {
        if (needsSyncChildren()) {
            synchronizeChildren();
        }
        return new NodeList() {
                /**
                 * @see NodeList.getLength()
                 */
                public int getLength() {
                    return nodeListGetLength();
                } 

                /**
                 * @see NodeList.item(int)
                 */
                public Node item(int index) {
                    return nodeListItem(index);
                } 
            };
    } 


    /**
     * Override default behavior to call normalize() on this Node's
     * children. It is up to implementors or Node to override normalize()
     * to take action.
     */
    public void normalize() {
        if (isNormalized()) {
            return;
        }
        if (needsSyncChildren()) {
            synchronizeChildren();
        }
        ChildNode kid;
        for (kid = firstChild; kid != null; kid = kid.nextSibling) {
            kid.normalize();
        }
        isNormalized(true);
    }

    /**
     * DOM Level 3 WD- Experimental.
     * Override inherited behavior from NodeImpl to support deep equal.
     */
    public boolean isEqualNode(Node arg) {
        if (!super.isEqualNode(arg)) {
            return false;
        }
        Node child1 = getFirstChild();
        Node child2 = arg.getFirstChild();
        while (child1 != null && child2 != null) {
            if (!child1.isEqualNode(child2)) {
                return false;
            }
            child1 = child1.getNextSibling();
            child2 = child2.getNextSibling();
        }
        if (child1 != child2) {
            return false;
        }
        return true;
    }


    /**
     * Override default behavior so that if deep is true, children are also
     * toggled.
     * @see Node
     * <P>
     * Note: this will not change the state of an EntityReference or its
     * children, which are always read-only.
     */
    public void setReadOnly(boolean readOnly, boolean deep) {

        super.setReadOnly(readOnly, deep);

        if (deep) {

            if (needsSyncChildren()) {
                synchronizeChildren();
            }

            for (ChildNode mykid = firstChild;
                 mykid != null;
                 mykid = mykid.nextSibling) {
                if (mykid.getNodeType() != Node.ENTITY_REFERENCE_NODE) {
                    mykid.setReadOnly(readOnly,true);
                }
            }
        }
    } 


    /**
     * Override this method in subclass to hook in efficient
     * internal data structure.
     */
    protected void synchronizeChildren() {
        needsSyncChildren(false);
    }

    /**
     * Checks the normalized state of this node after inserting a child.
     * If the inserted child causes this node to be unnormalized, then this
     * node is flagged accordingly.
     * The conditions for changing the normalized state are:
     * <ul>
     * <li>The inserted child is a text node and one of its adjacent siblings
     * is also a text node.
     * <li>The inserted child is is itself unnormalized.
     * </ul>
     *
     * @param insertedChild the child node that was inserted into this node
     *
     * @throws NullPointerException if the inserted child is <code>null</code>
     */
    void checkNormalizationAfterInsert(ChildNode insertedChild) {
        if (insertedChild.getNodeType() == Node.TEXT_NODE) {
            ChildNode prev = insertedChild.previousSibling();
            ChildNode next = insertedChild.nextSibling;
            if ((prev != null && prev.getNodeType() == Node.TEXT_NODE) ||
                (next != null && next.getNodeType() == Node.TEXT_NODE)) {
                isNormalized(false);
            }
        }
        else {
            if (!insertedChild.isNormalized()) {
                isNormalized(false);
            }
        }
    } 

    /**
     * Checks the normalized of this node after removing a child.
     * If the removed child causes this node to be unnormalized, then this
     * node is flagged accordingly.
     * The conditions for changing the normalized state are:
     * <ul>
     * <li>The removed child had two adjacent siblings that were text nodes.
     * </ul>
     *
     * @param previousSibling the previous sibling of the removed child, or
     * <code>null</code>
     */
    void checkNormalizationAfterRemove(ChildNode previousSibling) {
        if (previousSibling != null &&
            previousSibling.getNodeType() == Node.TEXT_NODE) {

            ChildNode next = previousSibling.nextSibling;
            if (next != null && next.getNodeType() == Node.TEXT_NODE) {
                isNormalized(false);
            }
        }
    } 


    /** Serialize object. */
    private void writeObject(ObjectOutputStream out) throws IOException {

        if (needsSyncChildren()) {
            synchronizeChildren();
        }
        out.defaultWriteObject();

    } 

    /** Deserialize object. */
    private void readObject(ObjectInputStream ois)
        throws ClassNotFoundException, IOException {

        ois.defaultReadObject();

        needsSyncChildren(false);

    } 

    /*
     * a class to store some user data along with its handler
     */
    class UserDataRecord implements Serializable {
        /** Serialization version. */
        private static final long serialVersionUID = 3258126977134310455L;

        @SuppressWarnings("serial") 
        Object fData;
        @SuppressWarnings("serial") 
        UserDataHandler fHandler;
        UserDataRecord(Object data, UserDataHandler handler) {
            fData = data;
            fHandler = handler;
        }
    }
} 
