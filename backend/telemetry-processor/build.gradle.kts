plugins {
	kotlin("jvm") version "2.2.21"
	kotlin("plugin.spring") version "2.2.21"
	war
	id("org.springframework.boot") version "3.2.5"
	id("io.spring.dependency-management") version "1.1.7"
	id("org.jetbrains.kotlinx.kover") version "0.9.8"
}

group = "com.lynceus"
version = "0.0.1-SNAPSHOT"

java {
	toolchain {
		languageVersion = JavaLanguageVersion.of(17)
	}
}

repositories {
	mavenCentral()
	mavenLocal()
}

dependencies {
	implementation("org.springframework.boot:spring-boot-starter-web")
	implementation("org.springframework.boot:spring-boot-starter-websocket")
	implementation("org.springframework.kafka:spring-kafka")
	implementation("org.springframework.boot:spring-boot-starter-data-jpa")
	implementation("org.springframework.boot:spring-boot-starter-data-redis")
	implementation("org.jetbrains.kotlin:kotlin-reflect")
	implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")
	runtimeOnly("org.postgresql:postgresql")
	providedRuntime("org.springframework.boot:spring-boot-starter-tomcat")
	testImplementation("org.springframework.boot:spring-boot-starter-test")
	testImplementation("org.springframework.kafka:spring-kafka-test")
	testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
	testImplementation("org.mockito.kotlin:mockito-kotlin:5.4.0")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")
	implementation("com.google.geometry:s2-geometry-library:HEAD-SNAPSHOT")
	implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.5.0")
}

kotlin {
	compilerOptions {
		freeCompilerArgs.addAll("-Xjsr305=strict")
	}
}

tasks.bootRun {
	jvmArgs = listOf("-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=5005")
}

tasks.withType<Test> {
	useJUnitPlatform()
	maxHeapSize = "4096m"
}

//jacoco {
//	toolVersion = "0.8.11"
//}
//
//tasks.jacocoTestReport {
//	dependsOn(tasks.test)
//	reports {
//		xml.required.set(true)
//		html.required.set(true)
//		csv.required.set(false)
//	}
//}