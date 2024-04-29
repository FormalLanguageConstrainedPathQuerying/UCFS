/*
 * Copyright (c) 2001, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4290801 4942982 5102005 8008577 8021121 8210153 8227313 8301991
 *      8174269
 * @summary Basic tests for currency formatting.
 *          Tests both COMPAT and CLDR data.
 * @modules jdk.localedata
 * @run junit/othervm -Djava.locale.providers=CLDR CurrencyFormat
 */

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Currency;
import java.util.Locale;
import java.util.Properties;
import java.util.StringTokenizer;
import java.util.TimeZone;
import java.text.DecimalFormatSymbols;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class CurrencyFormat {

    @ParameterizedTest
    @MethodSource("currencyFormatDataProvider")
    public void currencyFormatTest(String expected, Currency currency,
                                   NumberFormat format, Locale locale) {
        if (currency != null) {
            format.setCurrency(currency);
            int digits = currency.getDefaultFractionDigits();
            format.setMinimumFractionDigits(digits);
            format.setMaximumFractionDigits(digits);
        }
        String result = format.format(1234.56);
        assertEquals(expected, result, String.format("Failed with locale: %s%s",
                locale, (currency == null ? ", default currency" : (", currency: " + currency))));
    }

    private static Stream<Arguments> currencyFormatDataProvider() {
        ArrayList<Arguments> data = new ArrayList<Arguments>();
        Locale[] locales = {
                Locale.US,
                Locale.JAPAN,
                Locale.GERMANY,
                Locale.ITALY,
                Locale.of("it", "IT", "EURO"),
                Locale.forLanguageTag("de-AT"),
                Locale.forLanguageTag("fr-CH"),
        };
        Currency[] currencies = {
                null,
                Currency.getInstance("USD"),
                Currency.getInstance("JPY"),
                Currency.getInstance("DEM"),
                Currency.getInstance("EUR"),
        };
        String[][] expectedCLDRData = {
                {"$1,234.56", "$1,234.56", "\u00a51,235", "DEM1,234.56", "\u20ac1,234.56"},
                {"\uFFE51,235", "$1,234.56", "\uFFE51,235", "DEM1,234.56", "\u20ac1,234.56"},
                {"1.234,56\u00a0\u20ac", "1.234,56\u00a0$", "1.235\u00a0\u00a5", "1.234,56\u00a0DM", "1.234,56\u00a0\u20ac"},
                {"1.234,56\u00a0\u20ac", "1.234,56\u00a0USD", "1.235\u00a0JPY", "1.234,56\u00a0DEM", "1.234,56\u00a0\u20ac"},
                {"1.234,56\u00a0\u20ac", "1.234,56\u00a0USD", "1.235\u00a0JPY", "1.234,56\u00a0DEM", "1.234,56\u00a0\u20ac"},
                {"\u20ac\u00a01.234,56", "$\u00a01.234,56", "\u00a5\u00a01.235", "DM\u00a01.234,56", "\u20ac\u00a01.234,56"},
                {"1\u202f234.56\u00a0CHF", "1\u202f234.56\u00a0$US", "1\u202f235\u00a0JPY", "1\u202f234.56\u00a0DEM", "1\u202f234.56\u00a0\u20ac"},
        };
        for (int i = 0; i < locales.length; i++) {
            Locale locale = locales[i];
            NumberFormat format = NumberFormat.getCurrencyInstance(locale);
            for (int j = 0; j < currencies.length; j++) {
                Currency currency = currencies[j];
                String expected = expectedCLDRData[i][j];
                data.add(Arguments.of(expected, currency, format, locale));
            }
        }
        return data.stream();
    }
}
