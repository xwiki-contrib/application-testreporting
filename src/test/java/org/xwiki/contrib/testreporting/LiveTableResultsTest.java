/*
 * See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.xwiki.contrib.testreporting;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.stream.Stream;

import org.apache.commons.lang3.StringUtils;
import org.apache.velocity.runtime.RuntimeConstants;
import org.apache.velocity.runtime.resource.loader.ClasspathResourceLoader;
import org.apache.velocity.tools.generic.MathTool;
import org.apache.velocity.tools.generic.NumberTool;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.script.ModelScriptService;
import org.xwiki.query.internal.ScriptQuery;
import org.xwiki.query.script.QueryManagerScriptService;
import org.xwiki.rendering.syntax.Syntax;
import org.xwiki.script.service.ScriptService;
import org.xwiki.template.TemplateManager;
import org.xwiki.test.page.PageTest;
import org.xwiki.test.page.XWikiSyntax20ComponentList;
import org.xwiki.test.LogLevel;
import org.xwiki.test.junit5.LogCaptureExtension;
import org.xwiki.test.page.XWikiSyntax21ComponentList;
import org.xwiki.velocity.VelocityConfiguration;
import org.xwiki.velocity.tools.EscapeTool;
import org.xwiki.velocity.tools.JSONTool;
import org.xwiki.velocity.tools.RegexTool;

import com.xpn.xwiki.XWikiContext;
import com.xpn.xwiki.plugin.tag.TagPluginApi;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the {@code QA.LiveTableResults} and {@code QA.LiveTableResultsFullDetailed} pages.
 *
 * @version $Id$
 */
@XWikiSyntax20ComponentList
@XWikiSyntax21ComponentList
class LiveTableResultsTest extends PageTest
{
    @RegisterExtension
    LogCaptureExtension logCapture = new LogCaptureExtension(LogLevel.WARN);

    private QueryManagerScriptService queryService;

    @BeforeEach
    @SuppressWarnings("deprecation")
    void setUp() throws Exception
    {
        // The LiveTableResultsMacros page uses Velocity macros from the macros.vm template. We need to overwrite the
        // Velocity configuration in order to use the ClasspathResourceLoader for loading the macros.vm template from the
        // class path.
        VelocityConfiguration velocityConfiguration = this.oldcore.getMocker().getInstance(VelocityConfiguration.class);
        Properties velocityConfigProps = velocityConfiguration.getProperties();
        velocityConfigProps.put(RuntimeConstants.RESOURCE_LOADERS, "class");
        velocityConfigProps.put(RuntimeConstants.RESOURCE_LOADER + ".class." + RuntimeConstants.RESOURCE_LOADER_CLASS,
            ClasspathResourceLoader.class.getName());
        velocityConfigProps.put(RuntimeConstants.VM_LIBRARY, "/templates/macros.vm");
        velocityConfiguration = this.oldcore.getMocker().registerMockComponent(VelocityConfiguration.class);
        when(velocityConfiguration.getProperties()).thenReturn(velocityConfigProps);

        // The LiveTableResultsMacros page includes the hierarchy_macros.vm template.
        this.oldcore.getMocker().registerMockComponent(TemplateManager.class);

        // The LiveTable results pages expect that the HTTP query is done with the "get" action and asking for plain
        // output.
        setOutputSyntax(Syntax.PLAIN_1_0);
        this.request.put("outputSyntax", "plain");
        this.request.put("xpage", "plain");
        this.oldcore.getXWikiContext().setAction("get");

        // Mock the query service in order to capture the statements and return no results.
        ScriptQuery query = mock(ScriptQuery.class, RETURNS_SELF);
        when(query.execute()).thenReturn(new ArrayList<>());
        when(query.count()).thenReturn(0L);
        this.queryService = mock(QueryManagerScriptService.class);
        when(this.queryService.hql(any())).thenReturn(query);
        when(this.queryService.xwql(any())).thenReturn(query);
        this.oldcore.getMocker().registerComponent(ScriptService.class, "query", this.queryService);
        this.oldcore.getMocker().registerComponent(ScriptService.class, "model", mock(ModelScriptService.class));

        // The LiveTableResultsMacros page uses the tag plugin for the LiveTable tag cloud feature.
        TagPluginApi tagPluginApi = mock(TagPluginApi.class);
        doReturn(tagPluginApi).when(this.oldcore.getSpyXWiki()).getPluginApi(eq("tag"), any(XWikiContext.class));

        registerVelocityTool("stringtool", new StringUtils());
        registerVelocityTool("mathtool", new MathTool());
        registerVelocityTool("regextool", new RegexTool());
        registerVelocityTool("numbertool", new NumberTool());
        registerVelocityTool("escapetool", new EscapeTool());
        registerVelocityTool("jsontool", new JSONTool());

        loadPage(new DocumentReference("xwiki", "XWiki", "LiveTableResultsMacros"));
    }

