import com.android.build.api.dsl.LibraryExtension
import java.net.Inet4Address
import java.net.NetworkInterface
import project.convention.logic.config.LibraryModule
import project.convention.logic.kover.KoverExclusionRules
import project.convention.logic.kover.excludeFromKoverReport

plugins {
    id("project.android.feature")
}

/**
 * URL del backend de onboarding. En desarrollo se detecta la IP local del PC (el celular debe estar
 * en la misma red). Se puede forzar con -PbackendUrl=https://host  o  -PbackendHost=<ip> [-PbackendPort=8195].
 */
fun detectBackendUrl(): String {
    (findProperty("backendUrl") as String?)?.let { return it }
    val port = (findProperty("backendPort") as String?) ?: "8195"
    val host = (findProperty("backendHost") as String?)
        ?: runCatching {
            NetworkInterface.getNetworkInterfaces().toList()
                .filter { it.isUp && !it.isLoopback && !it.isVirtual && listOf("docker", "br-", "veth", "virbr").none { p -> it.name.startsWith(p) } }
                .flatMap { it.inetAddresses.toList() }
                .filterIsInstance<Inet4Address>()
                .firstOrNull { it.isSiteLocalAddress }?.hostAddress
        }.getOrNull()
        ?: "127.0.0.1"
    return "http://$host:$port"
}

extensions.configure<LibraryExtension>("android") {
    namespace = "eu.europa.ec.onboardingfeature"
    buildFeatures {
        buildConfig = true
    }
    defaultConfig {
        buildConfigField("String", "BACKEND_BASE_URL", "\"${detectBackendUrl()}\"")
    }
}

moduleConfig {
    module = LibraryModule.OnboardingFeature
}

dependencies {
    implementation(project(LibraryModule.CommonFeature.path))
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.androidx.camera.camera2)
}

excludeFromKoverReport(
    excludedClasses = KoverExclusionRules.OnboardingFeature.classes,
    excludedPackages = KoverExclusionRules.OnboardingFeature.packages,
)
