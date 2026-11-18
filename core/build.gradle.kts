plugins { `java-library` }
java { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
tasks.withType<JavaCompile>().configureEach { options.encoding = "UTF-8" }
tasks.register<JavaExec>("verifyCore") {
    dependsOn(tasks.testClasses)
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("com.miuing.core.PublicationParserTest")
}
tasks.check { dependsOn("verifyCore") }
