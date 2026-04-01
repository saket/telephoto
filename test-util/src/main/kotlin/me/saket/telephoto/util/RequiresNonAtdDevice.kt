package me.saket.telephoto.util

/**
 * Tests annotated with this will run on emulator.wtf with a non-ATD (Automated Test Device) emulator.
 * ATD images are stripped-down and don't support features like PixelCopy.
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class RequiresNonAtdDevice
