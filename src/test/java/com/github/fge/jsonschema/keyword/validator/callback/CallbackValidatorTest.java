/*
 * Copyright (c) 2013, Francis Galiegue <fgaliegue@gmail.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the Lesser GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * Lesser GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.github.fge.jsonschema.keyword.validator.callback;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.fge.jackson.JacksonUtils;
import com.github.fge.jackson.jsonpointer.JsonPointer;
import com.github.fge.jsonschema.core.exceptions.ProcessingException;
import com.github.fge.jsonschema.keyword.validator.KeywordValidator;
import com.github.fge.jsonschema.library.Dictionary;
import com.github.fge.jsonschema.messages.JsonSchemaValidationBundle;
import com.github.fge.jsonschema.processing.Processor;
import com.github.fge.jsonschema.processors.data.FullData;
import com.github.fge.jsonschema.report.LogLevel;
import com.github.fge.jsonschema.report.ProcessingMessage;
import com.github.fge.jsonschema.report.ProcessingReport;
import com.github.fge.jsonschema.tree.CanonicalSchemaTree;
import com.github.fge.jsonschema.tree.JsonTree;
import com.github.fge.jsonschema.tree.SchemaTree;
import com.github.fge.jsonschema.tree.SimpleJsonTree;
import com.github.fge.msgsimple.bundle.MessageBundle;
import com.github.fge.msgsimple.load.MessageBundles;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

import static com.github.fge.jsonschema.TestUtils.*;
import static org.mockito.Mockito.*;
import static org.testng.Assert.*;

public abstract class CallbackValidatorTest
{
    protected static final MessageBundle BUNDLE
        = MessageBundles.getBundle(JsonSchemaValidationBundle.class);
    protected static final JsonNodeFactory FACTORY = JacksonUtils.nodeFactory();
    protected static final ProcessingMessage MSG = new ProcessingMessage();

    protected static final ObjectNode sub1 = FACTORY.objectNode();
    protected static final ObjectNode sub2 = FACTORY.objectNode();

    protected final String keyword;
    private final Constructor<? extends KeywordValidator> constructor;
    protected final JsonPointer ptr1;
    protected final JsonPointer ptr2;

    private Processor<FullData, FullData> processor;
    private FullData data;
    private ProcessingReport report;
    private KeywordValidator validator;

    protected CallbackValidatorTest(
        final Dictionary<Constructor<? extends KeywordValidator>> dict,
        final String keyword, final JsonPointer ptr1, final JsonPointer ptr2)
    {
        this.keyword = keyword;
        constructor = dict.entries().get(keyword);
        this.ptr1 = ptr1;
        this.ptr2 = ptr2;
    }

    @BeforeMethod
    protected final void initEnvironment()
        throws IllegalAccessException, InvocationTargetException,
        InstantiationException
    {
        if (constructor == null)
            return;

        final SchemaTree tree = new CanonicalSchemaTree(generateSchema());
        final JsonTree instance = new SimpleJsonTree(generateInstance());
        data = new FullData(tree, instance);
        report = mock(ProcessingReport.class);
        when(report.getLogLevel()).thenReturn(LogLevel.DEBUG);
        validator = constructor.newInstance(generateDigest());
    }

    @Test
    public final void keywordExists()
    {
        assertNotNull(constructor, "no support for " + keyword + "??");
    }

    @Test(dependsOnMethods = "keywordExists")
    public final void exceptionOnFirstProcessingWorks()
        throws ProcessingException
    {
        processor = spy(new DummyProcessor(WantedState.EX, WantedState.OK, ptr1,
            ptr2));

        try {
            validator.validate(processor, report, BUNDLE, data);
            fail("No exception thrown!!");
        } catch (ProcessingException ignored) {
        }

        verify(processor, onlyOnce()).process(anyReport(), any(FullData.class));
    }

    @Test(dependsOnMethods = "keywordExists")
    public final void exceptionOnSecondProcessingWorks()
        throws ProcessingException
    {
        processor = spy(new DummyProcessor(WantedState.OK, WantedState.EX, ptr1,
            ptr2));

        try {
            validator.validate(processor, report, BUNDLE, data);
            fail("No exception thrown!!");
        } catch (ProcessingException ignored) {
        }

        verify(processor, times(2)).process(anyReport(), any(FullData.class));
    }

    @Test(dependsOnMethods = "keywordExists")
    public final void OkThenOkWorks()
        throws ProcessingException
    {
        processor = spy(new DummyProcessor(WantedState.OK, WantedState.OK, ptr1,
            ptr2));

        validator.validate(processor, report, BUNDLE, data);
        verify(processor, times(2)).process(anyReport(), any(FullData.class));

        checkOkOk(report);
    }

    protected abstract void checkOkOk(final ProcessingReport report)
        throws ProcessingException;

    @Test(dependsOnMethods = "keywordExists")
    public final void OkThenKoWorks()
        throws ProcessingException
    {
        processor = spy(new DummyProcessor(WantedState.OK, WantedState.KO, ptr1,
            ptr2));

        validator.validate(processor, report, BUNDLE, data);
        verify(processor, times(2)).process(anyReport(), any(FullData.class));

        checkOkKo(report);
    }

    protected abstract void checkOkKo(final ProcessingReport report)
        throws ProcessingException;

    @Test(dependsOnMethods = "keywordExists")
    public final void KoThenKoWorks()
        throws ProcessingException
    {
        processor = spy(new DummyProcessor(WantedState.KO, WantedState.KO, ptr1,
            ptr2));

        validator.validate(processor, report, BUNDLE, data);
        verify(processor, times(2)).process(anyReport(), any(FullData.class));

        checkKoKo(report);
    }

    protected abstract void checkKoKo(final ProcessingReport report)
        throws ProcessingException;

    protected abstract JsonNode generateSchema();

    protected abstract JsonNode generateInstance();

    protected abstract JsonNode generateDigest();

    private enum WantedState {
        OK
        {
            @Override
            void doIt(final ProcessingReport report)
                throws ProcessingException
            {
            }
        },
        KO
        {
            @Override
            void doIt(final ProcessingReport report)
                throws ProcessingException
            {
                report.error(MSG);
            }
        },
        EX
        {
            @Override
            void doIt(final ProcessingReport report)
                throws ProcessingException
            {
                throw new ProcessingException();
            }
        };

        abstract void doIt(final ProcessingReport report)
            throws ProcessingException;
    }

    private static class DummyProcessor
        implements Processor<FullData, FullData>
    {
        private final WantedState wanted1;
        private final WantedState wanted2;
        private final JsonPointer ptr1;
        private final JsonPointer ptr2;

        private DummyProcessor(final WantedState wanted1,
            final WantedState wanted2, final JsonPointer ptr1,
            final JsonPointer ptr2)
        {
            this.wanted1 = wanted1;
            this.wanted2 = wanted2;
            this.ptr1 = ptr1;
            this.ptr2 = ptr2;
        }

        @Override
        public FullData process(final ProcessingReport report,
            final FullData input)
            throws ProcessingException
        {
            final JsonNode schema = input.getSchema().getNode();

            final JsonPointer ptr = schema == sub1 ? ptr1 : ptr2;
            assertEquals(input.getSchema().getPointer(), ptr,
                "schema pointer differs from expectations");

            final WantedState wanted = schema == sub1 ? wanted1 : wanted2;
            wanted.doIt(report);
            return input;
        }
    }
}
