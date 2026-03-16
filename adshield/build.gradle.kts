plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("com.vanniktech.maven.publish")
}

android {
    namespace = "io.adshield.android"
    compileSdk = 34

    defaultConfig {
        minSdk = 21
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    kotlinOptions {
        jvmTarget = "1.8"
    }
}

dependencies {
}

mavenPublishing {
    publishToMavenCentral(com.vanniktech.maven.publish.SonatypeHost.CENTRAL_PORTAL)
    signAllPublications()

    coordinates("io.ad-shield", "adshield-android", findProperty("version") as String? ?: "0.0.2")

    pom {
        name.set("AdShield Android SDK")
        description.set("Ad-Shield mobile SDK for Android. Detects ad blocking and reports results.")
        url.set("https://www.ad-shield.io")
        inceptionYear.set("2026")

        licenses {
            license {
                name.set("Proprietary")
                url.set("https://www.ad-shield.io/terms")
            }
        }

        developers {
            developer {
                id.set("adshield")
                name.set("Ad-Shield")
                email.set("dev@ad-shield.io")
            }
        }

        scm {
            url.set("https://github.com/ad-shield/android")
            connection.set("scm:git:git://github.com/ad-shield/android.git")
            developerConnection.set("scm:git:ssh://git@github.com/ad-shield/android.git")
        }
    }
}
