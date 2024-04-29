/*
 * Copyright (c) 2010, 2023, Oracle and/or its affiliates. All rights reserved.
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

/*
 *******************************************************************************
 * Copyright (C) 2010, International Business Machines Corporation and         *
 * others. All Rights Reserved.                                                *
 *******************************************************************************
 */
package sun.util.locale;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IllformedLocaleException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;

public class LanguageTag {
    public static final String SEP = "-";
    public static final String PRIVATEUSE = "x";
    public static final String UNDETERMINED = "und";
    public static final String PRIVUSE_VARIANT_PREFIX = "lvariant";

    private String language = "";      
    private String script = "";        
    private String region = "";        
    private String privateuse = "";    

    private List<String> extlangs = Collections.emptyList();   
    private List<String> variants = Collections.emptyList();   
    private List<String> extensions = Collections.emptyList(); 
    private static final Map<String, String[]> LEGACY = new HashMap<>();

    static {

        final String[][] entries = {
            {"art-lojban",  "jbo"},
            {"cel-gaulish", "xtg-x-cel-gaulish"},   
            {"en-GB-oed",   "en-GB-x-oed"},         
            {"i-ami",       "ami"},
            {"i-bnn",       "bnn"},
            {"i-default",   "en-x-i-default"},      
            {"i-enochian",  "und-x-i-enochian"},    
            {"i-hak",       "hak"},
            {"i-klingon",   "tlh"},
            {"i-lux",       "lb"},
            {"i-mingo",     "see-x-i-mingo"},       
            {"i-navajo",    "nv"},
            {"i-pwn",       "pwn"},
            {"i-tao",       "tao"},
            {"i-tay",       "tay"},
            {"i-tsu",       "tsu"},
            {"no-bok",      "nb"},
            {"no-nyn",      "nn"},
            {"sgn-BE-FR",   "sfb"},
            {"sgn-BE-NL",   "vgt"},
            {"sgn-CH-DE",   "sgg"},
            {"zh-guoyu",    "cmn"},
            {"zh-hakka",    "hak"},
            {"zh-min",      "nan-x-zh-min"},        
            {"zh-min-nan",  "nan"},
            {"zh-xiang",    "hsn"},
        };
        for (String[] e : entries) {
            LEGACY.put(LocaleUtils.toLowerString(e[0]), e);
        }
    }

    private LanguageTag() {
    }

    /*
     * BNF in RFC5646
     *
     * Language-Tag  = langtag             ; normal language tags
     *               / privateuse          ; private use tag
     *               / grandfathered       ; grandfathered tags
     *
     *
     * langtag       = language
     *                 ["-" script]
     *                 ["-" region]
     *                 *("-" variant)
     *                 *("-" extension)
     *                 ["-" privateuse]
     *
     * language      = 2*3ALPHA            ; shortest ISO 639 code
     *                 ["-" extlang]       ; sometimes followed by
     *                                     ; extended language subtags
     *               / 4ALPHA              ; or reserved for future use
     *               / 5*8ALPHA            ; or registered language subtag
     *
     * extlang       = 3ALPHA              ; selected ISO 639 codes
     *                 *2("-" 3ALPHA)      ; permanently reserved
     *
     * script        = 4ALPHA              ; ISO 15924 code
     *
     * region        = 2ALPHA              ; ISO 3166-1 code
     *               / 3DIGIT              ; UN M.49 code
     *
     * variant       = 5*8alphanum         ; registered variants
     *               / (DIGIT 3alphanum)
     *
     * extension     = singleton 1*("-" (2*8alphanum))
     *
     *                                     ; Single alphanumerics
     *                                     ; "x" reserved for private use
     * singleton     = DIGIT               ; 0 - 9
     *               / %x41-57             ; A - W
     *               / %x59-5A             ; Y - Z
     *               / %x61-77             ; a - w
     *               / %x79-7A             ; y - z
     *
     * privateuse    = "x" 1*("-" (1*8alphanum))
     *
     */
    public static LanguageTag parse(String languageTag, ParseStatus sts) {
        if (sts == null) {
            sts = new ParseStatus();
        } else {
            sts.reset();
        }

        StringTokenIterator itr;

        String[] gfmap = LEGACY.get(LocaleUtils.toLowerString(languageTag));
        if (gfmap != null) {
            itr = new StringTokenIterator(gfmap[1], SEP);
        } else {
            itr = new StringTokenIterator(languageTag, SEP);
        }

        LanguageTag tag = new LanguageTag();

        if (tag.parseLanguage(itr, sts)) {
            tag.parseExtlangs(itr, sts);
            tag.parseScript(itr, sts);
            tag.parseRegion(itr, sts);
            tag.parseVariants(itr, sts);
            tag.parseExtensions(itr, sts);
        }
        tag.parsePrivateuse(itr, sts);
        if (!itr.isDone() && !sts.isError()) {
            String s = itr.current();
            sts.errorIndex = itr.currentStart();
            if (s.isEmpty()) {
                sts.errorMsg = "Empty subtag";
            } else {
                sts.errorMsg = "Invalid subtag: " + s;
            }
        }
        return tag;
    }


