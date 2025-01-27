# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

-dontwarn org.slf4j.impl.StaticLoggerBinder

-dontwarn javax.naming.ldap.LdapName
-dontwarn javax.naming.ldap.Rdn

# TrustWallet Core rules
-keep class wallet.core.jni.** { *; }
-keep class wallet.core.jni.proto.** { *; }

# Dkls/Schnorr classes used in JNI
-keep class com.silencelaboratories.** { *; }

# Apache Commons Compress
# We don't use these compression algorithms
-dontwarn com.github.luben.zstd.**
-dontwarn org.brotli.dec.**


# Spark
-keep class spark.** { *; }
-keep class spark.servlet.** { *; }
-keep class spark.route.** { *; }
-keep class spark.embeddedserver.jetty.** { *; }
-keep class spark.embeddedserver.** { *; }
-keep class spark.embeddedserver.jetty.websocket.** { *; }

# Jetty
-keep class org.eclipse.jetty.** { *; }
-keep class org.eclipse.jetty.websocket.** { *; }
-keep class org.eclipse.jetty.server.** { *; }

-dontwarn java.awt.Toolkit
-dontwarn java.beans.Introspector
-dontwarn java.lang.instrument.IllegalClassFormatException
-dontwarn java.lang.management.ManagementFactory
-dontwarn java.lang.management.MemoryMXBean
-dontwarn java.lang.management.MemoryUsage
-dontwarn javax.imageio.ImageIO
-dontwarn javax.naming.InitialContext
-dontwarn javax.naming.NamingException
-dontwarn javax.security.auth.login.AppConfigurationEntry$LoginModuleControlFlag
-dontwarn javax.security.auth.login.AppConfigurationEntry
-dontwarn javax.security.auth.login.Configuration
-dontwarn javax.security.auth.login.LoginContext
-dontwarn org.eclipse.jetty.jmx.ObjectMBean
-dontwarn org.ietf.jgss.GSSContext
-dontwarn org.ietf.jgss.GSSCredential
-dontwarn org.ietf.jgss.GSSException
-dontwarn org.ietf.jgss.GSSManager
-dontwarn org.ietf.jgss.GSSName
-dontwarn org.ietf.jgss.Oid


# OkHttp
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }
