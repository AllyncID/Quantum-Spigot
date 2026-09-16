plugins { application }

repositories {
    mavenCentral()
    maven("https://repo.opencollab.dev/main/")
}

dependencies {
    implementation("org.geysermc.mcprotocollib:protocol:26.2-20260907.143312-19")
    runtimeOnly("org.slf4j:slf4j-simple:2.0.9")
}

java { toolchain.languageVersion = JavaLanguageVersion.of(25) }
application {
    mainClass = "dev.quantumspigot.testbench.BotDriver"
    applicationDefaultJvmArgs = listOf("-Xms128M", "-Xmx2G", "-Dio.netty.eventLoopThreads=4")
}
dependencyLocking { lockAllConfigurations() }