    private boolean parseLanguage(StringTokenIterator itr, ParseStatus sts) {
        if (itr.isDone() || sts.isError()) {
            return false;
        }

        boolean found = false;

        String s = itr.current();
        if (isLanguage(s)) {
            found = true;
            language = s;
            sts.parseLength = itr.currentEnd();
            itr.next();
        }

        return found;
    }

    private boolean parseExtlangs(StringTokenIterator itr, ParseStatus sts) {
        if (itr.isDone() || sts.isError()) {
            return false;
        }

        boolean found = false;

        while (!itr.isDone()) {
            String s = itr.current();
            if (!isExtlang(s)) {
                break;
            }
            found = true;
            if (extlangs.isEmpty()) {
                extlangs = new ArrayList<>(3);
            }
            extlangs.add(s);
            sts.parseLength = itr.currentEnd();
            itr.next();

            if (extlangs.size() == 3) {
                break;
            }
        }

        return found;
    }

    private boolean parseScript(StringTokenIterator itr, ParseStatus sts) {
        if (itr.isDone() || sts.isError()) {
            return false;
        }

        boolean found = false;

        String s = itr.current();
        if (isScript(s)) {
            found = true;
            script = s;
            sts.parseLength = itr.currentEnd();
            itr.next();
        }

        return found;
    }

    private boolean parseRegion(StringTokenIterator itr, ParseStatus sts) {
        if (itr.isDone() || sts.isError()) {
            return false;
        }

        boolean found = false;

        String s = itr.current();
        if (isRegion(s)) {
            found = true;
            region = s;
            sts.parseLength = itr.currentEnd();
            itr.next();
        }

        return found;
    }

    private boolean parseVariants(StringTokenIterator itr, ParseStatus sts) {
        if (itr.isDone() || sts.isError()) {
            return false;
        }

        boolean found = false;

        while (!itr.isDone()) {
            String s = itr.current();
            if (!isVariant(s)) {
                break;
            }
            found = true;
            if (variants.isEmpty()) {
                variants = new ArrayList<>(3);
            }
            variants.add(s);
            sts.parseLength = itr.currentEnd();
            itr.next();
        }

        return found;
    }

    private boolean parseExtensions(StringTokenIterator itr, ParseStatus sts) {
        if (itr.isDone() || sts.isError()) {
            return false;
        }

        boolean found = false;

        while (!itr.isDone()) {
            String s = itr.current();
            if (isExtensionSingleton(s)) {
                int start = itr.currentStart();
                String singleton = s;
                StringBuilder sb = new StringBuilder(singleton);

                itr.next();
                while (!itr.isDone()) {
                    s = itr.current();
                    if (isExtensionSubtag(s)) {
                        sb.append(SEP).append(s);
                        sts.parseLength = itr.currentEnd();
                    } else {
                        break;
                    }
                    itr.next();
                }

                if (sts.parseLength <= start) {
                    sts.errorIndex = start;
                    sts.errorMsg = "Incomplete extension '" + singleton + "'";
                    break;
                }

                if (extensions.isEmpty()) {
                    extensions = new ArrayList<>(4);
                }
                extensions.add(sb.toString());
                found = true;
            } else {
                break;
            }
        }
        return found;
    }

    private boolean parsePrivateuse(StringTokenIterator itr, ParseStatus sts) {
        if (itr.isDone() || sts.isError()) {
            return false;
        }

        boolean found = false;

        String s = itr.current();
        if (isPrivateusePrefix(s)) {
            int start = itr.currentStart();
            StringBuilder sb = new StringBuilder(s);

            itr.next();
            while (!itr.isDone()) {
                s = itr.current();
                if (!isPrivateuseSubtag(s)) {
                    break;
                }
                sb.append(SEP).append(s);
                sts.parseLength = itr.currentEnd();

                itr.next();
            }

            if (sts.parseLength <= start) {
                sts.errorIndex = start;
                sts.errorMsg = "Incomplete privateuse";
            } else {
                privateuse = sb.toString();
                found = true;
            }
        }

        return found;
    }

