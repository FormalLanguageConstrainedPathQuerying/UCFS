/*
 * reserved comment block
 * DO NOT REMOVE OR ALTER!
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

package com.sun.org.apache.xalan.internal.xsltc.compiler;

import com.sun.org.apache.bcel.internal.generic.ALOAD;
import com.sun.org.apache.bcel.internal.generic.ASTORE;
import com.sun.org.apache.bcel.internal.generic.ConstantPoolGen;
import com.sun.org.apache.bcel.internal.generic.INVOKEINTERFACE;
import com.sun.org.apache.bcel.internal.generic.INVOKESPECIAL;
import com.sun.org.apache.bcel.internal.generic.InstructionList;
import com.sun.org.apache.bcel.internal.generic.LocalVariableGen;
import com.sun.org.apache.bcel.internal.generic.NEW;
import com.sun.org.apache.xalan.internal.xsltc.compiler.util.ClassGenerator;
import com.sun.org.apache.xalan.internal.xsltc.compiler.util.MethodGenerator;
import com.sun.org.apache.xalan.internal.xsltc.compiler.util.NodeType;
import com.sun.org.apache.xalan.internal.xsltc.compiler.util.Type;
import com.sun.org.apache.xalan.internal.xsltc.compiler.util.TypeCheckError;
import com.sun.org.apache.xalan.internal.xsltc.compiler.util.Util;

/**
 * @author G. Todd Miller
 */
final class FilteredAbsoluteLocationPath extends Expression {
    private Expression _path;   

    public FilteredAbsoluteLocationPath() {
        _path = null;
    }

    public FilteredAbsoluteLocationPath(Expression path) {
        _path = path;
        if (path != null) {
            _path.setParent(this);
        }
    }

    public void setParser(Parser parser) {
        super.setParser(parser);
        if (_path != null) {
            _path.setParser(parser);
        }
    }

    public Expression getPath() {
        return(_path);
    }

    public String toString() {
        return "FilteredAbsoluteLocationPath(" +
            (_path != null ? _path.toString() : "null") + ')';
    }

    public Type typeCheck(SymbolTable stable) throws TypeCheckError {
        if (_path != null) {
            final Type ptype = _path.typeCheck(stable);
            if (ptype instanceof NodeType) {            
                _path = new CastExpr(_path, Type.NodeSet);
            }
        }
        return _type = Type.NodeSet;
    }

    public void translate(ClassGenerator classGen, MethodGenerator methodGen) {
        final ConstantPoolGen cpg = classGen.getConstantPool();
        final InstructionList il = methodGen.getInstructionList();
        if (_path != null) {
            final int initDFI = cpg.addMethodref(DUP_FILTERED_ITERATOR,
                                                "<init>",
                                                "("
                                                + NODE_ITERATOR_SIG
                                                + ")V");


            LocalVariableGen pathTemp =
               methodGen.addLocalVariable("filtered_absolute_location_path_tmp",
                                          Util.getJCRefType(NODE_ITERATOR_SIG),
                                          null, null);
            _path.translate(classGen, methodGen);
            pathTemp.setStart(il.append(new ASTORE(pathTemp.getIndex())));

            il.append(new NEW(cpg.addClass(DUP_FILTERED_ITERATOR)));
            il.append(DUP);
            pathTemp.setEnd(il.append(new ALOAD(pathTemp.getIndex())));

            il.append(new INVOKESPECIAL(initDFI));
        }
        else {
            final int git = cpg.addInterfaceMethodref(DOM_INTF,
                                                      "getIterator",
                                                      "()"+NODE_ITERATOR_SIG);
            il.append(methodGen.loadDOM());
            il.append(new INVOKEINTERFACE(git, 1));
        }
    }
}
