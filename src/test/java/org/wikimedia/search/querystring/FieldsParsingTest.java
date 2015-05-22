package org.wikimedia.search.querystring;

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;
import org.wikimedia.search.querystring.FieldsHelper.UnauthorizedAction;
import org.wikimedia.search.querystring.query.FieldDefinition;

/**
 * Tests that the parser properly parses field specification.
 */
@RunWith(Parameterized.class)
public class FieldsParsingTest {
    @Parameters(name = "{1}")
    public static Collection<Object[]> params() {
        List<Object[]> params = new ArrayList<>();
        for (Object[] param : Arrays.asList(new Object[][] { //
                { "foo", "foo" }, //
                { "a", "b^2", "a, b^2" },
                { "a", "b^2", "a,b^2" },
                })) {
            String[] fields = new String[param.length - 1];
            System.arraycopy(param, 0, fields, 0, param.length - 1);
            params.add(new Object[] {fields, param[param.length - 1]});
        }
        return params;
    }

    public static final Pattern BOOST_PATTERN = Pattern.compile("(.+)\\^([0-9]*\\.?[0-9]+)");

    public static FieldDefinition fieldDefinition(String field, String phrasePrefix) {
        Matcher m = BOOST_PATTERN.matcher(field);
        float boost = 1;
        if (m.matches()) {
            field = m.group(1);
            boost = Float.parseFloat(m.group(2));
        }
        return new FieldDefinition(field, phrasePrefix + field, boost);
    }

    @Parameter(0)
    public String[] fields;
    @Parameter(1)
    public String toParse;

    @Test
    public void parse() {
        FieldsHelper fieldsHelper = new FieldsHelper();
        List<FieldDefinition> definitions = new ArrayList<>();
        for (String field : fields) {
            definitions.add(fieldDefinition(field, ""));
        }
        assertEquals(definitions, QueryParserHelper.parseFields(fieldsHelper, toParse, UnauthorizedAction.KEEP));
    }
}
