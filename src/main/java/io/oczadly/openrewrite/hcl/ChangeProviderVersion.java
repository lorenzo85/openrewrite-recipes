package io.oczadly.openrewrite.hcl;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.oczadly.openrewrite.hcl.utils.ModuleBlockPredicates;
import io.oczadly.openrewrite.hcl.utils.VersionConstraintMatcher;
import lombok.EqualsAndHashCode;
import lombok.Value;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.openrewrite.Cursor;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Option;
import org.openrewrite.Recipe;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.Validated;
import org.openrewrite.hcl.HclVisitor;
import org.openrewrite.hcl.format.SpacesVisitor;
import org.openrewrite.hcl.style.SpacesStyle;
import org.openrewrite.hcl.tree.Expression;
import org.openrewrite.hcl.tree.Hcl;
import org.openrewrite.hcl.tree.Space;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.marker.Markers;

import java.util.Collections;

@Value
@EqualsAndHashCode(callSuper = false)
public class ChangeProviderVersion extends Recipe {

    private static final String INVALID_PROVIDER_VERSION_MESSAGE = "'providerVersion' must be a valid semantic version constraint.";

    @Option(displayName = "Provider name", description = "Optional provider local name filter, i.e. the key in required_providers (for example 'aws')", required = false)
    @Nullable
    String providerName;

    @Option(displayName = "Provider source", description = "The provider source address to match exactly (for example 'hashicorp/aws')")
    @Nullable
    String providerSource;

    @Option(displayName = "Provider version", description = "Optional semantic version constraint filter for the provider's current version constraint", required = false)
    @Nullable
    String providerVersion;

    @Option(displayName = "New version", description = "The new version constraint to set for the provider (for example '~> 6.0')")
    @Nullable
    String newVersion;

    @Option(displayName = "File pattern", description = "A glob pattern to match files to apply this recipe to", required = false, example = "**/*.tf")
    @Nullable
    String filePattern;

    @JsonCreator
    public ChangeProviderVersion(@JsonProperty("providerName") @Nullable String providerName,
                                 @JsonProperty("providerSource") @Nullable String providerSource,
                                 @JsonProperty("providerVersion") @Nullable String providerVersion,
                                 @JsonProperty("newVersion") @Nullable String newVersion,
                                 @JsonProperty("filePattern") @Nullable String filePattern) {
        this.providerName = providerName;
        this.providerSource = providerSource;
        this.providerVersion = providerVersion;
        this.newVersion = newVersion;
        this.filePattern = filePattern;
    }

    @NullMarked
    @Override
    public String getDisplayName() {
        return "Change provider version";
    }

    @NullMarked
    @Override
    public String getDescription() {
        return "Changes the version constraint of a provider entry in terraform.required_providers.";
    }

    @Override
    public @NonNull TreeVisitor<?, ExecutionContext> getVisitor() {
        String resolvedProviderName = TopLevelBlockRecipeSupport.resolveOptionalFilterValue(providerName, "providerName");
        String resolvedProviderSource = TopLevelBlockRecipeSupport.resolveRequiredValue(providerSource, "providerSource");
        String resolvedProviderVersion = resolveProviderVersionFilter(providerVersion);
        String resolvedNewVersion = TopLevelBlockRecipeSupport.resolveRequiredValue(newVersion, "newVersion");

        return ProviderRecipeSupport.scopedVisitor(filePattern, new HclVisitor<ExecutionContext>() {
            @Override
            public @NonNull Hcl visitAttribute(Hcl.@NonNull Attribute attribute, ExecutionContext ctx) {
                Hcl.Attribute visited = (Hcl.Attribute) super.visitAttribute(attribute, ctx);

                if (!isRequiredProvidersEntry(getCursor())
                    || (resolvedProviderName != null && !resolvedProviderName.equals(visited.getSimpleName()))
                    || !(visited.getValue() instanceof Hcl.ObjectValue)) {
                    return visited;
                }

                Hcl.ObjectValue providerEntry = (Hcl.ObjectValue) visited.getValue();
                if (!resolvedProviderSource.equals(stringValueOf(findEntryAttribute(providerEntry, "source")))) {
                    return visited;
                }

                Hcl.Attribute versionAttribute = findEntryAttribute(providerEntry, "version");
                String currentVersion = stringValueOf(versionAttribute);
                if (versionAttribute == null || currentVersion == null || resolvedNewVersion.equals(currentVersion)) {
                    return visited;
                }
                if (resolvedProviderVersion != null && !VersionConstraintMatcher.matches(resolvedProviderVersion, currentVersion)) {
                    return visited;
                }

                Hcl.Attribute updatedVersionAttribute = versionAttribute.withValue(
                    withStringValue(versionAttribute.getValue(), resolvedNewVersion));
                Hcl.Attribute modified = visited.withValue(providerEntry.withArguments(ListUtils.map(
                    providerEntry.getAttributes(),
                    entryAttribute -> entryAttribute == versionAttribute ? updatedVersionAttribute : entryAttribute)));

                doAfterVisit(new SpacesVisitor<>(SpacesStyle.DEFAULT, modified));
                return modified;
            }
        });
    }