    public static String caseFoldTag(String tag) {
        ParseStatus sts = new ParseStatus();
        parse(tag, sts);
        if (sts.errorMsg != null) {
            throw new IllformedLocaleException(String.format("Ill formed tag:" +
                    " %s", sts.errorMsg));
        }
        String potentialLegacy = tag.toLowerCase(Locale.ROOT);
        if (LEGACY.containsKey(potentialLegacy)) {
            return LEGACY.get(potentialLegacy)[0];
        }
        StringBuilder bldr = new StringBuilder(tag.length());
        String[] subtags = tag.split("-");
        boolean privateFound = false;
        boolean singletonFound = false;
        boolean privUseVarFound = false;
        for (int i = 0; i < subtags.length; i++) {
            String subtag = subtags[i];
            if (privUseVarFound) {
                bldr.append(subtag);
            } else if (i > 0 && isVariant(subtag) && !singletonFound && !privateFound) {
                bldr.append(subtag);
            } else if (i > 0 && isRegion(subtag) && !singletonFound && !privateFound) {
                bldr.append(canonicalizeRegion(subtag));
            } else if (i > 0 && isScript(subtag) && !singletonFound && !privateFound) {
                bldr.append(canonicalizeScript(subtag));
            } else {
                if (isPrivateusePrefix(subtag)) {
                    privateFound = true;
                } else if (isExtensionSingleton(subtag)) {
                    singletonFound = true;
                } else if (subtag.equals(PRIVUSE_VARIANT_PREFIX)) {
                    privUseVarFound = true;
                }
                bldr.append(subtag.toLowerCase(Locale.ROOT));
            }
            if (i != subtags.length-1) {
                bldr.append("-");
            }
        }
        return bldr.substring(0);
    }

    public static LanguageTag parseLocale(BaseLocale baseLocale, LocaleExtensions localeExtensions) {
        LanguageTag tag = new LanguageTag();

        String language = baseLocale.getLanguage();
        String script = baseLocale.getScript();
        String region = baseLocale.getRegion();
        String variant = baseLocale.getVariant();

        boolean hasSubtag = false;

        String privuseVar = null;   

        if (isLanguage(language)) {
            if (language.equals("iw")) {
                language = "he";
            } else if (language.equals("ji")) {
                language = "yi";
            } else if (language.equals("in")) {
                language = "id";
            }
            tag.language = language;
        }

        if (isScript(script)) {
            tag.script = canonicalizeScript(script);
            hasSubtag = true;
        }

        if (isRegion(region)) {
            tag.region = canonicalizeRegion(region);
            hasSubtag = true;
        }

        if (tag.language.equals("no") && tag.region.equals("NO") && variant.equals("NY")) {
            tag.language = "nn";
            variant = "";
        }

        if (!variant.isEmpty()) {
            List<String> variants = null;
            StringTokenIterator varitr = new StringTokenIterator(variant, BaseLocale.SEP);
            while (!varitr.isDone()) {
                String var = varitr.current();
                if (!isVariant(var)) {
                    break;
                }
                if (variants == null) {
                    variants = new ArrayList<>();
                }
                variants.add(var);  
                varitr.next();
            }
            if (variants != null) {
                tag.variants = variants;
                hasSubtag = true;
            }
            if (!varitr.isDone()) {
                StringJoiner sj = new StringJoiner(SEP);
                while (!varitr.isDone()) {
                    String prvv = varitr.current();
                    if (!isPrivateuseSubtag(prvv)) {
                        break;
                    }
                    sj.add(prvv);
                    varitr.next();
                }
                if (sj.length() > 0) {
                    privuseVar = sj.toString();
                }
            }
        }

        List<String> extensions = null;
        String privateuse = null;

        if (localeExtensions != null) {
            Set<Character> locextKeys = localeExtensions.getKeys();
            for (Character locextKey : locextKeys) {
                Extension ext = localeExtensions.getExtension(locextKey);
                if (isPrivateusePrefixChar(locextKey)) {
                    privateuse = ext.getValue();
                } else {
                    if (extensions == null) {
                        extensions = new ArrayList<>();
                    }
                    extensions.add(locextKey.toString() + SEP + ext.getValue());
                }
            }
        }

        if (extensions != null) {
            tag.extensions = extensions;
            hasSubtag = true;
        }

        if (privuseVar != null) {
            if (privateuse == null) {
                privateuse = PRIVUSE_VARIANT_PREFIX + SEP + privuseVar;
            } else {
                privateuse = privateuse + SEP + PRIVUSE_VARIANT_PREFIX
                             + SEP + privuseVar.replace(BaseLocale.SEP, SEP);
            }
        }

        if (privateuse != null) {
            tag.privateuse = privateuse;
        }

        if (tag.language.isEmpty() && (hasSubtag || privateuse == null)) {
            tag.language = UNDETERMINED;
        }

        return tag;
    }


