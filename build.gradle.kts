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

plugins {
    id("java")
    id("jacoco")
    id("maven-publish")
}

group = "org.viablespark"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.springframework:spring-jdbc:5.3.3")
    implementation("org.slf4j:slf4j-api:2.0.5")


    compileOnly ("com.google.code.findbugs:jsr305:3.0.2")
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.9.1")
    testImplementation("org.hsqldb:hsqldb:2.7.1")
    testImplementation("org.springframework:spring-test:5.3.3")
    testImplementation("ch.qos.logback:logback-classic:1.4.5")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.9.1")
    testImplementation("org.mockito:mockito-core:4.10.0")
}


java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(11))
    }
}



tasks.getByName<Test>("test") {
    useJUnitPlatform()
}

tasks.jacocoTestReport {
    reports {
        csv.required.set(true)
    }
}



publishing {
    publications {
        create<MavenPublication>("maven") {
            groupId = "org.viablespark"
            artifactId = "goodenough-jdbc"
            version = "1.0-SNAPSHOT"

            from(components["java"])
        }
    }
}

tasks.jar {
    manifest {
        attributes("Automatic-Module-Name" to "org.viablespark.persistence")
    }
}