    @Override
    public @NonNull Validated<Object> validate() {
        Validated<Object> validated = super.validate();
        validated = TopLevelBlockRecipeSupport.validateOptionalNonBlank(validated, "providerName", providerName);
        validated = TopLevelBlockRecipeSupport.validateRequiredHclIdentifier(validated, "providerName", providerName);
        validated = TopLevelBlockRecipeSupport.validateRequiredNonBlank(validated, "providerSource", providerSource);
        validated = TopLevelBlockRecipeSupport.validateOptionalNonBlank(validated, "providerVersion", providerVersion);
        if (providerVersion != null && !providerVersion.trim().isEmpty() && !providerVersion.contains("${")
            && !VersionConstraintMatcher.isValidConstraint(providerVersion)) {
            validated = validated.and(Validated.invalid("providerVersion", providerVersion, INVALID_PROVIDER_VERSION_MESSAGE));
        }
        validated = TopLevelBlockRecipeSupport.validateRequiredNonBlank(validated, "newVersion", newVersion);
        return validated;
    }

    private static @Nullable String resolveProviderVersionFilter(@Nullable String value) {
        String resolved = TopLevelBlockRecipeSupport.resolveOptionalFilterValue(value, "providerVersion");
        if (resolved != null && !VersionConstraintMatcher.isValidConstraint(resolved)) {
            throw new IllegalStateException(INVALID_PROVIDER_VERSION_MESSAGE);
        }
        return resolved;
    }

    private static boolean isRequiredProvidersEntry(Cursor attributeCursor) {
        Cursor requiredProvidersCursor = attributeCursor.getParentTreeCursor();
        if (!(requiredProvidersCursor.getValue() instanceof Hcl.Block)
            || !ProviderRecipeSupport.isBlockType(requiredProvidersCursor.getValue(), "required_providers")) {
            return false;
        }

        Cursor terraformCursor = requiredProvidersCursor.getParentTreeCursor();
        return terraformCursor.getValue() instanceof Hcl.Block
               && ProviderRecipeSupport.isBlockType(terraformCursor.getValue(), "terraform")
               && terraformCursor.getParentTreeCursor().getValue() instanceof Hcl.ConfigFile;
    }

    private static Hcl.@Nullable Attribute findEntryAttribute(Hcl.ObjectValue providerEntry, String name) {
        for (Expression expression : providerEntry.getAttributes()) {
            if (expression instanceof Hcl.Attribute && name.equals(((Hcl.Attribute) expression).getSimpleName())) {
                return (Hcl.Attribute) expression;
            }
        }
        return null;
    }

    /**
     * Returns the value of a plain string attribute, or {@code null} when the attribute is missing
     * or its value is not a static string (for example an interpolated template or a variable reference).
     */
    private static @Nullable String stringValueOf(Hcl.@Nullable Attribute attribute) {
        if (attribute == null) {
            return null;
        }

        Expression value = attribute.getValue();
        if (value instanceof Hcl.Literal && ((Hcl.Literal) value).getValue() instanceof String) {
            return ModuleBlockPredicates.removeQuotes(((Hcl.Literal) value).getValueSource());
        }
        if (value instanceof Hcl.QuotedTemplate) {
            Hcl.QuotedTemplate template = (Hcl.QuotedTemplate) value;
            if (template.getExpressions().isEmpty()) {
                return "";
            }
            if (template.getExpressions().size() == 1 && template.getExpressions().get(0) instanceof Hcl.Literal) {
                return ((Hcl.Literal) template.getExpressions().get(0)).getValueSource();
            }
        }
        return null;
    }

    private static Expression withStringValue(Expression value, String newValue) {
        String quoted = TopLevelBlockRecipeSupport.quoteHclString(newValue);
        if (value instanceof Hcl.QuotedTemplate) {
            String unquoted = quoted.substring(1, quoted.length() - 1);
            return ((Hcl.QuotedTemplate) value).withExpressions(Collections.singletonList(
                new Hcl.Literal(Tree.randomId(), Space.EMPTY, Markers.EMPTY, newValue, unquoted)));
        }
        return ((Hcl.Literal) value).withValue(newValue).withValueSource(quoted);
    }
}