    public String getLanguage() {
        return language;
    }

    public List<String> getExtlangs() {
        if (extlangs.isEmpty()) {
            return Collections.emptyList();
        }
        return Collections.unmodifiableList(extlangs);
    }

    public String getScript() {
        return script;
    }

    public String getRegion() {
        return region;
    }

    public List<String> getVariants() {
        if (variants.isEmpty()) {
            return Collections.emptyList();
        }
        return Collections.unmodifiableList(variants);
    }

    public List<String> getExtensions() {
        if (extensions.isEmpty()) {
            return Collections.emptyList();
        }
        return Collections.unmodifiableList(extensions);
    }

    public String getPrivateuse() {
        return privateuse;
    }


    public static boolean isLanguage(String s) {
        int len = s.length();
        return (len >= 2) && (len <= 8) && LocaleUtils.isAlphaString(s);
    }

    public static boolean isExtlang(String s) {
        return (s.length() == 3) && LocaleUtils.isAlphaString(s);
    }

    public static boolean isScript(String s) {
        return (s.length() == 4) && LocaleUtils.isAlphaString(s);
    }

    public static boolean isRegion(String s) {
        return ((s.length() == 2) && LocaleUtils.isAlphaString(s))
                || ((s.length() == 3) && LocaleUtils.isNumericString(s));
    }

    public static boolean isVariant(String s) {
        int len = s.length();
        if (len >= 5 && len <= 8) {
            return LocaleUtils.isAlphaNumericString(s);
        }
        if (len == 4) {
            return LocaleUtils.isNumeric(s.charAt(0))
                    && LocaleUtils.isAlphaNumeric(s.charAt(1))
                    && LocaleUtils.isAlphaNumeric(s.charAt(2))
                    && LocaleUtils.isAlphaNumeric(s.charAt(3));
        }
        return false;
    }

    public static boolean isExtensionSingleton(String s) {

        return (s.length() == 1)
                && LocaleUtils.isAlphaString(s)
                && !LocaleUtils.caseIgnoreMatch(PRIVATEUSE, s);
    }

    public static boolean isExtensionSingletonChar(char c) {
        return isExtensionSingleton(String.valueOf(c));
    }

    public static boolean isExtensionSubtag(String s) {
        int len = s.length();
        return (len >= 2) && (len <= 8) && LocaleUtils.isAlphaNumericString(s);
    }

    public static boolean isPrivateusePrefix(String s) {
        return (s.length() == 1)
                && LocaleUtils.caseIgnoreMatch(PRIVATEUSE, s);
    }

    public static boolean isPrivateusePrefixChar(char c) {
        return (LocaleUtils.caseIgnoreMatch(PRIVATEUSE, String.valueOf(c)));
    }

    public static boolean isPrivateuseSubtag(String s) {
        int len = s.length();
        return (len >= 1) && (len <= 8) && LocaleUtils.isAlphaNumericString(s);
    }


    public static String canonicalizeLanguage(String s) {
        return LocaleUtils.toLowerString(s);
    }

    public static String canonicalizeExtlang(String s) {
        return LocaleUtils.toLowerString(s);
    }

    public static String canonicalizeScript(String s) {
        return LocaleUtils.toTitleString(s);
    }

    public static String canonicalizeRegion(String s) {
        return LocaleUtils.toUpperString(s);
    }

    public static String canonicalizeVariant(String s) {
        return LocaleUtils.toLowerString(s);
    }

    public static String canonicalizeExtension(String s) {
        return LocaleUtils.toLowerString(s);
    }

    public static String canonicalizeExtensionSingleton(String s) {
        return LocaleUtils.toLowerString(s);
    }

    public static String canonicalizeExtensionSubtag(String s) {
        return LocaleUtils.toLowerString(s);
    }

    public static String canonicalizePrivateuse(String s) {
        return LocaleUtils.toLowerString(s);
    }

    public static String canonicalizePrivateuseSubtag(String s) {
        return LocaleUtils.toLowerString(s);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();

        if (!language.isEmpty()) {
            sb.append(language);

            for (String extlang : extlangs) {
                sb.append(SEP).append(extlang);
            }

            if (!script.isEmpty()) {
                sb.append(SEP).append(script);
            }

            if (!region.isEmpty()) {
                sb.append(SEP).append(region);
            }

            for (String variant : variants) {
                sb.append(SEP).append(variant);
            }

            for (String extension : extensions) {
                sb.append(SEP).append(extension);
            }
        }
        if (!privateuse.isEmpty()) {
            if (sb.length() > 0) {
                sb.append(SEP);
            }
            sb.append(privateuse);
        }

        return sb.toString();
    }
}
