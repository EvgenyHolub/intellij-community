/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.java.psi.codeStyle;

import com.intellij.application.options.codeStyle.properties.AbstractCodeStylePropertyMapper;
import com.intellij.application.options.codeStyle.properties.CodeStylePropertyAccessor;
import com.intellij.ide.codeStyleSettings.CodeStyleTestCase;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.openapi.options.SchemeImportException;
import com.intellij.psi.codeStyle.*;
import com.intellij.psi.impl.source.codeStyle.json.CodeStyleSchemeJsonExporter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@SuppressWarnings("SameParameterValue")
public class JavaCodeStyleSettingsTest extends CodeStyleTestCase {

  public void testSettingsClone() {
    JavaCodeStyleSettings original = JavaCodeStyleSettings.getInstance(getProject());
    original.getImportLayoutTable().addEntry(new PackageEntry(false, "test", true));
    List<String> annotations = Arrays.asList("anno1", "anno2");
    original.setRepeatAnnotations(annotations);
    original.getPackagesToUseImportOnDemand().addEntry(new PackageEntry(false, "test2", true));
    original.FIELD_TYPE_TO_NAME.addPair("foo", "bar");
    original.STATIC_FIELD_TYPE_TO_NAME.addPair("one", "two");

    JavaCodeStyleSettings copy = (JavaCodeStyleSettings)original.clone();
    assertEquals(annotations, copy.getRepeatAnnotations());
    assertEquals("Import tables do not match", original.getImportLayoutTable(), copy.getImportLayoutTable());
    assertEquals("On demand packages do not match", original.getPackagesToUseImportOnDemand(), copy.getPackagesToUseImportOnDemand());
    assertEquals("Field type-to-name maps do not match", original.FIELD_TYPE_TO_NAME, copy.FIELD_TYPE_TO_NAME);
    assertEquals("Static field type-to-name maps do not match", original.STATIC_FIELD_TYPE_TO_NAME, copy.STATIC_FIELD_TYPE_TO_NAME);

    copy.setRepeatAnnotations(Collections.singletonList("anno1"));
    assertNotSame("Changed repeated annotations should reflect the equality relation", original, copy);
  }

  public void testSettingsCloneNotReferencingOriginal() throws IllegalAccessException {
    JavaCodeStyleSettings original = JavaCodeStyleSettings.getInstance(getProject());
    JavaCodeStyleSettings copy = (JavaCodeStyleSettings)original.clone();
    for (Field field : copy.getClass().getDeclaredFields()) {
      if (!isPrimitiveOrString(field.getType()) && (field.getModifiers() & Modifier.PUBLIC) != 0) {
        assertNotSame("Fields '" + field.getName() + "' reference the same value", field.get(original), field.get(copy));
      }
    }
  }

  public void testImportPre173Settings() throws SchemeImportException {
    CodeStyleSettings imported = importSettings();
    CommonCodeStyleSettings commonSettings = imported.getCommonSettings(JavaLanguage.INSTANCE);
    assertEquals("testprefix", imported.getCustomSettings(JavaCodeStyleSettings.class).FIELD_NAME_PREFIX);
    assertTrue(commonSettings.WRAP_COMMENTS);
    //noinspection deprecation
    assertFalse(imported.WRAP_COMMENTS);
  }

  public void testJsonExport() throws IOException {
    CodeStyleScheme testScheme = createTestScheme();
    final CodeStyleSettings settings = testScheme.getCodeStyleSettings();
    final CommonCodeStyleSettings commonJavaSettings = settings.getCommonSettings(JavaLanguage.INSTANCE);
    commonJavaSettings.METHOD_PARAMETERS_WRAP = CommonCodeStyleSettings.WRAP_AS_NEEDED;
    commonJavaSettings.CALL_PARAMETERS_WRAP = CommonCodeStyleSettings.WRAP_ON_EVERY_ITEM;
    final JavaCodeStyleSettings javaSettings = settings.getCustomSettings(JavaCodeStyleSettings.class);
    javaSettings.FIELD_NAME_PREFIX = "m_";
    javaSettings.STATIC_FIELD_NAME_SUFFIX = "_s";

    CodeStyleSchemeJsonExporter exporter = new CodeStyleSchemeJsonExporter();
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    exporter.exportScheme(testScheme, outputStream, Collections.singletonList("java"));
    String expected = loadExpected(j2eeProviderExists() ? "j2ee.json" : "json");
    assertEquals(expected, outputStream.toString());
  }

