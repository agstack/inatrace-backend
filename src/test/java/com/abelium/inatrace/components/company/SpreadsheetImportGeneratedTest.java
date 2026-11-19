package com.abelium.inatrace.components.company;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import org.springframework.test.context.TestContextManager;

import java.lang.reflect.Method;

/** Runs each jqwik case in its own Spring test transaction and rolls it back. */
class SpreadsheetImportGeneratedTest {
    @Provide
    Arbitrary<String> identifiers() {
        return Arbitraries.strings().withCharRange('a', 'z').ofMinLength(8).ofMaxLength(12);
    }

    @Provide
    Arbitrary<String> exportNames() {
        return Arbitraries.of("=1+1", "Name, \"Café\"", "Line\nBreak", "農家", "+Formula");
    }

    @Property(tries = 5)
    void duplicateIdentifiersAreReportedByTheRealImporter(@ForAll("identifiers") String identifier) throws Exception {
        runFixture("verifyGeneratedDuplicateIdentifier", String.class, identifier);
    }

    @Property(tries = 5)
    void invalidFormulaAndErrorCellsAreRejectedByTheRealImporter(@ForAll boolean formula) throws Exception {
        runFixture("verifyGeneratedInvalidIdentifier", boolean.class, formula);
    }

    @Property(tries = 5)
    void exportedWorkbookPreservesGeneratedNames(@ForAll("exportNames") String name) throws Exception {
        runFixture("verifyGeneratedFarmerExport", String.class, name);
    }

    private void runFixture(String methodName, Class<?> parameterType, Object value) throws Exception {
        var fixture = new FarmerImportDocumentAuthorizationTest();
        var manager = new TestContextManager(FarmerImportDocumentAuthorizationTest.class);
        Method method = FarmerImportDocumentAuthorizationTest.class
                .getDeclaredMethod(methodName, parameterType);
        manager.beforeTestClass();
        manager.prepareTestInstance(fixture);
        manager.beforeTestMethod(fixture, method);
        Throwable failure = null;
        try {
            fixture.seedImportPermissionFixture();
            method.invoke(fixture, value);
        } catch (Throwable thrown) {
            failure = thrown;
            throw thrown;
        } finally {
            try {
                fixture.removeOwnedUploadedFiles();
            } finally {
                try {
                    manager.afterTestMethod(fixture, method, failure);
                } finally {
                    manager.afterTestClass();
                }
            }
        }
    }
}
