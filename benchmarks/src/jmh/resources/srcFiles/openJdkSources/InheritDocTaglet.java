/*
 * Copyright (c) 2001, 2023, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

package jdk.javadoc.internal.doclets.formats.html.taglets;

import java.util.EnumSet;
import java.util.List;

import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;

import com.sun.source.doctree.DocTree;
import com.sun.source.doctree.InheritDocTree;
import com.sun.source.util.DocTreePath;

import jdk.javadoc.doclet.Taglet.Location;
import jdk.javadoc.internal.doclets.formats.html.HtmlConfiguration;
import jdk.javadoc.internal.doclets.formats.html.Content;
import jdk.javadoc.internal.doclets.toolkit.util.CommentHelper;
import jdk.javadoc.internal.doclets.toolkit.util.DocFinder;
import jdk.javadoc.internal.doclets.toolkit.util.DocFinder.Result;
import jdk.javadoc.internal.doclets.toolkit.util.Utils;
import jdk.javadoc.internal.doclets.toolkit.util.VisibleMemberTable;

/**
 * A taglet that represents the {@code {@inheritDoc}} tag.
 */
public class InheritDocTaglet extends BaseTaglet {

    /**
     * Construct a new InheritDocTaglet.
     */
    InheritDocTaglet(HtmlConfiguration config) {
        super(config, DocTree.Kind.INHERIT_DOC, true, EnumSet.of(Location.METHOD));
    }

    /**
     * Given an element and {@code @inheritDoc} tag in that element's doc comment,
     * returns the (recursive) expansion of that tag.
     *
     * <p>This method does not expand all {@code {@inheritDoc}} tags in the given
     * element's doc comment. To do this, the method must be called for every
     * such tag.</p>
     *
     * @param writer the writer that is writing the output.
     * @param method the method that we are documenting.
     * @param inheritDoc the {@code {@inheritDoc}} tag
     * @param isFirstSentence true if we only want to inherit the first sentence
     */
    private Content retrieveInheritedDocumentation(TagletWriter writer,
                                                   ExecutableElement method,
                                                   InheritDocTree inheritDoc,
                                                   boolean isFirstSentence) {
        Content replacement = writer.getOutputInstance();
        CommentHelper ch = utils.getCommentHelper(method);
        DocTreePath inheritDocPath = ch.getDocTreePath(inheritDoc);
        var path = inheritDocPath.getParentPath();
        DocTree holderTag = path.getLeaf();

        ExecutableElement src = null;
        if (inheritDoc.getSupertype() != null) {
            var supertype = (TypeElement) ch.getReferencedElement(inheritDoc.getSupertype());
            if (supertype == null) {
                messages.error(inheritDocPath, "doclet.inheritDocBadSupertype");
                return replacement;
            }
            VisibleMemberTable visibleMemberTable = config.getVisibleMemberTable(supertype);
            List<Element> methods = visibleMemberTable.getAllVisibleMembers(VisibleMemberTable.Kind.METHODS);
            for (Element e : methods) {
                ExecutableElement m = (ExecutableElement) e;
                if (utils.elementUtils.overrides(method, m, (TypeElement) method.getEnclosingElement())) {
                    assert !method.equals(m) : Utils.diagnosticDescriptionOf(method);
                    src = m;
                    break;
                }
            }
            if (src == null) {
                messages.error(inheritDocPath, "doclet.inheritDocBadSupertype");
                return replacement;
            }
        }

        if (holderTag.getKind() == DocTree.Kind.DOC_COMMENT) {
            try {
                var docFinder = utils.docFinder();
                Result<Documentation> d;
                if (src == null) {
                    d = docFinder.find(method, m -> extractMainDescription(m, isFirstSentence, utils));
                } else {
                    d = docFinder.search(src, m -> extractMainDescription(m, isFirstSentence, utils));
                }
                if (d instanceof Result.Conclude<Documentation> doc) {
                    replacement = writer.commentTagsToOutput(doc.value().method, null,
                            doc.value().mainDescription, isFirstSentence);
                }
            } catch (DocFinder.NoOverriddenMethodFound e) {
                String signature = utils.getSimpleName(method)
                        + utils.flatSignature(method, writer.getCurrentPageElement());
                messages.warning(method, "doclet.noInheritedDoc", signature);
            }
            return replacement;
        }

        Taglet taglet = config.tagletManager.getTaglet(ch.getTagName(holderTag));
        assert taglet != null;
        if (!(taglet instanceof InheritableTaglet inheritableTaglet)) {
            messages.warning(path, "doclet.inheritDocWithinInappropriateTag");
            return replacement;
        }

        InheritableTaglet.Output inheritedDoc = inheritableTaglet.inherit(method, src, holderTag, isFirstSentence);

        if (inheritedDoc.isValidInheritDocTag()) {
            if (!inheritedDoc.inlineTags().isEmpty()) {
                replacement = writer.commentTagsToOutput(inheritedDoc.holder(), inheritedDoc.holderTag(),
                        inheritedDoc.inlineTags(), isFirstSentence);
            }
        } else {
            String signature = utils.getSimpleName(method)
                    + utils.flatSignature(method, writer.getCurrentPageElement());
            messages.warning(method, "doclet.noInheritedDoc", signature);
        }
        return replacement;
    }

    private record Documentation(List<? extends DocTree> mainDescription, ExecutableElement method) { }

    private static Result<Documentation> extractMainDescription(ExecutableElement m,
                                                                boolean extractFirstSentenceOnly,
                                                                Utils utils) {
        var mainDescriptionTrees = extractFirstSentenceOnly
                ? utils.getFirstSentenceTrees(m)
                : utils.getFullBody(m);
        return mainDescriptionTrees.isEmpty() ? Result.CONTINUE() : Result.CONCLUDE(new Documentation(mainDescriptionTrees, m));
    }

    @Override
    public Content getInlineTagOutput(Element e, DocTree inheritDoc, TagletWriter tagletWriter) {
        if (e.getKind() != ElementKind.METHOD) {
            return tagletWriter.getOutputInstance();
        }
        return retrieveInheritedDocumentation(tagletWriter,
                (ExecutableElement) e,
                (InheritDocTree) inheritDoc,
                tagletWriter.context.isFirstSentence);
    }
}
