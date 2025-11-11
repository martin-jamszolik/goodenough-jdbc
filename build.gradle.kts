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
    id("java")
    id("jacoco")
    id("maven-publish")
    id("checkstyle")
    kotlin("jvm") version "1.9.24"
    id("com.diffplug.spotless") version "6.25.0"
}

var libReleaseVersion = "2.0-RC"

group = "org.viablespark"
version = libReleaseVersion

repositories {
    mavenCentral()
}

dependencies {
    compileOnly("org.springframework:spring-jdbc:5.3.39")
    compileOnly("org.slf4j:slf4j-api:2.0.5")


    compileOnly ("com.google.code.findbugs:jsr305:3.0.2")
    testImplementation(kotlin("stdlib"))
    testImplementation(kotlin("reflect"))
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.9.1")
    testImplementation("org.hsqldb:hsqldb:2.7.3")
    testImplementation("org.springframework:spring-jdbc:5.3.39")
    testImplementation("org.springframework:spring-test:5.3.39")
    testImplementation("ch.qos.logback:logback-classic:1.5.6")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.9.1")
    testImplementation("org.mockito:mockito-core:4.10.0")
}


java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
    withSourcesJar()
}

configurations.implementation {
    withDependencies {
        removeAll { dep -> dep.group == "org.jetbrains.kotlin" }
    }
}

kotlin {
    jvmToolchain(17)
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
    dependsOn("spotlessCheck")
}



tasks.getByName<Test>("test") {
    useJUnitPlatform()
}

tasks.jacocoTestReport {
    reports {
        csv.required.set(true)
    }
}

val privateRegistryURL: String = System.getenv("PRIVATE_REGISTRY_URL")  ?: "private"

publishing {
    repositories {
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/martin-jamszolik/goodenough-jdbc")
            credentials {
                username = project.findProperty("gpr.user") as String? ?: System.getenv("USERNAME")
                password = project.findProperty("gpr.key") as String? ?: System.getenv("TOKEN")
            }
        }
        // Private Repository
        maven {
            name = "Private"
            url = uri("https://$privateRegistryURL/m2/maven-releases")
            credentials {
                username = project.findProperty("gpr.user") as String? ?: System.getenv("USERNAME")
                password = project.findProperty("gpr.key") as String? ?: System.getenv("TOKEN")
            }
        }
    }
    publications {
        create<MavenPublication>("maven") {
            groupId = "org.viablespark"
            artifactId = "goodenough-jdbc"
            version = libReleaseVersion

            from(components["java"])
            pom {
                description.set("Good Enough JDBC")
                scm {
                    connection.set("scm:git:git@github.com:martin-jamszolik/goodenough-jdbc.git")
                    url.set("https://github.com/martin-jamszolik/goodenough-jdbc")
                }
                licenses {
                    license {
                        name.set("The Apache License, Version 2.0")
                        url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                    }
                }
                developers {
                    developer {
                        id.set("martin-jamszolik")
                        name.set("Martin Jamszolik")
                    }
                }
            }
        }

    }
}

tasks.jar {
    manifest {
        attributes("Automatic-Module-Name" to "org.viablespark.persistence")
    }
}

