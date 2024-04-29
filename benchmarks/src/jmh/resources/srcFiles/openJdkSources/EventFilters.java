/*
 * Copyright (c) 2006, 2023, Oracle and/or its affiliates. All rights reserved.
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
package nsk.share.jdi;

import java.lang.reflect.*;
import com.sun.jdi.*;
import com.sun.jdi.event.*;
import com.sun.jdi.request.*;
import nsk.share.TestBug;

/*
 * EventFilters class just contain all filter classes
 */
public class EventFilters
{
    /*
     * Class is intended for testing event filters.
     *
     * Since different request classes have identical methods for adding filters(e.g. addInstanceFilter(),addClassFilter())
     * but this classes don't have common superclass reflection is used for filter adding.
     * Subclasses should implement following methods:
     * - getMethodName(), provide filter adding method's name
     * - getParametersTypes(), provide filter adding method's parameters types
     * - getFilterParameters(), provide parameters to be passed in filter adding method
     *
     * Also to check is generated event was really filtered subclasses should implement method 'isObjectMatch(ObjectReference eventObject, ThreadReference eventThread)',
     * this method should check is event generated by given object in given thread accepted by filter.
     */
    public abstract static class DebugEventFilter
    {
        abstract public boolean isObjectMatch(ObjectReference eventObject, ThreadReference eventThread);

        public void addFilter(EventRequest request)
        throws Throwable
        {
            java.lang.reflect.Method method;

            try
            {
                method = request.getClass().getMethod(getMethodName(), getParametersTypes());
            }
            catch(Exception e)
            {
                throw new TestBug("Can't get method '" + getMethodName() + "'");
            }

            try
            {
                method.setAccessible(true);
                method.invoke(request, getFilterParameters());
            }
            catch(IllegalAccessException e)
            {
                TestBug testBug = new TestBug("Can't call method '" + getMethodName() + "'");
                testBug.initCause(e);
                throw testBug;
            }
            catch(InvocationTargetException e)
            {
                throw e.getCause();
            }
        }

        public boolean isSupported(VirtualMachine vm)
        {
            return true;
        }

        abstract protected String getMethodName();
        abstract protected Class[] getParametersTypes();
        abstract protected Object[] getFilterParameters();
    }

    /*
     * Restricts the events to those whose method is in a class whose name matches this
     * restricted regular expression. Only simple regular expressions that begin with '*' or end with '*' are supported
     */
    public static class ClassFilter
    extends DebugEventFilter
    {
        protected String classPattern;

        private String startsWithPattern;
        private String endsWithPattern;

        public ClassFilter(String classPattern)
        {
            this.classPattern = classPattern;

            if(classPattern.startsWith("*"))
                endsWithPattern = classPattern.substring(1);
            else
                if(classPattern.endsWith("*"))
                    startsWithPattern = classPattern.substring(0, classPattern.length() - 1);
        }

        public String toString()
        {
            return "ClassFilter: classes should match pattern: " + classPattern;
        }

        protected String getMethodName()
        {
            return "addClassFilter";
        }

        protected Class[] getParametersTypes()
        {
            return new Class[]{String.class};
        }

        protected Object[] getFilterParameters()
        {
            return new Object[]{classPattern};
        }

        public boolean isObjectMatch(ObjectReference eventObject, ThreadReference eventThread)
        {
            if(!isNameMatch(eventObject.referenceType().name()))
                return false;
            else
                return true;
        }

        protected boolean isNameMatch(String className)
        {
            if(startsWithPattern != null)
                return className.startsWith(startsWithPattern);
            else
                if(endsWithPattern != null)
                    return className.endsWith(endsWithPattern);
                else
                    return className.equals(classPattern);
        }
    }

    /*
     * Restricts the events to those whose method is in a class whose name doesn't matches
     * restricted regular expression. Only simple regular expressions that begin with '*' or end with '*' are supported
     */
    public static class ClassExclusionFilter
    extends ClassFilter
    {
        public ClassExclusionFilter(String classPattern)
        {
            super(classPattern);
        }

        public String toString()
        {
            return "ClassExclusionFilter: classes match follows pattern should be excluded: " + classPattern;
        }

        protected String getMethodName()
        {
            return "addClassExclusionFilter";
        }

        public boolean isObjectMatch(ObjectReference eventObject, ThreadReference eventThread)
        {
            if(isNameMatch(eventObject.referenceType().name()))
                return false;
            else
                return true;
        }
    }

