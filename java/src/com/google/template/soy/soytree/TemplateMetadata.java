/*
 * Copyright 2018 Google Inc.
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
package com.google.template.soy.soytree;

import static com.google.common.base.Preconditions.checkState;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.base.internal.SanitizedContentKind;
import com.google.template.soy.base.internal.SoyFileKind;
import com.google.template.soy.exprtree.VarRefNode;
import com.google.template.soy.soytree.defn.TemplateParam;
import com.google.template.soy.types.SoyType;
import com.google.template.soy.types.UnknownType;
import java.util.HashSet;
import java.util.Set;
import javax.annotation.Nullable;

/**
 * An abstract representation of a template that provides the minimal amount of information needed
 * compiling against dependency templates.
 *
 * <p>When compiling with dependencies the compiler needs to examine certain information from
 * dependent templates in order to validate calls and escape call sites. Traditionally, the Soy
 * compiler accomplished this by having each compilation parse all transitive dependencies. This is
 * an expensive solution. So instead of that we instead use this object to represent the minimal
 * information we need about dependencies.
 *
 * <p>The APIs on this class mirror ones available on {@link TemplateNode}.
 */
@AutoValue
public abstract class TemplateMetadata {
  // TODO(lukes): add a serialized form of this object

  /** Builds a Template from a parsed TemplateNode. */
  public static TemplateMetadata fromTemplate(TemplateNode template) {
    TemplateMetadata.Builder builder =
        new AutoValue_TemplateMetadata.Builder()
            .setTemplateName(template.getTemplateName())
            .setSourceLocation(template.getSourceLocation())
            .setSoyFileKind(template.getParent().getSoyFileKind())
            .setContentKind(template.getContentKind())
            .setStrictHtml(template.isStrictHtml())
            .setDelPackageName(template.getDelPackageName())
            .setVisibility(template.getVisibility())
            .setParameters(Parameter.directParametersFromTemplate(template))
            .setCallSituations(CallSituation.templateCallSituations(template))
            .setTemplateNodeForTemporaryCompatibility(template)
            .setTemplateNode(
                template.getParent().getSoyFileKind() == SoyFileKind.SRC ? template : null);
    switch (template.getKind()) {
      case TEMPLATE_BASIC_NODE:
        builder.setTemplateKind(Kind.BASIC);
        break;
      case TEMPLATE_DELEGATE_NODE:
        builder.setTemplateKind(Kind.DELTEMPLATE);
        TemplateDelegateNode deltemplate = (TemplateDelegateNode) template;
        builder.setDelTemplateName(deltemplate.getDelTemplateName());
        builder.setDelTemplateVariant(deltemplate.getDelTemplateVariant());
        break;
      case TEMPLATE_ELEMENT_NODE:
        builder.setTemplateKind(Kind.ELEMENT);
        break;
      default:
        throw new AssertionError("unexpected template kind: " + template.getKind());
    }
    return builder.build();
  }

  /** Represents minimal information about a template parameter. */
  @AutoValue
  public abstract static class Parameter {

    static ImmutableList<Parameter> directParametersFromTemplate(TemplateNode node) {
      ImmutableList.Builder<Parameter> params = ImmutableList.builder();
      for (TemplateParam param : node.getAllParams()) {
        params.add(
            builder()
                .setName(param.name())
                .setType(param.type())
                .setInjected(param.isInjected())
                .setRequired(param.isRequired())
                .build());
      }
      Set<String> dollarSignIjParams = new HashSet<>();
      for (VarRefNode varRef : SoyTreeUtils.getAllNodesOfType(node, VarRefNode.class)) {
        // N.B. we don't rely on the defnDecl because if this is a dependency template (which it
        // will be during the migration to use TemplateHeaders), then the ResolveNamesPass will not
        // have run.
        if (varRef.isDollarSignIjParameter() && dollarSignIjParams.add(varRef.getName())) {
          params.add(
              builder()
                  // TODO(lukes): do we need to mark it as $ij.name?  these parameters technically
                  // live in a slightly different namespace
                  .setName(varRef.getName())
                  .setType(UnknownType.getInstance())
                  .setRequired(false) // $ij params are never required to be present
                  .setInjected(true)
                  .build());
        }
      }
      return params.build();
    }

    private static Builder builder() {
      return new AutoValue_TemplateMetadata_Parameter.Builder();
    }

    public abstract String getName();

    // TODO(lukes): this will likely not work once we start compiling templates separately,
    // especially if we want to start pruning the proto descriptors required by the compiler.
    public abstract SoyType getType();

    public abstract boolean isInjected();

    public abstract boolean isRequired();

    @AutoValue.Builder
    abstract static class Builder {
      abstract Builder setName(String name);

      abstract Builder setType(SoyType type);

      abstract Builder setInjected(boolean isInjected);

      abstract Builder setRequired(boolean isRequired);

      abstract Parameter build();
    }
  }

