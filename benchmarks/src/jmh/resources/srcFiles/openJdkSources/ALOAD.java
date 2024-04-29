/*
 * Copyright (c) 2017, 2020, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.org.apache.bcel.internal.generic;

/**
 * ALOAD - Load reference from local variable
 *
 * <PRE>
 * Stack: ... -&gt; ..., objectref
 * </PRE>
 * @LastModified: Jan 2020
 */
public class ALOAD extends LoadInstruction {

    /**
     * Empty constructor needed for Instruction.readInstruction. Not to be used otherwise.
     */
    ALOAD() {
        super(com.sun.org.apache.bcel.internal.Const.ALOAD, com.sun.org.apache.bcel.internal.Const.ALOAD_0);
    }

    /**
     * Load reference from local variable
     *
     * @param n index of local variable
     */
    public ALOAD(final int n) {
        super(com.sun.org.apache.bcel.internal.Const.ALOAD, com.sun.org.apache.bcel.internal.Const.ALOAD_0, n);
    }

    /**
     * Call corresponding visitor method(s). The order is: Call visitor methods of implemented interfaces first, then call
     * methods according to the class hierarchy in descending order, i.e., the most specific visitXXX() call comes last.
     *
     * @param v Visitor object
     */
    @Override
    public void accept(final Visitor v) {
        super.accept(v);
        v.visitALOAD(this);
    }
}