    /*
     * Restricts the events to those whose method is in the given reference type or any of its subtypes
     */
    public static class ClassReferenceFilter
    extends DebugEventFilter
    {
        private Class<?> filterClass;
        private ReferenceType referenceType;

        public ClassReferenceFilter(ReferenceType referenceType)
        {
            this.referenceType = referenceType;
            filterClass = findClass(referenceType);
        }

        public String toString()
        {
            return "ClassReferenceFilter: expect only " + filterClass.getName() + " and its subclasses";
        }

        protected String getMethodName()
        {
            return "addClassFilter";
        }

        protected Class[] getParametersTypes()
        {
            return new Class[]{ReferenceType.class};
        }

        protected Object[] getFilterParameters()
        {
            return new Object[]{referenceType};
        }

        public boolean isObjectMatch(ObjectReference eventObject, ThreadReference eventThread)
        {
            Class<?> eventObjectClass = findClass(eventObject.referenceType());

            if(!filterClass.isAssignableFrom(eventObjectClass))
                return false;
            else
                return true;
        }

        private Class<?> findClass(ReferenceType referenceType)
        {
            try
            {
                return Class.forName(referenceType.name());
            }
            catch(ClassNotFoundException e)
            {
                throw new TestBug("Can't find class: " + referenceType.name());
            }
        }
    }

    /*
     * Restricts the events to those in which the currently executing instance ("this") is the given object
     */
    public static class ObjectReferenceFilter
    extends DebugEventFilter
    {
        private ObjectReference objectReference;

        public ObjectReferenceFilter(ObjectReference objectReference)
        {
            this.objectReference = objectReference;
        }

        public String toString()
        {
            return "ObjectReferenceFilter: expect only object " + objectReference;
        }

        protected String getMethodName()
        {
            return "addInstanceFilter";
        }

        protected Class[] getParametersTypes()
        {
            return new Class[]{ObjectReference.class};
        }

        protected Object[] getFilterParameters()
        {
            return new Object[]{objectReference};
        }

        public boolean isObjectMatch(ObjectReference eventObject, ThreadReference eventThread)
        {
            return objectReference.equals(eventObject);
        }

        public boolean isSupported(VirtualMachine vm)
        {
            return vm.canUseInstanceFilters();
        }
    }

    /*
     * Restricts the events to those in the given thread
     */
    public static class ThreadFilter
    extends DebugEventFilter
    {
        private ThreadReference threadReference;

        public ThreadFilter(ThreadReference threadReference)
        {
            this.threadReference = threadReference;
        }

        public String toString()
        {
            return "ThreadReferenceFilter: expect only thread " + threadReference;
        }

        protected String getMethodName()
        {
            return "addThreadFilter";
        }

        protected Class[] getParametersTypes()
        {
            return new Class[]{ThreadReference.class};
        }

        protected Object[] getFilterParameters()
        {
            return new Object[]{threadReference};
        }

        public boolean isObjectMatch(ObjectReference eventObject, ThreadReference eventThread)
        {
            return threadReference.equals(eventThread);
        }
    }

    public static boolean filtered(Event event) {
        if (event instanceof ThreadStartEvent) {
            ThreadStartEvent tse = (ThreadStartEvent)event;
            String tname = tse.thread().name();
            String knownThreads[] = {
                "VM JFR Buffer Thread",
                "JFR request timer",
                "Reference Handler",
                "VirtualThread-unparker",
                "Cleaner-",
                "Common-Cleaner",
                "FinalizerThread",
                "ForkJoinPool"
            };
            for (String s : knownThreads) {
                if (tname.startsWith(s)) {
                    return true;
                }
            }
        }
        return false;
    }

    public static boolean filtered(Event event, String typeName) {
        if (event instanceof Locatable) {
            Location location = ((Locatable) event).location();
            if (location != null) {
                ReferenceType declaringType = location.declaringType();
                if (declaringType != null && typeName.equals(declaringType.name())) {
                    return false;
                }
            }
        }
        return true;
    }
}
