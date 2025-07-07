-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
# Keep all annotations (especially runtime annotations like @Inject, etc.)
-keepattributes *Annotation*
# Keep InnerClasses attribute to help with anonymous inner classes reflection
-keepattributes InnerClasses
# Keep EnclosingMethod for inner class reflection
-keepattributes EnclosingMethod
# CRUCIAL for web3j to correctly determine generic type parameters at runtime.
-keepattributes Signature

-dontwarn org.slf4j.impl.StaticLoggerBinder

-dontwarn javax.naming.ldap.LdapName
-dontwarn javax.naming.ldap.Rdn

# Serializable routes
-keep class com.vultisig.wallet.ui.navigation.Route { *; }
-keep class com.vultisig.wallet.ui.navigation.Route$* { *; }
-keep class com.vultisig.wallet.data.models.TssAction { *; }
-keep class com.vultisig.wallet.data.models.SigningLibType { *; }

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

# Umbrella protection
-keep class org.web3j.** { *; }

# Sometimes the previous rule is not enough and some classes are leaked.
# Keep classes that extend TypeReference.
-keep class * extends org.web3j.abi.TypeReference

# Keep Web3j data types. Sometimes the previous rule is not enough and data types are leaked.
-keep class org.web3j.abi.datatypes.** { *; }