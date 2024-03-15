import java.io.*
import java.net.HttpURLConnection
import java.net.URL
import java.util.*
import org.json.*

plugins {
    id("com.android.application")
    id("kotlin-android")
}

val local = Properties().apply {
    FileInputStream(rootProject.file("local.properties"))
        .reader(Charsets.UTF_8)
        .use(this::load)
}

fun getGitHeadRefsSuffix(): String {
    val url = local.requireProperty("project.geoip_mmdb_version")
    val version = (URL(url).openConnection() as HttpURLConnection).apply {
        instanceFollowRedirects = true
        useCaches = false
        requestMethod = "GET"
        setRequestProperty("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/89.0.4389.114 Safari/537.36")
    }.inputStream.bufferedReader().use { input ->
        JSONObject(input.readText()).getString("tag_name")
    }
    return version
}


android {
    compileSdk = 31

    defaultConfig {
        applicationId = local.requireProperty("project.package_name")

        minSdk = 29
        targetSdk = 31

        versionCode = local.requireProperty("project.version_code").toInt()
        versionName = local.requireProperty("project.version_name")

        resValue("string", "package_label", local.requireProperty("project.package_label"))
        resValue("string", "geoip_version", getGitHeadRefsSuffix())

        val iconId = if (local.getProperty("project.package_icon_url") != null)
            "@mipmap/ic_icon"
        else
            "@android:drawable/sym_def_app_icon"

        manifestPlaceholders["applicationIcon"] = iconId
    }

    signingConfigs {
        maybeCreate("release").apply {
            storeFile = rootProject.file(local.requireProperty("keystore.file"))
            storePassword = local.requireProperty("keystore.password")
            keyAlias = local.requireProperty("keystore.key_alias")
            keyPassword = local.requireProperty("keystore.key_password")
        }
    }

    buildTypes {
        named("release") {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs["release"]
        }
    }

    buildFeatures {
        viewBinding = true
    }

    sourceSets {
        named("main") {
            assets {
                srcDir(buildDir.resolve("mmdb"))
            }
            res {
                srcDir(buildDir.resolve("icon"))
            }
        }
    }

    applicationVariants.all {
        val variant = this
        variant.outputs
                .map { it as com.android.build.gradle.internal.api.BaseVariantOutputImpl }
                .forEach { output ->
                    output.outputFileName = output.outputFileName
                            .replace("app-", "geoip.clash.dev-")
                            .replace(".apk", "-${getGitHeadRefsSuffix().trim()}(${variant.versionName}).apk")
                }
    }
}



task("fetchMMDB") {
    val url = local.requireProperty("project.geoip_mmdb_url")
    val outputDir = buildDir.resolve("mmdb").apply { mkdirs() }

    doLast {
        //URL(url).openStream()
        (URL(url).openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = true
            useCaches = false
            requestMethod = "GET"
            setRequestProperty("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/89.0.4389.114 Safari/537.36")
        }.inputStream.use { input ->
            FileOutputStream(outputDir.resolve("Country.mmdb")).use { output ->
                input.copyTo(output)
            }
        }
    }
}

task("fetchIcon") {
    val url = local.getProperty("project.package_icon_url")

    if (url != null) {
        val outputDir = buildDir.resolve("icon/mipmap").apply { mkdirs() }

        require(url.endsWith(".png")) {
            throw IllegalArgumentException("icon must be .png file")
        }

        doLast {
            URL(url).openStream().use { input ->
                FileOutputStream(outputDir.resolve("ic_icon.png")).use { output ->
                    input.copyTo(output)
                }
            }
        }
    }
}

afterEvaluate {
    android.applicationVariants.forEach {
        it.mergeResourcesProvider.get().dependsOn(tasks["fetchIcon"])
        it.mergeAssetsProvider.get().dependsOn(tasks["fetchMMDB"])
    }
}


dependencies {
    implementation("androidx.appcompat:appcompat:1.2.0")
    implementation("org.jetbrains.kotlin:kotlin-stdlib:${rootProject.extra["kotlin_version"]}")
    implementation("androidx.constraintlayout:constraintlayout:2.0.4")
    implementation("com.google.android.material:material:1.6.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.0-RC2")
}

fun Properties.requireProperty(key: String): String {
    return getProperty(key)
            ?: throw GradleScriptException(
                    "property \"$key\" not found in local.properties",
                    FileNotFoundException()
            )
}
