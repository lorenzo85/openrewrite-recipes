# Change provider version

`io.oczadly.openrewrite.hcl.ChangeProviderVersion`

Changes the version constraint of a provider entry in `terraform.required_providers`.

## Recipe source

[ChangeProviderVersion.java](../../src/main/java/io/oczadly/openrewrite/hcl/ChangeProviderVersion.java)

## Options

| Type     | Name            | Description                                                                                                            | Example            |
|----------|-----------------|------------------------------------------------------------------------------------------------------------------------|--------------------|
| `String` | providerName    | *Optional*. Local name of the provider (the key in `required_providers`). Supports placeholders like `${property}`.    | `"aws"`            |
| `String` | providerSource  | Source address of the provider entry to modify, compared exactly. Required and cannot be blank. Supports placeholders. | `"hashicorp/aws"`  |
| `String` | providerVersion | *Optional*. Semantic version constraint filter for the provider's current version constraint. Supports placeholders.   | `"~> 5.0"`         |
| `String` | newVersion      | The new version constraint to set, written as given. Required and cannot be blank. Supports placeholders.              | `"~> 6.0"`         |
| `String` | filePattern     | *Optional*. A glob pattern to match files to apply this recipe to. Defaults to `**/*.tf`.                              | `"**/versions.tf"` |

## Matching semantics

Only the `version` attribute of object-style entries inside a top-level `terraform { required_providers { ... } }` block is changed. Provider configuration blocks (`provider "aws" {}`), module blocks, and any other attribute are left untouched.

An entry matches when:

* its `source` attribute is a plain string equal to `providerSource`. The comparison is exact, so `hashicorp/aws` does not match `registry.terraform.io/hashicorp/aws`;
* its key equals `providerName`, when `providerName` is specified;
* its current `version` matches `providerVersion`, when `providerVersion` is specified.

Entries without a `version` attribute, entries whose `version` is not a plain string, and legacy shorthand entries such as `aws = "~> 5.0"` (which have no `source`) are left unchanged. Entries already set to `newVersion` are left unchanged.

## Version filter semantics

When `providerVersion` is specified, it is interpreted as a semantic version constraint, using the same matcher as [Change module version](ChangeModuleVersion.md). The matcher supports `=`, `!=`, `>`, `>=`, `<`, `<=`, `~>`, and comma-separated AND constraints such as `>= 5.0, < 6.0`. Provider entries match when their `version` is either a concrete stable version literal such as `5.82.1`, or a single operator-prefixed value such as `~> 5.82` where the operator is stripped and the numeric portion is matched. Missing, dynamic/interpolated, invalid, and multi-clause provider version values do not match.

## Example

The following example demonstrates changing the version constraint of the `hashicorp/aws` provider.

| Parameter       | Value             |
|-----------------|-------------------|
| providerSource  | `"hashicorp/aws"` |
| providerVersion | `"~> 5.0"`        |
| newVersion      | `"~> 6.0"`        |

**Before**

```hcl
terraform {
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
  }
}
```

**After**

```hcl
terraform {
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 6.0"
    }
  }
}
```

## Usage

This recipe requires configuration parameters. In your **rewrite.yml** create a new recipe with a unique name. For example: `io.oczadly.providers.ChangeAwsProviderFrom5xTo6x`. Here's how you can define and customize such a recipe within your **rewrite.yml**:

```yaml title="rewrite.yml"
---
type: specs.openrewrite.org/v1beta/recipe
name: io.oczadly.providers.ChangeAwsProviderFrom5xTo6x
displayName: hashicorp/aws 5.x -> 6.x
description: Update the hashicorp/aws provider constraint from 5.x to 6.x
recipeList:
  - io.oczadly.openrewrite.hcl.ChangeProviderVersion:
      providerSource: "hashicorp/aws"
      providerVersion: "~> 5.0"
      newVersion: "~> 6.0"
```

Now that `io.oczadly.providers.ChangeAwsProviderFrom5xTo6x` has been defined, activate it in your build file:

1. Add the following to your **build.gradle.kts** file:

```kotlin title="build.gradle.kts"
plugins {
    id("org.openrewrite.rewrite") version "latest.release"
}

repositories {
    mavenCentral()
}

dependencies {
    rewrite("io.oczadly:openrewrite-recipes:1.6.6")
}

rewrite {
    activeRecipe("io.oczadly.providers.ChangeAwsProviderFrom5xTo6x")
}
```

2. Run `gradle rewriteRun` to run the recipe.
