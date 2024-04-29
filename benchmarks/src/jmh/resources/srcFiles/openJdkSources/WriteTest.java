/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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

/*
 * @test
 * @summary Testing ClassFile class building.
 * @run junit WriteTest
 */
import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;

import helpers.TestConstants;
import java.lang.classfile.AccessFlags;
import java.lang.reflect.AccessFlag;
import java.lang.classfile.ClassFile;
import java.lang.classfile.TypeKind;
import java.lang.classfile.Label;
import java.lang.classfile.attribute.SourceFileAttribute;
import org.junit.jupiter.api.Test;

import static helpers.TestConstants.MTD_VOID;
import static java.lang.constant.ConstantDescs.*;
import static java.lang.classfile.Opcode.*;
import static java.lang.classfile.TypeKind.IntType;
import static java.lang.classfile.TypeKind.ReferenceType;
import static java.lang.classfile.TypeKind.VoidType;

class WriteTest {

    @Test
    void testJavapWrite() {

        byte[] bytes = ClassFile.of().build(ClassDesc.of("MyClass"), cb -> {
            cb.withFlags(AccessFlag.PUBLIC);
            cb.with(SourceFileAttribute.of(cb.constantPool().utf8Entry(("MyClass.java"))))
              .withMethod("<init>", MethodTypeDesc.of(CD_void), 0, mb -> mb
                      .withCode(codeb -> codeb.loadInstruction(TypeKind.ReferenceType, 0)
                                              .invokeInstruction(INVOKESPECIAL, CD_Object, "<init>",
                                                                 MethodTypeDesc.ofDescriptor("()V"), false)
                                              .returnInstruction(VoidType)
                      )
              )
              .withMethod("main", MethodTypeDesc.of(CD_void, CD_String.arrayType()),
                          AccessFlags.ofMethod(AccessFlag.PUBLIC, AccessFlag.STATIC).flagsMask(),
                          mb -> mb.withCode(c0 -> {
                                  Label loopTop = c0.newLabel();
                                  Label loopEnd = c0.newLabel();
                                  c0
                                          .constantInstruction(ICONST_1, 1)         
                                          .storeInstruction(TypeKind.IntType, 1)          
                                          .constantInstruction(ICONST_1, 1)         
                                          .storeInstruction(TypeKind.IntType, 2)          
                                          .labelBinding(loopTop)
                                          .loadInstruction(TypeKind.IntType, 2)           
                                          .constantInstruction(BIPUSH, 10)         
                                          .branchInstruction(IF_ICMPGE, loopEnd) 
                                          .loadInstruction(TypeKind.IntType, 1)           
                                          .loadInstruction(TypeKind.IntType, 2)           
                                          .operatorInstruction(IMUL)             
                                          .storeInstruction(TypeKind.IntType, 1)          
                                          .incrementInstruction(2, 1)    
                                          .branchInstruction(GOTO, loopTop)     
                                          .labelBinding(loopEnd)
                                          .fieldInstruction(GETSTATIC, TestConstants.CD_System, "out", TestConstants.CD_PrintStream)   
                                          .loadInstruction(TypeKind.IntType, 1)
                                          .invokeInstruction(INVOKEVIRTUAL, TestConstants.CD_PrintStream, "println", TestConstants.MTD_INT_VOID, false)  
                                          .returnInstruction(VoidType);
                              }));
        });
    }

    @Test
    void testPrimitiveWrite() {

        byte[] bytes = ClassFile.of().build(ClassDesc.of("MyClass"), cb -> {
            cb.withFlags(AccessFlag.PUBLIC)
              .with(SourceFileAttribute.of(cb.constantPool().utf8Entry(("MyClass.java"))))
              .withMethod("<init>", MethodTypeDesc.of(CD_void), 0, mb -> mb
                      .withCode(codeb -> codeb.loadInstruction(ReferenceType, 0)
                                              .invokeInstruction(INVOKESPECIAL, CD_Object, "<init>", MTD_VOID, false)
                                              .returnInstruction(VoidType)
                      )
              )
              .withMethod("main", MethodTypeDesc.of(CD_void, CD_String.arrayType()),
                          AccessFlags.ofMethod(AccessFlag.PUBLIC, AccessFlag.STATIC).flagsMask(),
                          mb -> mb.withCode(c0 -> {
                                  Label loopTop = c0.newLabel();
                                  Label loopEnd = c0.newLabel();
                                  c0
                                          .constantInstruction(ICONST_1, 1)        
                                          .storeInstruction(IntType, 1)          
                                          .constantInstruction(ICONST_1, 1)        
                                          .storeInstruction(IntType, 2)          
                                          .labelBinding(loopTop)
                                          .loadInstruction(IntType, 2)           
                                          .constantInstruction(BIPUSH, 10)         
                                          .branchInstruction(IF_ICMPGE, loopEnd) 
                                          .loadInstruction(IntType, 1)           
                                          .loadInstruction(IntType, 2)           
                                          .operatorInstruction(IMUL)             
                                          .storeInstruction(IntType, 1)          
                                          .incrementInstruction(2, 1)    
                                          .branchInstruction(GOTO, loopTop)     
                                          .labelBinding(loopEnd)
                                          .fieldInstruction(GETSTATIC, TestConstants.CD_System, "out", TestConstants.CD_PrintStream)   
                                          .loadInstruction(IntType, 1)
                                          .invokeInstruction(INVOKEVIRTUAL, TestConstants.CD_PrintStream, "println", TestConstants.MTD_INT_VOID, false)  
                                          .returnInstruction(VoidType);
                              }));
        });
    }
}
