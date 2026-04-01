package me.saket.telephoto.util

/** Tests annotated with this will run on emulator.wtf with GPU hardware acceleration enabled. */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class RequiresHwAcceleration
