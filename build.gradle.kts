/*
 * Copyright (c) 2023 the original author or authors.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and limitations under the License.
 */

import org.gradle.api.plugins.quality.Checkstyle

plugins {
    id("java-library")
    id("jacoco")
    id("maven-publish")
    id("checkstyle")
    kotlin("jvm") version "2.2.21"
    id("com.diffplug.spotless") version "6.25.0"
}

val springFrameworkVersion = "6.2.12"
val slf4jVersion = "2.0.17"
val junitVersion = "5.10.3"

group = providers.gradleProperty("projectGroup").get()
version = providers.gradleProperty("projectVersion").get()

repositories {
    mavenCentral()
}

dependencies {
    api("org.springframework:spring-jdbc:$springFrameworkVersion")
    implementation("org.slf4j:slf4j-api:$slf4jVersion")

    // Kotlin only needed for tests (compatibility checks / data classes)
    testImplementation(kotlin("stdlib"))
    testImplementation(kotlin("reflect"))
    testImplementation(platform("org.junit:junit-bom:$junitVersion"))
    testImplementation("org.junit.jupiter:junit-jupiter-api")
    testImplementation("org.hsqldb:hsqldb:2.7.4")
    testImplementation("org.springframework:spring-jdbc:$springFrameworkVersion")
    testImplementation("org.springframework:spring-test:$springFrameworkVersion")
    testImplementation("ch.qos.logback:logback-classic:1.5.20")
    testImplementation("org.mockito:mockito-core:5.20.0")

    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}


java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
    withSourcesJar()
    withJavadocJar()
}

kotlin {
    jvmToolchain(17)
}

// Disable automatic stdlib addition via project property in gradle.properties.
// Keep kotlin only for tests; no main Kotlin sources are present.
configurations.configureEach {
    // Do not mutate testImplementation; rely on explicit testImplementation(kotlin("stdlib")).
    if (name == "implementation") {
        // Guard in case plugin tries to inject stdlib; remove after evaluation of dependencies.
        withDependencies { removeIf { it.group == "org.jetbrains.kotlin" } }
    }
}

checkstyle {
    toolVersion = "10.17.0"
    configDirectory.set(layout.projectDirectory.dir("config/checkstyle"))
}

tasks.withType<Checkstyle>().configureEach {
    reports {
        html.required.set(true)
        xml.required.set(false)
    }
    exclude("**/module-info.java")
}

spotless {
    java {
        target("src/**/*.java")
        googleJavaFormat("1.28.0")
        trimTrailingWhitespace()
        endWithNewline()
    }
}

tasks.named("check") {
    dependsOn("spotlessCheck", "jacocoTestCoverageVerification")
}



tasks.getByName<Test>("test") {
    useJUnitPlatform()
}

tasks.jacocoTestReport {
    reports {
        csv.required.set(true)
    }
}

tasks.jacocoTestCoverageVerification {
    violationRules {
        rule {
            limit {
                counter = "LINE"
                value = "COVEREDRATIO"
                minimum = "0.95".toBigDecimal()
            }
        }
    }
}

val privateRegistryUrl = providers.environmentVariable("PRIVATE_REGISTRY_URL").orNull

publishing {
    repositories {
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/martin-jamszolik/goodenough-jdbc")
            credentials {
                username = project.findProperty("gpr.user") as String? ?: System.getenv("GITHUB_ACTOR")
                password = project.findProperty("gpr.key") as String? ?: System.getenv("GITHUB_TOKEN")
            }
        }
        maven {
            name = "LocalBuild"
            url = layout.buildDirectory.dir("publication-repository").get().asFile.toURI()
        }
        if (privateRegistryUrl != null) {
            maven {
                name = "Private"
                url = uri("https://$privateRegistryUrl/m2/maven-releases")
                credentials {
                    username = project.findProperty("private.user") as String?
                        ?: System.getenv("PRIVATE_REGISTRY_USERNAME")
                    password = project.findProperty("private.key") as String?
                        ?: System.getenv("PRIVATE_REGISTRY_TOKEN")
                }
            }
        }
    }
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
            versionMapping {
                usage("java-api") { fromResolutionOf("runtimeClasspath") }
                usage("java-runtime") { fromResolutionResult() }
            }
            pom {
                name.set("Good Enough JDBC")
                description.set(
                    "A lightweight schema-first repository and mapping library built on Spring JDBC."
                )
                url.set("https://github.com/martin-jamszolik/goodenough-jdbc")
                scm {
                    connection.set(
                        "scm:git:https://github.com/martin-jamszolik/goodenough-jdbc.git"
                    )
                    developerConnection.set(
                        "scm:git:ssh://git@github.com/martin-jamszolik/goodenough-jdbc.git"
                    )
                    url.set("https://github.com/martin-jamszolik/goodenough-jdbc")
                    tag.set("HEAD")
                }
                licenses {
                    license {
                        name.set("The Apache License, Version 2.0")
                        url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                        distribution.set("repo")
                    }
                }
                developers {
                    developer {
                        id.set("martin-jamszolik")
                        name.set("Martin Jamszolik")
                        url.set("https://github.com/martin-jamszolik")
                    }
                }
            }
        }

    }
}

tasks.register("publicationCheck") {
    group = "verification"
    description = "Builds and publishes all artifacts to a local repository for validation."
    dependsOn(
        "check",
        "javadocJar",
        "sourcesJar",
        "generatePomFileForMavenPublication",
        "generateMetadataFileForMavenPublication",
        "publishMavenPublicationToLocalBuildRepository"
    )
}

tasks.register("printVersion") {
    doLast { println(project.version) }
}

tasks.jar {
    manifest {
        attributes("Automatic-Module-Name" to "org.viablespark.persistence")
    }
}