  private static boolean j2eeProviderExists() {
    List<CodeStyleSettingsProvider> providers = CodeStyleSettingsProvider.EXTENSION_POINT_NAME.getExtensionList();
    for (CodeStyleSettingsProvider provider : providers) {
      if (provider.getClass().getName().equals("com.intellij.javaee.JavaeeCodeStyleSettingsProvider")) {
        return true;
      }
    }
    return false;
  }

  public void testSetProperties() {
    final CodeStyleSettings settings = getCurrentCodeStyleSettings();
    AbstractCodeStylePropertyMapper mapper =
      LanguageCodeStyleSettingsProvider.forLanguage(JavaLanguage.INSTANCE).getPropertyMapper(settings);
    setSimple(mapper, "align_group_field_declarations", "true");
    setSimple(mapper, "blank_lines_after_class_header", "1");
    setSimple(mapper, "brace_style", "next_line");
    setSimple(mapper, "indent_size", "2");
    setSimple(mapper, "doc_align_param_comments", "true");
    setList(mapper, "imports_layout",
            Arrays.asList("com.jetbrains.*", "blank_line", "org.eclipse.bar", "static  **", "static org.eclipse.foo.**"));
    final CommonCodeStyleSettings commonJavaSettings = settings.getCommonSettings(JavaLanguage.INSTANCE);
    final JavaCodeStyleSettings javaSettings = settings.getCustomSettings(JavaCodeStyleSettings.class);
    assertTrue(commonJavaSettings.ALIGN_GROUP_FIELD_DECLARATIONS);
    assertEquals(1, commonJavaSettings.BLANK_LINES_AFTER_CLASS_HEADER);
    assertEquals(CommonCodeStyleSettings.NEXT_LINE, commonJavaSettings.BRACE_STYLE);
    assertEquals(2, commonJavaSettings.getIndentOptions().INDENT_SIZE);
    assertTrue(javaSettings.JD_ALIGN_PARAM_COMMENTS);
    PackageEntryTable importsTable = javaSettings.getImportLayoutTable();
    assertEquals(new PackageEntry(false, "com.jetbrains", false), importsTable.getEntryAt(0));
    assertEquals(PackageEntry.BLANK_LINE_ENTRY, importsTable.getEntryAt(1));
    assertEquals(new PackageEntry(false, "org.eclipse.bar", false), importsTable.getEntryAt(2));
    assertEquals(PackageEntry.ALL_OTHER_STATIC_IMPORTS_ENTRY, importsTable.getEntryAt(3));
    assertEquals(new PackageEntry(true, "org.eclipse.foo", true), importsTable.getEntryAt(4));
  }
  
  private static void setSimple(@NotNull AbstractCodeStylePropertyMapper mapper, @NotNull String name, @NotNull String value) {
    CodeStylePropertyAccessor accessor = mapper.getAccessor(name);
    assertNotNull(name + " not found", accessor);
    //noinspection unchecked
    accessor.set(value);
  }

  private static void setList(@NotNull AbstractCodeStylePropertyMapper mapper, @NotNull String name, @NotNull List<String> value) {
    CodeStylePropertyAccessor accessor = mapper.getAccessor(name);
    assertNotNull(name + " not found", accessor);
    //noinspection unchecked
    accessor.set(value);
  }

  private static boolean isPrimitiveOrString(Class type) {
    return type.isPrimitive() || type.equals(String.class);
  }

  @Override
  protected String getBasePath() {
    return PathManagerEx.getTestDataPath() + "/codeStyle";
  }

  @Nullable
  @Override
  protected String getTestDir() {
    return "";
  }
}
