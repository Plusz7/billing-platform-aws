plugins {
    kotlin("jvm") version "2.2.20"
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("com.amazonaws:aws-lambda-java-core:1.2.3")
    implementation("com.amazonaws:aws-lambda-java-events:3.14.0")
    implementation(platform("software.amazon.awssdk:bom:2.57.8"))
    implementation("software.amazon.awssdk:s3")
    implementation("software.amazon.awssdk:dynamodb")
    implementation("software.amazon.awssdk:url-connection-client")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.18.2")

    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

kotlin {
    jvmToolchain(21)
}

tasks.test {
    useJUnitPlatform()
}

// Lambda's Java runtime loads classes from the zip root and dependency jars from lib/.
tasks.register<Zip>("buildZip") {
    from(tasks.compileKotlin)
    from(tasks.processResources)
    into("lib") { from(configurations.runtimeClasspath) }
    archiveFileName.set("invoice-lambda.zip")
}
