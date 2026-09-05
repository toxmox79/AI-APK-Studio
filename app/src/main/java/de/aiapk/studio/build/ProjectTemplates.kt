package de.aiapk.studio.build

object ProjectTemplates {
    fun chooseType(requested: String, description: String): String {
        if (requested != "Automatisch") return requested
        val nativeHints = listOf(
            "bluetooth", "nfc", "widget", "kamera", "camera", "hintergrund",
            "background", "service", "usb", "sensor", "wear", "notification listener"
        )
        return if (nativeHints.any { description.contains(it, ignoreCase = true) }) {
            "Native Android"
        } else {
            "Quick App"
        }
    }

    fun files(type: String, packageName: String, appName: String): List<ProjectFileChange> =
        if (type == "Quick App") quickApp(packageName, appName) else nativeCompose(packageName, appName)

    private fun quickApp(pkg: String, appName: String): List<ProjectFileChange> = listOf(
        ProjectFileChange(
            "settings.gradle.kts",
            """
            pluginManagement { repositories { google(); mavenCentral(); gradlePluginPortal() } }
            dependencyResolutionManagement {
                repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
                repositories { google(); mavenCentral() }
            }
            rootProject.name = ${q(appName)}
            include(":app")
            """.trimIndent()
        ),
        ProjectFileChange(
            "build.gradle.kts",
            """
            plugins {
                id("com.android.application") version "8.10.1" apply false
            }
            """.trimIndent()
        ),
        ProjectFileChange(
            "gradle.properties",
            """
            org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8
            android.useAndroidX=true
            """.trimIndent()
        ),
        ProjectFileChange(
            ".gitignore",
            """
            .gradle/
            **/build/
            local.properties
            *.jks
            *.keystore
            """.trimIndent()
        ),
        ProjectFileChange(
            "app/build.gradle.kts",
            """
            plugins { id("com.android.application") }

            android {
                namespace = ${q(pkg)}
                compileSdk = 36
                defaultConfig {
                    applicationId = ${q(pkg)}
                    minSdk = 26
                    targetSdk = 36
                    versionCode = 1
                    versionName = "1.0"
                }
            }
            """.trimIndent()
        ),
        ProjectFileChange(
            "app/src/main/AndroidManifest.xml",
            """
            <?xml version="1.0" encoding="utf-8"?>
            <manifest xmlns:android="http://schemas.android.com/apk/res/android">
                <uses-permission android:name="android.permission.INTERNET" />
                <application
                    android:theme="@style/AppTheme"
                    android:label=${xml(appName)}
                    android:usesCleartextTraffic="true">
                    <activity android:name=".MainActivity" android:exported="true">
                        <intent-filter>
                            <action android:name="android.intent.action.MAIN" />
                            <category android:name="android.intent.category.LAUNCHER" />
                        </intent-filter>
                    </activity>
                </application>
            </manifest>
            """.trimIndent()
        ),
        ProjectFileChange(
            "app/src/main/res/values/styles.xml",
            """
            <resources>
                <style name="AppTheme" parent="android:style/Theme.Material.Light.NoActionBar">
                    <item name="android:fontFamily">sans</item>
                    <item name="android:colorAccent">#6C63FF</item>
                    <item name="android:navigationBarColor">#E6EBF2</item>
                    <item name="android:statusBarColor">#E6EBF2</item>
                </style>
            </resources>
            """.trimIndent()
        ),
        ProjectFileChange(
            "app/src/main/java/${pkg.replace('.', '/')}/MainActivity.java",
            """
            package $pkg;

            import android.app.Activity;
            import android.os.Bundle;
            import android.webkit.WebChromeClient;
            import android.webkit.WebSettings;
            import android.webkit.WebView;
            import android.webkit.WebViewClient;

            public class MainActivity extends Activity {
                private WebView webView;

                @Override public void onCreate(Bundle state) {
                    super.onCreate(state);
                    webView = new WebView(this);
                    WebSettings settings = webView.getSettings();
                    settings.setJavaScriptEnabled(true);
                    settings.setDomStorageEnabled(true);
                    webView.setWebViewClient(new WebViewClient());
                    webView.setWebChromeClient(new WebChromeClient());
                    webView.loadUrl("file:///android_asset/www/index.html");
                    setContentView(webView);
                }

                @Override public void onBackPressed() {
                    if (webView.canGoBack()) webView.goBack(); else super.onBackPressed();
                }
            }
            """.trimIndent()
        ),
        ProjectFileChange(
            "app/src/main/assets/www/index.html",
            """
            <!doctype html>
            <html lang="de">
            <head>
                <meta charset="utf-8">
                <meta name="viewport" content="width=device-width,initial-scale=1">
                <link rel="stylesheet" href="style.css">
                <title>${html(appName)}</title>
            </head>
            <body>
                <main class="card">
                    <div class="badge">AI APK Studio</div>
                    <h1>${html(appName)}</h1>
                    <p>Deine App ist bereit für die erste KI-Änderung.</p>
                    <button id="action">Testen</button>
                    <div id="out"></div>
                </main>
                <script src="app.js"></script>
            </body>
            </html>
            """.trimIndent()
        ),
        ProjectFileChange(
            "app/src/main/assets/www/style.css",
            """
            :root{font-family:system-ui;background:#e6ebf2;color:#252833}
            body{margin:0;min-height:100vh;display:grid;place-items:center;padding:20px;box-sizing:border-box}
            .card{width:min(420px,100%);box-sizing:border-box;padding:28px;border-radius:28px;background:#e6ebf2;box-shadow:12px 12px 28px #bec3ca,-12px -12px 28px #fff}
            .badge{display:inline-block;padding:7px 12px;border-radius:999px;color:#6c63ff;font-weight:700;background:#eef1f7}
            h1{font-size:32px;margin:18px 0 8px}p{opacity:.72;line-height:1.5}
            button{border:0;border-radius:18px;padding:14px 22px;background:#6c63ff;color:white;font-weight:700;font-size:16px;box-shadow:7px 7px 16px #bec3ca,-7px -7px 16px #fff}
            #out{margin-top:18px;color:#2c9f7c}
            """.trimIndent()
        ),
        ProjectFileChange(
            "app/src/main/assets/www/app.js",
            "document.querySelector('#action').onclick=()=>document.querySelector('#out').textContent='✓ Funktioniert';"
        )
    )

