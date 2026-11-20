# Change provider version

`io.oczadly.openrewrite.hcl.ChangeProviderVersion`

Changes the version of a provider in `terraform { required_providers { } }`.

## Recipe source

[ChangeProviderVersion.java](../../src/main/java/io/oczadly/openrewrite/hcl/ChangeProviderVersion.java)

## Options

| Type     | Name            | Description                                                                  | Example            |
|----------|-----------------|------------------------------------------------------------------------------|--------------------|
| `String` | providerSource  | The source of the provider to change. It must match exactly.                 | `"hashicorp/aws"`  |
| `String` | newVersion      | The new version. It is written as you give it.                               | `"~> 6.0"`         |
| `String` | providerName    | *Optional*. Change only the provider with this name in `required_providers`. | `"aws"`            |
| `String` | providerVersion | *Optional*. Change the provider only if its current version matches this.    | `"~> 5.0"`         |
| `String` | filePattern     | *Optional*. Change only the files that match this pattern.                   | `"**/versions.tf"` |

You can use placeholders like `${property}` in every option except `filePattern`.

## What it changes

The recipe changes the version of a provider in the `required_providers` block of a top-level `terraform` block. It works with both forms:

```hcl
terraform {
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
    helm = "~> 2.17"
  }
}
```

A short entry such as `helm = "~> 2.17"` has no `source`. Terraform reads it as `hashicorp/helm`, so it matches `providerSource: "hashicorp/helm"`.

The recipe does not change:

* `provider` blocks and `module` blocks.
* A provider without a version.
* A version that is not a plain string, such as `var.aws_version`.

`providerVersion` works like the `version` option of [Change module version](ChangeModuleVersion.md). For example, `~> 5.0` matches a current version of `5.82.1` or `~> 5.82`.

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
