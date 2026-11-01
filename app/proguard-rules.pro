# The Claude Java SDK ships its own consumer rules (Jackson, Kotlin reflection).
# These classes are compile-time-only annotations referenced by transitive
# dependencies (OpenTelemetry API) and are never needed at runtime.
-dontwarn com.google.auto.value.AutoValue
