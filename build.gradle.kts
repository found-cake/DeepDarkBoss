plugins {
    kotlin("jvm") version "2.3.21"
    id("com.gradleup.shadow") version "9.4.3"
}

group = "io.github.found_cake.deep_dark_boss"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    maven {
        name = "papermc"
        url = uri("https://repo.papermc.io/repository/maven-public/")
    }
}


dependencies {
    compileOnly("io.papermc.paper:paper-api:26.2.build.+")
    implementation(kotlin("stdlib"))
    implementation(kotlin("reflect"))
}

kotlin {
    jvmToolchain(25)
}

tasks{
    shadowJar{
        archiveFileName.set("DeepDarkBoss.jar")
    }

    processResources{
        from("$rootDir/LICENSE") {
            into("META-INF") // 루트 경로에 포함
        }
    }
}