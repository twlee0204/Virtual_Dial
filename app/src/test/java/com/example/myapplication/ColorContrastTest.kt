package com.example.myapplication

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.math.pow

/**
 * Pleos 가이드의 대비 기준(4.5:1 이상)을 주간/야간 컬러 토큰 전체에 대해 검증한다.
 * colors.xml 값을 바꾸면 이 테스트가 깨지는지 확인하면 된다.
 */
class ColorContrastTest {

    private fun resFile(dir: String): File {
        val candidates = listOf(
            File("src/main/res/$dir/colors.xml"),
            File("app/src/main/res/$dir/colors.xml")
        )
        return candidates.firstOrNull { it.exists() }
            ?: error("colors.xml not found for $dir (cwd=${File(".").absolutePath})")
    }

    private fun parse(dir: String): Map<String, Int> {
        val regex = Regex("""<color name="([^"]+)">#([0-9A-Fa-f]{6})</color>""")
        return regex.findAll(resFile(dir).readText())
            .associate { it.groupValues[1] to it.groupValues[2].toInt(16) }
    }

    private fun channel(c: Int): Double {
        val s = c / 255.0
        return if (s <= 0.03928) s / 12.92 else ((s + 0.055) / 1.055).pow(2.4)
    }

    private fun luminance(rgb: Int): Double =
        0.2126 * channel((rgb shr 16) and 0xFF) +
            0.7152 * channel((rgb shr 8) and 0xFF) +
            0.0722 * channel(rgb and 0xFF)

    private fun contrast(a: Int, b: Int): Double {
        val la = luminance(a)
        val lb = luminance(b)
        return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
    }

    private val backgrounds = listOf("pleos_bg", "pleos_surface", "pleos_surface_variant", "pleos_popup")
    private val foregrounds = listOf(
        "pleos_text_primary", "pleos_text_secondary", "pleos_accent",
        "dial_blue", "dial_green", "dial_amber", "dial_gray"
    )

    private fun checkTheme(dir: String) {
        val colors = parse(dir)
        val failures = mutableListOf<String>()
        for (bg in backgrounds) for (fg in foregrounds) {
            val ratio = contrast(colors.getValue(fg), colors.getValue(bg))
            if (ratio < 4.5) failures += "$dir: $fg on $bg = ${"%.2f".format(ratio)}"
        }
        val onAccent = contrast(colors.getValue("pleos_on_accent"), colors.getValue("pleos_accent"))
        if (onAccent < 4.5) failures += "$dir: on_accent on accent = ${"%.2f".format(onAccent)}"
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    @Test
    fun dayTheme_meetsContrast() = checkTheme("values")

    @Test
    fun nightTheme_meetsContrast() = checkTheme("values-night")

    @Test
    fun dayAndNight_defineSameTokens() {
        val day = parse("values").keys
        val night = parse("values-night").keys
        assertTrue("missing in night: ${day - night}", night.containsAll(day))
        assertTrue("missing in day: ${night - day}", day.containsAll(night))
    }
}