  /**
   * Represents information about a templates called by a given template.
   *
   * <p>This doesn't necessarily represent a single call site since if a template is called multiple
   * times in ways that aren't different according to this data structure we only record it once.
   */
  @AutoValue
  public abstract static class CallSituation {
    static ImmutableList<CallSituation> templateCallSituations(TemplateNode node) {
      ImmutableSet.Builder<CallSituation> calls = ImmutableSet.builder();
      for (CallNode call : SoyTreeUtils.getAllNodesOfType(node, CallNode.class)) {
        CallSituation.Builder builder = builder().setDataAllCall(call.isPassingAllData());
        if (call.isPassingAllData()) {
          ImmutableList.Builder<String> explicitlyPassedParams = ImmutableList.builder();
          for (CallParamNode param : call.getChildren()) {
            explicitlyPassedParams.add(param.getKey().identifier());
          }
          builder.setExplicitlyPassedParametersForDataAllCalls(explicitlyPassedParams.build());
        } else {
          builder.setExplicitlyPassedParametersForDataAllCalls(ImmutableList.of());
        }
        switch (call.getKind()) {
          case CALL_BASIC_NODE:
            builder.setDelCall(false).setTemplateName(((CallBasicNode) call).getCalleeName());
            break;
          case CALL_DELEGATE_NODE:
            builder.setDelCall(true).setTemplateName(((CallDelegateNode) call).getDelCalleeName());
            break;
          default:
            throw new AssertionError("unexpected call kind: " + call.getKind());
        }
        calls.add(builder.build());
      }
      return calls.build().asList();
    }

    /** The fully qualified name of the called template. */
    public abstract String getTemplateName();

    /** Whether this is a delcall or not. */
    public abstract boolean isDelCall();

    /** Whether this is a data="all" call site */
    public abstract boolean isDataAllCall();

    /**
     * Records the names of the parameters that were explicitly passed for data="all" calls.
     *
     * <p>This is necessary to calculate indirect parameters.
     */
    public abstract ImmutableList<String> getExplicitlyPassedParametersForDataAllCalls();

    private static Builder builder() {
      return new AutoValue_TemplateMetadata_CallSituation.Builder();
    }

    @AutoValue.Builder
    abstract static class Builder {
      abstract Builder setTemplateName(String templateName);

      abstract Builder setDelCall(boolean isDelCall);

      abstract Builder setDataAllCall(boolean isDataAllCall);

      abstract Builder setExplicitlyPassedParametersForDataAllCalls(
          ImmutableList<String> parameters);

      abstract CallSituation build();
    }
  }

  /** The kind of template. */
  public enum Kind {
    BASIC,
    DELTEMPLATE,
    ELEMENT;
  }

  public abstract SoyFileKind getSoyFileKind();

  public abstract SourceLocation getSourceLocation();

  public abstract Kind getTemplateKind();

  public abstract String getTemplateName();

  /** Guaranteed to be non-null for deltemplates, null otherwise. */
  @Nullable
  public abstract String getDelTemplateName();

  @Nullable
  public abstract String getDelTemplateVariant();

  @Nullable
  public abstract SanitizedContentKind getContentKind();

  public abstract boolean isStrictHtml();

  public abstract Visibility getVisibility();

  @Nullable
  public abstract String getDelPackageName();

  /**
   * The actual parsed template. Will only be non-null for templates with {@link #getSoyFileKind} of
   * {@link SoyFileKind#SRC}
   */
  @Nullable
  public abstract TemplateNode getTemplateNode();

  /**
   * Same as {@link #getTemplateNode} but is available for non {@link SoyFileKind#SRC} templates.
   * This is provided for temporary compatibility while we fill out this API.
   *
   * <p>TODO(b/63212073): migrate all callers off of this API
   */
  public abstract TemplateNode getTemplateNodeForTemporaryCompatibility();

  /** The Parameters defined directly on the template. Includes {@code $ij} parameters. */
  public abstract ImmutableList<Parameter> getParameters();

  /**
   * The unique template calls that are performed by this template.
   *
   * <p>This is needed to calculate information about transitive parameters.
   */
  public abstract ImmutableList<CallSituation> getCallSituations();

  @AutoValue.Builder
  abstract static class Builder {
    abstract Builder setSoyFileKind(SoyFileKind location);

    abstract Builder setSourceLocation(SourceLocation location);

    abstract Builder setTemplateKind(Kind kind);

    abstract Builder setTemplateName(String templateName);

    abstract Builder setDelTemplateName(String delTemplateName);

    abstract Builder setDelTemplateVariant(String delTemplateVariant);

    abstract Builder setContentKind(@Nullable SanitizedContentKind contentKind);

    abstract Builder setTemplateNode(@Nullable TemplateNode template);

    abstract Builder setTemplateNodeForTemporaryCompatibility(TemplateNode template);

    abstract Builder setStrictHtml(boolean strictHtml);

    abstract Builder setDelPackageName(@Nullable String delPackageName);

    abstract Builder setVisibility(Visibility visibility);

    abstract Builder setParameters(ImmutableList<Parameter> parameters);

    abstract Builder setCallSituations(ImmutableList<CallSituation> callSituations);

    final TemplateMetadata build() {
      TemplateMetadata built = autobuild();
      if (built.getSoyFileKind() == SoyFileKind.SRC) {
        checkState(built.getTemplateNode() != null, "source templates must have a templatenode");
      } else {
        checkState(
            built.getTemplateNode() == null, "non-source templates must not have a templatenode");
      }
      if (built.getTemplateKind() == Kind.DELTEMPLATE) {
        checkState(built.getDelTemplateName() != null, "Deltemplates must have a deltemplateName");
      } else {
        checkState(
            built.getDelTemplateVariant() == null, "non-Deltemplates must not have a variant");
        checkState(
            built.getDelTemplateName() == null, "non-Deltemplates must not have a deltemplateName");
      }
      return built;
    }

    abstract TemplateMetadata autobuild();
  }
}