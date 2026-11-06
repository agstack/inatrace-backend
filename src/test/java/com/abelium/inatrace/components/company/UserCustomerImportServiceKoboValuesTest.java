package com.abelium.inatrace.components.company;

import com.abelium.inatrace.types.Gender;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * The value conversions a KoboToolbox export needs: forms are answered in French, English or
 * Spanish, and with "labels" rather than "values" the export carries the wording the enumerator
 * saw, not a code.
 */
class UserCustomerImportServiceKoboValuesTest {

    // ------------------------------------------------------------------ gender

    @ParameterizedTest
    @ValueSource(strings = {"m", "M", "male", "Male", "masculin", "Masculin", "homme", "hombre", "masculino", " M "})
    void everyMaleWording_isMale(String value) {
        assertEquals(Gender.MALE, UserCustomerImportService.toGender(value));
    }

    @ParameterizedTest
    @ValueSource(strings = {"f", "F", "female", "feminin", "féminin", "Féminin", "femme", "mujer", "femenino", "FEMME"})
    void everyFemaleWording_isFemale(String value) {
        assertEquals(Gender.FEMALE, UserCustomerImportService.toGender(value));
    }

    @ParameterizedTest
    @ValueSource(strings = {"n/a", "N/A", "na", "NA"})
    void notApplicableWordings_areNA(String value) {
        assertEquals(Gender.N_A, UserCustomerImportService.toGender(value));
    }

    @ParameterizedTest
    @ValueSource(strings = {"diverse", "DIVERSE", "divers", "diverso"})
    void diverseWordings_areDiverse(String value) {
        assertEquals(Gender.DIVERSE, UserCustomerImportService.toGender(value));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "  ", "x", "man", "woman", "fem", "other"})
    void unknownGenderWording_isNull(String value) {
        assertNull(UserCustomerImportService.toGender(value));
    }

    @org.junit.jupiter.api.Test
    void nullGender_isNull() {
        assertNull(UserCustomerImportService.toGender(null));
    }

    // ------------------------------------------------------------------ yes / no

    @ParameterizedTest
    @ValueSource(strings = {"y", "Y", "yes", "YES", "oui", "Oui", "si", "sí", "Sí", "true", "TRUE", "1", " oui "})
    void everyYesWording_isTrue(String value) {
        assertEquals(Boolean.TRUE, UserCustomerImportService.toBoolean(value));
    }

    @ParameterizedTest
    @ValueSource(strings = {"n", "N", "no", "NO", "non", "Non", "false", "FALSE", "0"})
    void everyNoWording_isFalse(String value) {
        assertEquals(Boolean.FALSE, UserCustomerImportService.toBoolean(value));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "  ", "maybe", "2", "ja", "nein", "x"})
    void unknownYesNoWording_isNull(String value) {
        assertNull(UserCustomerImportService.toBoolean(value));
    }

    @org.junit.jupiter.api.Test
    void nullYesNo_isNull() {
        assertNull(UserCustomerImportService.toBoolean(null));
    }
}
