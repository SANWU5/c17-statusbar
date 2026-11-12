// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 aiingjie
package dev.puitheme

/**
 * "Welcome" in every offered language, shown in turn on the first page before any language is chosen (App285), so a
 * reader finds their own language whatever the page starts in. Not translated resources: all of them appear in every
 * locale. The order alternates scripts and keeps identical words apart (Persian/Urdu, Indonesian/Malay).
 */
internal object WelcomeGreetings {
    val WORDS: Map<String, String> = linkedMapOf(
        "en" to "Welcome", "zh-Hans" to "欢迎", "es" to "Bienvenido", "ja" to "ようこそ", "fr" to "Bienvenue",
        "ko" to "환영합니다", "de" to "Willkommen", "ar" to "أهلاً وسهلاً", "pt" to "Bem-vindo", "ru" to "Добро пожаловать",
        "it" to "Benvenuto", "hi" to "स्वागत है", "nl" to "Welkom", "th" to "ยินดีต้อนรับ", "pl" to "Witamy",
        "he" to "ברוכים הבאים", "tr" to "Hoş geldiniz", "vi" to "Chào mừng", "sv" to "Välkommen", "fa" to "خوش آمدید",
        "el" to "Καλώς ήρθατε", "id" to "Selamat datang", "cs" to "Vítejte", "bn" to "স্বাগতম", "ro" to "Bun venit",
        "uk" to "Ласкаво просимо", "ms" to "Selamat datang", "ur" to "خوش آمدید", "sw" to "Karibu", "zh-Hant" to "歡迎",
    )
    val ORDER: List<String> = WORDS.keys.toList()

    /** Where the cycle starts: the language the page is shown in, so the first word is one the reader can read. */
    fun start(tag: String): Int = ORDER.indexOf(tag).coerceAtLeast(0)
}
