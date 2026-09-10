# Aperture No-Op ships no keep rules, on purpose.
#
# Every method in this module is an empty `inline` function, so a Kotlin caller holds no
# reference to it once the compiler is done. Keeping the public API would pin these stub classes
# into the consumer's release build for nothing. Without a keep rule R8 removes whatever is
# genuinely unreachable, which for a no-op is all of it.
#
# A Java caller does reference the @JvmStatic bridge methods, and R8 keeps reachable code by
# itself, so those consumers are unaffected.
