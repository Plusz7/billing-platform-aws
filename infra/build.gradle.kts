plugins {
    kotlin("jvm") version "2.0.21"
    application
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("software.amazon.awscdk:aws-cdk-lib:2.180.0")
    implementation("software.constructs:constructs:10.4.2")
}

kotlin {
    jvmToolchain(21)
}

application {
    mainClass.set("com.project.billing.infra.InfraAppKt")
}
