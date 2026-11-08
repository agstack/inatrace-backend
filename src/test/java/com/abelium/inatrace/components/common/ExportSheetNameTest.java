package com.abelium.inatrace.components.common;

import com.abelium.inatrace.configuration.CustomLanguageConfiguration;
import com.abelium.inatrace.tools.TranslateTools;
import com.abelium.inatrace.types.Language;
import org.apache.poi.ss.util.WorkbookUtil;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.ResourceBundleMessageSource;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every translated worksheet name must be one Excel will accept.
 *
 * <p>This runs POI's own validator rather than a copy of its rules, over every
 * {@code *.sheet.name} key in the English bundle resolved in every {@link Language}, so a new key
 * or a new language is covered without touching this test.
 */
class ExportSheetNameTest {

	private final ResourceBundleMessageSource messageSource = new CustomLanguageConfiguration().messageSource();

	@Test
	void everyTranslatedSheetNameIsAcceptedByExcel() throws Exception {
		List<String> keys = sheetNameKeys();
		assertFalse(keys.isEmpty(), "No *.sheet.name keys found -- has the naming changed?");

		List<String> rejected = new ArrayList<>();
		for (String key : keys) {
			for (Language language : Language.values()) {
				String name = TranslateTools.getTranslatedValue(messageSource, key, language);
				assertNotNull(name, key + " has no value in " + language);
				assertFalse(key.equals(name), key + " is missing from the " + language + " bundle");
				try {
					WorkbookUtil.validateSheetName(name);
				} catch (RuntimeException e) {
					rejected.add(language + " " + key + " = \"" + name + "\" -- " + e.getMessage()
							+ " (a valid alternative: \"" + WorkbookUtil.createSafeSheetName(name) + "\")");
				}
			}
		}

		assertTrue(rejected.isEmpty(), "Excel will not accept these worksheet names:\n  "
				+ String.join("\n  ", rejected));
	}

	private List<String> sheetNameKeys() throws IOException {
		Properties english = new Properties();
		try (InputStream input = getClass().getResourceAsStream("/i18n/messages_en.properties")) {
			assertNotNull(input, "The English bundle is required as the reference");
			try (Reader reader = new InputStreamReader(input, StandardCharsets.UTF_8)) {
				english.load(reader);
			}
		}
		List<String> keys = new ArrayList<>();
		for (String key : new TreeSet<>(english.stringPropertyNames())) {
			if (key.contains("sheet.name")) {
				keys.add(key);
			}
		}
		return keys;
	}

	@Test
	void poiRejectsTheNamesThisTestIsAbout() {
		assertEquals("Landwirt innen", WorkbookUtil.createSafeSheetName("Landwirt*innen"));
		assertEquals("empty", WorkbookUtil.createSafeSheetName(""));
		for (String invalid : List.of("a*b", "a:b", "a/b", "a\\b", "a?b", "a[b", "a]b", "", "a".repeat(32))) {
			assertTrue(throwsFor(invalid), "POI should reject \"" + invalid + "\" as a sheet name");
		}
		for (String valid : List.of("Farmers", "Landwirtinnen", "Grundstücke", "Ibyazanywe|Ibyatanzwe")) {
			assertFalse(throwsFor(valid), "POI should accept \"" + valid + "\" as a sheet name");
		}
	}

	private boolean throwsFor(String name) {
		try {
			WorkbookUtil.validateSheetName(name);
			return false;
		} catch (RuntimeException e) {
			return true;
		}
	}
}