    private fun nativeCompose(pkg: String, appName: String): List<ProjectFileChange> = listOf(
        ProjectFileChange(
            "settings.gradle.kts",
            """
            pluginManagement { repositories { google(); mavenCentral(); gradlePluginPortal() } }
            dependencyResolutionManagement {
                repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
                repositories { google(); mavenCentral() }
            }
            rootProject.name = ${q(appName)}
            include(":app")
            """.trimIndent()
        ),
        ProjectFileChange(
            "build.gradle.kts",
            """
            plugins {
                id("com.android.application") version "8.10.1" apply false
                id("org.jetbrains.kotlin.android") version "2.2.10" apply false
                id("org.jetbrains.kotlin.plugin.compose") version "2.2.10" apply false
            }
            """.trimIndent()
        ),
        ProjectFileChange(
            "gradle.properties",
            """
            org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8
            android.useAndroidX=true
            kotlin.code.style=official
            """.trimIndent()
        ),
        ProjectFileChange(
            ".gitignore",
            """
            .gradle/
            **/build/
            local.properties
            *.jks
            *.keystore
            """.trimIndent()
        ),
        ProjectFileChange(
            "app/build.gradle.kts",
            """
            plugins {
                id("com.android.application")
                id("org.jetbrains.kotlin.android")
                id("org.jetbrains.kotlin.plugin.compose")
            }

            android {
                namespace = ${q(pkg)}
                compileSdk = 36
                defaultConfig {
                    applicationId = ${q(pkg)}
                    minSdk = 26
                    targetSdk = 36
                    versionCode = 1
                    versionName = "1.0"
                }
                compileOptions {
                    sourceCompatibility = JavaVersion.VERSION_17
                    targetCompatibility = JavaVersion.VERSION_17
                }
                kotlinOptions { jvmTarget = "17" }
                buildFeatures { compose = true }
            }

            dependencies {
                implementation("androidx.core:core-ktx:1.17.0")
                implementation("androidx.activity:activity-compose:1.11.0")
                implementation("androidx.compose.ui:ui:1.9.3")
                implementation("androidx.compose.foundation:foundation:1.9.3")
                implementation("androidx.compose.material3:material3:1.4.0")
                implementation("androidx.compose.material:material-icons-extended:1.9.3")
            }
            """.trimIndent()
        ),
        ProjectFileChange(
            "app/src/main/AndroidManifest.xml",
            """
            <?xml version="1.0" encoding="utf-8"?>
            <manifest xmlns:android="http://schemas.android.com/apk/res/android">
                <application android:theme="@style/AppTheme" android:label=${xml(appName)}>
                    <activity android:name=".MainActivity" android:exported="true">
                        <intent-filter>
                            <action android:name="android.intent.action.MAIN" />
                            <category android:name="android.intent.category.LAUNCHER" />
                        </intent-filter>
                    </activity>
                </application>
            </manifest>
            """.trimIndent()
        ),
        ProjectFileChange(
            "app/src/main/res/values/styles.xml",
            """
            <resources>
                <style name="AppTheme" parent="android:style/Theme.Material.Light.NoActionBar">
                    <item name="android:statusBarColor">#E6EBF2</item>
                    <item name="android:navigationBarColor">#E6EBF2</item>
                </style>
            </resources>
            """.trimIndent()
        ),
        ProjectFileChange(
            "app/src/main/java/${pkg.replace('.', '/')}/MainActivity.kt",
            """
            package $pkg

            import android.os.Bundle
            import androidx.activity.ComponentActivity
            import androidx.activity.compose.setContent
            import androidx.compose.foundation.background
            import androidx.compose.foundation.layout.*
            import androidx.compose.foundation.shape.RoundedCornerShape
            import androidx.compose.material3.*
            import androidx.compose.runtime.Composable
            import androidx.compose.ui.Alignment
            import androidx.compose.ui.Modifier
            import androidx.compose.ui.draw.shadow
            import androidx.compose.ui.graphics.Color
            import androidx.compose.ui.unit.dp

            class MainActivity : ComponentActivity() {
                override fun onCreate(savedInstanceState: Bundle?) {
                    super.onCreate(savedInstanceState)
                    setContent { App() }
                }
            }

            @Composable
            fun App() {
                val bg = Color(0xFFE6EBF2)
                MaterialTheme {
                    Box(
                        Modifier.fillMaxSize().background(bg).padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            Modifier.fillMaxWidth()
                                .shadow(12.dp, RoundedCornerShape(28.dp))
                                .background(bg, RoundedCornerShape(28.dp))
                                .padding(28.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(${q(appName)}, style = MaterialTheme.typography.headlineMedium)
                            Spacer(Modifier.height(12.dp))
                            Text("Bereit für die erste KI-Änderung.")
                            Spacer(Modifier.height(18.dp))
                            Button(onClick = {}) { Text("Start") }
                        }
                    }
                }
            }
            """.trimIndent()
        )
    )

    private fun q(v: String): String = "\"" + v.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
    private fun xml(v: String): String = "\"" + v.replace("&", "&amp;").replace("\"", "&quot;").replace("<", "&lt;") + "\""
    private fun html(v: String): String = v.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
}
