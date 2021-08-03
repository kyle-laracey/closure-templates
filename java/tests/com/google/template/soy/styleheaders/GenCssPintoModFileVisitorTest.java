/**
 * Copyright 2021 Google Inc.
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 *
 * <p>Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.template.soy.styleheaders;

import static com.google.common.truth.Truth.assertThat;

import com.google.template.soy.SoyFileSetParser.ParseResult;
import com.google.template.soy.base.SourceFilePath;
import com.google.template.soy.base.internal.SoyFileSupplier;
import com.google.template.soy.testing.SoyFileSetParserBuilder;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link GenJsCodeVisitor}. */
@RunWith(JUnit4.class)
public final class GenCssPintoModFileVisitorTest {

  private GenCssPintoModFileVisitor genCssPintoModFileVisitor;

  @Before
  public void setUp() {
    genCssPintoModFileVisitor = new GenCssPintoModFileVisitor();
  }

  @Test
  public void testSoyFileWithRequireCssOnNamespace() {

    String testFileContent =
        ""
            + "{namespace boo.foo\n"
            + "    requirecss=\"\n"
            + "        ddd.eee.fff.ggg,\n"
            + "        aaa.bbb.ccc\"}\n"
            + "\n"
            + "/** Test template. */\n"
            + "{template .goo}\n"
            + "  blah\n"
            + "{/template}\n";

    ParseResult parseResult =
        SoyFileSetParserBuilder.forSuppliers(
                SoyFileSupplier.Factory.create(
                    testFileContent, SourceFilePath.create("my/dir/some_file.soy")))
            .parse();

    String expectedJsFileContents =
        ""
            + "// This file was automatically generated by the Soy compiler.\n"
            + "// Please don't edit this file by hand.\n"
            + "// source: my/dir/some_file.soy\n"
            + "\n"
            + "/**\n"
            + " * @fileoverview Pintomodule for CSS needed by: my/dir/some_file.soy.\n"
            + " * @pintomodule\n"
            + " * @requirecss { ./some_file.soy}\n"
            + " */\n"
            + "goog.module('boo.foo.csspinto');\n"
            + "\n";

    List<String> jsFilesContents = genCssPintoModFileVisitor.gen(parseResult.fileSet());
    assertThat(jsFilesContents.get(0)).isEqualTo(expectedJsFileContents);
  }

  @Test
  public void testEmptySoyFile() {

    String testFileContent = "";

    ParseResult parseResult =
        SoyFileSetParserBuilder.forSuppliers(
                SoyFileSupplier.Factory.create(
                    testFileContent, SourceFilePath.create("my/dir/some_file.soy")))
            .parse();

    String expectedJsFileContents =
        ""
            + "// This file was automatically generated by the Soy compiler.\n"
            + "// Please don't edit this file by hand.\n"
            + "// source: my/dir/some_file.soy\n";

    List<String> jsFilesContents = genCssPintoModFileVisitor.gen(parseResult.fileSet());
    assertThat(jsFilesContents.get(0)).isEqualTo(expectedJsFileContents);
  }
}
