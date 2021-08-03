/*
 * Copyright 2008 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.template.soy;

import com.google.common.collect.ImmutableList;
import java.io.IOException;

/**
 * Executable for compiling a set of Soy files into tiny JS @pintomodule header files to create CSS
 * modules for each Soy file. We generate one JS file per Soy file, and each @require-s the
 * corresponding Sass gen file for that Soy file.
 *
 * <p>Note that soy_server_css_modules will decide which libraries to actually create these header
 * files for (by depending on the js providers), so we are only creating one pinto module for every
 * "root" Soy file.
 */
public final class CssPintoModFileGenerator extends AbstractSoyCompiler {

  private final PerInputOutputFiles outputFiles =
      new PerInputOutputFiles("soy.css_pinto_mod.js", PerInputOutputFiles.JS_JOINER);

  CssPintoModFileGenerator(PluginLoader loader, SoyInputCache cache) {
    super(loader, cache);
  }

  CssPintoModFileGenerator() {}

  /**
   * Compiles a set of Soy files into corresponding JS source files.
   *
   * @param args Should contain command-line flags and the list of paths to the Soy files.
   * @throws IOException If there are problems reading the input files or writing the output file.
   */
  public static void main(final String[] args) throws IOException {
    new CssPintoModFileGenerator().runMain(args);
  }

  @Override
  protected void validateFlags() {
    outputFiles.validateFlags();
  }

  @Override
  Iterable<?> extraFlagsObjects() {
    return ImmutableList.of(outputFiles);
  }

  @Override
  protected void compile(SoyFileSet.Builder sfsBuilder) throws IOException {
    SoyFileSet sfs = sfsBuilder.build();
    outputFiles.writeFiles(srcs, sfs.generateCssPintoModFiles(), /*locale=*/ null);
  }
}