    static Stream<Arguments> versionOrders()
    {
        String fullDetailedWarning = "Deprecated usage of method [org.apache.velocity.tools.generic.MathTool.toInteger]"
            + " in xwiki:QA.LiveTableResultsFullDetailed@53,26";
        return Stream.of(
            arguments("LiveTableResults", "product", "asc", emptyList()),
            arguments("LiveTableResults", "java", "desc", emptyList()),
            arguments("LiveTableResults", "servletContainer", "asc", emptyList()),
            arguments("LiveTableResultsFullDetailed", "product", "asc", singletonList(fullDetailedWarning)),
            arguments("LiveTableResultsFullDetailed", "product", "desc", singletonList(fullDetailedWarning)));
    }

    /**
     * @see "TESTREPORT-57: Hibernate HHH000174 warnings flood the logs when sorting test LiveTables by an environment
     *      column"
     */
    @ParameterizedTest
    @MethodSource("versionOrders")
    void versionOrderMatchesHibernateFunctionTemplates(String page, String sortColumn, String direction,
        List<String> expectedWarnings) throws Exception
    {
        this.request.put("testSpace", "Space");
        this.request.put("test", "Space.Test");
        this.request.put("collist", "doc.title," + sortColumn);
        this.request.put("sort", sortColumn);
        this.request.put("dir", direction);

        renderPage(new DocumentReference("xwiki", "QA", page));

        // QA.LiveTableResults uses HQL and QA.LiveTableResultsFullDetailed uses XWQL.
        ArgumentCaptor<String> statementCaptor = ArgumentCaptor.forClass(String.class);
        verify(this.queryService, atLeast(0)).hql(statementCaptor.capture());
        verify(this.queryService, atLeast(0)).xwql(statementCaptor.capture());
        assertVersionOrder(statementCaptor.getAllValues());

        for (int i = 0; i < expectedWarnings.size(); i++) {
            assertEquals(expectedWarnings.get(i), this.logCapture.getMessage(i));
        }
    }

    /**
     * Hibernate logs a HHH000174 warning when the arguments of a function call don't match the template of the
     * dialect, i.e. {@code locate(?1, ?2, ?3)} and {@code trim(?1 ?2 ?3 ?4)}.
     */
    private void assertVersionOrder(List<String> statements)
    {
        String statement = statements.stream().filter(s -> s.contains("locate(")).findFirst().orElse(null);
        assertTrue(statement != null, "No statement orders by version: " + statements);

        List<String> locateCalls = getFunctionCallArguments(statement, "locate");
        assertFalse(locateCalls.isEmpty());
        for (String arguments : locateCalls) {
            assertEquals(3, splitArguments(arguments).size(), "Unexpected locate arguments: " + arguments);
        }

        List<String> trimCalls = getFunctionCallArguments(statement, "trim");
        assertFalse(trimCalls.isEmpty());
        for (String arguments : trimCalls) {
            assertTrue(arguments.startsWith("both ' ' from "), "Unexpected trim arguments: " + arguments);
        }
    }

    /**
     * @return the arguments, as a single string, of each call of the given function in the given statement
     */
    private List<String> getFunctionCallArguments(String statement, String function)
    {
        List<String> calls = new ArrayList<>();
        int index = statement.indexOf(function + '(');
        while (index >= 0) {
            boolean isFunctionName = index == 0 || !Character.isJavaIdentifierPart(statement.charAt(index - 1));
            int start = index + function.length() + 1;
            if (isFunctionName) {
                calls.add(statement.substring(start, getClosingParenthesis(statement, start)));
            }
            index = statement.indexOf(function + '(', start);
        }
        return calls;
    }

    private int getClosingParenthesis(String statement, int start)
    {
        int depth = 0;
        boolean inString = false;
        for (int i = start; i < statement.length(); i++) {
            char c = statement.charAt(i);
            if (c == '\'') {
                inString = !inString;
            } else if (!inString && c == '(') {
                depth++;
            } else if (!inString && c == ')') {
                if (depth == 0) {
                    return i;
                }
                depth--;
            }
        }
        throw new IllegalArgumentException("Unbalanced parentheses in: " + statement);
    }

    /**
     * @return the top level arguments of a function call
     */
    private List<String> splitArguments(String arguments)
    {
        List<String> result = new ArrayList<>();
        int depth = 0;
        boolean inString = false;
        int start = 0;
        for (int i = 0; i < arguments.length(); i++) {
            char c = arguments.charAt(i);
            if (c == '\'') {
                inString = !inString;
            } else if (!inString && c == '(') {
                depth++;
            } else if (!inString && c == ')') {
                depth--;
            } else if (!inString && depth == 0 && c == ',') {
                result.add(arguments.substring(start, i).trim());
                start = i + 1;
            }
        }
        result.add(arguments.substring(start).trim());
        return result;
    }
}
