plugins {
    java
    antlr
    application
    id("idea")
}

group = "org.kokolor"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    antlr("org.antlr:antlr4:4.13.2")
    implementation("org.antlr:antlr4-runtime:4.13.2")
    implementation("org.bytedeco:llvm-platform:19.1.3-1.5.11")
    implementation("org.bytedeco:javacpp:1.5.9")
    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

idea {
    module {
        generatedSourceDirs.add(
            layout.buildDirectory
                .dir("generated-src/antlr/main")
                .get()
                .asFile
        )
    }
}

tasks.test {
    useJUnitPlatform()
}

tasks.generateGrammarSource {
    arguments = arguments + listOf("-visitor", "-long-messages")
    outputDirectory = layout.buildDirectory
        .dir("generated-src/antlr/main")
        .get()
        .asFile
}

sourceSets {
    main {
        java {
            srcDir(layout.buildDirectory.dir("generated-src/antlr/main").get().asFile)
        }
    }
}

tasks.register<Copy>("moveAntlrToPackage") {
    dependsOn(tasks.named("generateGrammarSource"))
    from(layout.buildDirectory.dir("generated-src/antlr/main"))
    into(layout.buildDirectory.dir("generated-src/antlr/main/org/kokolor").get().asFile)
    include("*.java")
    doLast {
        layout.buildDirectory.dir("generated-src/antlr/main").get().asFile
            .listFiles { f -> f.isFile && f.extension == "java" }
            ?.forEach { it.delete() }
    }
}

tasks.named("compileJava") {
    dependsOn(tasks.named("clean"))
    dependsOn(tasks.named("moveAntlrToPackage"))
    dependsOn(tasks.named("generateGrammarSource"))
}

tasks.register<JavaExec>("runCompiler") {
    group = "application"
    description = "Run the Majus compiler with arguments"

    classpath = sourceSets.main.get().runtimeClasspath
    mainClass = "org.kokolor.MajusCompiler"

    if (project.hasProperty("args")) {
        args(project.property("args").toString().split(" "))
    }
}