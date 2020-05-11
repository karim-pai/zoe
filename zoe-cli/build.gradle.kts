// Copyright (c) 2020 Adevinta.
//
// Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
//
// The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
//
// THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.

import com.adevinta.oss.gradle.plugins.*
import com.adevinta.oss.gradle.plugins.Platform
import com.fasterxml.jackson.databind.ObjectMapper
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet
import java.time.LocalDateTime

plugins {
    application
    id("com.google.cloud.tools.jib")
    id("com.github.johnrengelman.shadow")
}

apply<DistributionWithRuntimePlugin>()

application {
    mainClassName = "com.adevinta.oss.zoe.cli.MainKt"
    applicationDefaultJvmArgs = listOf("-client")
    applicationName = "zoe"
}

configure<DistributionWithRuntimeExtension> {

    distribution {
        base = "shadow"
    }

    runtime {
        version = "11"
        distribution = "AdoptOpenJDK"
        platform = findProperty("runtime.os")?.let(Any::toString)?.let(Platform::valueOf) ?: Platform.Linux
    }
}

tasks.register("jpackage", JPackageTask::class.java) {
    imageName.set("zoe")
    installerName.set("zoe")
    mainClass.set("com.adevinta.oss.zoe.cli.MainKt")
    mainJar.set(tasks.jar.flatMap { it.archiveFileName })
    appVersion.set(project.version.toString())

    libs.set(
        tasks
            .named("installDist")
            .map { it.outputs.files.singleFile.resolve("lib").absolutePath }
    )

    output.set(
        findProperty("jpackage.output")
            ?.let { file(it.toString()) }
            ?: buildDir.resolve("jpackageOutput")
    )

    installType.set(
        findProperty("jpackage.installerType")
            ?.toString()
            ?: "app-image"
    )
}


mapOf("zip" to Zip::class, "tar" to Tar::class).forEach { (archiveType, archiveClass) ->

    mapOf(
        "WithRuntime" to tasks.named<Sync>("installWithRuntimeDist"),
        "WithoutRuntime" to tasks.named<Sync>("installDist")
    ).forEach { (alias, installTask) ->
        tasks.register("${archiveType}Dist${alias}", archiveClass) {
            from(installTask)
            into("zoe")

            archiveFileName.set("zoe${findProperty("${name}.suffix") ?: alias}-${project.version}.${archiveType}")
            destinationDirectory.set(file(findProperty("${name}.outputDir") ?: "$buildDir/dist${alias}"))
        }

    }
}

jib {

    from {
        image = "openjdk:11-jre-slim"
    }

    to {
        image = "adevinta/zoe-cli"
        tags = setOf(project.version.toString(), "latest")
    }

    container {
        jvmFlags = listOf("-client")
        mainClass = "com.adevinta.oss.zoe.cli.MainKt"
    }
}

tasks {
    val generateVersionFile by registering {
        val output = buildDir.resolve("resources/main/version.json")
        outputs.file(output)

        doLast {
            output.writeText(
                ObjectMapper().writeValueAsString(
                    mapOf(
                        "projectVersion" to project.version,
                        "buildTimestamp" to LocalDateTime.now().toString(),
                        "createdBy" to "Gradle ${gradle.gradleVersion}",
                        "buildJdk" to with(System.getProperties()) {
                            "${get("java.version")} (${get("java.vendor")} ${get("java.vm.version")})"
                        },
                        "buildOS" to with(System.getProperties()) {
                            "${get("os.name")} ${get("os.arch")} ${get("os.version")}"
                        }
                    )
                )
            )
        }
    }

    val processResources by getting(ProcessResources::class) {
        dependsOn(generateVersionFile)
    }

    compileKotlin {
        kotlinOptions.jvmTarget = "1.8"
    }

    compileTestKotlin {
        kotlinOptions.jvmTarget = "1.8"
    }

    test {
        useJUnitPlatform {
            includeEngines("spek2")
        }
    }
}

sourceSets {
    main {
        resources.srcDir("resources")
        withConvention(KotlinSourceSet::class) {
            kotlin.srcDir("src")
        }
    }

    test {
        resources.srcDir("testResources")
        withConvention(KotlinSourceSet::class) {
            kotlin.srcDir("test")
        }
    }
}

dependencies {
    implementation(project(":zoe-service"))
    implementation(project(":zoe-core"))

    implementation("com.amazonaws:aws-java-sdk-lambda:1.11.779")
    implementation("com.amazonaws:aws-java-sdk-iam:1.11.779")
    implementation("com.amazonaws:aws-java-sdk-cloudformation:1.11.779")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.3.6")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-jdk8:1.3.6")

    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:2.11.0")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.11.0")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.eclipse.jgit:org.eclipse.jgit:5.7.0.202003110725-r")

    implementation("org.koin:koin-core:2.0.1")
    implementation("com.jakewharton.picnic:picnic:0.3.1")
    implementation("com.github.ajalt:clikt:2.5.0")
    implementation("com.github.ajalt:mordant:1.2.1")
    implementation("org.slf4j:slf4j-log4j12:1.7.30")
    implementation("log4j:log4j:1.2.17")

    testImplementation(group = "junit", name = "junit", version = "4.12")

    testImplementation("org.spekframework.spek2:spek-dsl-jvm:2.0.10")
    testRuntimeOnly("org.spekframework.spek2:spek-runner-junit5:2.0.10")
}
