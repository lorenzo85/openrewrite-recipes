# Change provider version

`io.oczadly.openrewrite.hcl.ChangeProviderVersion`

Changes the version constraint of a provider entry in `terraform.required_providers`.

## Recipe source

[ChangeProviderVersion.java](../../src/main/java/io/oczadly/openrewrite/hcl/ChangeProviderVersion.java)

## Options

| Type     | Name            | Description                                                             | Example            |
|----------|-----------------|-------------------------------------------------------------------------|--------------------|
| `String` | providerSource  | Source of the provider entry to change, compared exactly.               | `"hashicorp/aws"`  |
| `String` | newVersion      | The version constraint to set, written as given.                        | `"~> 6.0"`         |
| `String` | providerName    | *Optional*. The provider's key in `required_providers`.                 | `"aws"`            |
| `String` | providerVersion | *Optional*. Semantic version constraint the current version must match. | `"~> 5.0"`         |
| `String` | filePattern     | *Optional*. A glob pattern to match files to apply this recipe to.      | `"**/versions.tf"` |

All options except `filePattern` support placeholders like `${property}`.

## Matching semantics

Only the version of an entry in a top-level `terraform { required_providers { } }` block changes: the `version` of an object entry, or the value of a shorthand entry such as `aws = "~> 5.0"`, whose source is the implied `hashicorp/<name>`. Provider configuration blocks, modules, entries without a `version`, and versions that are not a plain string are left alone.

`providerVersion` uses the same matcher as [Change module version](ChangeModuleVersion.md): a current version matches when it is a concrete version such as `5.82.1`, or one operator and a version such as `~> 5.82`, whose version is matched.

## Example

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

Define a recipe in your **rewrite.yml**:

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

Activate it in your **build.gradle.kts**:

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

Then run `gradle rewriteRun`.
