package io.oczadly.openrewrite.hcl;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.openrewrite.DocumentExample;
import org.openrewrite.Validated;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static io.oczadly.openrewrite.hcl.utils.SystemPropertyTestSupport.restoreSystemProperty;
import static org.assertj.core.api.Assertions.assertThat;
import static org.openrewrite.hcl.Assertions.hcl;

class ChangeProviderVersionTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new ChangeProviderVersion(null, "hashicorp/aws", null, "~> 6.0", null));
    }

    @DocumentExample
    @Test
    void shouldChangeProviderVersion() {
        rewriteRun(
            hcl(
                """
                terraform {
                  required_providers {
                    aws = {
                      source  = "hashicorp/aws"
                      version = "~> 5.0"
                    }
                  }
                }
                """,
                """
                terraform {
                  required_providers {
                    aws = {
                      source  = "hashicorp/aws"
                      version = "~> 6.0"
                    }
                  }
                }
                """
            )
        );
    }

    @Test
    void shouldOnlyChangeMatchingProviderAmongMultipleProviders() {
        rewriteRun(
            hcl(
                """
                terraform {
                  required_version = ">= 1.5.0"

                  required_providers {
                    aws = {
                      source  = "hashicorp/aws"
                      version = "~> 5.0"
                    }
                    kubernetes = {
                      source  = "hashicorp/kubernetes"
                      version = "~> 2.30"
                    }
                    helm = {
                      source  = "hashicorp/helm"
                      version = "~> 5.0"
                    }
                  }
                }
                """,
                """
                terraform {
                  required_version = ">= 1.5.0"

                  required_providers {
                    aws = {
                      source  = "hashicorp/aws"
                      version = "~> 6.0"
                    }
                    kubernetes = {
                      source  = "hashicorp/kubernetes"
                      version = "~> 2.30"
                    }
                    helm = {
                      source  = "hashicorp/helm"
                      version = "~> 5.0"
                    }
                  }
                }
                """
            )
        );
    }

    @Test
    void shouldPreserveOtherAttributesInProviderEntry() {
        rewriteRun(
            hcl(
                """
                terraform {
                  required_providers {
                    aws = {
                      source                = "hashicorp/aws"
                      version               = "~> 5.0"
                      configuration_aliases = [aws.us_east_1]
                    }
                  }
                }
                """,
                """
                terraform {
                  required_providers {
                    aws = {
                      source                = "hashicorp/aws"
                      version               = "~> 6.0"
                      configuration_aliases = [aws.us_east_1]
                    }
                  }
                }
                """
            )
        );
    }

    @ParameterizedTest(name = "should change provider version when filter constraint is ''{0}'' and provider version is ''{1}''")
    @CsvSource(delimiter = '|', quoteCharacter = '"', textBlock = """
      "~> 5.0"          | "~> 5.0"
      "~> 5.0"          | "~> 5.82"
      "~> 5.0"          | "5.82.1"
      ">= 5.0, < 6.0"   | ">= 5.40"
      "5.0"             | "= 5.0"
      """)
    void shouldChangeProviderVersionWhenVersionFilterMatches(String providerVersion, String currentVersion) {
        rewriteRun(
            spec -> spec.recipe(new ChangeProviderVersion(null, "hashicorp/aws", providerVersion, "~> 6.0", null)),
            hcl(
                """
                terraform {
                  required_providers {
                    aws = {
                      source  = "hashicorp/aws"
                      version = "%s"
                    }
                  }
                }
                """.formatted(currentVersion),
                """
                terraform {
                  required_providers {
                    aws = {
                      source  = "hashicorp/aws"
                      version = "~> 6.0"
                    }
                  }
                }
                """
            )
        );
    }

    @ParameterizedTest(name = "should not change provider version ''{1}'' for filter constraint ''{0}''")
    @CsvSource(delimiter = '|', quoteCharacter = '"', textBlock = """
      "~> 5.0" | "~> 4.67"
      "~> 5.0" | "~> 6.1"
      "~> 5.0" | ">= 5.0, < 6.0"
      "~> 5.0" | "!= 5.1.0"
      """)
    void shouldNotChangeProviderVersionWhenVersionFilterDoesNotMatch(String providerVersion, String currentVersion) {
        rewriteRun(
            spec -> spec.recipe(new ChangeProviderVersion(null, "hashicorp/aws", providerVersion, "~> 6.0", null)),
            hcl(
                """
                terraform {
                  required_providers {
                    aws = {
                      source  = "hashicorp/aws"
                      version = "%s"
                    }
                  }
                }
                """.formatted(currentVersion)
            )
        );
    }

    @Test
    void shouldNotChangeWhenAlreadyAtNewVersion() {
        rewriteRun(
            spec -> spec.recipe(new ChangeProviderVersion(null, "hashicorp/aws", "~> 6.0", "~> 6.0", null)),
            hcl(
                """
                terraform {
                  required_providers {
                    aws = {
                      source  = "hashicorp/aws"
                      version = "~> 6.0"
                    }
                  }
                }
                """
            )
        );
    }

    @Test
    void shouldOnlyChangeProviderWithMatchingName() {
        rewriteRun(
            spec -> spec.recipe(new ChangeProviderVersion("aws", "hashicorp/aws", null, "~> 6.0", null)),
            hcl(
                """
                terraform {
                  required_providers {
                    aws = {
                      source  = "hashicorp/aws"
                      version = "~> 5.0"
                    }
                    aws_legacy = {
                      source  = "hashicorp/aws"
                      version = "~> 5.0"
                    }
                  }
                }
                """,
                """
                terraform {
                  required_providers {
                    aws = {
                      source  = "hashicorp/aws"
                      version = "~> 6.0"
                    }
                    aws_legacy = {
                      source  = "hashicorp/aws"
                      version = "~> 5.0"
                    }
                  }
                }
                """
            )
        );
    }

    @Test
    void shouldMatchSourceExactly() {
        rewriteRun(
            hcl(
                """
                terraform {
                  required_providers {
                    aws = {
                      source  = "registry.terraform.io/hashicorp/aws"
                      version = "~> 5.0"
                    }
                  }
                }
                """
            )
        );
    }

    @Test
    void shouldNotChangeProviderEntryWithoutVersion() {
        rewriteRun(
            hcl(
                """
                terraform {
                  required_providers {
                    aws = {
                      source = "hashicorp/aws"
                    }
                  }
                }
                """
            )
        );
    }

    @Test
    void shouldNotChangeShorthandProviderEntry() {
        rewriteRun(
            hcl(
                """
                terraform {
                  required_providers {
                    aws = "~> 5.0"
                  }
                }
                """
            )
        );
    }

    @Test
    void shouldNotChangeProviderConfigurationBlocksOrModules() {
        rewriteRun(
            hcl(
                """
                provider "aws" {
                  region  = "eu-central-1"
                  version = "~> 5.0"
                }

                module "vpc" {
                  source  = "hashicorp/aws"
                  version = "~> 5.0"
                }

                locals {
                  required_providers = {
                    aws = {
                      source  = "hashicorp/aws"
                      version = "~> 5.0"
                    }
                  }
                }
                """
            )
        );
    }

    @Test
    void shouldOnlyModifyFilesMatchingPattern() {
        rewriteRun(
            spec -> spec.recipe(new ChangeProviderVersion(null, "hashicorp/aws", null, "~> 6.0", "**/prod/**/*.tf")),
            hcl(
                """
                terraform {
                  required_providers {
                    aws = {
                      source  = "hashicorp/aws"
                      version = "~> 5.0"
                    }
                  }
                }
                """,
                """
                terraform {
                  required_providers {
                    aws = {
                      source  = "hashicorp/aws"
                      version = "~> 6.0"
                    }
                  }
                }
                """,
                spec -> spec.path("env/prod/versions.tf")
            ),
            hcl(
                """
                terraform {
                  required_providers {
                    aws = {
                      source  = "hashicorp/aws"
                      version = "~> 5.0"
                    }
                  }
                }
                """,
                spec -> spec.path("env/dev/versions.tf")
            )
        );
    }

    @Test
    void shouldResolvePlaceholderInNewVersion() {
        String key = "dv.aws.provider.version";
        String previousValue = System.getProperty(key);
        System.setProperty(key, "~> 6.0");

        try {
            rewriteRun(
                spec -> spec.recipe(new ChangeProviderVersion(null, "hashicorp/aws", null, "${dv.aws.provider.version}", null)),
                hcl(
                    """
                    terraform {
                      required_providers {
                        aws = {
                          source  = "hashicorp/aws"
                          version = "~> 5.0"
                        }
                      }
                    }
                    """,
                    """
                    terraform {
                      required_providers {
                        aws = {
                          source  = "hashicorp/aws"
                          version = "~> 6.0"
                        }
                      }
                    }
                    """
                )
            );
        } finally {
            restoreSystemProperty(key, previousValue);
        }
    }

    @ParameterizedTest(name = "should reject providerName=''{0}'' providerSource=''{1}'' providerVersion=''{2}'' newVersion=''{3}''")
    @CsvSource(delimiter = '|', quoteCharacter = '"', nullValues = "null", textBlock = """
      " "   | hashicorp/aws | null      | "~> 6.0" | 'providerName' cannot be empty when specified.
      aws-1 | hashicorp/aws | null      | "~> 6.0" | 'providerName' must be a valid HCL identifier matching [A-Za-z_][A-Za-z0-9_]*.
      aws   | null          | null      | "~> 6.0" | 'providerSource' must be specified and cannot be empty.
      aws   | " "           | null      | "~> 6.0" | 'providerSource' must be specified and cannot be empty.
      aws   | hashicorp/aws | " "       | "~> 6.0" | 'providerVersion' cannot be empty when specified.
      aws   | hashicorp/aws | ">= 5.x"  | "~> 6.0" | 'providerVersion' must be a valid semantic version constraint.
      aws   | hashicorp/aws | "~> 5.0"  | null     | 'newVersion' must be specified and cannot be empty.
      aws   | hashicorp/aws | "~> 5.0"  | " "      | 'newVersion' must be specified and cannot be empty.
      """)
    void shouldRejectInvalidOptions(String providerName,
                                    String providerSource,
                                    String providerVersion,
                                    String newVersion,
                                    String expectedMessage) {
        ChangeProviderVersion recipe = new ChangeProviderVersion(providerName, providerSource, providerVersion, newVersion, null);

        Validated<Object> validated = recipe.validate();

        assertThat(validated.isValid()).isFalse();
        assertThat(validated.failures()).hasSize(1);
        assertThat(validated.failures().getFirst().getMessage()).isEqualTo(expectedMessage);
    }
}
